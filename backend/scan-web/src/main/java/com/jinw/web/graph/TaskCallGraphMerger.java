package com.jinw.web.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.graph.CallEdge;
import com.jinw.common.domain.graph.ProjectCallGraph;
import com.jinw.common.domain.graph.resolver.CallGraphResolver;
import com.jinw.web.config.exception.BusinessException;
import com.jinw.web.util.FileStorageUtil;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class TaskCallGraphMerger {

    private String taskId;
    private final ProjectCallGraph merged = new ProjectCallGraph();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r);
                t.setName("cg-merge-" + taskId);
                return t;
            });

    public TaskCallGraphMerger(String taskId) {
        this.taskId = taskId;
    }

    /** 合并单个文件 Graph */
    public void merge(ProjectCallGraph graph) {
        executor.submit(() -> merged.merge(graph));
    }

    /** task 结束：resolveAll + 导出 */
    public void finish() {
        executor.submit(() -> {
            try {
                CallGraphResolver resolver = new CallGraphResolver(merged);
                resolver.resolveAll();
                String projectPath = FileStorageUtil.toAbsolutePath(taskId, ScanConstant.GRAPH_FILE_NAME);
                export(merged.getEdges(),projectPath);
            } finally {
                shutdown();
            }
        });
    }

    private void export(List<CallEdge> edges,String projectPath) {
        try{
            ObjectMapper mapper = new ObjectMapper();
            // 过滤：只保留 .c / .cpp 文件，且 callee.definedInFile 不为空
            List<CallEdge> filteredEdges = edges.stream()
                    .filter(edge -> isCOrCppFile(edge.getFile()))
                    .filter(edge -> edge.getCallee() != null
                            && StringUtils.isNotBlank(edge.getCallee().getDefinedInFile()))
                    .collect(Collectors.toList());
            String json = mapper.writeValueAsString(filteredEdges);
            Path outputPath = Paths.get(projectPath);
            Files.writeString(outputPath, json, StandardCharsets.UTF_8);
        }catch (Exception e){
            e.printStackTrace();
            throw new BusinessException("合并出错");
        }

    }

    private boolean isCOrCppFile(String file) {
        if (file == null) {
            return false;
        }
        String lower = file.toLowerCase();
        return lower.endsWith(".c") || lower.endsWith(".cpp");
    }

    private void shutdown() {
        executor.shutdown();
    }
}