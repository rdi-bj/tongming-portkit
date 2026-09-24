package com.jinw.worker.task;

import com.jinw.common.domain.ScanResultMessage;
import com.jinw.mq.task.ScanQueuePublisher;
import com.jinw.worker.llm.utils.OpenCodeLLMClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 垃圾回收定时任务：检测假死的扫描任务并标记为失败
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerGarbageCollector {

    private final TaskActivityTracker taskActivityTracker;
    private final ScanQueuePublisher scanQueuePublisher;
    private final OpenCodeLLMClient openCodeLLMClient;

    @Value("${cscan.worker.task-timeout-ms:300000}")
    private long taskTimeoutMs;

    @Scheduled(fixedDelayString = "${cscan.worker.gc-interval-ms:60000}")
    public void gc() {
        List<TaskActivityTracker.Activity> stale = taskActivityTracker.stale(taskTimeoutMs);
        if (stale.isEmpty()) {
            return;
        }
        for (TaskActivityTracker.Activity activity : stale) {
            String taskId = activity.getTaskId();
            log.warn("检测到假死任务, taskId={}, 最后动作时间={}ms 前, sessionId={}",
                    taskId, System.currentTimeMillis() - activity.getLastActiveTime(), activity.getSessionId());
            try {
                // 存在 session 时中止 opencode 会话，释放 AI 适配线程
                if (StringUtils.hasText(activity.getSessionId())) {
                    openCodeLLMClient.abortSession(activity.getSessionId());
                }
            } catch (Exception e) {
                log.error("中止 opencode 会话失败, taskId={}, sessionId={}",
                        taskId, activity.getSessionId(), e);
            }
            try {
                // 标记检测失败并结束任务
                ScanResultMessage result = new ScanResultMessage();
                result.setTaskId(taskId);
                result.setError("扫描任务假死超时");
                scanQueuePublisher.sendFileResult(result);
                log.info("已标记检测失败, taskId={}", taskId);
            } catch (Exception e) {
                log.error("发送检测失败消息失败, taskId={}", taskId, e);
            } finally {
                taskActivityTracker.remove(taskId);
            }
        }
    }
}
