/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code test_agent_mode} expectations for
 * {@code openjiuwen/harness/schema/agent_mode.py}.
 */
class AgentModeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testEnumValues() {
        assertEquals("plan", AgentMode.PLAN.value());
        assertEquals("normal", AgentMode.NORMAL.value());
    }

    @Test
    void testFromValueDefaultsToNormal() {
        assertEquals(AgentMode.PLAN, AgentMode.fromValue("plan"));
        assertEquals(AgentMode.NORMAL, AgentMode.fromValue("normal"));
        assertEquals(AgentMode.NORMAL, AgentMode.fromValue(null));
        assertEquals(AgentMode.NORMAL, AgentMode.fromValue(""));
        assertEquals(AgentMode.NORMAL, AgentMode.fromValue("unexpected"));
    }

    @Test
    void testJacksonRoundTrip() throws Exception {
        assertEquals("\"plan\"", mapper.writeValueAsString(AgentMode.PLAN));
        assertEquals(AgentMode.NORMAL, mapper.readValue("\"normal\"", AgentMode.class));
    }
}
