/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SectionNameTest {

    @Test
    void testExpectedSectionNames() {
        assertEquals("identity", SectionName.IDENTITY);
        assertEquals("safety", SectionName.SAFETY);
        assertEquals("skills", SectionName.SKILLS);
        assertEquals("tools", SectionName.TOOLS);
        assertEquals("todo", SectionName.TODO);
        assertEquals("task_tool", SectionName.TASK_TOOL);
        assertEquals("tool_navigation", SectionName.TOOL_NAVIGATION);
        assertEquals("progressive_tool_rules", SectionName.PROGRESSIVE_TOOL_RULES);
        assertEquals("runtime", SectionName.RUNTIME);
        assertEquals("memory", SectionName.MEMORY);
        assertEquals("session_tools", SectionName.SESSION_TOOLS);
        assertEquals("mode_instructions", SectionName.MODE_INSTRUCTIONS);
        assertEquals("workspace", SectionName.WORKSPACE);
        assertEquals("heartbeat", SectionName.HEARTBEAT);
        assertEquals("context", SectionName.CONTEXT);
        assertEquals("external_memory", SectionName.EXTERNAL_MEMORY);
        assertEquals("completion_signal", SectionName.COMPLETION_SIGNAL);
        assertEquals("verification_contract", SectionName.VERIFICATION_CONTRACT);
    }
}
