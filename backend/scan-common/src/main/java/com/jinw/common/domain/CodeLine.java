package com.jinw.common.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 非注释代码段的位置信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CodeLine {

    /**
     * 行号（0-based）
     */
    private int line;

    /**
     * 起始列（0-based，含）
     */
    private int startCol;

    /**
     * 结束列（0-based，不含）
     */
    private int endCol;

    /**
     * 该位置的有效文本
     */
    private String text;

}
