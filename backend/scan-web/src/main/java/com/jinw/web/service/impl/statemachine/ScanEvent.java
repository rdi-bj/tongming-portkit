package com.jinw.web.service.impl.statemachine;

/**
 * 扫描任务事件枚举
 *
 * <p>用于驱动状态机流转，表示扫描过程中发生的各类事件。
 * 每个事件都会触发状态从当前节点向目标节点迁移。
 */
public enum ScanEvent {

    /**
     * 开始解压
     */
    UNZIP,
    /**
     * 扫描开始执行
     */
    START,
    /**
     * 扫描正常结束
     */
    FINISH,
    /**
     * AI验证开始
     */
    AI_START,
    /**
     * AI验证结束
     */
    AI_FINISH,
    /**
     * 扫描过程/AI验证过程发生异常
     */
    ERROR
}