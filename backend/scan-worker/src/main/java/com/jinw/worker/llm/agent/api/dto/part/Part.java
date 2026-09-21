package com.jinw.worker.llm.agent.api.dto.part;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextPart.class, name = "text"),
        @JsonSubTypes.Type(value = SubtaskPart.class, name = "subtask"),
        @JsonSubTypes.Type(value = ReasoningPart.class, name = "reasoning"),
        @JsonSubTypes.Type(value = FilePart.class, name = "file"),
        @JsonSubTypes.Type(value = ToolPart.class, name = "tool"),
        @JsonSubTypes.Type(value = StepStartPart.class, name = "step-start"),
        @JsonSubTypes.Type(value = StepFinishPart.class, name = "step-finish"),
        @JsonSubTypes.Type(value = SnapshotPart.class, name = "snapshot"),
        @JsonSubTypes.Type(value = PatchPart.class, name = "patch"),
        @JsonSubTypes.Type(value = AgentPart.class, name = "agent"),
        @JsonSubTypes.Type(value = RetryPart.class, name = "retry"),
        @JsonSubTypes.Type(value = CompactionPart.class, name = "compaction")
})
public abstract class Part {
    private String id;
    private String sessionID;
    private String messageID;
    private String type;
    private String text;
}