package com.jinw.web.base;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

public class BaseEntity implements Serializable {
    @TableId(
            type = IdType.ASSIGN_UUID,
            value = "ID"
    )
    @Schema(description = "主键ID")
    protected String id;

    @TableField(
            value = "CREATE_USER",
            fill = FieldFill.INSERT
    )
    @Schema(description = "创建人")
    protected String createUser;

    @JsonFormat(
            pattern = "yyyy-MM-dd HH:mm:ss",
            timezone = "GMT+8"
    )
    @TableField(
            value = "CREATE_TIME",
            fill = FieldFill.INSERT
    )
    @Schema(description = "创建时间")
    protected String createTime;

    @TableField(
            value = "UPDATE_USER",
            fill = FieldFill.INSERT_UPDATE
    )
    @Schema(description = "更新人")
    protected String updateUser;

    @JsonFormat(
            pattern = "yyyy-MM-dd HH:mm:ss",
            timezone = "GMT+8"
    )
    @TableField(
            value = "UPDATE_TIME",
            fill = FieldFill.INSERT_UPDATE
    )
    @Schema(description = "更新时间")
    protected String updateTime;

    @TableLogic(
            value = "0",
            delval = "1"
    )
    @TableField(
            value = "IS_DELETE",
            fill = FieldFill.INSERT
    )
    @Schema(description = "逻辑删除标识符")
    protected String isDelete;

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCreateUser() {
        return this.createUser;
    }

    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    public String getUpdateUser() {
        return this.updateUser;
    }

    public void setUpdateUser(String updateUser) {
        this.updateUser = updateUser;
    }

    public String getCreateTime() {
        return this.createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return this.updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public String getIsDelete() {
        return this.isDelete;
    }

    public void setIsDelete(String isDelete) {
        this.isDelete = isDelete;
    }
}
