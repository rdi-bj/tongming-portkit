package com.jinw.web.service.impl.statemachine;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jinw.common.constant.FileCategory;
import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.*;
import com.jinw.common.utils.BuildSystemDetector;
import com.jinw.common.utils.ScanFileUtil;
import com.jinw.mq.task.ScanQueuePublisher;
import com.jinw.mq.task.VerifyQueuePublisher;
import com.jinw.web.config.exception.BusinessException;
import com.jinw.web.domain.BTAppInfo;
import com.jinw.web.domain.BTLlmConfig;
import com.jinw.web.domain.BTQuestionFileInfo;
import com.jinw.web.domain.BTQuestionInfo;
import com.jinw.web.mapper.BTAppInfoMapper;
import com.jinw.web.mapper.BTLlmConfigMapper;
import com.jinw.web.mapper.BTQuestionFileInfoMapper;
import com.jinw.web.mapper.BTQuestionInfoMapper;
import com.jinw.web.service.BTAppInfoService;
import com.jinw.web.service.impl.statemachine.ws.WebSocketMessageDispatcher;
import com.jinw.web.service.impl.statemachine.ws.WebSocketMessageDispatcherFactory;
import com.jinw.web.util.FileStorageUtil;
import com.jinw.web.util.LlmConfigValidator;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.progress.ProgressMonitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于内存的状态机
 */
@Component
@Slf4j
public class InMemoryScanStateMachine implements ScanStateMachine {

    /**
     * 解压文件夹名称
     */
    public final static String UNZIP_DIR = "unzip";

    /**
     * 状态
     */
    private final Map<String, ScanState> stateStore = new ConcurrentHashMap<>();
    /**
     * 进度详细信息
     */
    private final Map<String, ScanProgress> progressMap = new ConcurrentHashMap<>();
    /**
     * 每个 taskId 对应的 WebSocket 消息分发器
     */
    private final Map<String, WebSocketMessageDispatcher> wsDispatcherMap = new ConcurrentHashMap<>();

    /**
     * taskId -> 应用id
     */
    private final Map<String, String> appIdMap = new ConcurrentHashMap<>();

    /**
     * 扫描执行槽位：同一时刻只允许一个任务执行，用于实现队列串行
     */
    private final AtomicBoolean scanRunning = new AtomicBoolean(false);

    @Autowired
    private BTAppInfoService service;
    @Autowired
    private ScanQueuePublisher scanQueuePublisher;
    @Autowired
    private WebSocketMessageDispatcherFactory factory;
    @Autowired
    private BTAppInfoMapper bTAppInfoMapper;
    @Autowired
    private BTQuestionFileInfoMapper bTQuestionFileInfoMapper;
    @Autowired
    private BTQuestionInfoMapper bTQuestionInfoMapper;
    @Autowired
    private VerifyQueuePublisher verifyQueuePublisher;
    @Autowired
    private BTLlmConfigMapper bTLlmConfigMapper;

    @Override
    public void init(String appId, String taskId, String relativePath, String scanType, String calcMd5) {
        BTAppInfo app = service.getById(appId);
        if (app == null) {
            throw new BusinessException("项目不存在");
        }

        appIdMap.put(taskId, appId);
        stateStore.put(taskId, ScanState.CREATED);
        // 初始化扫描进度
        ScanProgress progress = new ScanProgress();
        progress.setStartTime(System.currentTimeMillis());
        progress.setRelativePath(relativePath);
        progressMap.put(taskId, progress);

        // ====== 新增：创建 WebSocket 消息分发器 ======
        WebSocketMessageDispatcher dispatcher = factory.getOrCreate(taskId);
        wsDispatcherMap.put(taskId, dispatcher);
        // 立即通知客户端：任务已创建
        dispatcher.notifyStateChange(ScanState.CREATED);

        app.setStatus(ScanState.CREATED.name());
        app.setFilePath(relativePath);
        app.setFileName(FileStorageUtil.resolve(relativePath).toFile().getName());
        app.setTaskId(taskId);
        // 这里重新评估了就直接重置
        app.setSessionId(null);
        app.setScanType(scanType);
        app.setFileMd5(calcMd5);
        app.setScanTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        service.update(app);

        log.debug("taskId={} 状态机初始化完成，WebSocket服务已启动", taskId);
    }

