package com.jinw.worker.ast.common;

import com.jinw.common.domain.CodeStat;
import com.jinw.common.domain.graph.*;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 抽象 Tree‑sitter 解析器
 *
 * <p>负责：
 * <ul>
 *   <li>文件读取</li>
 *   <li>行索引构建</li>
 *   <li>AST 遍历流程</li>
 *   <li>代码 / 注释 / 调用图统计</li>
 * </ul>
 *
 * <p>不负责：
 * <ul>
 *   <li>C / C++ 语法差异</li>
 *   <li>函数名提取规则</li>
 *   <li>类 / 命名空间 / 模板</li>
 * </ul>
 */
public abstract class AbstractTreeSitterParser {

    /* =========================
     * 线程安全上下文
     * ========================= */
    protected final ThreadLocal<Deque<FunctionKey>> FUNC_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    /* =========================
     * 对外入口
     * ========================= */
    public ProjectCallGraph parse(String filePath) throws Exception {
        ProjectCallGraph graph = new ProjectCallGraph();

        byte[] bytes = Files.readAllBytes(Paths.get(filePath));
        String source = new String(bytes, StandardCharsets.UTF_8);

        TSParser parser = createParser();
        TSTree tree = parser.parseString(null, source);
        TSNode root = tree.getRootNode();

        int[] lineIndex = buildLineIndex(bytes);
        ModuleInfo module = graph.getOrCreateModule(filePath);
        CodeStat stat = new CodeStat();

        walk(root, bytes, lineIndex, graph, module, new File(filePath), stat);

        stat.end();
        stat.ensureLineCount(source.lines().count());
        module.setCodeStat(stat);

        parser.reset();
        return graph;
    }

    /* =========================
     * 语言相关（子类实现）
     * ========================= */
    protected abstract TSParser createParser();

    protected abstract boolean isFunctionDefinition(String type);

    protected abstract String extractFunctionName(TSNode node, byte[] bytes);

    protected abstract boolean isStaticFunction(TSNode node);

    /* =========================
     * AST 遍历（核心流程）
     * ========================= */
    protected void walk(
            TSNode node,
            byte[] bytes,
            int[] lineIndex,
            ProjectCallGraph graph,
            ModuleInfo module,
            File file,
            CodeStat stat
    ) {
        if (node == null || node.isNull()) {
            return;
        }

//        System.out.println("WALK type=" + node.getType()
//                + " children=" + node.getChildCount()
//                + " start=" + node.getStartByte()
//                + " end=" + node.getEndByte());

        String type = node.getType();

        if ("ERROR".equals(type)) {
            if (isAsm(node, bytes)) {
                handleAsm(node, bytes, lineIndex, module, stat);
            }
            // 不再向下遍历，避免 null node
            return;
        }

        int startByte = clamp(node.getStartByte(), bytes.length - 1);
        int endByte = clamp(node.getEndByte(), bytes.length - 1);
        int startLine = lineIndex[startByte] + 1;
        int endLine = lineIndex[endByte] + 1;

        /* ===== 注释 ===== */
        if (isComment(type)) {
            for (int l = startLine; l <= endLine; l++) {
                stat.recordLine(l);
                stat.markComment();
            }
            return;
        }

        if ("preproc_defined".equals(type)) {
            handleDefinedMacro(node, bytes, lineIndex, module);
        }

        /* ===== include ===== */
        if ("preproc_include".equals(type)) {
            handleInclude(node, bytes, lineIndex, module, stat);
            return;
        }

        /* ===== macro ===== */
        if (isMacro(type)) {
            handleMacro(node, bytes, lineIndex, module, stat);
        }

        /* ===== asm（非 ERROR 路径） ===== */
        if (isAsm(node, bytes)) {
            handleAsm(node, bytes, lineIndex, module, stat);
            return;
        }

        /* ===== 函数定义 ===== */
        if (isFunctionDefinition(type)) {
            handleFunctionDefinition(node, bytes, lineIndex, graph, module, stat);
        }

        /* ===== 函数调用 ===== */
        if ("call_expression".equals(type)) {
            handleCallExpression(node, bytes, lineIndex, graph, module, stat);
        }

        /* ===== 普通代码叶子 ===== */
        if (node.getChildCount() == 0 && startByte < endByte) {
            for (int l = startLine; l <= endLine; l++) {
                stat.recordLine(l);
                stat.markCode();
            }
        }

        /* ===== 递归子节点 ===== */
        for (int i = 0; i < node.getChildCount(); i++) {
            walk(node.getChild(i), bytes, lineIndex, graph, module, file, stat);
        }

        /* ===== 离开函数 ===== */
        if (isFunctionDefinition(type)) {
            FUNC_STACK.get().pollLast();
        }
    }

