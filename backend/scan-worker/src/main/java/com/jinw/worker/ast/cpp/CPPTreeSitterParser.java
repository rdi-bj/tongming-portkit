package com.jinw.worker.ast.cpp;

import com.jinw.worker.ast.common.AbstractTreeSitterParser;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TreeSitterCpp;

import java.nio.charset.StandardCharsets;

/**
 * C++ 解析器
 *
 * <p>严格遵循 AbstractTreeSitterParser 的流程，
 * 输出结果与 CTreeSitterParser 完全一致。
 */
public class CPPTreeSitterParser extends AbstractTreeSitterParser {

    /* =========================
     * Tree‑sitter 语言
     * ========================= */
    @Override
    protected TSParser createParser() {
        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterCpp());
        return parser;
    }

    /* =========================
     * 函数定义识别（C++）
     * ========================= */
    @Override
    protected boolean isFunctionDefinition(String type) {
        return "function_definition".equals(type)
                || "constructor_or_destructor".equals(type);
    }

    /* =========================
     * 函数名提取（C++）
     * ========================= */
    @Override
    protected String extractFunctionName(TSNode node, byte[] bytes) {
        if (node == null || node.isNull()) {
            return null;
        }

        TSNode declarator = node.getChildByFieldName("declarator");
        if (declarator == null || declarator.isNull()) {
            return null;
        }

        // 普通函数
        TSNode identifier = declarator.getChildByFieldName("declarator");
        if (identifier != null && !identifier.isNull()) {
            return safeText(identifier, bytes);
        }

        // 构造函数 / 析构函数
        TSNode name = declarator.getChildByFieldName("name");
        if (name != null && !name.isNull()) {
            return safeText(name, bytes);
        }

        return null;
    }

    /* =========================
     * static / inline / constexpr
     * ========================= */
    @Override
    protected boolean isStaticFunction(TSNode node) {
        if (node == null || node.isNull()) {
            return false;
        }

        TSNode specs = node.getChildByFieldName("declaration_specifiers");
        if (specs == null || specs.isNull()) {
            return false;
        }

        for (int i = 0; i < specs.getChildCount(); i++) {
            TSNode child = specs.getChild(i);
            if (child == null || child.isNull()) {
                continue;
            }

            if ("storage_class_specifier".equals(child.getType())) {
                TSNode kw = child.getChild(0);
                if (kw != null && "static".equals(kw.getType())) {
                    return true;
                }
            }
        }
        return false;
    }

    /* =========================
     * ASM（兼容 ERROR 节点）
     * ========================= */
    @Override
    protected boolean isAsm(TSNode node, byte[] bytes) {
        if (node == null || node.isNull()) {
            return false;
        }

        String type = node.getType();

//        System.out.println(type);
        return "gnu_asm_expression".equals(type)
                || "gnu_asm_qualifier".equals(type);
    }

    /* =========================
     * 工具方法
     * ========================= */
    private String safeText(TSNode node, byte[] bytes) {
        int s = clamp(node.getStartByte(), bytes.length);
        int e = clamp(node.getEndByte(), bytes.length);
        if (e <= s) {
            return null;
        }
        return new String(bytes, s, e - s, StandardCharsets.UTF_8);
    }
}