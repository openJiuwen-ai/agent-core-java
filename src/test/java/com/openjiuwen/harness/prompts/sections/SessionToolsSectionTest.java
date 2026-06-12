/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code session_tools} section contract in
 * {@code openjiuwen/harness/prompts/sections/session_tools.py}.
 */
class SessionToolsSectionTest {

    @Test
    void buildSessionToolsSystemPromptSupportsEnglish() {
        String prompt = SessionToolsSection.buildSessionToolsSystemPrompt("en");

        assertTrue(prompt.contains("temporary subagents"));
        assertTrue(prompt.contains("status of pending"));
    }

    @Test
    void buildSessionToolsSystemPromptFallsBackToChinese() {
        String prompt = SessionToolsSection.buildSessionToolsSystemPrompt("fr");

        assertTrue(prompt.contains("sessions_spawn"));
        assertTrue(prompt.contains("pending"));
    }

    @Test
    void buildSessionToolsSectionUsesSessionToolsNameAndPriority() {
        PromptSection section = SessionToolsSection.buildSessionToolsSection("en");

        assertEquals(SectionName.SESSION_TOOLS, section.getName());
        assertEquals(85, section.getPriority());
        assertTrue(section.render("en").contains("subagents"));
    }
}
