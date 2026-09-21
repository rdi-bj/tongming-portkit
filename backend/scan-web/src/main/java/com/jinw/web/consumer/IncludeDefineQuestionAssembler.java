package com.jinw.web.consumer;

import cn.hutool.core.util.StrUtil;
import com.jinw.common.constant.AdaptStatus;
import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.graph.AsmDefine;
import com.jinw.common.domain.graph.IncludeDefine;
import com.jinw.common.domain.graph.MacroDefine;
import com.jinw.common.domain.graph.SourceLoc;
import com.jinw.web.domain.BTQuestionInfo;
import com.jinw.web.service.impl.statemachine.InMemoryScanStateMachine;
import com.jinw.web.util.FileStorageUtil;
import com.jinw.web.util.InstructionSetAnalyzer;
import lombok.Getter;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class IncludeDefineQuestionAssembler {
    private String taskId;
    private String filePath;

    @Getter
    private List<BTQuestionInfo> questionInfos;

    private IncludeDefineQuestionAssembler() {
    }

    /**
     * 创建组装器
     *
     * @param taskId   所属任务id
     * @param filePath 所属文件路径（支持相对路径/绝对路径）
     * @return
     */
    public static IncludeDefineQuestionAssembler create(String taskId, String filePath) {
        IncludeDefineQuestionAssembler questionInfoBuilder = new IncludeDefineQuestionAssembler();
        questionInfoBuilder.questionInfos = new ArrayList<>();
        questionInfoBuilder.taskId = taskId;

        // 存储到数据库的路径，应该为相对于解压路径的目录
        Path path = Paths.get(filePath).normalize();
        // 构造需要去掉的前缀：taskId/unzip
        Path unzipPrefix = Paths.get(taskId, InMemoryScanStateMachine.UNZIP_DIR).normalize();
        if (path.startsWith(unzipPrefix)) {
            path = unzipPrefix.relativize(path);
        }
        questionInfoBuilder.filePath = path.toString();
        return questionInfoBuilder;
    }

    public IncludeDefineQuestionAssembler assemble(IncludeDefine includeDefine, String ruleId, String fileType, String description, Boolean hitFlag, String haveAdapt) {
        BTQuestionInfo info = buildBTQuestionInfo(includeDefine, ruleId);

        // TODO 继续补充
        info.setQuestionType(ScanConstant.TYPE_INCLUDE);
        info.setFileType(fileType);
        info.setDescription(StrUtil.blankToDefault(description, "RISC-V架构不支持"));
        info.setHitKnowledge(hitFlag ? "1" : "0");
        info.setHaveAdapt(haveAdapt);
        return this;
    }

    public IncludeDefineQuestionAssembler assemble(MacroDefine macroDefine, String ruleId, String fileType, String description, Boolean hitFlag) {
        BTQuestionInfo info = buildBTQuestionInfo(macroDefine, ruleId);

        // TODO 继续补充
        info.setQuestionType(ScanConstant.TYPE_MACRO);
        info.setFileType(fileType);
        info.setDescription(StrUtil.blankToDefault(description, "RISC-V架构不支持"));
        info.setHitKnowledge(hitFlag ? "1" : "0");
        info.setHaveAdapt(hitFlag ? "1" : "0");
        return this;
    }

    public IncludeDefineQuestionAssembler assemble(AsmDefine asmDefine, String ruleId, String fileType, String description, Boolean hitFlag) {
        BTQuestionInfo info = buildBTQuestionInfo(asmDefine, ruleId);

        // TODO 继续补充
        info.setQuestionType(ScanConstant.TYPE_ASM);
        info.setFileType(fileType);
        info.setDescription(StrUtil.blankToDefault(description, "RISC-V架构不支持"));
        info.setHitKnowledge(hitFlag ? "1" : "0");
        List<String> asmTextToList = InstructionSetAnalyzer.parseAsmTextToList(info.getText());
        InstructionSetAnalyzer.AnalysisResult analysisResult = InstructionSetAnalyzer.analyzeInstructionSet(asmTextToList);
        info.setFramework(analysisResult.getArchitecture());
        info.setHaveAdapt(hitFlag ? "1" : "0");
        return this;
    }

    /**
     * 通用源码位置信息记录
     *
     * @param sourceLoc
     * @return
     */
    public BTQuestionInfo buildBTQuestionInfo(SourceLoc sourceLoc, String ruleId) {
        BTQuestionInfo info = new BTQuestionInfo();
        info.setTaskId(this.taskId);

        // 如果输入的是绝对路径需要处理成相对路径
        info.setFilePath(FileStorageUtil.toRelativePath(this.taskId + File.separator + InMemoryScanStateMachine.UNZIP_DIR, this.filePath));

        info.setText(sourceLoc.getText());
        info.setStartLine(sourceLoc.getStartLine());
        info.setEndLine(sourceLoc.getEndLine());
        info.setStartCol(sourceLoc.getStartCol());
        info.setEndCol(sourceLoc.getEndCol());
        info.setRuleId(ruleId);
        info.setStatus(AdaptStatus.TODO.name());

        this.questionInfos.add(info);
        return info;
    }
}
