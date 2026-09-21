package com.jinw.web.graph;

import com.jinw.common.domain.graph.ProjectCallGraph;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TaskMergeManager {

    private final Map<String, TaskCallGraphMerger> mergers =
            new ConcurrentHashMap<>();

    public void onResult(String taskId, ProjectCallGraph graph) {
        mergers
                .computeIfAbsent(taskId, TaskCallGraphMerger::new)
                .merge(graph);
    }

    public void onFinished(String taskId) {
        TaskCallGraphMerger merger = mergers.remove(taskId);
        if (merger != null) {
            merger.finish();
        }
    }
}