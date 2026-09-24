package com.jinw.common.domain;

import lombok.Data;

import java.util.List;

@Data
public class MigrationResult {

    private String fileType;

    private String riskLevel;

    private String summary;

    private List<MigrationChange> changes;
}