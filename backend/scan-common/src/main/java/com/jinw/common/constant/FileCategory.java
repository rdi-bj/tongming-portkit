package com.jinw.common.constant;

/**
 * 文件分类枚举
 * <p>
 * 用于标识扫描到的文件类型，便于按语言或用途进行统计与处理。
 * </p>
 */
public enum FileCategory {

    /**
     * C 语言源文件
     * <p>
     * 示例：.c
     * </p>
     */
    C_SOURCE,

    /**
     * C++ 源文件
     * <p>
     * 示例：.cpp / .cc / .cxx
     * </p>
     */
    CPP_SOURCE,

    /**
     * C 头文件
     * <p>
     * 示例：.h
     * </p>
     */
    C_HEADER,

    /**
     * C++ 头文件
     * <p>
     * 示例：.hpp / .hh
     * </p>
     */
    CPP_HEADER,

    /**
     * 汇编源文件
     * <p>
     * 示例：.asm / .s
     * </p>
     */
    ASSEMBLY,

    /**
     * 脚本文件
     * <p>
     * 示例：.sh / .bat / .py / .js
     * </p>
     */
    SCRIPT,

    /**
     * 文档类文件
     * <p>
     * 示例：.md / .txt / .doc / .pdf
     * </p>
     */
    DOCUMENT,

    /**
     * 配置文件
     * <p>
     * 示例：.properties / .yml / .json / .xml（配置型）
     * </p>
     */
    CONFIG,

    /**
     * 构建相关文件
     * <p>
     * 示例：Makefile / CMakeLists.txt / pom.xml / build.gradle
     * </p>
     */
    BUILD,

    /**
     * 无法归类的其他文件
     * <p>
     * 作为兜底分类使用
     * </p>
     */
    OTHER
}