    /* =========================
     * defined(...)
     * ========================= */
    protected void handleDefinedMacro(
            TSNode node,
            byte[] bytes,
            int[] lineIndex,
            ModuleInfo module
    ) {
        for (int i = 0; i < node.getChildCount(); i++) {

            TSNode child = node.getChild(i);

            if (child == null || !"identifier".equals(child.getType())) {
                continue;
            }

            MacroDefine macro = new MacroDefine();

            // 注意这里定位到 identifier
            fillSourceLoc(macro, child, bytes, lineIndex);

            int s = clamp(child.getStartByte(), bytes.length);
            int e = clamp(child.getEndByte(), bytes.length);

            macro.setName(new String(bytes, s, e - s, StandardCharsets.UTF_8));
            macro.setValue("");
            macro.setFunctionLike(false);
            macro.setUnsafe(false);

            module.getMacros().add(macro);

            // 一个 preproc_defined 只有一个 identifier
            return;
        }
    }

    /* =========================
     * 默认 Hook（可被覆盖）
     * ========================= */
    protected boolean isMacro(String type) {
        return "preproc_def".equals(type)
                || "preproc_function_def".equals(type);
    }

    protected boolean isComment(String type) {
        return type.contains("comment");
    }

    protected boolean isAsm(TSNode node, byte[] bytes) {
        return false;
    }

    /* =========================
     * include
     * ========================= */
    protected void handleInclude(
            TSNode node,
            byte[] bytes,
            int[] lineIndex,
            ModuleInfo module,
            CodeStat stat
    ) {
        int startByte = clamp(node.getStartByte(), bytes.length - 1);
        int endByte = clamp(node.getEndByte(), bytes.length - 1);
        int startLine = lineIndex[startByte] + 1;
        int endLine = lineIndex[endByte] + 1;

        for (int l = startLine; l <= endLine; l++) {
            stat.recordLine(l);
            stat.markCode();
        }

        IncludeDefine include = new IncludeDefine();
        fillSourceLoc(include, node, bytes, lineIndex);
        include.setPath(extractIncludePath(node, bytes));
        include.setSystemHeader(isSystemInclude(node));
        module.getIncludes().add(include);
    }

    /* =========================
     * macro
     * ========================= */
    protected void handleMacro(
            TSNode node,
            byte[] bytes,
            int[] lineIndex,
            ModuleInfo module,
            CodeStat stat
    ) {
        int startByte = clamp(node.getStartByte(), bytes.length - 1);
        int endByte = clamp(node.getEndByte(), bytes.length - 1);
        int startLine = lineIndex[startByte] + 1;
        int endLine = lineIndex[endByte] + 1;

        for (int l = startLine; l <= endLine; l++) {
            stat.recordLine(l);
            stat.markCode();
        }

        MacroDefine macro = new MacroDefine();
        fillSourceLoc(macro, node, bytes, lineIndex);
        macro.setName(extractMacroName(node, bytes));
        macro.setValue(extractMacroValue(node, bytes));
        macro.setFunctionLike("preproc_function_def".equals(node.getType()));
        module.getMacros().add(macro);
    }

    /* =========================
     * asm
     * ========================= */
    protected void handleAsm(
            TSNode node,
            byte[] bytes,
            int[] lineIndex,
            ModuleInfo module,
            CodeStat stat
    ) {
        int startByte = clamp(node.getStartByte(), bytes.length - 1);
        int endByte = clamp(node.getEndByte(), bytes.length - 1);
        int startLine = lineIndex[startByte] + 1;
        int endLine = lineIndex[endByte] + 1;

        for (int l = startLine; l <= endLine; l++) {
            stat.recordLine(l);
            stat.markCode();
        }

        AsmDefine asm = new AsmDefine();
        fillSourceLoc(asm, node, bytes, lineIndex);
        asm.setVolatileAsm(contains(bytes, "volatile"));
        asm.setGnuStyle(contains(bytes, "__asm__"));
        module.getAsms().add(asm);
    }

    /* =========================
     * 函数定义
     * ========================= */
    protected void handleFunctionDefinition(
            TSNode node,
            byte[] bytes,
            int[] lineIndex,
            ProjectCallGraph graph,
            ModuleInfo module,
            CodeStat stat
    ) {
        int startByte = clamp(node.getStartByte(), bytes.length - 1);
        int endByte = clamp(node.getEndByte(), bytes.length - 1);
        int startLine = lineIndex[startByte] + 1;
        int endLine = lineIndex[endByte] + 1;

        for (int l = startLine; l <= endLine; l++) {
            stat.recordLine(l);
            stat.markCode();
        }

        String funcName = extractFunctionName(node, bytes);
        boolean isStatic = isStaticFunction(node);

        if (funcName != null) {
            FunctionSymbol sym = graph.getOrCreateFunction(
                    module.getFile(), funcName, isStatic
            );
            fillSourceLoc(sym, node, bytes, lineIndex);
            sym.setKey(new FunctionKey(module.getFile(), funcName));
            sym.setDefinedInFile(module.getFile());
            sym.setName(funcName);
            module.getFunctions().add(sym.getKey());

            FUNC_STACK.get().push(sym.getKey());
        }
    }

