/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Tool information descriptor for LLM function calling.
 * <p>
 * Mirrors Python's {@code ToolInfo} in
 * {@code openjiuwen/core/foundation/tool/schema.py}.
 * Follows the OpenAI function calling format.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolInfo {

    /** Tool type, defaults to "function". */
    @Builder.Default
    private String type = "function";

    /** Tool name. */
    @Builder.Default
    private String name = "";

    /** Tool description. */
    @Builder.Default
    private String description = "";

    /**
     * Parameter schema — follows JSON Schema format.
     * <p>
     * Example: {@code {"type": "object", "properties": {"query": {"type": "string"}}}}
     */
    @Builder.Default
    private Map<String, Object> parameters = Map.of();
}
