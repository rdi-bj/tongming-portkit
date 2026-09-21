package com.jinw.web.service.impl.statemachine;

public enum ScanState {

    CREATED("已创建"),
    UNZIP("解压中"),
    SCANNING("扫描中"),
    AI_VERIFY("AI验证中"),
    SUCCESS("扫描成功"),
    FAILED("扫描失败");

    private final String desc;

    ScanState(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}