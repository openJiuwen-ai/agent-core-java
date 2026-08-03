/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeContextTest {

    @Test
    void getSetAndDefaultMirrorPythonContextStorage() {
        RuntimeContext context = new RuntimeContext();
        context.set("name", "memory");
        context.set("count", 2);

        assertEquals("memory", context.get("name"));
        assertEquals(2, context.get("count"));
        assertEquals("fallback", context.get("missing", "fallback"));
    }

    @Test
    void toDictReturnsCopyAndToStringShowsPayload() {
        RuntimeContext context = new RuntimeContext();
        context.set("nested", Map.of("kind", "ace"));

        Map<String, Object> snapshot = context.toDict();
        snapshot.put("extra", true);

        assertEquals(Map.of("kind", "ace"), context.get("nested"));
        assertTrue(context.toString().contains("nested"));
        assertTrue(context.toString().startsWith("RuntimeContext("));
    }
}
