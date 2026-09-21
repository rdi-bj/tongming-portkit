package com.jinw.common.domain;

import lombok.Data;

@Data
public class ScanTaskMessage {

    private String taskId;

    private String filePath;

    private String language;

}