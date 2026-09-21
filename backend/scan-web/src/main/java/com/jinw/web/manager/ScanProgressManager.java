package com.jinw.web.manager;

import com.jinw.web.domain.ScanProgress;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class ScanProgressManager {

    /**
     * taskId -> progress
     */
    private final ConcurrentHashMap<String, ScanProgress> progressMap =
            new ConcurrentHashMap<>();

    public void put(String taskId, ScanProgress progress) {
        progressMap.put(taskId, progress);
    }

    public ScanProgress get(String taskId) {
        return progressMap.get(taskId);
    }

    public void remove(String taskId) {
        progressMap.remove(taskId);
    }
}