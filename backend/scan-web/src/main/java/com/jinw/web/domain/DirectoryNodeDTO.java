package com.jinw.web.domain;

import com.jinw.web.enums.IssueLevel;
import com.jinw.web.enums.NodeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "目录查询-DTO")
public class DirectoryNodeDTO {
    @Schema(description = "目录/文件名称")
    private String name;
    @Schema(description = "相对路径")
    private String path;
    @Schema(description = "节点类型")
    private NodeType type;
    @Schema(description = "节点级别")
    private IssueLevel level;
    @Schema(description = "当前节点下的问题数量")
    private Integer errCount;
    @Schema(description = "子节点数量")
    private Integer childrenCount;

    public DirectoryNodeDTO(String name, String path, NodeType type, IssueLevel level, Integer errCount, Integer childrenCount) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.level = level;
        this.errCount = errCount;
        this.childrenCount = childrenCount;
    }
}