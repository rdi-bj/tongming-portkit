package com.jinw.web.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jinw.web.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("b_t_app_info")
@Schema(description = "项目信息-DTO")
public class BTAppInfo extends BaseEntity {
    @Schema(description = "项目名称")
    private String name;

    @Schema(description = "项目描述")
    private String description;

    @Schema(description = "项目的源码名称")
    private String fileName;

    @Schema(description = "项目的源码存储路径")
    private String filePath;

    @Schema(description = "项目对应的任务ID")
    private String taskId;

    @Schema(description = "项目文件信息")
    private String fileInfo;

    @Schema(description = "项目状态")
    private String status;

    @Schema(description = "opencode SessionId")
    private String sessionId;

    @Schema(description = "已适配文件数量")
    private Integer adaptQuestionFile;

    @Schema(description = "总需适配文件数量")
    private Integer totalQuestionFile;

    @Schema(description = "已适配问题数量")
    private Integer adaptQuestion;

    @Schema(description = "总需适配问题数量")
    private Integer totalQuestion;

    @Schema(description = "项目扫描类型")
    private String scanType;

    @Schema(description = "是否已经开启批量适配")
    private String adaptStatus;

    @Schema(description = "文件MD5值")
    private String fileMd5;

    @Schema(description = "扫描开始时间")
    private String scanTime;

    @Schema(description = "系统名称")
    private String systemName;

    @Schema(description = "系统版本")
    private String systemVersion;
}