    /* =========================
     * 函数调用
     * ========================= */
    protected void handleCallExpression(
            TSNode node,
            byte[] bytes,
            int[] lineIndex,
            ProjectCallGraph graph,
            ModuleInfo module,
            CodeStat stat
    ) {
        TSNode fn = node.getChildByFieldName("function");
        if (fn == null || !"identifier".equals(fn.getType())) {
            return;
        }

        int start = clamp(fn.getStartByte(), bytes.length);
        int end = clamp(fn.getEndByte(), bytes.length);
        if (end < start) return;

        String callee = new String(bytes, start, end - start, StandardCharsets.UTF_8);
        FunctionKey callerKey = FUNC_STACK.get().peek();

        if (callerKey != null) {
            FunctionKey calleeKey = new FunctionKey(null, callee);
            CallEdge edge = new CallEdge();
            edge.setCaller(callerKey);
            edge.setCallee(calleeKey);
            edge.setFile(module.getFile());
            edge.setLine(lineIndex[start] + 1);
            edge.setUnresolved(true);

            graph.addEdge(edge);
            module.getExternalCalls().add(calleeKey);
        }
    }

    /* =========================
     * 工具方法（完全复用）
     * ========================= */
    protected int[] buildLineIndex(byte[] bytes) {
        int[] index = new int[bytes.length];
        int line = 0;
        for (int i = 0; i < bytes.length; i++) {
            index[i] = line;
            if (bytes[i] == '\n') line++;
        }
        return index;
    }

    protected void fillSourceLoc(
            SourceLoc loc,
            TSNode node,
            byte[] bytes,
            int[] lineIndex
    ) {
        int s = clamp(node.getStartByte(), bytes.length);
        int e = clamp(node.getEndByte(), bytes.length);

        while (e > s && (bytes[e - 1] == '\n' || bytes[e - 1] == '\r')) {
            e--;
        }

        loc.setStartByte(s);
        loc.setEndByte(e);
        loc.setStartLine(lineIndex[s] + 1);
        loc.setEndLine(lineIndex[Math.max(e - 1, 0)] + 1);
        loc.setStartCol(s - findLineStart(bytes, s));
        loc.setEndCol(e - findLineStart(bytes, e));
        loc.setText(new String(bytes, s, e - s, StandardCharsets.UTF_8));
    }

    protected String extractIncludePath(TSNode node, byte[] bytes) {
        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode c = node.getChild(i);
            if ("string_literal".equals(c.getType())
                    || "system_lib_string".equals(c.getType())) {
                int s = clamp(c.getStartByte(), bytes.length);
                int e = clamp(c.getEndByte(), bytes.length);
                return new String(bytes, s, e - s, StandardCharsets.UTF_8)
                        .replace("\"", "")
                        .replace("<", "")
                        .replace(">", "");
            }
        }
        return null;
    }

    protected boolean isSystemInclude(TSNode node) {
        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode child = node.getChild(i);
            if (child != null && "system_lib_string".equals(child.getType())) {
                return true;
            }
        }
        return false;
    }

    protected String extractMacroName(TSNode node, byte[] bytes) {
        TSNode name = node.getChildByFieldName("name");
        if (name == null) return null;
        int s = clamp(name.getStartByte(), bytes.length);
        int e = clamp(name.getEndByte(), bytes.length);
        return new String(bytes, s, e - s, StandardCharsets.UTF_8);
    }

    protected String extractMacroValue(TSNode node, byte[] bytes) {
        TSNode val = node.getChildByFieldName("value");
        if (val == null || val.isNull()) {
            return "";
        }
        int s = clamp(val.getStartByte(), bytes.length);
        int e = clamp(val.getEndByte(), bytes.length);
        if (e <= s) {
            return "";
        }
        return new String(bytes, s, e - s, StandardCharsets.UTF_8).trim();
    }

    protected int clamp(int v, int maxLen) {
        return Math.max(0, Math.min(v, maxLen - 1));
    }

    protected boolean contains(byte[] bytes, String s) {
        return new String(bytes, StandardCharsets.UTF_8).contains(s);
    }

    protected int findLineStart(byte[] bytes, int index) {
        int last = 0;
        for (int i = 0; i < index && i < bytes.length; i++) {
            if (bytes[i] == '\n') last = i + 1;
        }
        return last;
    }
}
