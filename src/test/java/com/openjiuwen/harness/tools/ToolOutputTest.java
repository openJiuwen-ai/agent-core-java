/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code ToolOutput} behavior in
 * {@code openjiuwen/harness/tools/base_tool.py}.
 */
class ToolOutputTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testFactoryHelpers() {
        ToolOutput success = ToolOutput.success(Map.of("path", "/tmp/demo"));
        assertTrue(success.isSuccess());
        assertEquals(Map.of("path", "/tmp/demo"), success.getData());
        assertNull(success.getError());

        ToolOutput failure = ToolOutput.failure("boom");
        assertEquals("boom", failure.getError());
        assertNull(failure.getData());
    }

    @Test
    void testJacksonRoundTrip() throws Exception {
        ToolOutput output = ToolOutput.of(false, Map.of("partial", true), "denied");
        String json = mapper.writeValueAsString(output);
        ToolOutput restored = mapper.readValue(json, ToolOutput.class);
        assertEquals(output.isSuccess(), restored.isSuccess());
        assertEquals(output.getData(), restored.getData());
        assertEquals(output.getError(), restored.getError());
    }
}
