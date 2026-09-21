package com.jinw.common.domain.graph;

import lombok.Data;

import java.io.Serializable;

/**
 * 源码位置（Tree‑sitter 原生映射）
 */
@Data
public abstract class SourceLoc implements Serializable {

    /**
     * 起始行（1-based）
     */
    private int startLine;

    /**
     * 结束行（1-based）
     */
    private int endLine;

    /**
     * 起始列（0-based）
     */
    private int startCol;

    /**
     * 结束列（0-based）
     */
    private int endCol;

    /**
     * 起始字节偏移
     */
    private int startByte;

    /**
     * 结束字节偏移
     */
    private int endByte;

    /**
     * 原始文本
     */
    private String text;
}