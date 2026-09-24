package com.jinw.common.domain;

import lombok.Data;

@Data
public class AbortTaskMessage {

    private String taskId;

    private String sessionId;

}
