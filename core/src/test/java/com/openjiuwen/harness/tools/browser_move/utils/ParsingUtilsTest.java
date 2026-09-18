/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParsingUtilsTest {

    @Test
    void extractJsonObjectHandlesFencedJson() {
        assertEquals(
                Map.of("ok", true, "value", 1),
                ParsingUtils.extractJsonObject("```json\n{\"ok\": true, \"value\": 1}\n```")
        );
    }

    @Test
    void extractJsonObjectUnwrapsStringifiedJson() {
        assertEquals(
                Map.of("ok", true),
                ParsingUtils.extractJsonObject("\"{\\\"ok\\\": true}\"")
        );
    }

    @Test
    void sanitizeJsonSchemaStripsUnsupportedKeysAndCollapsesNullableAnyOf() {
        Object sanitized = ParsingUtils.sanitizeJsonSchema(Map.of(
                "$schema", "https://json-schema.org",
                "properties", Map.of(
                        "name", Map.of(
                                "anyOf", List.of(Map.of("type", "string"), Map.of("type", "null"))
                        )
                )
        ));

        Map<?, ?> root = assertInstanceOf(Map.class, sanitized);
        Map<?, ?> properties = assertInstanceOf(Map.class, root.get("properties"));
        Map<?, ?> nameSchema = assertInstanceOf(Map.class, properties.get("name"));
        assertEquals("string", nameSchema.get("type"));
    }
}
