package com.jinw.web.service.impl.statemachine.ws;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
@ServerEndpoint("/ws/scan/{taskId}")
public class ScanWebSocketEndpoint {

    private static WebSocketSessionManager sessionManager;
    private static HeartbeatMonitor heartbeatMonitor;
    private static WebSocketMessageDispatcherFactory dispatcherFactory;

    private String taskId;
    private Session session;

    @Autowired
    public void setSessionManager(WebSocketSessionManager sessionManager) {
        ScanWebSocketEndpoint.sessionManager = sessionManager;
    }

    @Autowired
    public void setHeartbeatMonitor(HeartbeatMonitor heartbeatMonitor) {
        ScanWebSocketEndpoint.heartbeatMonitor = heartbeatMonitor;
    }

    @Autowired
    public void setDispatcherFactory(WebSocketMessageDispatcherFactory dispatcherFactory) {
        ScanWebSocketEndpoint.dispatcherFactory = dispatcherFactory;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("taskId") String taskId) throws IOException {
        this.session = session;
        this.taskId = taskId;

        sessionManager.register(taskId, session);
        log.debug("WebSocket连接建立: taskId={}, sessionId={}", taskId, session.getId());

        session.getBasicRemote()
                .sendText("{\"type\":\"CONNECTED\",\"taskId\":\"" + taskId + "\"}");
    }

    @OnMessage
    public void onMessage(String message) throws IOException {
        if (message.contains("\"type\":\"PING\"") || "PING".equals(message)) {
            heartbeatMonitor.updateHeartbeat(session.getId());
            session.getBasicRemote().sendText("{\"type\":\"PONG\"}");
            log.trace("taskId={} 收到PING，回复PONG", taskId);
            return;
        }

        log.debug("taskId={} 收到未知消息: {}", taskId, message);
    }

    @OnClose
    public void onClose(CloseReason closeReason) {
        if (taskId != null) {
            sessionManager.unregister(taskId, session);
            heartbeatMonitor.removeHeartbeat(session.getId());
            log.debug("WebSocket连接关闭: taskId={}, reason={}", taskId, closeReason);
        }
    }

    @OnError
    public void onError(Throwable throwable) {
        log.warn("taskId={} WebSocket传输错误: {}", taskId, throwable.getMessage());
    }
}