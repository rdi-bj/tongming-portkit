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

@TableName("b_t_inline_asm")
@Data
public class BTInlineAsm extends BaseEntity {

    private static final long serialVersionUID = 1L;
    /**
     * 指令名称
     */
    @JsonProperty("INSTRUCTION_NAME")
    @TableField(value = "INSTRUCTION_NAME", typeHandler = EncryptTypeHandler.class)
    private String instructionName;
    /**
     * 所属指令集
     */
    @JsonProperty("INSTRUCTION_SET_CODE")
    @TableField(value = "INSTRUCTION_SET_CODE", typeHandler = EncryptTypeHandler.class)
    private String instructionSetCode;
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
