/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's helper behavior in
 * {@code openjiuwen/extensions/context_evolver/summary/task/ace/utils.py}.
 */
class AceUtilsTest {

    @Test
    void parsesDirectJsonObject() {
        assertEquals(Map.of("answer", 1), AceUtils.safeJsonLoads("{\"answer\": 1}"));
    }

    @Test
    void parsesJsonInsideMarkdownCodeFence() {
        String response = """
                ```json
                {
                  "status": "ok"
                }
                ```
                """;

        assertEquals(Map.of("status", "ok"), AceUtils.safeJsonLoads(response));
    }

    @Test
    void parsesEmbeddedJsonObjectFromPlainText() {
        String response = "summary => {\"score\": 7, \"label\": \"good\"}";

        assertEquals(Map.of("score", 7, "label", "good"), AceUtils.safeJsonLoads(response));
    }

    @Test
    void invalidJsonRaisesIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> AceUtils.safeJsonLoads("not-json"));
    }
}
