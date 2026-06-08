/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's task-tool prompt-section behavior for DeepAgent.
 */
class TaskToolSectionTest {

    @Test
    void testBuildTaskSystemPromptCn() {
        String prompt = TaskToolSection.buildTaskSystemPrompt("cn");
        assertTrue(prompt.contains("主动委派子代理"));
        assertTrue(prompt.contains("强制使用场景"));
        assertTrue(prompt.contains("task_tool"));
    }

    @Test
    void testBuildTaskSystemPromptUnknownLanguageFallsBackToCn() {
        String prompt = TaskToolSection.buildTaskSystemPrompt("fr");
        assertTrue(prompt.contains("主上下文窗口"));
    }

    @Test
    void testBuildTaskSectionEn() {
        PromptSection section = TaskToolSection.buildTaskSection("en");
        assertEquals(SectionName.TASK_TOOL, section.getName());
        assertEquals(85, section.getPriority());
        assertTrue(section.getContent().get("en").contains("protect your context window"));
    }
}
