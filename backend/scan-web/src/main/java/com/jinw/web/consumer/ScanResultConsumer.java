package com.jinw.web.consumer;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jinw.common.constant.AdaptStatus;
import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.CodeLine;
import com.jinw.common.domain.CodeRange;
import com.jinw.common.domain.ScanResultMessage;
import com.jinw.common.domain.ScanStartMessage;
import com.jinw.common.domain.graph.*;
import com.jinw.common.utils.ScanFileUtil;
import com.jinw.web.domain.*;
import com.jinw.web.graph.TaskMergeManager;
import com.jinw.web.manager.ScanProgressManager;
import com.jinw.web.mapper.*;
import com.jinw.web.service.impl.statemachine.InMemoryScanStateMachine;
import com.jinw.web.service.impl.statemachine.ScanEvent;
import com.jinw.web.service.impl.statemachine.ScanStateMachine;
import com.jinw.web.service.impl.statemachine.ws.WebSocketMessageDispatcher;
import com.jinw.web.config.exception.BusinessException;
import com.jinw.web.util.FileStorageUtil;
import com.jinw.web.util.InstructionSetAnalyzer;
import com.rabbitmq.client.Channel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@DependsOn("aesConfig")
public class ScanResultConsumer {
    @Autowired
    private BTArchMacroMapper btArchMacroMapper;
    @Autowired
    private BTHeaderMapper btHeaderMapper;
    @Autowired
    private BTInlineAsmMapper btInlineAsmMapper;
    @Autowired
    private BTQuestionInfoMapper btQuestionInfoMapper;
    @Autowired
    private ScanProgressManager scanProgressManager;
    @Autowired
    private BTAppInfoMapper btAppInfoMapper;
    @Autowired
    private BTLibMapper btLibMapper;
    @Autowired
    private ScanStateMachine scanStateMachine;
    @Autowired
    private TaskMergeManager taskMergeManager;

    private Map<String, String> headerMap;
    private Map<String, String> macroMap;
    private Map<String, String> asmMap;
    private Map<String, String> asmArchMap;
    private Map<String, Set<String>> archKeywordMap;
    private Set<String> unsupportedLibIds;
    @Autowired
    private BTQuestionFileInfoMapper bTQuestionFileInfoMapper;
    @Autowired
    private BTArchKeywordMapper btArchKeywordMapper;
    @Autowired
    private BTDependencyMapper bTDependencyMapper;

    @PostConstruct
    public void init() {

        unsupportedLibIds = btLibMapper.selectList(null).stream()
                .filter(lib -> "0".equals(lib.getSupportRiscv()))
                .map(BTLib::getId)
                .collect(Collectors.toSet());

        headerMap = btHeaderMapper.selectList(null)
                .stream()
//                .filter(h -> unsupportedLibIds.contains(h.getLibId()))
                .collect(Collectors.toMap(
                        BTHeader::getId,
                        BTHeader::getHeaderName
                ));

        macroMap = btArchMacroMapper.selectList(null)
                .stream()
                .collect(Collectors.toMap(
                        BTArchMacro::getId,
                        BTArchMacro::getMacroName
                ));

        List<BTInlineAsm> asmList = btInlineAsmMapper.selectList(null);
        asmMap = asmList.stream()
                .collect(Collectors.toMap(
                        BTInlineAsm::getId,
                        BTInlineAsm::getInstructionName
                ));
        asmArchMap = asmList.stream()
                .collect(Collectors.toMap(
                        BTInlineAsm::getId,
                        a -> a.getInstructionSetCode() != null ? a.getInstructionSetCode() : ""
                ));

        archKeywordMap = new HashMap<>();
        for (BTArchKeyword kw : btArchKeywordMapper.selectList(null)) {
            if (kw.getArchCode() != null && kw.getKeyword() != null) {
                archKeywordMap
                        .computeIfAbsent(kw.getArchCode(), k -> new HashSet<>())
                        .add(kw.getKeyword().toLowerCase());
            }
        }
    }

