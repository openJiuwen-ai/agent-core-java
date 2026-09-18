/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HeadersHelperTest {

    @Test
    void mergeHeadersCaseInsensitivePreservesOriginalKeyCasing() {
        Map<String, String> base = new LinkedHashMap<>();
        base.put("X-Trace-Id", "old");

        HeadersHelper.mergeHeadersCaseInsensitive(base, Map.of("x-trace-id", "new", "X-Other", 1));

        assertEquals(Map.of("X-Trace-Id", "new", "X-Other", "1"), base);
    }

    @Test
    void buildBaseHeadersSanitizesProtectedAndBlankValues() {
        Map<String, String> headers = HeadersHelper.buildBaseHeaders(Map.of(
                "Authorization", "secret",
                "X-Test", "value"
        ));

        assertEquals("value", headers.get("X-Test"));
        assertFalse(headers.containsKey("Authorization"));
    }

    @Test
    void mergeRequestHeadersSanitizesBaseAndRequestLevels() {
        Map<String, String> merged = HeadersHelper.mergeRequestHeaders(
                Map.of("X-Test", "base", "Authorization", "secret"),
                Map.of("x-test", "override", "X-Next", "2")
        );

        assertEquals("override", merged.get("X-Test"));
        assertEquals("2", merged.get("X-Next"));
        assertFalse(merged.containsKey("Authorization"));
    }
}
