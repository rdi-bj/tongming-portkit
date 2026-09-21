package com.jinw.web.domain.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
public class FrameworkPortraitResponse {

    @Schema(description = "问题信息")
    private Map<String, Integer> questionType;

    @Schema(description = "文件信息")
    private Map<String, Integer> fileTypeStats;
}