    @RabbitListener(queues = ScanConstant.SCAN_FILE_RESULT_QUEUE,
            containerFactory = "llmListenerContainerFactory")
    public void consume(
            ScanResultMessage result,
            Message message,
            Channel channel
    ) throws Exception {

        long deliveryTag =
                message.getMessageProperties().getDeliveryTag();

        // worker 检测到假死任务时发送失败消息
        if (StringUtils.isNotBlank(result.getError())) {
            log.error("检测任务失败, taskId={}, error={}", result.getTaskId(), result.getError());
            scanStateMachine.fire(result.getTaskId(), ScanEvent.ERROR);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            if (ScanConstant.LANGUAGE_C.equals(result.getLanguage()) || ScanConstant.LANGUAGE_CPP.equals(result.getLanguage())){
                // 一个文件肯定只有一个module，设计成hashmap是为了后续多文件的grapf对象合并
                ProjectCallGraph graph = ProjectCallGraph.convert2graph(result.getGraph());
                taskMergeManager.onResult(result.getTaskId(),graph);
                Map<String, ModuleInfo> modules = graph.getModules();
                Optional<String> firstKey = modules.keySet().stream().findFirst();
                if (!firstKey.isPresent()) {
                    scanStateMachine.updateScanProgress(result.getTaskId(), result.getFilePath(), null, 0);
                    channel.basicAck(deliveryTag, false);

                    if (scanStateMachine.isFinished(result.getTaskId())) {
                        taskMergeManager.onFinished(result.getTaskId());
                        scanStateMachine.fire(result.getTaskId(), ScanEvent.FINISH);
                    }
                    return;
                }
                ModuleInfo moduleInfo = modules.get(firstKey.get());

                IncludeDefineQuestionAssembler assembler = IncludeDefineQuestionAssembler.create(result.getTaskId(), result.getFilePath());
                String fileType = ScanFileUtil.getFileCategory(new File(result.getFilePath())).name();
                Boolean isQuestion = false;
                Boolean isAdapt = false;
                for (IncludeDefine include : moduleInfo.getIncludes()) {
                    // TODO 如果和知识库匹配有问题则加入
                    String ruleId = getKeyByContainingValue(headerMap, include.getText(),ScanConstant.TYPE_INCLUDE, result.getFilePath());
                    if (ruleId != null) {
                        BTHeader btHeader = btHeaderMapper.selectById(ruleId);
                        if (unsupportedLibIds.contains(btHeader.getLibId())){
                            isQuestion = true;
                            BTLib btLib = btLibMapper.selectById(btHeader.getLibId());
                            String remark = StringUtils.isBlank(btHeader.getRemark()) ? btLib.getRemark() : btHeader.getRemark();
                            if ("1".equals(btLib.getSupportAdapt())){
                                isAdapt = true;
                            }
                            assembler.assemble(include, ruleId, fileType, remark, true,btLib.getSupportAdapt());
                        }else {
                            assembler.assemble(include, ruleId, fileType, btHeader.getRemark(), false,"1");
                        }
                    }
                }

                for (MacroDefine macro : moduleInfo.getMacros()) {
                    String ruleId = getKeyByContainingValue(macroMap, macro.getText(), ScanConstant.TYPE_MACRO, result.getFilePath());
                    if (ruleId != null) {
                        BTArchMacro btArchMacro = btArchMacroMapper.selectById(ruleId);
                        isQuestion = true;
                        isAdapt = true;
                        assembler.assemble(macro, ruleId, fileType, btArchMacro.getRemark(),true);
                    }
                }

                for (AsmDefine asm : moduleInfo.getAsms()) {
                    String detectedArch = detectAsmArchitecture(result.getFilePath(), asm.getText());
                    if (isX86OrArm(detectedArch)) {
                        isQuestion = true;
                        isAdapt = true;
                        assembler.assemble(asm, null, fileType, "RISC-V架构不支持", true);
                    } else {
                        assembler.assemble(asm, null, fileType, null, false);
                    }
                }

                List<BTQuestionInfo> questionInfos = assembler.getQuestionInfos();
                long count = questionInfos.stream().filter(btQuestionInfo -> "1".equals(btQuestionInfo.getHitKnowledge())).count();

                scanStateMachine.updateScanProgress(result.getTaskId(), result.getFilePath(), moduleInfo.getCodeStat(), (int) count);

                if (!questionInfos.isEmpty()) {
                    BTQuestionFileInfo btQuestionFileInfo = new BTQuestionFileInfo();
                    btQuestionFileInfo.setFilePath(questionInfos.get(0).getFilePath());
                    btQuestionFileInfo.setTaskId(result.getTaskId());
                    long adaptCount = questionInfos.stream().filter(
                            btQuestionInfo -> "1".equals(btQuestionInfo.getHitKnowledge()) && "1".equals(btQuestionInfo.getHaveAdapt())).count();
                    btQuestionFileInfo.setTotalQuestionCount((int) adaptCount);
                    btQuestionFileInfo.setStatus(AdaptStatus.TODO.name());
                    btQuestionFileInfo.setHaveAdapt(isAdapt ? "1" : "0" );
                    btQuestionFileInfo.setHitKnowledge(isQuestion ? "1" : "0");

                    bTQuestionFileInfoMapper.insert(btQuestionFileInfo);

                    String fileId = btQuestionFileInfo.getId();

                    for (BTQuestionInfo question : questionInfos) {
                        question.setFileId(fileId);
                    }

                    btQuestionInfoMapper.insertBatch(questionInfos);
                }

            }else if (ScanConstant.LANGUAGE_JAVA.equals(result.getLanguage())){
                List<CodeLine> codeLines = result.getCodeLines();
                List<BTQuestionInfo> questionInfos = new ArrayList<>();
                String ext = ScanFileUtil.getFileExtension(new File(result.getFilePath()).getName());
                List<BTDependency> dependencies = bTDependencyMapper.selectList(new QueryWrapper<BTDependency>()
                        .like("FILE_SUFFIX", ext));
                Boolean isAdapt = false;
                for (CodeLine codeLine : codeLines) {
                    for (BTDependency dependency : dependencies) {
                        BTLib btLib = btLibMapper.selectById(dependency.getLibId());
                        Matcher matcher = Pattern.compile(dependency.getRegular()).matcher(codeLine.getText());
                        if (matcher.find()) {
                            BTQuestionInfo info = new BTQuestionInfo();
                            info.setTaskId(result.getTaskId());
                            info.setFilePath(FileStorageUtil.toRelativePath(result.getTaskId() + File.separator + InMemoryScanStateMachine.UNZIP_DIR, result.getFilePath()));
                            info.setText(matcher.group());
                            info.setQuestionType(ScanConstant.TYPE_JAVA);
                            String fileType = ScanFileUtil.getFileCategory(new File(result.getFilePath())).name();
                            info.setFileType(fileType);
                            info.setStartLine(codeLine.getLine() + 1);
                            info.setEndLine(codeLine.getLine() + 1);
                            info.setStartCol(codeLine.getStartCol() + matcher.start());
                            info.setEndCol(codeLine.getStartCol() + matcher.end());
                            info.setRuleId(dependency.getId());
                            info.setStatus(AdaptStatus.TODO.name());
                            info.setHitKnowledge("0".equals(btLib.getSupportRiscv()) ? "1" : "0");
                            if ("0".equals(btLib.getSupportRiscv()) && "1".equals(btLib.getSupportAdapt())){
                                isAdapt = true;
                            }
                            info.setHaveAdapt(btLib.getSupportAdapt());
                            String javaDesc = StringUtils.isBlank(dependency.getRemark()) ? btLib.getRemark() : dependency.getRemark();
                            info.setDescription(StringUtils.isBlank(javaDesc) ? "RISC-V架构不支持" : javaDesc);
                            questionInfos.add(info);
                            break;
                        }
                    }
                }
                long count = questionInfos.stream().filter(btQuestionInfo -> "1".equals(btQuestionInfo.getHitKnowledge())).count();

                scanStateMachine.updateScanProgress(result.getTaskId(), result.getFilePath(), null, (int) count);

                if (!questionInfos.isEmpty()) {
                    BTQuestionFileInfo btQuestionFileInfo = new BTQuestionFileInfo();
                    btQuestionFileInfo.setFilePath(questionInfos.get(0).getFilePath());
                    btQuestionFileInfo.setTaskId(result.getTaskId());
                    long adaptCount = questionInfos.stream().filter(
                            btQuestionInfo -> "1".equals(btQuestionInfo.getHitKnowledge()) && "1".equals(btQuestionInfo.getHaveAdapt())).count();
                    btQuestionFileInfo.setTotalQuestionCount((int) adaptCount);
                    btQuestionFileInfo.setStatus(AdaptStatus.TODO.name());
                    btQuestionFileInfo.setHitKnowledge("1");
                    btQuestionFileInfo.setHaveAdapt(isAdapt ? "1" : "0");
                    bTQuestionFileInfoMapper.insert(btQuestionFileInfo);

                    String fileId = btQuestionFileInfo.getId();

                    for (BTQuestionInfo question : questionInfos) {
                        question.setFileId(fileId);
                    }

                    btQuestionInfoMapper.insertBatch(questionInfos);
                }
            }

            channel.basicAck(deliveryTag, false);

            if (scanStateMachine.isFinished(result.getTaskId())) {
                taskMergeManager.onFinished(result.getTaskId());
                log.info("扫描任务完成: {}", result.getTaskId());
                scanStateMachine.fire(result.getTaskId(), ScanEvent.FINISH);
            }

        } catch (BusinessException e) {
            log.error("业务异常, taskId={}, message={}", result != null ? result.getTaskId() : null, e.getMessage());
            scanStateMachine.fire(result != null ? result.getTaskId() : null, ScanEvent.ERROR);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {

            log.error("消费失败", e);
            log.error("{}",result);
            channel.basicNack(
                    deliveryTag,
                    false,
                    true
            );
        }
    }

    /**
     * 判断字符串中是否包含 map 的某个 value
     * 如果包含，返回对应的 key；否则返回 null
     */
    private String getKeyByContainingValue(Map<String, String> map, String content, String type, String filePath) {
        if (map == null || content == null) {
            return null;
        }

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String value = entry.getValue();
            boolean match = false;
            switch (type) {
                // 头文件：<> + 文本
                case ScanConstant.TYPE_INCLUDE:
                    match = content.contains("<" + value + ">");
                    break;

                // 普通：\bvalue\b 整词匹配
                case ScanConstant.TYPE_MACRO:
                    match = Pattern.compile("(?<![a-zA-Z0-9_])" + Pattern.quote(value) + "(?![a-zA-Z0-9_])")
                            .matcher(content)
                            .find();
                    break;
            }

            if (value != null && match) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 内联汇编架构检测：
     * 1. 优先通过文件名/路径匹配架构关键字；
     * 2. 匹配不上时，使用代码内容判断架构概率。
     */
    private String detectAsmArchitecture(String filePath, String asmText) {
        if (filePath != null && !archKeywordMap.isEmpty()) {
            String lowerPath = filePath.toLowerCase();
            for (Map.Entry<String, Set<String>> entry : archKeywordMap.entrySet()) {
                for (String keyword : entry.getValue()) {
                    if (lowerPath.contains(keyword)) {
                        return entry.getKey();
                    }
                }
            }
        }

        List<String> instructions = InstructionSetAnalyzer.parseAsmTextToList(asmText);
        InstructionSetAnalyzer.AnalysisResult analysisResult = InstructionSetAnalyzer.analyzeInstructionSet(instructions);
        return analysisResult.getArchitecture();
    }

    private boolean isX86OrArm(String arch) {
        if (arch == null) {
            return false;
        }
        String lowerArch = arch.toLowerCase();
        return lowerArch.contains("x86") || lowerArch.contains("arm") || lowerArch.contains("aarch");
    }

    /**
     * 监听单文件开始扫描
     *
     * @param result
     * @param message
     * @param channel
     * @throws Exception
     */
    @RabbitListener(queues = ScanConstant.SCAN_FILE_START_QUEUE)
    public void onStartScanf(
            ScanStartMessage result,
            Message message,
            Channel channel
    ) throws Exception {
        long deliveryTag =
                message.getMessageProperties().getDeliveryTag();
        // 该消息不重要，直接确认即可
        channel.basicAck(deliveryTag, false);

        // 通知前端文件开始扫描
        WebSocketMessageDispatcher dispatcher = scanStateMachine.getDispatcher(result.getTaskId());
        File file = new File(result.getFilePath());
        dispatcher.notifyFileStateToScanf(file.getName(), FileStorageUtil.toRelativePath(result.getTaskId() + File.separator + InMemoryScanStateMachine.UNZIP_DIR, result.getFilePath()));
    }

    private String match(CodeRange range) {

        String text = range.getText();

        switch (range.getQuestionType()) {

            case ScanConstant.TYPE_INCLUDE:

                for (Map.Entry<String, String> entry : headerMap.entrySet()) {

                    if (text.contains(entry.getValue())) {
                        return entry.getKey();
                    }
                }

                break;

            case ScanConstant.TYPE_MACRO:

                for (Map.Entry<String, String> entry : macroMap.entrySet()) {

                    if (text.contains(entry.getValue())) {
                        return entry.getKey();
                    }
                }

                break;

            case ScanConstant.TYPE_ASM:

                for (Map.Entry<String, String> entry : asmMap.entrySet()) {

                    if (text.contains(entry.getValue())) {
                        return entry.getKey();
                    }
                }

                break;
        }

        return null;
    }
}