    @Override
    public synchronized void fire(String taskId, ScanEvent event) {
        ScanState current = stateStore.get(taskId);
        String appId = appIdMap.get(taskId);
        BTAppInfo btAppInfo = bTAppInfoMapper.selectById(appId);
        boolean isDepthScan = "1".equals(btAppInfo.getScanType());
        if (event.name().equals(ScanEvent.FINISH.name()) && isDepthScan){
            // 查看是否有问题需要进行AI验证
            Long questionCount = bTQuestionInfoMapper.selectCount(new QueryWrapper<BTQuestionInfo>()
                    .eq("TASK_ID", taskId)
                    .eq("HIT_KNOWLEDGE","1"));
            isDepthScan = questionCount > 0;
            if (isDepthScan){
                event = ScanEvent.AI_START;
                log.info("有问题需要进行AI验证");
            }
        }
        ScanState next = doTransition(current, event, isDepthScan);

        if (next == null) {
            throw new IllegalStateException(
                    "非法状态转移: " + current + " -> " + event);
        }

        ScanProgress progress = progressMap.get(taskId);

        // 获取该 taskId 的 WebSocket 分发器
        WebSocketMessageDispatcher dispatcher = wsDispatcherMap.get(taskId);
        // ====== 状态变化：立即通知客户端 ======
        if (dispatcher != null) {
            dispatcher.notifyStateChange(next);
        }

        if (next == ScanState.UNZIP) {
            // 执行解压
            unzip(taskId, progress);
        } else if (next == ScanState.SCANNING) {
            // 开始扫描
            Map<FileCategory, FileCategoryStat> stats = new EnumMap<>(FileCategory.class);
            File unzipDir = FileStorageUtil.resolve(taskId, UNZIP_DIR).toFile();
            List<BTQuestionInfo> btQuestionInfos = new ArrayList<>();
            for (File file : FileUtil.loopFiles(unzipDir)) {
                if (!file.isFile()) {
                    continue;
                }

                // 后缀名获取
                String ext = ScanFileUtil.getFileExtension(file.getName());

                FileCategory fileCategory = ScanFileUtil.getFileCategory(file);
                FileCategoryStat stat =
                        stats.computeIfAbsent(
                                fileCategory,
                                k -> new FileCategoryStat()
                        );
                stat.setFileCount(stat.getFileCount() + 1);
                stat.addFilePath(
                        FileStorageUtil.toRelativePath(taskId + File.separator + InMemoryScanStateMachine.UNZIP_DIR, file.getAbsolutePath()));
                LineStat lineStat = ScanFileUtil.countLines(file);
                stat.setBlankLines(stat.getBlankLines() + lineStat.getBlankLines());
                stat.setCodeLines(stat.getCodeLines() + lineStat.getCodeLines());
                stat.setCommentLines(stat.getCommentLines() + lineStat.getCommentLines());
                stat.setTotalLines(stat.getTotalLines() + lineStat.getTotalLines());

                if (FileCategory.BUILD.equals(fileCategory)){
                    Path path = Paths.get(file.getAbsolutePath());
                    try{
                        BuildSystemInfo detect = BuildSystemDetector.detect(path);
                        String name = detect.system.name();
                        BTQuestionInfo btQuestionInfo = new BTQuestionInfo();
                        btQuestionInfo.setFilePath(
                                FileStorageUtil.toRelativePath(taskId + File.separator + InMemoryScanStateMachine.UNZIP_DIR, file.getAbsolutePath()));
                        btQuestionInfo.setHitKnowledge("0");
                        btQuestionInfo.setFileType(FileCategory.BUILD.name());
                        btQuestionInfo.setQuestionType(name);
                        btQuestionInfo.setTaskId(taskId);
                        btQuestionInfos.add(btQuestionInfo);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }

                String language = null;
                if (ScanConstant.LANGUAGE_C_SUFFIX.contains(ext)) {
                    language = ScanConstant.LANGUAGE_C;
                } else if (ScanConstant.LANGUAGE_CPP_SUFFIX.contains(ext)) {
                    language = ScanConstant.LANGUAGE_CPP;
                } else if ("pom.xml".equals(file.getName()) || ScanConstant.LANGUAGE_JAVA_SUFFIX.contains(ext)){
                    language = ScanConstant.LANGUAGE_JAVA;
                }

                if (language != null && file.length() != 0) {
                    ScanTaskMessage message = new ScanTaskMessage();
                    message.setTaskId(taskId);
                    message.setFilePath(file.getAbsolutePath());
                    message.setLanguage(language);
                    scanQueuePublisher.sendFileTask(message);
                    // 发送socket
                    dispatcher.notifyFileStateToWait(file.getName(), FileStorageUtil.toRelativePath(taskId + File.separator + InMemoryScanStateMachine.UNZIP_DIR, file.getAbsolutePath()));
                }
            }

            btAppInfo.setFileInfo(JSONUtil.toJsonStr(stats));
            bTAppInfoMapper.updateById(btAppInfo);

            if (btQuestionInfos.size() > 0){
                bTQuestionInfoMapper.insertBatch(btQuestionInfos);
            }

        } else if (next == ScanState.AI_VERIFY){
            List<BTQuestionFileInfo> btQuestionFileInfos =
                    bTQuestionFileInfoMapper.selectList(
                            new QueryWrapper<BTQuestionFileInfo>()
                                    .eq("TASK_ID", taskId)
                                    .eq("HIT_KNOWLEDGE","1"));
            BTLlmConfig btLlmConfig = bTLlmConfigMapper.selectOne(new QueryWrapper<BTLlmConfig>().eq("ENABLED", "1"));
            LlmConfigValidator.validate(btLlmConfig);
            LlmConfig llmConfig = new LlmConfig();
            BeanUtil.copyProperties(btLlmConfig, llmConfig);
            ScanProgress scanProgress = progressMap.get(taskId);
            scanProgress.setTotalVerifyFileCount(btQuestionFileInfos.size());
            for (BTQuestionFileInfo btQuestionFileInfo : btQuestionFileInfos) {
                List<BTQuestionInfo> btQuestionInfos =
                        bTQuestionInfoMapper.selectList(
                                new QueryWrapper<BTQuestionInfo>()
                                        .eq("FILE_ID", btQuestionFileInfo.getId())
                                        .eq("HIT_KNOWLEDGE","1"));
                List<LlmQuestionInfo> llmQuestionInfos =
                        btQuestionInfos.stream()
                                .sorted(Comparator.comparingInt(BTQuestionInfo::getStartLine))
                                .map(q -> {
                                    LlmQuestionInfo llmQuestionInfo = new LlmQuestionInfo();
                                    BeanUtil.copyProperties(q,llmQuestionInfo);
                                    return llmQuestionInfo;
                                }).toList();
                VerifyTaskMessage verifyTaskMessage = new VerifyTaskMessage();
                verifyTaskMessage.setTaskId(taskId);
                String absolutePath = FileStorageUtil.toAbsolutePath(taskId + File.separator + InMemoryScanStateMachine.UNZIP_DIR, btQuestionFileInfo.getFilePath());
                File file = new File(absolutePath);
                verifyTaskMessage.setFilePath(absolutePath);
                verifyTaskMessage.setQuestionInfos(llmQuestionInfos);
                verifyTaskMessage.setLlmConfig(llmConfig);
                verifyTaskMessage.setFileId(btQuestionFileInfo.getId());
                verifyTaskMessage.setProjectPath(FileStorageUtil.toAbsolutePath("", taskId));
                verifyQueuePublisher.sendFileTask(verifyTaskMessage);
                dispatcher.notifyFileStateToVerifyWait(file.getName(), btQuestionFileInfo.getFilePath(), llmQuestionInfos.size());
            }
        } else if (next == ScanState.SUCCESS || next == ScanState.FAILED) {
            // 结束时记录结束时间
            progress.setEndTime(System.currentTimeMillis());

            if (next == ScanState.SUCCESS){
                // 统计所需AI适配的文件和问题数量
                int questionFileCount = bTQuestionFileInfoMapper.selectList(new QueryWrapper<BTQuestionFileInfo>()
                        .eq("TASK_ID", taskId)
                        .eq("AI_VERIFY_STATUS","1")
                        .eq("HIT_KNOWLEDGE","1")
                        .eq("HAVE_ADAPT","1")).size();
                int questionCount = bTQuestionInfoMapper.selectList(new QueryWrapper<BTQuestionInfo>()
                        .eq("TASK_ID", taskId)
                        .eq("AI_VERIFY_STATUS","1")
                        .eq("HIT_KNOWLEDGE","1")
                        .eq("HAVE_ADAPT","1")).size();
                btAppInfo.setTotalQuestion(questionCount);
                btAppInfo.setTotalQuestionFile(questionFileCount);
                bTAppInfoMapper.updateById(btAppInfo);
            }

            // ====== 任务结束：发送最终消息并销毁 WebSocket 连接 ======
            if (dispatcher != null) {
                dispatcher.notifyTaskFinished(next);
            }
            wsDispatcherMap.remove(taskId);
        }

        stateStore.put(taskId, next);
        updateDb(taskId, next);

        // 任务结束（成功/失败）释放执行槽位，允许下一个排队任务启动
        if (next == ScanState.SUCCESS || next == ScanState.FAILED) {
            scanRunning.set(false);
        }
    }

    @Override
    public WebSocketMessageDispatcher getDispatcher(String taskId) {
        return wsDispatcherMap.get(taskId);
    }

    @Override
    public void initScanProgress(String taskId, long total) {
        WebSocketMessageDispatcher dispatcher = wsDispatcherMap.get(taskId);

        // 进度更新
        ScanProgress scanProgress = progressMap.get(taskId);
        scanProgress.setTotal(total);
        dispatcher.notifyProgress(scanProgress);
    }

    @Override
    public void updateScanProgress(String taskId, String path, CodeStat codeStat, int errorNum) {
        WebSocketMessageDispatcher dispatcher = wsDispatcherMap.get(taskId);

        // 进度更新
        ScanProgress scanProgress = progressMap.get(taskId);
        if (dispatcher == null){
            log.info("taskId:{}无dispatcher",taskId);
            return;
        }
        if (scanProgress == null) {
            log.info("taskId:{}无scanProgress",taskId);
            return;
        }
        scanProgress.addCompleted();
        if (codeStat != null) {
            scanProgress.addTotalRow(codeStat.getTotalLines());
        }
        dispatcher.notifyProgress(scanProgress);

        // 文件状态更新
        String fileName = new File(path).getName();
        dispatcher.notifyFileStateToComplete(
                fileName, FileStorageUtil.toRelativePath(taskId + File.separator + UNZIP_DIR, path), errorNum
        );
    }

    @Override
    public Boolean isFinished(String taskId) {
        ScanProgress scanProgress = progressMap.get(taskId);
        return scanProgress != null ? scanProgress.getCompleted().get() == scanProgress.getTotal() : true;
    }

    @Override
    public void updateVerifyProgress(String taskId, String path, int errorNum, int acNum, int totalNum, int waitNum) {
        WebSocketMessageDispatcher dispatcher = wsDispatcherMap.get(taskId);

        // 进度更新
        ScanProgress scanProgress = progressMap.get(taskId);
        if (scanProgress == null) {
            return;
        }
        if (waitNum == 0){
            scanProgress.addVerifyCompleted();
        }
        dispatcher.notifyProgress(scanProgress);

        // 文件状态更新
        String fileName = new File(path).getName();
        dispatcher.notifyFileStateToVerifyCompleteOrVerify(
                fileName, FileStorageUtil.toRelativePath(taskId + File.separator + UNZIP_DIR, path), acNum, errorNum, totalNum, waitNum
        );
    }

    @Override
    public Boolean isVerifyFinished(String taskId) {
        ScanProgress scanProgress = progressMap.get(taskId);
        return scanProgress != null ? scanProgress.getAlreadyVerifyFileCount().get() == scanProgress.getTotalVerifyFileCount() : true;
    }

    private void unzip(String taskId, ScanProgress progress) {
        WebSocketMessageDispatcher dispatcher = wsDispatcherMap.get(taskId);

        Path zipPath = FileStorageUtil.resolve(progress.getRelativePath());
        File unzipDir = new File(zipPath.getParent().toFile(), UNZIP_DIR);

        if (!unzipDir.exists()) {
            unzipDir.mkdirs();
        }

        if (!tryUnzipWithZip4j(zipPath, unzipDir, progress, dispatcher)) {
            if (!tryUnzipWithSystemCmd(zipPath, unzipDir, dispatcher)) {
                fire(taskId, ScanEvent.ERROR);
                throw new BusinessException("源码包解析失败：文件可能并非标准 ZIP（如 RAR/7z/tar.gz 改后缀），或存在加密（AES）、分卷、损坏、下载不完整等情况。请解压后使用 WinRAR / 7-Zip 以 ZIP 格式（不加密或 ZipCrypto）重新打包上传。");
            }
        }
    }

    private boolean tryUnzipWithZip4j(Path zipPath, File unzipDir, ScanProgress progress, WebSocketMessageDispatcher dispatcher) {
        try {
            ZipFile zipFile = new ZipFile(zipPath.toFile());
            ProgressMonitor monitor = zipFile.getProgressMonitor();

            zipFile.setRunInThread(true);
            zipFile.extractAll(unzipDir.getAbsolutePath());

            while (!monitor.getState().equals(ProgressMonitor.State.READY)) {
                int percent = monitor.getPercentDone();
                String currentFile = monitor.getFileName();
                progress.setUnzipPercent(percent);
                log.debug("进度: " + percent + "%，当前文件: " + currentFile);

                if (dispatcher != null) {
                    dispatcher.notifyProgress(progress);
                }

                Thread.sleep(100);
            }

            if (monitor.getResult().equals(ProgressMonitor.Result.SUCCESS)) {
                log.debug("zip4j 解压完成");
                return true;
            } else if (monitor.getResult().equals(ProgressMonitor.Result.ERROR)) {
                throw monitor.getException();
            }
        } catch (Exception e) {
            log.warn("zip4j 解压失败，尝试系统 unzip 命令: " + e.getMessage());
            return false;
        }
        return false;
    }

    private boolean tryUnzipWithSystemCmd(Path zipPath, File unzipDir, WebSocketMessageDispatcher dispatcher) {
        try {
            String zipFilePath = zipPath.toAbsolutePath().toString();
            String unzipDirPath = unzipDir.getAbsolutePath();
            log.info("开始使用系统 unzip 命令解压: " + zipFilePath);

            ProcessBuilder pb = new ProcessBuilder("unzip", "-o", zipFilePath, "-d", unzipDirPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.waitFor();
            log.info("系统 unzip 命令结束, exitCode=" + exitCode);

            if (exitCode != 0) {
                log.warn("系统 unzip 命令失败: " + output);
                return false;
            }

            log.debug("系统 unzip 命令解压完成");
            return true;
        } catch (Exception e) {
            log.warn("系统 unzip 命令执行异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 定义状态流转逻辑
     *
     * @param current
     * @param event
     * @param isDepthScan
     * @return
     */
    private ScanState doTransition(ScanState current, ScanEvent event, boolean isDepthScan) {
        // 任何状态下发生 ERROR，直接失败
        if (event == ScanEvent.ERROR) {
            return ScanState.FAILED;
        }

        switch (current) {
            case CREATED:
                return event == ScanEvent.UNZIP ? ScanState.UNZIP : null;
            case UNZIP:
                return event == ScanEvent.START ? ScanState.SCANNING : null;
            case SCANNING:
                if (!isDepthScan){
                    return event == ScanEvent.FINISH ? ScanState.SUCCESS : null;
                }else {
                    return event == ScanEvent.AI_START ? ScanState.AI_VERIFY : null;
                }
            case AI_VERIFY:
                return event == ScanEvent.AI_FINISH ? ScanState.SUCCESS : null;
            default:
                return null;
        }
    }

    private void updateDb(String taskId, ScanState state) {
        String appId = appIdMap.get(taskId);
        BTAppInfo appInfo = service.getById(appId);
        appInfo.setStatus(state.name());
        service.update(appInfo);
    }

    @Override
    public boolean tryAcquireScanSlot() {
        return scanRunning.compareAndSet(false, true);
    }

    @Override
    public void releaseScanSlot() {
        scanRunning.set(false);
    }
}