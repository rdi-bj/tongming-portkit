package com.jinw.common.domain.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinw.common.domain.graph.dto.ProjectCallGraphDTO;
import lombok.Data;

import java.util.*;

/**
 * 工程级调用图（多文件安全）
 */
@Data
public class ProjectCallGraph {

    /**
     * 函数唯一 key -> 函数符号
     */
    private Map<FunctionKey, FunctionSymbol> functions = new HashMap<>();

    /**
     * 调用边集合
     */
    private List<CallEdge> edges = new ArrayList<>();

    /**
     * 文件 -> 模块信息
     */
    private Map<String, ModuleInfo> modules = new HashMap<>();

    private static ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 业务方法 ====================

    /**
     * 获取或创建函数符号
     */
    public FunctionSymbol getOrCreateFunction(
            String definedInFile,
            String functionName,
            boolean isStatic
    ) {
        FunctionKey key = new FunctionKey(definedInFile, functionName);

        return functions.computeIfAbsent(key, k -> {
            FunctionSymbol symbol = new FunctionSymbol();
            symbol.setKey(k);
            symbol.setName(functionName);
            symbol.setDefinedInFile(definedInFile);
            symbol.setStatic(isStatic);
            return symbol;
        });
    }

    /**
     * 获取或创建模块信息
     */
    public ModuleInfo getOrCreateModule(String filePath) {
        return modules.computeIfAbsent(filePath, p -> {
            ModuleInfo module = new ModuleInfo();
            module.setFile(p);
            return module;
        });
    }

    /**
     * 添加调用边，并维护双向关系
     */
    public void addEdge(CallEdge edge) {
//        edges.add(edge);
//
//        FunctionSymbol caller = functions.get(edge.getCaller());
//        FunctionSymbol callee = functions.get(edge.getCallee());
//
//        if (caller != null && callee != null) {
//            caller.getCallees().add(callee.getKey());
//            callee.getCallers().add(caller.getKey());
//        }
    }

    public void merge(ProjectCallGraph other) {
        if (other == null) return;

        // ===== 1. 合并 functions =====
        for (FunctionSymbol f : other.getFunctions().values()) {
            FunctionSymbol target = functions.computeIfAbsent(
                    f.getKey(),
                    k -> new FunctionSymbol()
            );
            mergeFunctionSymbol(target, f);
        }

        // ===== 2. 合并 modules =====
        for (ModuleInfo m : other.getModules().values()) {
            ModuleInfo target = modules.computeIfAbsent(
                    m.getFile(),
                    k -> new ModuleInfo()
            );
            mergeModuleInfo(target, m);
        }

        // ===== 3. 合并 edges（去重） =====
        Set<CallEdge> edgeSet = new HashSet<>(edges);
        edgeSet.addAll(other.getEdges());
        edges.clear();
        edges.addAll(edgeSet);
    }

    private void mergeFunctionSymbol(FunctionSymbol target, FunctionSymbol src) {

        // 永远以 (definedInFile, name) 为准
        if (target.getDefinedInFile() == null) {
            target.setDefinedInFile(src.getDefinedInFile());
        }

        if (target.getName() == null) {
            target.setName(src.getName());
        }

        target.ensureKey();

        target.getDeclaredInFiles().addAll(src.getDeclaredInFiles());
        target.getCallees().addAll(src.getCallees());
        target.getCallers().addAll(src.getCallers());

        target.setStatic(src.isStatic());
        target.setExtern(src.isExtern());
        target.setMacro(src.isMacro());

        if (src.getStartLine() > 0) {
            target.setStartLine(src.getStartLine());
            target.setEndLine(src.getEndLine());
        }
    }

    private void mergeModuleInfo(ModuleInfo target, ModuleInfo src) {

        target.getIncludes().addAll(src.getIncludes());
        target.getFunctions().addAll(src.getFunctions());
        target.getExternalCalls().addAll(src.getExternalCalls());

        if (src.getCodeStat() != null) {
            target.setCodeStat(src.getCodeStat());
        }
    }

    public void resolveUnresolvedCalls() {
        for (CallEdge edge : edges) {
            if (!edge.isUnresolved()) continue;

            FunctionSymbol callee =
                    functions.get(edge.getCallee());

            if (callee != null && callee.getDefinedInFile() != null) {
                edge.setUnresolved(false);
                edge.setCallee(callee.getKey());
            }
        }
    }

    public ProjectCallGraphDTO convert2DTO() {
        Map<String, FunctionSymbol> functions = new HashMap<>();

        ProjectCallGraphDTO dto = new ProjectCallGraphDTO();
        dto.setEdges(this.getEdges());
        dto.setModules(this.getModules());
        dto.setFunctions(functions);

        this.functions.forEach((k, v) -> {
            try {
                functions.put(objectMapper.writeValueAsString(k), v);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
        return dto;
    }

    public static ProjectCallGraph convert2graph(ProjectCallGraphDTO dto) {
        Map<FunctionKey, FunctionSymbol> functions = new HashMap<>();

        ProjectCallGraph graph = new ProjectCallGraph();
        graph.setEdges(dto.getEdges());
        graph.setModules(dto.getModules());
        graph.setFunctions(functions);

        dto.getFunctions().forEach((k, v) -> {
            try {
                FunctionKey key = objectMapper.readValue(k, FunctionKey.class);

                // 兜底
                if (v.getKey() == null) {
                    v.setKey(key);
                }

                functions.put(key, v);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
        return graph;
    }
}