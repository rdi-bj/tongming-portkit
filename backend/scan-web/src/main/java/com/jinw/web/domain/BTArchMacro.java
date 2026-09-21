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

@TableName("b_t_arch_macro")
@Data
public class BTArchMacro extends BaseEntity {

    private static final long serialVersionUID = 1L;
    /**
     * 宏名称
     */
    @JsonProperty("MACRO_NAME")
    @TableField(value = "MACRO_NAME", typeHandler = EncryptTypeHandler.class)
    private String macroName;
    /**
     * 备注
     */
    @JsonProperty("REMARK")
    @TableField(value = "REMARK", typeHandler = EncryptTypeHandler.class)
    private String remark;
    /**
     * 部门ID
     */
    @JsonProperty("ORG_ID")
    private String orgId;

}
