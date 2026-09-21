package com.jinw.web.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jinw.web.base.BaseEntity;
import com.jinw.web.config.handler.EncryptTypeHandler;
import lombok.Data;

/**
 * @author kingow
 * @Description: 构建系统规则
 */
@TableName("b_t_cmake")
@Data
public class BTCmake extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 所属架构
     */
    @JsonProperty("FRAMEWORK")
    @TableField(value = "FRAMEWORK", typeHandler = EncryptTypeHandler.class)
    private String framework;

    /**
     * 关键词
     */
    @JsonProperty("KEYWORD")
    @TableField(value = "KEYWORD", typeHandler = EncryptTypeHandler.class)
    private String keyword;

    /**
     * 部门ID
     */
    @JsonProperty("ORG_ID")
    private String orgId;
}
