package com.jinw.web.service.impl;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinw.common.constant.FileCategory;
import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.FileCategoryStat;
import com.jinw.common.domain.graph.CallEdge;
import com.jinw.common.utils.ScanFileUtil;
import com.jinw.mq.task.ScanQueuePublisher;
import com.jinw.web.config.exception.BusinessException;
import com.jinw.web.domain.*;
import com.jinw.web.domain.response.AsmInfoResponse;
import com.jinw.web.domain.response.FrameworkPortraitResponse;
import com.jinw.web.enums.IssueLevel;
import com.jinw.web.enums.NodeType;
import com.jinw.web.manager.ScanProgressManager;
import com.jinw.web.mapper.*;
import com.jinw.web.service.WebService;
import com.jinw.web.task.ScanQueueScheduler;
import com.jinw.web.service.impl.statemachine.InMemoryScanStateMachine;
import com.jinw.web.service.impl.statemachine.ScanState;
import com.jinw.web.service.impl.statemachine.ScanStateMachine;
import com.jinw.web.util.FileStorageUtil;
import com.jinw.web.util.LlmConfigValidator;
import com.jinw.web.util.WordReportGenerator;
import com.jinw.web.util.ZipValidatorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletResponse;

import static com.jinw.web.service.impl.statemachine.InMemoryScanStateMachine.UNZIP_DIR;

@Service
@Slf4j
public class WebServiceImpl implements WebService {

    @Autowired
    private ScanQueuePublisher scanQueuePublisher;
    @Autowired
    private BTAppInfoMapper btAppInfoMapper;
    @Autowired
    private ScanProgressManager scanProgressManager;
    @Autowired
    private BTQuestionInfoMapper btQuestionInfoMapper;
    @Autowired
    private BTLibMapper btLibMapper;
    @Autowired
    private BTHeaderMapper btHeaderMapper;
    @Autowired
    private BTInlineAsmMapper btInlineAsmMapper;
    @Autowired
    private BTArchMacroMapper btArchMacroMapper;
    @Autowired
    private ScanStateMachine scanStateMachine;
    @Autowired
    private ScanQueueScheduler scanQueueScheduler;
    @Autowired
    private BTLlmConfigMapper btLlmConfigMapper;

    /**
     * 去掉 parentPath 前缀
     *
     * @param fullPath
     * @param parentPath
     * @return
     */
    private static String removeParentPrefix(String fullPath, String parentPath) {
        if (File.separator.equals(parentPath)) {
            return fullPath;
        }

        String normalizedFull = Paths.get(fullPath).normalize().toString();
        String normalizedParent = Paths.get(parentPath).normalize().toString();

        if (!normalizedFull.startsWith(normalizedParent)) {
            return normalizedFull;
        }

        String relative = normalizedFull.substring(normalizedParent.length());
        return Paths.get(relative).normalize().toString();
    }

    @Override
    public String startScan(MultipartFile multipartFile, String appId, String scanType) {
        // 深度检测依赖大模型，启动前先校验启用的模型是否可用
        if ("1".equals(scanType)) {
            BTLlmConfig btLlmConfig = btLlmConfigMapper.selectOne(new QueryWrapper<BTLlmConfig>().eq("ENABLED", "1"));
            LlmConfigValidator.validate(btLlmConfig);
        }

        // 初始化检测任务
        String taskId = UUID.randomUUID().toString();

        // 存储源码包
        String relativePath = taskId + File.separator + multipartFile.getOriginalFilename();
        String calcMd5 = null;
        try {
            File savedFile = FileStorageUtil.save(multipartFile, relativePath);
            ZipValidatorUtil.validate(savedFile);
            calcMd5 = calcMd5(savedFile);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new BusinessException("源码包存储失败");
        }

        // 初始化状态机，任务进入排队状态（CREATED），由定时任务按 scanTime 顺序启动
        scanStateMachine.init(appId, taskId, relativePath, scanType, calcMd5);

        // 主动触发一次队列处理：若当前无任务在执行则立即开始扫描，否则进入排队
        scanQueueScheduler.processScanQueue();

        return taskId;
    }

    private String calcMd5(File file) throws IOException {
        try (InputStream inputStream = new java.io.FileInputStream(file)) {
            return DigestUtils.md5DigestAsHex(inputStream);
        }
    }

