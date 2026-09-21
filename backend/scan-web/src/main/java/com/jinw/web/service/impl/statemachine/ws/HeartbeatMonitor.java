package com.jinw.web.service.impl.statemachine.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 心跳监听器
 * <p>
 * 定期检查所有活跃 WebSocket 连接的心跳状态，
 * 如果超过指定时间未收到心跳，则主动断开连接。
 */
@Component
@Slf4j
public class HeartbeatMonitor {

    /**
     * 心跳超时时间（毫秒），超过此时间未收到PONG则断开
     */
    private static final long HEARTBEAT_TIMEOUT = 30000;
    /**
     * 记录每个 session 最后一次收到心跳的时间
     */
    private final ConcurrentHashMap<String, Long> lastHeartbeatMap = new ConcurrentHashMap<>();
    @Autowired
    private WebSocketSessionManager sessionManager;

    /**
     * 定时检查心跳，每 10 秒执行一次
     */
    @Scheduled(fixedDelay = 10000)
    public void checkHeartbeat() {
        Set<String> taskIds = sessionManager.getTaskIds();
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();

        for (String taskId : taskIds) {
            var sessions = sessionManager.getSessions(taskId);
            for (var session : sessions) {
                String sessionId = session.getId();
                Long lastHeartbeat = lastHeartbeatMap.get(sessionId);

                if (lastHeartbeat == null) {
                    // 新连接，初始化心跳时间
                    lastHeartbeatMap.put(sessionId, now);
                    continue;
                }

                if (now - lastHeartbeat > HEARTBEAT_TIMEOUT) {
                    log.warn("taskId={} sessionId={} 心跳超时，主动断开连接", taskId, sessionId);
                    try {
                        session.close();
                    } catch (Exception e) {
                        log.warn("关闭超时连接失败: {}", e.getMessage());
                    }
                    sessionManager.unregister(taskId, session);
                    lastHeartbeatMap.remove(sessionId);
                }
            }
        }
    }

    /**
     * 更新指定 session 的心跳时间
     *
     * @param sessionId WebSocket会话ID
     */
    public void updateHeartbeat(String sessionId) {
        lastHeartbeatMap.put(sessionId, System.currentTimeMillis());
    }

    /**
     * 移除指定 session 的心跳记录
     *
     * @param sessionId WebSocket会话ID
     */
    public void removeHeartbeat(String sessionId) {
        lastHeartbeatMap.remove(sessionId);
    }
}
