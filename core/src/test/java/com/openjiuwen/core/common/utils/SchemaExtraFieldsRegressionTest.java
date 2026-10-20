/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

class SchemaExtraFieldsRegressionTest {
    @Test
    void strictToolSchemaRejectsUnknownArgumentsWithValidationCode() {
        Map<String, Object> schema = Map.of("properties", Map.of("a", Map.of("type", "integer")));
        ValidationError failure = assertThrows(ValidationError.class,
                () -> SchemaUtils.validateWithSchema(Map.of("a", 1, "extra", 2), schema));
        assertEquals(StatusCode.SCHEMA_VALIDATE_INVALID.getCode(), failure.getCode());
        assertEquals("Extra field is not permitted: extra", failure.getCause().getMessage());
    }

    @Test
    void explicitAdditionalPropertyPolicyPreservesPythonAllowAndIgnoreBehavior() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("properties", Map.of("a", Map.of("type", "integer")));
        schema.put("additionalProperties", true);
        assertDoesNotThrow(() -> SchemaUtils.validateWithSchema(Map.of("a", 1, "extra", 2), schema));
        schema.put("additionalProperties", Map.of("type", "integer"));
        assertDoesNotThrow(() -> SchemaUtils.validateWithSchema(Map.of("a", 1, "extra", 2), schema));
        schema.put("additionalProperties", false);
        assertThrows(ValidationError.class,
                () -> SchemaUtils.validateWithSchema(Map.of("a", 1, "extra", 2), schema));
    }
}
