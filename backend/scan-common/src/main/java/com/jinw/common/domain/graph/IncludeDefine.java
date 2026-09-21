package com.jinw.common.domain.graph;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 头文件引用
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IncludeDefine extends SourceLoc {

    /**
     * 头文件路径（相对 / 绝对）
     */
    private String path;

    /**
     * 是否系统头文件
     */
    private boolean systemHeader;
}