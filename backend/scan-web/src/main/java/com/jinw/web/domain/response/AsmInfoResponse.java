package com.jinw.web.domain.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
public class AsmInfoResponse {

    @Schema(description = "汇编问题信息")
    private Map<String, Long> asmInfo;

    @Schema(description = "汇编架构信息")
    private Map<String, Long> instructionSetCodeCount;
}
