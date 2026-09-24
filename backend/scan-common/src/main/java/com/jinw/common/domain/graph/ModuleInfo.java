package com.jinw.common.domain.graph;

import com.jinw.common.domain.CodeStat;
import lombok.Data;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * 文件（编译单元）
 */
@Data
public class ModuleInfo implements Serializable {

    /**
     * 文件路径
     */
    private String file;

    /**
     * include 的头文件
     */
    private Set<IncludeDefine> includes = new HashSet<>();
    /**
     * 宏
     */
    private Set<MacroDefine> macros = new HashSet<>();
    /**
     * 内联汇编
     */
    private Set<AsmDefine> asms = new HashSet<>();
    /**
     * 本文件定义的函数
     */
    private Set<FunctionKey> functions = new HashSet<>();
    /**
     * 本文件调用的外部函数
     */
    private Set<FunctionKey> externalCalls = new HashSet<>();
    /**
     * 文件级代码统计
     */
    private CodeStat codeStat;
}