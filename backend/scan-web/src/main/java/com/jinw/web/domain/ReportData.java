package com.jinw.web.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReportData {
    private String subjectName = "OpenBLAS";
    private String reportDate = "2026年7月";
    private String platformName = "OpenBLAS";

    // 基本信息表
    private String language = "C/C++、汇编";
    private String systemName;
    private String buildSystem;
    private String totalFiles = "7300 个";
    private String totalLines = "3708602 行";

    // 架构相关文件检测
    private int srcFileCount = 48;
    private int srcFileLines;
    private int asmFileCount = 8;
    private int asmFileLines;
    private int buildFileCount = 12;
    private int buildFileLines;
    private int otherFileCount;
    private int otherFileLines;

    // 源码文件检测统计
    private int scannedFiles = 7300;
    private int codeLines = 3708602;
    private int modifyFiles = 48;
    private int modifyLocations = 274;
    private int coveredRules = 20;
    private int headerIssues = 39;
    private String headerPercent = "14.23%";
    private int macroIssues = 222;
    private String macroPercent = "81.02%";
    private int asmIssues = 8;
    private String asmPercent = "2.92%";
    private int otherIssues;
    private String otherPercent;

    // 架构相关代码核心数据
    private int coreSrcFiles = 48;
    private int coreSrcLines = 274;
    private String coreSrcPercent = "0.74%";
    private int coreAsmFiles = 8;
    private int coreAsmLines = 23;
    private String coreAsmPercent = "0.06%";
    private int coreBuildFiles = 12;
    private int coreBuildLines = 45;
    private String coreBuildPercent = "0.12%";

    // 检测结论
    private int conclusionArchIssues = 12;
    private int conclusionAsmIssues = 3;
    private int conclusionBuildIssues = 5;
    private int conclusionOtherIssues;

    // 是否为 AI 适配后生成的报告
    private boolean aiAdapted;

    // 问题明细（单表，不区分文件类别）
    private List<IssueItem> issues;
}