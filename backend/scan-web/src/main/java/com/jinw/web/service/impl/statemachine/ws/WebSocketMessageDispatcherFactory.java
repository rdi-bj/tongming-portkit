package com.jinw.web.service.impl.statemachine.ws;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketMessageDispatcherFactory {

    private final Map<String, WebSocketMessageDispatcher> dispatchers =
            new ConcurrentHashMap<>();
    @Autowired
    private WebSocketSessionManager sessionManager;

    public WebSocketMessageDispatcher getOrCreate(String taskId) {
        return dispatchers.computeIfAbsent(taskId,
                t -> new WebSocketMessageDispatcher(t, sessionManager));
    }

    public void destroy(String taskId) {
        WebSocketMessageDispatcher dispatcher = dispatchers.remove(taskId);
        if (dispatcher != null) {
            dispatcher.notifyTaskFinished(null);
        }
    }
}