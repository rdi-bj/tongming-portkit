package com.jinw.common.domain;

import com.jinw.common.domain.graph.dto.ProjectCallGraphDTO;
import lombok.Data;

import java.util.List;

@Data
public class ScanResultMessage {

    private String taskId;

    private String filePath;

    private ProjectCallGraphDTO graph;

    private String language;

    private List<CodeLine> codeLines;

    private String error;
}