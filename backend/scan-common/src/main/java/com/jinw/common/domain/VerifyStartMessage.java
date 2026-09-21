package com.jinw.common.domain;

import lombok.Data;

@Data
public class VerifyStartMessage {

    private String taskId;

    private String filePath;

    private int totalNum;
}