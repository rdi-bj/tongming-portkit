package com.jinw.web.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jinw.web.domain.BTAppInfo;
import com.jinw.web.executor.AsyncScanExecutor;
import com.jinw.web.mapper.BTAppInfoMapper;
import com.jinw.web.service.impl.statemachine.ScanState;
import com.jinw.web.service.impl.statemachine.ScanStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 扫描队列调度器
 * <p>
 * startScan 只负责存储源码包和初始化任务状态（CREATED），
 * 真正的扫描执行由本定时任务按 scanTime 先后顺序逐个启动，形成队列效果。
 * 同一时刻只允许一个任务执行，通过 {@link ScanStateMachine#tryAcquireScanSlot()} 抢占执行槽位。
 */
@Component
@Slf4j
public class ScanQueueScheduler {

    @Autowired
    private BTAppInfoMapper btAppInfoMapper;
    @Autowired
    private AsyncScanExecutor asyncScanExecutor;
    @Autowired
    private ScanStateMachine scanStateMachine;

    /**
     * 定时轮询扫描队列，按 scanTime 先后顺序启动排队中的任务
     * <p>
     * 该方法可能同时被定时任务与 startScan 调用，通过执行槽位保证同一时刻只启动一个任务。
     */
    @Scheduled(fixedDelayString = "${cscan.scan.queue-poll-interval-ms:5000}")
    public void processScanQueue() {
        // 抢占执行槽位：已有任务在执行则直接返回（新任务进入排队）
        if (!scanStateMachine.tryAcquireScanSlot()) {
            return;
        }

        BTAppInfo pending;
        try {
            // 取出 scanTime 最早的排队任务
            pending = btAppInfoMapper.selectOne(new QueryWrapper<BTAppInfo>()
                    .eq("STATUS", ScanState.CREATED.name())
                    .orderByAsc("SCAN_TIME")
                    .last("LIMIT 1"));
        } catch (Exception e) {
            log.error("查询排队任务失败", e);
            scanStateMachine.releaseScanSlot();
            throw e;
        }

        if (pending == null || pending.getTaskId() == null) {
            // 无排队任务，释放槽位
            scanStateMachine.releaseScanSlot();
            return;
        }

        try {
            log.info("启动扫描任务 taskId={} appId={}", pending.getTaskId(), pending.getId());
            asyncScanExecutor.executeAsyncScan(pending.getTaskId());
        } catch (Exception e) {
            log.error("启动扫描任务失败 taskId={}", pending.getTaskId(), e);
            scanStateMachine.releaseScanSlot();
        }
    }
}