    @Override
    public List<BTAppInfo> scanQueueList() {
        return btAppInfoMapper.selectList(new QueryWrapper<BTAppInfo>()
                .in("STATUS",
                        ScanState.CREATED.name(),
                        ScanState.UNZIP.name(),
                        ScanState.SCANNING.name(),
                        ScanState.AI_VERIFY.name())
                .orderByAsc("SCAN_TIME"));
    }

    @Override
    public List<BTQuestionInfo> questionList(String taskId, String isAdapt) {
        QueryWrapper<BTQuestionInfo> queryWrapper = new QueryWrapper<BTQuestionInfo>()
                .eq("TASK_ID", taskId)
                .eq("HIT_KNOWLEDGE", "1");
        if ("1".equals(isAdapt)){
            queryWrapper.eq("HAVE_ADAPT","1");
        }
        List<BTQuestionInfo> questionInfos = btQuestionInfoMapper.selectList(queryWrapper);
        return questionInfos;
    }

    @Override
    public ScanProgress progress(String taskId) {
        BTAppInfo btAppInfo = btAppInfoMapper.selectOne(new QueryWrapper<BTAppInfo>().eq("TASK_ID", taskId));

        ScanProgress progress =
                scanProgressManager.get(taskId);

        if (btAppInfo == null && progress == null) {
            throw new BusinessException("任务不存在");
        }

        if (ScanConstant.SCAN_STATUS_FINISHED.equals(btAppInfo.getStatus())) {
            ScanProgress scanProgress = new ScanProgress();
            scanProgress.setStatus(ScanConstant.SCAN_STATUS_FINISHED);
            return scanProgress;
        }

        return progress;
    }

    @Override
    public FrameworkPortraitResponse frameworkPortrait(String taskId) {

        List<BTQuestionInfo> questionInfos =
                btQuestionInfoMapper.selectList(
                        new QueryWrapper<BTQuestionInfo>()
                                .eq("TASK_ID", taskId)
                                .eq("AI_VERIFY_STATUS","1")
                                .eq("HIT_KNOWLEDGE","1")
                );

        Map<String, Integer> questionTypeStats = new LinkedHashMap<>();

        questionTypeStats.put(ScanConstant.TYPE_INCLUDE, 0);
        questionTypeStats.put(ScanConstant.TYPE_MACRO, 0);
        questionTypeStats.put(ScanConstant.TYPE_ASM, 0);

        Map<String, Integer> fileTypeStats = new LinkedHashMap<>();

        for (FileCategory category : FileCategory.values()) {
            fileTypeStats.put(category.name(), 0);
        }

        Set<String> files = new HashSet<>();

        for (BTQuestionInfo info : questionInfos) {
            if (info.getRuleId() == null) {
                continue;
            }
            String type = info.getQuestionType();
            questionTypeStats.put(
                    type,
                    questionTypeStats.getOrDefault(type, 0) + 1
            );

            if (!files.contains(info.getFilePath())) {
                String fileType = info.getFileType();
                fileTypeStats.put(
                        fileType,
                        fileTypeStats.getOrDefault(fileType, 0) + 1
                );
                files.add(info.getFilePath());
            }
        }

        BTAppInfo btAppInfo = btAppInfoMapper.selectOne(new QueryWrapper<BTAppInfo>().eq("TASK_ID", taskId));
        Map<String, FileCategoryStat> map =
                JSONUtil.toBean(
                        btAppInfo.getFileInfo(),
                        new TypeReference<Map<String, FileCategoryStat>>() {
                        },
                        false
                );
//        fileTypeStats.put(
//                FileCategory.ASSEMBLY.name(),
//                fileTypeStats.getOrDefault(FileCategory.ASSEMBLY.name(), 0) + map.get(FileCategory.ASSEMBLY.name()).getFileCount()
//        );
        // 返回
        FrameworkPortraitResponse frameworkPortraitResponse = new FrameworkPortraitResponse();
        frameworkPortraitResponse.setQuestionType(questionTypeStats);
        frameworkPortraitResponse.setFileTypeStats(fileTypeStats);

        return frameworkPortraitResponse;
    }

