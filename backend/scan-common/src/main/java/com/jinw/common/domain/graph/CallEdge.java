package com.jinw.common.domain.graph;

import lombok.Data;

/**
 * 函数调用边
 */
@Data
public class CallEdge {

    /**
     * 调用方函数
     */
    private FunctionKey caller;

    /**
     * 被调函数
     */
    private FunctionKey callee;

    /**
     * 调用所在文件
     */
    private String file;

    /**
     * 调用所在行号
     */
    private int line;

    /**
     * 是否间接调用（函数指针 / dlsym）
     */
    private boolean indirect;

    /**
     * 是否 unresolved
     */
    private boolean unresolved;
}