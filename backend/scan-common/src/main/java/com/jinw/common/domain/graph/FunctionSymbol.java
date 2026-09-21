package com.jinw.common.domain.graph;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * 函数符号（带完整源码位置）
 */
@Data
public class FunctionSymbol extends SourceLoc {

    /**
     * 函数唯一标识
     */
    private FunctionKey key;

    /**
     * 函数名（冗余）
     */
    private String name;

    /**
     * 定义所在文件
     */
    private String definedInFile;

    /**
     * 声明出现文件
     */
    private Set<String> declaredInFiles = new HashSet<>();

    /**
     * 是否 static
     */
    private boolean isStatic;

    /**
     * 是否 extern
     */
    private boolean isExtern;

    /**
     * 是否宏展开
     */
    private boolean isMacro;

    /**
     * 调用了谁
     */
    private Set<FunctionKey> callees = new HashSet<>();

    /**
     * 被谁调用
     */
    private Set<FunctionKey> callers = new HashSet<>();

    public FunctionKey ensureKey() {
        if (this.key == null
                && this.definedInFile != null
                && this.name != null) {
            this.key = new FunctionKey(this.definedInFile, this.name);
        }
        return this.key;
    }
}