package com.jinw.common.domain.graph;

import lombok.Data;

/**
 * 内联汇编定义
 */
@Data
public class AsmDefine extends SourceLoc {

    /**
     * 是否 volatile
     */
    private boolean volatileAsm;

    /**
     * 是否 GNU __asm__
     */
    private boolean gnuStyle;
}