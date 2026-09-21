package com.jinw.common.domain.graph;

import lombok.Data;

/**
 * 宏定义
 */
@Data
public class MacroDefine extends SourceLoc {

    /**
     * 宏名
     */
    private String name;

    /**
     * 宏值（可为空）
     */
    private String value;

    /**
     * 是否函数宏
     */
    private boolean functionLike;

    /**
     * 是否 unsafe（包含 asm / 内存操作）
     */
    private boolean unsafe;
}