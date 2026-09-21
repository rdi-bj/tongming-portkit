package com.jinw.worker.ast.c;

import com.jinw.worker.ast.common.AbstractTreeSitterParser;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TreeSitterC;

import java.nio.charset.StandardCharsets;

/**
 * C 语言解析器
 *
 * <p>只负责：
 * <ul>
 *   <li>绑定 Tree‑sitter C 语言</li>
 *   <li>C 的函数定义 / static 判断</li>
 * </ul>
 */
public class CTreeSitterParser extends AbstractTreeSitterParser {

    @Override
    protected TSParser createParser() {
        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterC());
        return parser;
    }

    @Override
    protected boolean isFunctionDefinition(String type) {
        return "function_definition".equals(type);
    }

    @Override
    protected String extractFunctionName(TSNode node, byte[] bytes) {
        if (node == null || node.isNull()) {
            return null;
        }

        TSNode decl = node.getChildByFieldName("declarator");
        if (decl == null || decl.isNull()) {
            return null;
        }

        TSNode ident = decl.getChildByFieldName("declarator");
        if (ident == null || ident.isNull()) {
            return null;
        }

        int s = clamp(ident.getStartByte(), bytes.length);
        int e = clamp(ident.getEndByte(), bytes.length);

        if (e <= s) {
            return null;
        }

        return new String(bytes, s, e - s, StandardCharsets.UTF_8);
    }

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

            if (!"storage_class_specifier".equals(child.getType())) {
                continue;
            }

            TSNode kw = child.getChild(0);
            if (kw == null || kw.isNull()) {
                continue;
            }

            if ("static".equals(kw.getType())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean isAsm(TSNode node, byte[] bytes) {
        if (node.getType().contains("asm")) {
            return true;
        }
        if ("ERROR".equals(node.getType())) {
            String txt = new String(
                    bytes,
                    clamp(node.getStartByte(), bytes.length),
                    clamp(node.getEndByte(), bytes.length)
                            - clamp(node.getStartByte(), bytes.length),
                    StandardCharsets.UTF_8
            );
            return txt.contains("asm") || txt.contains("__asm");
        }
        return false;
    }
}