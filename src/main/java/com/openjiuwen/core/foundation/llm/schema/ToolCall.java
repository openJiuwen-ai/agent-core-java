/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Represents a tool call from LLM output.
 * <p>
 * Mirrors Python's {@code ToolCall} model from the foundation LLM schema.
 *
 * @see <a href="https://platform.openai.com/docs/api-reference/chat/object">OpenAI Tool Call</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCall implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Tool call ID. */
    private String id;

    /** Tool call type (e.g., "function"). */
    @Builder.Default
    private String type = "function";

    /** Tool name. */
    private String name;

    /** Tool arguments as JSON string. */
    private String arguments;

    /** Tool call index, used to distinguish multiple tool calls in a single response. */
    @JsonProperty("index")
    private Integer index;
}
