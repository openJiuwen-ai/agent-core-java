/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Payload for user input response.
 * <p>
 * Aligned with Python {@code AskUserPayload}.
 *
 * @since 0.1.14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AskUserPayload implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Question text to answer mapping.
     */
    @Builder.Default
    private Map<String, String> answers = new LinkedHashMap<>();

    /**
     * JSON Schema for resume payload, mirroring Python {@code AskUserPayload.to_schema()}.
     *
     * @return schema map
     */
    public static Map<String, Object> toSchema() {
        Map<String, Object> answersProp = new LinkedHashMap<>();
        answersProp.put("type", "object");
        answersProp.put("additionalProperties", Map.of("type", "string"));
        answersProp.put("description", "Question text to answer mapping");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("answers", answersProp);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("title", "AskUserPayload");
        return schema;
    }
}
