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
@TableName("b_t_lib")
@Data
public class BTLib extends BaseEntity {

    private static final long serialVersionUID = 1L;
    /**
     * 支持RISC-V
     */
    @JsonProperty("SUPPORT_RISCV")
    private String supportRiscv;
    /**
     * 库名
     */
    @JsonProperty("LIB_NAME")
    @TableField(value = "LIB_NAME", typeHandler = EncryptTypeHandler.class)
    private String libName;
    /**
     * 类别
     */
    @JsonProperty("LIB_CATEGORY")
    @TableField(value = "LIB_CATEGORY", typeHandler = EncryptTypeHandler.class)
    private String libCategory;
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

    /**
     * 是否需要AI适配
     */
    @JsonProperty("SUPPORT_ADAPT")
    private String supportAdapt;

}
