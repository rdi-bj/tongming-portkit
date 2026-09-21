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

@TableName("b_t_regexp")
@Data
public class BTDependency extends BaseEntity {

    private static final long serialVersionUID = 1L;
    /**
     * 正则
     */
    @JsonProperty("REGULAR")
    @TableField(value = "REGULAR", typeHandler = EncryptTypeHandler.class)
    private String regular;
    /**
     * 备注
     */
    @JsonProperty("REMARK")
    @TableField(value = "REMARK", typeHandler = EncryptTypeHandler.class)
    private String remark;
    /**
     * 所属库
     */
    @JsonProperty("LIB_ID")
    private String libId;
    /**
     * 部门ID
     */
    @JsonProperty("ORG_ID")
    private String orgId;

    /** 支持RISC-V */
//    @JsonProperty("SUPPORT_RISCV")
//    private String supportRiscv;

    /** 文件后缀 */
    @JsonProperty("FILE_SUFFIX")
    private String fileSuffix;

}
