package com.jinw.corpus.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jinw.web.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("b_t_corpus_info")
@Schema(description = "语料信息-DTO")
public class BTCorpusInfo extends BaseEntity {
    @Schema(description = "项目名称")
    private String name;

    @Schema(description = "项目的源码名称")
    private String fileName;

    @Schema(description = "文件MD5值")
    private String fileMd5;

    @Schema(description = "任务ID")
    private String taskId;
}
