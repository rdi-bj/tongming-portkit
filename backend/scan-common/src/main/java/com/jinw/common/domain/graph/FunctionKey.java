package com.jinw.common.domain.graph;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 函数唯一标识（C 语义）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class FunctionKey implements Serializable {

    /**
     * 定义所在文件（null 表示只有声明）
     */
    private String definedInFile;

    /**
     * 函数名
     */
    private String functionName;
}