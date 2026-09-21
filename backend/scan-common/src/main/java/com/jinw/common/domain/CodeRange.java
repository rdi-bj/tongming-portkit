package com.jinw.common.domain;

import lombok.Data;

@Data
public class CodeRange {

    private int startLine;
    private int endLine;
    private int startCol;
    private int endCol;
    private int startByte;
    private int endByte;
    private String text;
    private String questionType;
    private String fileType;

    @Override
    public String toString() {
        return "CodeRange{" +
                "startLine=" + startLine +
                ", endLine=" + endLine +
                ", startCol=" + startCol +
                ", endCol=" + endCol +
                ", startByte=" + startByte +
                ", endByte=" + endByte +
                ", text='" + text + '\'' +
                ", questionType='" + questionType + '\'' +
                ", fileType='" + fileType + '\'' +
                '}';
    }
}