package com.jinw.web.service.impl.statemachine;

import com.jinw.common.domain.CodeStat;
import com.jinw.web.service.impl.statemachine.ws.WebSocketMessageDispatcher;

public interface ScanStateMachine {

    /**
     * 初始化任务状态
     *
     * @param appId        应用id
     * @param taskId       任务id
     * @param relativePath 源代码文件的相对路径
     * @param scanType
     * @param calcMd5
     */
    void init(String appId, String taskId, String relativePath, String scanType, String calcMd5);

    /**
     * 触发状态事件
     */
    void fire(String taskId, ScanEvent event);

    /**
     * 获取任务的websocket句柄
     *
     * @param taskId
     * @return
     */
    WebSocketMessageDispatcher getDispatcher(String taskId);

    /**
     * 初始化总文件数
     *
     * @param total
     */
    void initScanProgress(String taskId, long total);

    /**
     * 更新进度
     *
     * @param taskId
     * @param path
     * @param codeStat
     * @param errorNum
     */
    void updateScanProgress(
            String taskId,
            String path,
            CodeStat codeStat,
            int errorNum);

    Boolean isFinished(
            String taskId
    );

    void updateVerifyProgress(
            String taskId,
            String path,
            int errorNum,
            int acNum,
            int totalNum,
            int waitNum);

    Boolean isVerifyFinished(
            String taskId
    );

    /**
     * 尝试抢占扫描执行槽位（队列串行），成功返回 true
     */
    boolean tryAcquireScanSlot();

    /**
     * 释放扫描执行槽位
     */
    void releaseScanSlot();
}