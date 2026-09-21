package com.jinw.common.domain.graph.resolver;

import com.jinw.common.domain.graph.CallEdge;
import com.jinw.common.domain.graph.FunctionKey;
import com.jinw.common.domain.graph.FunctionSymbol;
import com.jinw.common.domain.graph.ProjectCallGraph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 跨文件 callee 溯源解析器（NPE-safe）
 */
public class CallGraphResolver {

    private final ProjectCallGraph graph;

    private final Map<String, List<FunctionSymbol>> name2Defs = new HashMap<>();
    private final Map<FunctionKey, Map<FunctionKey, Integer>> distanceCache = new HashMap<>();

    public CallGraphResolver(ProjectCallGraph graph) {
        this.graph = graph;
    }

    public void resolveAll() {
        buildFunctionIndex();

        // 关键：先补边
        buildCallGraphFromEdges();

        buildDistanceCache();
        resolveEdges();
    }

    /* ================= 函数索引（严格校验 key） ================= */

    private void buildFunctionIndex() {
        for (FunctionSymbol symbol : graph.getFunctions().values()) {

            // 强制自救
            FunctionKey key = symbol.ensureKey();
            if (key == null) continue;

            if (symbol.getDefinedInFile() == null) continue;

            name2Defs
                    .computeIfAbsent(symbol.getName(), k -> new ArrayList<>())
                    .add(symbol);
        }
    }

    /* ================= 最短路径（BFS，key 安全） ================= */

    private void buildDistanceCache() {
        for (FunctionSymbol start : graph.getFunctions().values()) {
            if (start.getKey() == null) continue;
            if (start.getDefinedInFile() == null) continue;
            bfs(start);
        }
    }

    private void bfs(FunctionSymbol start) {
        Map<FunctionKey, Integer> dist = new HashMap<>();
        Queue<FunctionKey> queue = new ArrayDeque<>();

        FunctionKey startKey = start.getKey();
        dist.put(startKey, 0);
        queue.add(startKey);

        while (!queue.isEmpty()) {
            FunctionKey current = queue.poll();
            int d = dist.get(current);

            FunctionSymbol currentFn = graph.getFunctions().get(current);
            if (currentFn == null) continue;

            for (FunctionKey callee : currentFn.getCallees()) {
                if (callee == null) continue;
                if (!dist.containsKey(callee)) {
                    dist.put(callee, d + 1);
                    queue.add(callee);
                }
            }
        }

        distanceCache.put(startKey, dist);
    }

    private int distance(FunctionKey from, FunctionKey to) {
        Map<FunctionKey, Integer> map = distanceCache.get(from);
        if (map == null) return Integer.MAX_VALUE;
        return map.getOrDefault(to, Integer.MAX_VALUE);
    }

    /* ================= Edge 解析 ================= */

    private void resolveEdges() {
        for (CallEdge edge : graph.getEdges()) {
            if (!edge.isUnresolved()) continue;
            if (edge.getCaller() == null) continue;
            if (edge.getCallee() == null) continue;

            resolveEdge(edge);
        }
    }

    private void resolveEdge(CallEdge edge) {
        String calleeName = edge.getCallee().getFunctionName();
        List<FunctionSymbol> candidates = name2Defs.get(calleeName);

        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        // 本文件优先（无视一切）
        Optional<FunctionSymbol> sameFile = candidates.stream()
                .filter(f -> f.getDefinedInFile().equals(edge.getFile()))
                .findFirst();

        if (sameFile.isPresent()) {
            bind(edge, sameFile.get());
            return;
        }

        // 只有一个候选：直接绑定（不再碰 distance）
        if (candidates.size() == 1) {
            bind(edge, candidates.get(0));
            return;
        }

        // 多个候选：尽量用 distance，但不强依赖
        FunctionSymbol best = null;
        int minDist = Integer.MAX_VALUE;

        FunctionSymbol caller = graph.getFunctions().get(edge.getCaller());
        if (caller != null && caller.getKey() != null) {
            for (FunctionSymbol candidate : candidates) {
                int d = distance(caller.getKey(), candidate.getKey());
                if (d < minDist) {
                    minDist = d;
                    best = candidate;
                }
            }
        }

        // 兜底（这是关键中的关键）
        if (best == null) {
            best = candidates.get(0);
        }

        bind(edge, best);
    }

    /* ================= 绑定 ================= */

    private void bind(CallEdge edge, FunctionSymbol callee) {
        edge.setCallee(callee.getKey());
        edge.setUnresolved(false);

        FunctionSymbol caller = graph.getFunctions().get(edge.getCaller());
        if (caller != null) {
            caller.getCallees().add(callee.getKey());
            callee.getCallers().add(caller.getKey());
        }
    }

    /**
     * 从 CallEdge 建调用图
     */
    private void buildCallGraphFromEdges() {
        for (CallEdge edge : graph.getEdges()) {
            if (edge.getCaller() == null || edge.getCallee() == null) continue;

            FunctionSymbol caller = graph.getFunctions().get(edge.getCaller());
            FunctionSymbol callee = graph.getFunctions().get(edge.getCallee());

            if (caller != null && callee != null) {
                caller.getCallees().add(callee.getKey());
                callee.getCallers().add(caller.getKey());
            }
        }
    }
}