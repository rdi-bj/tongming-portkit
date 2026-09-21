package com.jinw.common.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RiscvAnalysisResult {

    @JsonProperty("selected_scheme")
    private String selectedScheme;   // "A" / "B" / "C"

    private String reasoning;

    @JsonProperty("original_file_path")
    private String originalFilePath;

    @JsonProperty("suggested_riscv_file_path")
    private String suggestedRiscvFilePath;

    @JsonProperty("modification_summary")
    private String modificationSummary;

}