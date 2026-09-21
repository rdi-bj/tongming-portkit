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
@TableName("b_t_corpus_jsonl")
@Schema(description = "语料详细信息-DTO")
public class BTCorpusJsonl extends BaseEntity {
    @Schema(description = "语料ID")
    private String corpusId;

    @Schema(description = "来源类型")
    private String type;

    @Schema(description = "语言类型")
    private String language;

    @Schema(description = "源架构")
    private String sourceChip;

    @Schema(description = "目标架构")
    private String targetChip;

    @Schema(description = "源代码")
    private String sourceCode;

    @Schema(description = "适配后代码")
    private String targetCode;

    @Schema(description = "上下文描述")
    private String description;
}
