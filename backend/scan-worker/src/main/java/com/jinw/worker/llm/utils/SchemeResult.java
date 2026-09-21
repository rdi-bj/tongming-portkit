package com.jinw.worker.llm.utils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchemeResult {
    @JsonProperty("written_file_path")
    private String writtenFilePath;
    private String status;   // "success" / "failed"
    private String message;
}