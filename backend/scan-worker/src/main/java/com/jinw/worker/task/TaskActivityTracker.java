package com.jinw.worker.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 记录 worker 中每个任务的最新动作时间，用于检测假死任务。
 * 采用引用计数：一个任务可能有多个消息在并行处理，全部处理完后才移除记录。
 */
@Slf4j
@Component
public class TaskActivityTracker {

    private final ConcurrentHashMap<String, Activity> activities = new ConcurrentHashMap<>();

    /**
     * 标记一个消息开始处理，并关联 opencode sessionId（可为 null）
     */
    public void begin(String taskId, String sessionId) {
        if (taskId == null) {
            return;
        }
        Activity activity = activities.computeIfAbsent(taskId, Activity::new);
        activity.incrementInFlight();
        if (sessionId != null && !sessionId.isBlank()) {
            activity.setSessionId(sessionId);
        }
        activity.touch();
    }

    /**
     * 更新任务的最新动作时间（心跳）
     */
    public void touch(String taskId) {
        if (taskId == null) {
            return;
        }
        Activity activity = activities.get(taskId);
        if (activity != null) {
            activity.touch();
        }
    }

    /**
     * 记录任务关联的 opencode sessionId（处理过程中拿到 session 后调用）
     */
    public void setSession(String taskId, String sessionId) {
        if (taskId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        Activity activity = activities.get(taskId);
        if (activity != null) {
            activity.setSessionId(sessionId);
        }
    }

    /**
     * 标记一个消息处理结束；当该任务所有消息都处理完后移除记录
     */
    public void end(String taskId) {
        if (taskId == null) {
            return;
        }
        Activity activity = activities.get(taskId);
        if (activity != null && activity.decrementInFlight() <= 0) {
            activities.remove(taskId, activity);
        }
    }

    /**
     * 强制移除任务追踪记录（用于假死任务清理）
     */
    public void remove(String taskId) {
        if (taskId != null) {
            activities.remove(taskId);
        }
    }

    /**
     * 返回超过指定时间未动作的任务（假死任务）
     */
    public List<Activity> stale(long timeoutMillis) {
        long now = System.currentTimeMillis();
        List<Activity> result = new ArrayList<>();
        for (Activity activity : activities.values()) {
            if (now - activity.getLastActiveTime() > timeoutMillis) {
                result.add(activity);
            }
        }
        return result;
    }

    public static class Activity {
        private final String taskId;
        private final AtomicInteger inFlight = new AtomicInteger(0);
        private volatile long lastActiveTime;
        private volatile String sessionId;

        public Activity(String taskId) {
            this.taskId = taskId;
            this.lastActiveTime = System.currentTimeMillis();
        }

        public void touch() {
            this.lastActiveTime = System.currentTimeMillis();
        }

        public void incrementInFlight() {
            inFlight.incrementAndGet();
        }

        public int decrementInFlight() {
            return inFlight.decrementAndGet();
        }

        public String getTaskId() {
            return taskId;
        }

        public long getLastActiveTime() {
            return lastActiveTime;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }
    }
}