    @Override
    public List<BTLib> libList(String taskId) {
        List<BTQuestionInfo> questionInfos =
                btQuestionInfoMapper.selectList(
                        new QueryWrapper<BTQuestionInfo>()
                                .eq("TASK_ID", taskId)
                );

        Set<String> ruleIds = questionInfos.stream()
                .filter(questionInfo -> ScanConstant.TYPE_INCLUDE.equals(questionInfo.getQuestionType()))
                .map(BTQuestionInfo::getRuleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<BTLib> libs = ruleIds.stream()
                .map(btHeaderMapper::selectById)
                .filter(Objects::nonNull)
                .map(header -> btLibMapper.selectById(header.getLibId()))
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                BTLib::getId,
                                lib -> lib,
                                (a, b) -> a
                        ),
                        map -> new ArrayList<>(map.values())
                ));
        // 命中的问题 hitKnowledge=0 表示支持 RISC-V
        Set<String> supportedLibIds = questionInfos.stream()
                .filter(info -> "0".equals(info.getHitKnowledge()))
                .map(BTQuestionInfo::getRuleId)
                .filter(Objects::nonNull)
                .map(btHeaderMapper::selectById)
                .filter(Objects::nonNull)
                .map(BTHeader::getLibId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (BTLib lib : libs) {
            boolean exists = btHeaderMapper.existsSupportRiscvZero(lib.getId());
            if (exists) {
                lib.setSupportRiscv("0");
            }

            if (supportedLibIds.contains(lib.getId())) {
                lib.setSupportRiscv("1");
            }
        }
        return libs;
    }

    @Override
    public AsmInfoResponse asmList(String taskId) {
        List<BTQuestionInfo> questionInfos =
                btQuestionInfoMapper.selectList(
                        new QueryWrapper<BTQuestionInfo>()
                                .eq("TASK_ID", taskId)
                                .eq("QUESTION_TYPE", ScanConstant.TYPE_ASM)
                );

//        List<BTInlineAsm> asms = new ArrayList<>();
//
//        for (BTQuestionInfo questionInfo : questionInfos) {
//            BTInlineAsm btInlineAsm = btInlineAsmMapper.selectById(questionInfo.getRuleId());
//            asms.add(btInlineAsm);
//        }

        Map<String, Long> instructionSetCount =
                questionInfos.stream()
                        .filter(Objects::nonNull)
                        .map(BTQuestionInfo::getFramework)
                        .filter(StrUtil::isNotBlank)
                        .collect(Collectors.groupingBy(
                                s -> s,
                                Collectors.counting()
                        ));

        Map<String, Long> questionTotalInfo = new HashMap<>();
        BTAppInfo btAppInfo = btAppInfoMapper.selectOne(new QueryWrapper<BTAppInfo>().eq("TASK_ID", taskId));
        Map<String, FileCategoryStat> map =
                JSONUtil.toBean(
                        btAppInfo.getFileInfo(),
                        new TypeReference<Map<String, FileCategoryStat>>() {
                        },
                        false
                );

        FileCategoryStat assemblyStat =
                map.getOrDefault(FileCategory.ASSEMBLY.name(), new FileCategoryStat());

        long asmFileCount = assemblyStat.getFileCount();
        questionTotalInfo.put("total", asmFileCount + questionInfos.size());
        questionTotalInfo.put("inlineAsm", (long) questionInfos.size());
        questionTotalInfo.put("asmFile", asmFileCount);

        AsmInfoResponse asmInfoResponse = new AsmInfoResponse();
        asmInfoResponse.setAsmInfo(questionTotalInfo);
        asmInfoResponse.setInstructionSetCodeCount(instructionSetCount);
        return asmInfoResponse;
    }

    @Override
    public List<DirectoryNodeDTO> listTree(String taskId, String parentPath, String isAdapt) {
        if (parentPath == null) {
            parentPath = "";
        }
        // 去掉第一个/
        if (parentPath.startsWith(File.separator)) {
            parentPath = parentPath.substring(1);
        }
        // 源码根目录
        Path unzipPath = FileStorageUtil.resolve(taskId, InMemoryScanStateMachine.UNZIP_DIR);
        // 进入相对目录
        Path path = FileStorageUtil.resolveByBase(unzipPath, parentPath);
        File dir = path.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            return Collections.emptyList();
        }

        List<DirectoryNodeDTO> result = new ArrayList<>();

        // 扫描硬盘（只扫一层）
        File[] children = dir.listFiles();
        if (children == null) {
            return result;
        }

        // 一次性查询该目录下所有问题文件
        QueryWrapper<BTQuestionInfo> queryWrapper = new QueryWrapper<BTQuestionInfo>()
                .eq("TASK_ID", taskId)
                .eq("AI_VERIFY_STATUS","1")
                .eq("HIT_KNOWLEDGE","1");
        if ("1".equals(isAdapt)){
            queryWrapper.eq("HAVE_ADAPT","1");
        }
        // 如果是根路径，直接查询全部，不需要前缀匹配，提升查询效率
        if (!"".equals(parentPath)) {
            // 上面已经判断了路径的合法性，这里不会造成注入问题
            queryWrapper = queryWrapper.like("FILE_PATH", parentPath + "/%");
        }
        List<BTQuestionInfo> questionInfos =
                btQuestionInfoMapper.selectList(queryWrapper);

        // 统计parentPath路径下第一层文件（包括文件夹和文件）所包含的问题数量
        Map<String, Integer> counter = new HashMap<>();

        String finalParentPath = parentPath;
        questionInfos.forEach(questionInfo -> {
            String _path = questionInfo.getFilePath();
            if (_path == null || _path.isEmpty()) {
                return;
            }

            // 获取文件相对于parentPath的相对路径（二重相对路径）
            String relativePath = removeParentPrefix(_path, finalParentPath);
            if (relativePath.isEmpty()) {
                return;
            }

            Path normalized = Paths.get(relativePath);

            // 如果没有层级目录则直接结束
            if (normalized.getNameCount() == 0) {
                return;
            }

            String firstLevel = normalized.getName(0).toString();
            counter.merge(firstLevel, 1, Integer::sum);
        });

        // 遍历硬盘上的文件，整理成返回信息
        for (File child : children) {
            String fileName = child.getName();
            Integer errCount = counter.getOrDefault(
                    fileName,
                    0
            );
            IssueLevel level = errCount > 0 ? IssueLevel.SEVERE : IssueLevel.NONE;
            result.add(new DirectoryNodeDTO(
                    fileName,
                    // 确保路径准确
                    Paths.get((
                            "".equals(parentPath) ? "" : parentPath + File.separator
                    ) + fileName).normalize().toString(),
                    child.isDirectory() ? NodeType.DIR : NodeType.FILE,
                    level,
                    errCount,
                    // 如果是文件夹则计算子节点数量
                    child.isDirectory() ? child.listFiles().length : 0
            ));
        }


        return result;
    }

    @Override
    public String getFileText(String taskId, String filePath) {
        String absolutePath = FileStorageUtil.toAbsolutePath(taskId + File.separator + UNZIP_DIR, filePath);
        return ScanFileUtil.readFileContent(absolutePath);
    }

    @Override
    public List<BTQuestionInfo> getFileQuestions(String filePath, String taskId, String isAdapt) {
        QueryWrapper<BTQuestionInfo> queryWrapper = new QueryWrapper<BTQuestionInfo>()
                .eq("TASK_ID", taskId)
                .eq("FILE_PATH", filePath)
                .eq("AI_VERIFY_STATUS", "1")
                .eq("HIT_KNOWLEDGE", "1");
        if ("1".equals(isAdapt)) {
            queryWrapper.eq("HAVE_ADAPT", "1");
        }
        return btQuestionInfoMapper.selectList(queryWrapper);
    }

    @Override
    public List<CallEdge> getProjectGraph(String taskId) {
        try{
            ObjectMapper objectMapper = new ObjectMapper();
            String filePath = FileStorageUtil.toAbsolutePath(taskId, ScanConstant.GRAPH_FILE_NAME);
            List<CallEdge> callEdges = objectMapper.readValue(new File(filePath),
                    new com.fasterxml.jackson.core.type.TypeReference<List<CallEdge>>() {
                    });
            for (CallEdge callEdge : callEdges) {
                callEdge.setFile(FileStorageUtil.toRelativePath(
                        taskId + File.separator + InMemoryScanStateMachine.UNZIP_DIR,callEdge.getFile()));
                if (callEdge.getCallee().getDefinedInFile() !=null){
                    callEdge.getCallee().setDefinedInFile(
                            FileStorageUtil.toRelativePath(taskId + File.separator + InMemoryScanStateMachine.UNZIP_DIR,callEdge.getCallee().getDefinedInFile()));
                }

                if (callEdge.getCaller().getDefinedInFile() != null){
                    callEdge.getCaller().setDefinedInFile(
                            FileStorageUtil.toRelativePath(taskId + File.separator + InMemoryScanStateMachine.UNZIP_DIR,callEdge.getCaller().getDefinedInFile()));
                }
            }
            return callEdges;
        }catch (Exception e){
            e.printStackTrace();
            throw new BusinessException("读取图错误");
        }
    }

    @Override
    public List<BTQuestionInfo> getMakeFileInfo(String taskId) {
        List<BTQuestionInfo> btQuestionInfos = btQuestionInfoMapper.selectList(new QueryWrapper<BTQuestionInfo>()
                .eq("FILE_TYPE", FileCategory.BUILD)
                .eq("TASK_ID",taskId));
        return btQuestionInfos;
    }

    @Override
    public ReportData getReportInfo(String id) {
        BTAppInfo btAppInfo = btAppInfoMapper.selectById(id);
        if (btAppInfo == null) {
            throw new BusinessException("项目不存在");
        }
        return buildReportData(btAppInfo, "0");
    }

    @Override
    public void downloadReport(String id, String status, HttpServletResponse response) throws IOException {
        BTAppInfo btAppInfo = btAppInfoMapper.selectById(id);
        if (btAppInfo == null) {
            throw new BusinessException("项目不存在");
        }
        ReportData reportData = buildReportData(btAppInfo, status);
        String name = StrUtil.blankToDefault(btAppInfo.getName(), "RISC-V");
        String fileName = URLEncoder.encode(name + "跨平台移植检测报告.docx", StandardCharsets.UTF_8.name())
                .replaceAll("\\+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName);
        try {
            WordReportGenerator.generate(response.getOutputStream(), reportData);
        } catch (Exception e) {
            log.error("生成报告失败, id={}", id, e);
            throw new IOException("生成报告失败", e);
        }
    }

    private ReportData buildReportData(BTAppInfo btAppInfo, String status) {
        String taskId = btAppInfo.getTaskId();
        EnumMap<FileCategory, FileCategoryStat> fileInfoMap = StrUtil.isBlank(btAppInfo.getFileInfo())
                ? new EnumMap<>(FileCategory.class)
                : parse(btAppInfo.getFileInfo());

        boolean aiAdapted = "1".equals(status);

        // 已确认的问题（AI 验证通过且命中知识库），AI 适配报告仅保留支持适配的问题
        QueryWrapper<BTQuestionInfo> issueWrapper = new QueryWrapper<BTQuestionInfo>()
                .eq("TASK_ID", taskId)
                .eq("AI_VERIFY_STATUS", "1")
                .eq("HIT_KNOWLEDGE", "1");
        if (aiAdapted) {
            issueWrapper.eq("HAVE_ADAPT", "1");
        }
        List<BTQuestionInfo> issues = btQuestionInfoMapper.selectList(issueWrapper);

        int headerIssues = countByQuestionType(issues, ScanConstant.TYPE_INCLUDE);
        int macroIssues = countByQuestionType(issues, ScanConstant.TYPE_MACRO);
        int asmIssues = countByQuestionType(issues, ScanConstant.TYPE_ASM);
        int otherIssues = countByQuestionType(issues, ScanConstant.TYPE_JAVA);
        int buildIssues = countByFileType(issues, FileCategory.BUILD.name());
        int totalIssues = issues.size();
        int coveredRules = (int) issues.stream()
                .map(BTQuestionInfo::getRuleId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        FileCategoryStat srcStat = mergeStats(fileInfoMap, FileCategory.C_SOURCE, FileCategory.CPP_SOURCE);
        FileCategoryStat asmStat = mergeStats(fileInfoMap, FileCategory.ASSEMBLY);
        FileCategoryStat buildStat = mergeStats(fileInfoMap, FileCategory.BUILD);
        FileCategoryStat otherStat = mergeStats(fileInfoMap, FileCategory.OTHER);

        int totalFiles = fileInfoMap.values().stream().mapToInt(FileCategoryStat::getFileCount).sum();
        long totalLines = fileInfoMap.values().stream().mapToLong(FileCategoryStat::getTotalLines).sum();
        long codeLines = fileInfoMap.values().stream().mapToLong(FileCategoryStat::getCodeLines).sum();

        String name = StrUtil.blankToDefault(btAppInfo.getName(), "");

        ReportData reportData = new ReportData();
        // 基本信息
        reportData.setSubjectName(name);
        reportData.setReportDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月")));
        reportData.setPlatformName(name);
        reportData.setLanguage(languageOf(fileInfoMap));
        String systemName = StrUtil.blankToDefault(btAppInfo.getSystemName(), "");
        String systemVersion = StrUtil.blankToDefault(btAppInfo.getSystemVersion(), "");
        reportData.setSystemName(StrUtil.isNotBlank(systemVersion) ? systemName + " " + systemVersion : systemName);
        reportData.setBuildSystem(buildSystemOf(taskId));
        reportData.setTotalFiles(totalFiles + " 个");
        reportData.setTotalLines(totalLines + " 行");

        // 架构相关文件检测
        reportData.setSrcFileCount(srcStat.getFileCount());
        reportData.setSrcFileLines((int) srcStat.getCodeLines());
        reportData.setAsmFileCount(asmStat.getFileCount());
        reportData.setAsmFileLines((int) asmStat.getCodeLines());
        reportData.setBuildFileCount(buildStat.getFileCount());
        reportData.setBuildFileLines((int) buildStat.getCodeLines());
        reportData.setOtherFileCount(otherStat.getFileCount());
        reportData.setOtherFileLines((int) otherStat.getCodeLines());

        // 源码文件检测统计
        reportData.setScannedFiles(totalFiles);
        reportData.setCodeLines((int) codeLines);
        reportData.setModifyFiles(valueOf(btAppInfo.getTotalQuestionFile()));
        reportData.setModifyLocations(valueOf(btAppInfo.getTotalQuestion()));
        reportData.setCoveredRules(coveredRules);
        reportData.setHeaderIssues(headerIssues);
        reportData.setHeaderPercent(percent(headerIssues, totalIssues));
        reportData.setMacroIssues(macroIssues);
        reportData.setMacroPercent(percent(macroIssues, totalIssues));
        reportData.setAsmIssues(asmIssues);
        reportData.setAsmPercent(percent(asmIssues, totalIssues));
        reportData.setOtherIssues(otherIssues);
        reportData.setOtherPercent(percent(otherIssues, totalIssues));

        // 架构相关代码核心数据
        reportData.setCoreSrcFiles(srcStat.getFileCount());
        reportData.setCoreSrcLines((int) srcStat.getCodeLines());
        reportData.setCoreSrcPercent(percent(srcStat.getCodeLines(), totalLines));
        reportData.setCoreAsmFiles(asmStat.getFileCount());
        reportData.setCoreAsmLines((int) asmStat.getCodeLines());
        reportData.setCoreAsmPercent(percent(asmStat.getCodeLines(), totalLines));
        reportData.setCoreBuildFiles(buildStat.getFileCount());
        reportData.setCoreBuildLines((int) buildStat.getCodeLines());
        reportData.setCoreBuildPercent(percent(buildStat.getCodeLines(), totalLines));

        // 检测结论
        reportData.setConclusionArchIssues(headerIssues + macroIssues);
        reportData.setConclusionAsmIssues(asmIssues);
        reportData.setConclusionBuildIssues(buildIssues);
        reportData.setConclusionOtherIssues(otherIssues);

        // 问题明细（附件一：架构相关问题检测明细，单表不分文件类别）
        reportData.setAiAdapted(aiAdapted);
        reportData.setIssues(buildIssueItems(issues));

        return reportData;
    }

    private static List<IssueItem> buildIssueItems(List<BTQuestionInfo> issues) {
        List<IssueItem> items = new ArrayList<>();
        int index = 0;
        for (BTQuestionInfo info : issues) {
            index++;
            items.add(new IssueItem(
                    index,
                    StrUtil.blankToDefault(info.getDescription(), "RISC-V架构不支持"),
                    StrUtil.blankToDefault(info.getAdaptResult(), ""),
                    StrUtil.blankToDefault(info.getFilePath(), ""),
                    info.getStartLine()
            ));
        }
        return items;
    }

    private static int countByQuestionType(List<BTQuestionInfo> issues, String questionType) {
        return (int) issues.stream()
                .filter(info -> questionType.equals(info.getQuestionType()))
                .count();
    }

    private static int countByFileType(List<BTQuestionInfo> issues, String fileType) {
        return (int) issues.stream()
                .filter(info -> fileType.equals(info.getFileType()))
                .count();
    }

    private static FileCategoryStat mergeStats(EnumMap<FileCategory, FileCategoryStat> map, FileCategory... categories) {
        FileCategoryStat total = new FileCategoryStat();
        for (FileCategory category : categories) {
            FileCategoryStat stat = map.get(category);
            if (stat == null) {
                continue;
            }
            total.setFileCount(total.getFileCount() + stat.getFileCount());
            total.setCodeLines(total.getCodeLines() + stat.getCodeLines());
            total.setTotalLines(total.getTotalLines() + stat.getTotalLines());
            if (stat.getFilePath() != null) {
                for (String path : stat.getFilePath()) {
                    total.addFilePath(path);
                }
            }
        }
        return total;
    }

    private static String percent(long part, long total) {
        if (total <= 0) {
            return "0.00%";
        }
        return String.format("%.2f%%", part * 100.0 / total);
    }

    private static String languageOf(EnumMap<FileCategory, FileCategoryStat> map) {
        List<String> languages = new ArrayList<>();
        if (hasFiles(map, FileCategory.C_SOURCE, FileCategory.CPP_SOURCE,
                FileCategory.C_HEADER, FileCategory.CPP_HEADER)) {
            languages.add("C/C++");
        }
        if (hasFiles(map, FileCategory.ASSEMBLY)) {
            languages.add("汇编");
        }
        if (hasFiles(map, FileCategory.SCRIPT)) {
            languages.add("脚本");
        }
        return languages.isEmpty() ? null : String.join(" / ", languages);
    }

    private String buildSystemOf(String taskId) {
        List<String> systems = getMakeFileInfo(taskId).stream()
                .map(BTQuestionInfo::getQuestionType)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        return systems.isEmpty() ? null : String.join(" / ", systems);
    }

    private static boolean hasFiles(EnumMap<FileCategory, FileCategoryStat> map, FileCategory... categories) {
        for (FileCategory category : categories) {
            FileCategoryStat stat = map.get(category);
            if (stat != null && stat.getFileCount() > 0) {
                return true;
            }
        }
        return false;
    }

    /** 核心文件目录：按问题记录的 fileType 区分，取其 filePath 所在目录，去重后最多 10 个。 */
    private static String dirsOf(List<BTQuestionInfo> issues, String... fileTypes) {
        Set<String> fileTypeSet = new HashSet<>(Arrays.asList(fileTypes));
        return issues.stream()
                .filter(info -> fileTypeSet.contains(info.getFileType()))
                .map(BTQuestionInfo::getFilePath)
                .filter(StrUtil::isNotBlank)
                .map(WebServiceImpl::parentDirOf)
                .distinct()
                .limit(10)
                .collect(Collectors.joining("、"));
    }

    private static String parentDirOf(String path) {
        String p = path.replace('\\', '/');
        int idx = p.lastIndexOf('/');
        return idx >= 0 ? p.substring(0, idx) : p;
    }

    private static int valueOf(Integer value) {
        return value == null ? 0 : value;
    }

    public static EnumMap<FileCategory, FileCategoryStat> parse(String jsonStr) {
        // 1. Hutool 解析成 JSONObject（实现 Map<String, Object>）
        JSONObject root = JSONUtil.parseObj(jsonStr);

        // 2. 创建 EnumMap
        EnumMap<FileCategory, FileCategoryStat> stats =
                new EnumMap<>(FileCategory.class);

        // 3. 遍历 JSON 的每一个 key
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            String key = entry.getKey();
            JSONObject value = (JSONObject) entry.getValue();

            // String -> Enum
            FileCategory category = FileCategory.valueOf(key);

            // JSONObject -> POJO
            FileCategoryStat stat = JSONUtil.toBean(value, FileCategoryStat.class);

            stats.put(category, stat);
        }
        return stats;
    }

}
