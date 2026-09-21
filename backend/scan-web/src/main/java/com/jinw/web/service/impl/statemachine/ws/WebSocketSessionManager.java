package com.jinw.web.service.impl.statemachine.ws;

import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * WebSocket 会话管理器
 * <p>
 * 每个 taskId 对应一组 Session，支持多用户同时连接，
 * 相互之间不受影响。当 taskId 对应的所有会话关闭或任务结束时，自动销毁。
 */
@Component
@Slf4j
public class WebSocketSessionManager {

    /**
     * key: taskId, value: 该 taskId 下的所有 Session
     */
    private final Map<String, Set<Session>> taskSessions = new ConcurrentHashMap<>();

    /**
     * 心跳检测任务是否已启动
     */
    private volatile boolean heartbeatStarted = false;

    /**
     * 注册一个新的 Session 到指定的 taskId
     *
     * @param taskId  扫描任务ID
     * @param session WebSocket会话
     */
    public synchronized void register(String taskId, Session session) {
        taskSessions.computeIfAbsent(taskId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.debug("taskId={} 注册WebSocket会话, sessionId={}, 当前连接数={}",
                taskId, session.getId(), taskSessions.get(taskId).size());
    }

    /**
     * 注销指定 taskId 下的 Session
     *
     * @param taskId  扫描任务ID
     * @param session WebSocket会话
     */
    public synchronized void unregister(String taskId, Session session) {
        Set<Session> sessions = taskSessions.get(taskId);
        if (sessions != null) {
            sessions.remove(session);
            log.debug("taskId={} 注销WebSocket会话, sessionId={}, 剩余连接数={}",
                    taskId, session.getId(), sessions.size());
            // 如果该 taskId 下没有活跃会话了，自动清理
            if (sessions.isEmpty()) {
                taskSessions.remove(taskId);
                log.debug("taskId={} 所有WebSocket会话已关闭，自动销毁", taskId);
            }
        }
    }

    /**
     * 获取指定 taskId 下所有活跃的 Session
     *
     * @param taskId 扫描任务ID
     * @return 活跃会话集合
     */
    public Set<Session> getSessions(String taskId) {
        return taskSessions.getOrDefault(taskId, ConcurrentHashMap.newKeySet());
    }

    /**
     * 向指定 taskId 下的所有活跃会话广播消息
     *
     * @param taskId  扫描任务ID
     * @param message 消息内容
     */
    public void broadcast(String taskId, String message) {
        Set<Session> sessions = getSessions(taskId);
        if (sessions.isEmpty()) {
            return;
        }

        Iterator<Session> iterator = sessions.iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (!session.isOpen()) {
                iterator.remove();
                continue;
            }

            //对 Session 本身加锁
            synchronized (session) {
                if (!session.isOpen()) {
                    iterator.remove();
                    continue;
                }
                try {
                    session.getBasicRemote().sendText(message);
                    log.trace("taskId={} 发送socket信息：{}", taskId, message);
                } catch (IOException e) {
                    log.warn("taskId={} 向sessionId={}发送消息失败: {}",
                            taskId, session.getId(), e.getMessage());
                    iterator.remove();
                    unregister(taskId, session);
                }
            }
        }
    }

    /**
     * 销毁指定 taskId 的所有 WebSocket 连接
     *
     * @param taskId 扫描任务ID
     */
    public synchronized void destroy(String taskId) {
        Set<Session> sessions = taskSessions.remove(taskId);
        if (sessions != null && !sessions.isEmpty()) {
            for (Session session : sessions) {
                try {
                    if (session.isOpen()) {
                        session.close();
                    }
                } catch (IOException e) {
                    log.warn("taskId={} 关闭WebSocket会话失败: {}", taskId, e.getMessage());
                }
            }
            log.debug("taskId={} 已销毁所有WebSocket连接，共 {} 个", taskId, sessions.size());
        }
    }

    public void destroyWithDelay(String taskId, long delayMs) {
        Set<Session> sessions = taskSessions.get(taskId);
        if (sessions == null || sessions.isEmpty()) {
            taskSessions.remove(taskId);
            return;
        }

        // 复制一份，避免并发修改
        Set<Session> sessionsToClose = Set.copyOf(sessions);
        taskSessions.remove(taskId);

        // 异步延迟关闭
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            for (Session session : sessionsToClose) {
                try {
                    if (session.isOpen()) {
                        session.close();
                    }
                } catch (IOException e) {
                    log.warn("taskId={} 延迟关闭WebSocket会话失败: {}", taskId, e.getMessage());
                }
            }
            log.debug("taskId={} 延迟 {}ms 后已销毁所有WebSocket连接，共 {} 个",
                    taskId, delayMs, sessionsToClose.size());
        }, "ws-delay-close-" + taskId).start();
    }

    /**
     * 获取所有有活跃连接的 taskId 集合
     *
     * @return taskId 集合
     */
    public Set<String> getTaskIds() {
        return taskSessions.keySet();
    }

    /**
     * 检查指定 taskId 是否有活跃连接
     *
     * @param taskId 扫描任务ID
     * @return 是否有活跃连接
     */
    public boolean hasActiveConnections(String taskId) {
        Set<Session> sessions = taskSessions.get(taskId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        // 清理已关闭的会话后再判断
        long activeCount = sessions.stream().filter(Session::isOpen).count();
        return activeCount > 0;
    }
}
