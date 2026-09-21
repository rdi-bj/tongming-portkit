package com.jinw.web.enums;

public enum IssueLevel {
    NONE("默认"),
    SEVERE("严重"),
    MODERATE("中等"),
    MILD("轻微");

    private final String desc;

    IssueLevel(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}