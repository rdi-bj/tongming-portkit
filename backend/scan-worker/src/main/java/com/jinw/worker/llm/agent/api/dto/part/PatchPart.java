package com.jinw.worker.llm.agent.api.dto.part;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
public class PatchPart extends Part {
    private String hash;
    private List<String> files;
}