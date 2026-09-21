package com.jinw.common.domain;

import lombok.Data;

@Data
public class VerifyResultMessage {

    private String taskId;

    private String fileId;

    private String filePath;

    private String questionId;

    private Boolean isQuestion;

    private int waitNum;

    private int acNum;

    private int errorNum;

    private int totalNum;

    private String error;

}