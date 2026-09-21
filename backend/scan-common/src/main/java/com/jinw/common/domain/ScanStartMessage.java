package com.jinw.common.domain;

import lombok.Data;

@Data
public class ScanStartMessage {

    private String taskId;

    private String filePath;

}