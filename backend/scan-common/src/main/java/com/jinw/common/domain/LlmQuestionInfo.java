package com.jinw.common.domain;

import lombok.Data;

@Data
public class LlmQuestionInfo {

    private String id;

    private String text;

    private String description;

    private int startLine;

    private int endLine;
}
