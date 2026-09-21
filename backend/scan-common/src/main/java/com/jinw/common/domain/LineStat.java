package com.jinw.common.domain;

import lombok.Data;

@Data
public class LineStat {

    private long codeLines;

    private long commentLines;

    private long blankLines;

    private long totalLines;
}