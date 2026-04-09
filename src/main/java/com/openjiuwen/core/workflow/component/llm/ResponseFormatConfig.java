/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow.component.llm;

import java.util.Map;
import java.util.Set;

/**
 * Configuration model for LLM response format.
 * <p>
 * Mirrors Python's {@code ResponseFormatConfig} Pydantic model.
 * Validates that the response type is one of: text, markdown, json.
 */
public class ResponseFormatConfig {

    private static final Set<String> VALID_TYPES = Set.of("text", "markdown", "json");

    private final String responseType;

    public ResponseFormatConfig(String responseType) {
        if (responseType == null || !VALID_TYPES.contains(responseType)) {
            throw new IllegalArgumentException(
                    "responseType must be one of " + VALID_TYPES + ", got: " + responseType);
        }
        this.responseType = responseType;
    }

    public String getResponseType() {
        return responseType;
    }

    /**
     * Validate and create from a map (looks for "type" key).
     * Mirrors Python's {@code ResponseFormatConfig.model_validate(dict)}.
     */
    public static ResponseFormatConfig fromMap(Map<String, Object> map) {
        if (map == null) {
            throw new IllegalArgumentException("response format map must not be null");
        }
        Object type = map.get("type");
        if (!(type instanceof String)) {
            throw new IllegalArgumentException("response format must have a 'type' key of type String");
        }
        return new ResponseFormatConfig((String) type);
    }
}
