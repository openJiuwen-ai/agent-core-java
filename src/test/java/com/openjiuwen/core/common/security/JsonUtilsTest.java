/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's behavior around
 * {@code openjiuwen/core/common/security/json_utils.py}.
 */
class JsonUtilsTest {

    @Test
    void safeJsonLoadsReturnsParsedJsonWhenInputIsValid() {
        Object value = JsonUtils.safeJsonLoads("{\"items\":[1,2],\"enabled\":true}");

        assertInstanceOf(Map.class, value);
        assertEquals(List.of(1, 2), ((Map<?, ?>) value).get("items"));
    }

    @Test
    void safeJsonLoadsRaisesFrameworkErrorWhenDefaultIsNull() {
        BaseError error = assertThrows(
                BaseError.class,
                () -> JsonUtils.safeJsonLoads("{invalid")
        );

        assertEquals(StatusCode.COMMON_JSON_INPUT_PROCESS_ERROR, error.getStatus());
    }

    @Test
    void safeJsonLoadsReturnsDefaultWhenFallbackIsProvided() {
        Map<String, Object> fallback = Map.of("fallback", true);

        assertEquals(fallback, JsonUtils.safeJsonLoads("{invalid", fallback));
    }

    @Test
    void safeJsonDumpsRaisesFrameworkErrorForUnsupportedObject() {
        BaseError error = assertThrows(
                BaseError.class,
                () -> JsonUtils.safeJsonDumps(new UnsupportedBean())
        );

        assertEquals(StatusCode.COMMON_JSON_EXECUTION_PROCESS_ERROR, error.getStatus());
    }

    @Test
    void safeJsonDumpsReturnsDefaultWhenFallbackIsProvided() {
        Map<String, Object> selfReferencing = new HashMap<>();
        selfReferencing.put("self", selfReferencing);

        assertEquals("fallback-json", JsonUtils.safeJsonDumps(selfReferencing, "fallback-json"));
    }

    private static final class UnsupportedBean {
    }
}
