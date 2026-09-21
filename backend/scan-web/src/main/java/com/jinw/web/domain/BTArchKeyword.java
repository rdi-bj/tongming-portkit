package com.jinw.web.domain;


import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jinw.web.base.BaseEntity;
import com.jinw.web.config.handler.EncryptTypeHandler;
import lombok.Data;

/**
 * @author kingow
 * @Description:
 * @date 2026-05-09
 */

@TableName("b_t_arch_keyword")
@Data
public class BTArchKeyword extends BaseEntity {

    private static final long serialVersionUID = 1L;
    /**
     * 备注
     */
    @JsonProperty("REMARK")
    @TableField(value = "REMARK", typeHandler = EncryptTypeHandler.class)
    private String remark;
    /**
     * 关键词
     */
    @JsonProperty("KEYWORD")
    @TableField(value = "KEYWORD", typeHandler = EncryptTypeHandler.class)
    private String keyword;
    /**
     * 所属架构
     */
    @JsonProperty("ARCH_CODE")
    @TableField(value = "ARCH_CODE", typeHandler = EncryptTypeHandler.class)
    private String archCode;
    /**
     * 部门ID
     */
    @JsonProperty("ORG_ID")
    private String orgId;

}
