package com.jinw.web.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jinw.web.base.BaseEntity;
import com.jinw.web.config.handler.EncryptTypeHandler;
import lombok.Data;

/**
 * @author kingow
 * @Description: 操作系统规则
 */
@TableName("b_t_os")
@Data
public class BTOs extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 标题
     */
    @JsonProperty("TITLE")
    @TableField(value = "TITLE", typeHandler = EncryptTypeHandler.class)
    private String title;

    /**
     * 文件
     */
    @JsonProperty("DOCUMENT")
    @TableField(value = "DOCUMENT", typeHandler = EncryptTypeHandler.class)
    private String document;

    /**
     * 部门ID
     */
    @JsonProperty("ORG_ID")
    private String orgId;
}
