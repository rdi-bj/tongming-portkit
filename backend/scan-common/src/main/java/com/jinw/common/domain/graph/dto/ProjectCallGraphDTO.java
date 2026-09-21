package com.jinw.common.domain.graph.dto;

import com.jinw.common.domain.graph.CallEdge;
import com.jinw.common.domain.graph.FunctionSymbol;
import com.jinw.common.domain.graph.ModuleInfo;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ProjectCallGraphDTO {
    /**
     * 函数唯一 key -> 函数符号
     */
    private Map<String, FunctionSymbol> functions = new HashMap<>();

    /**
     * 调用边集合
     */
    private List<CallEdge> edges = new ArrayList<>();

    /**
     * 文件 -> 模块信息
     */
    private Map<String, ModuleInfo> modules = new HashMap<>();
}
