package com.jinw.worker.ast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.ScanTaskMessage;
import com.jinw.common.domain.graph.CallEdge;
import com.jinw.common.domain.graph.ProjectCallGraph;
import com.jinw.common.domain.graph.resolver.CallGraphResolver;
import com.jinw.worker.ast.c.CTreeSitterParser;
import com.jinw.worker.ast.cpp.CPPTreeSitterParser;

import java.io.IOException;
import java.util.Objects;

/**
 * 对外唯一入口
 */
public class TreeSitterUtil {

    private static ObjectMapper mapper = new ObjectMapper();

    public static ProjectCallGraph parse(String filePath) throws Exception {
        if (filePath.endsWith(".c") || filePath.endsWith(".h")) {
            return new CTreeSitterParser().parse(filePath);
        } else if (filePath.endsWith(".cpp")) {
            return new CPPTreeSitterParser().parse(filePath);
        }
        throw new IllegalArgumentException("Unsupported file: " + filePath);
    }

    public static ProjectCallGraph parse(ScanTaskMessage scanTaskMessage) throws Exception {
        if (ScanConstant.LANGUAGE_C.equals(scanTaskMessage.getLanguage())) {
            return new CTreeSitterParser().parse(scanTaskMessage.getFilePath());
        } else if (ScanConstant.LANGUAGE_CPP.equals(scanTaskMessage.getLanguage())) {
            return new CPPTreeSitterParser().parse(scanTaskMessage.getFilePath());
        }
        return null;
    }

    /* =======================
     * 测试用 main
     * ======================= */
    public static void main(String[] args) throws Exception {
        ProjectCallGraph g1 = parse("/Users/luosz/Downloads/0601/demo_code/demo_code/group1/main.c");
        ProjectCallGraph g2 = parse("/Users/luosz/Downloads/0601/demo_code/demo_code/group2/utils.c");
        ProjectCallGraph g3 = parse("/Users/luosz/Downloads/0601/demo_code/demo_code/group3/example.cpp");

        g1.merge(g2);
        g1.merge(g3);
//        System.out.println(g3);
//        System.out.println();

        // 核心：解析所有 unresolved callee
        CallGraphResolver resolver = new CallGraphResolver(g1);
        resolver.resolveAll();

        /*for (CallEdge edge : g1.getEdges()) {
            if(Objects.equals(edge.getCallee().getFunctionName(),"extern_func")){
                System.out.println(edge);
            }
        }*/

        String json = mapper.writeValueAsString(g1.getEdges());
        System.out.println(json);
    }
}