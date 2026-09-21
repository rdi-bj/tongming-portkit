package com.jinw.common.domain;

import lombok.Data;

@Data
public class MigrationChange {

    private String oldLine;

    private String newLine;

    private String type;

    private String oldCode;

    private String newCode;

    private String description;
}