/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/prompts/test_task_completion.py}
 * for the builder-level prompt section behavior.
 */
class TaskCompletionSectionTest {

    @Test
    void testBuildCompletionSignalSectionCn() {
        PromptSection section = TaskCompletionSection.buildCompletionSignalSection("cn", "TASK_DONE");
        assertEquals(SectionName.COMPLETION_SIGNAL, section.getName());
        assertEquals(85, section.getPriority());
        assertTrue(section.getContent().get("cn").contains("<promise>TASK_DONE</promise>"));
        assertTrue(section.getContent().get("cn").contains("完成信号"));
    }

    @Test
    void testBuildCompletionSignalSectionEn() {
        PromptSection section = TaskCompletionSection.buildCompletionSignalSection("en", "TASK_DONE");
        assertTrue(section.getContent().get("en").contains("<promise>TASK_DONE</promise>"));
        assertTrue(section.getContent().get("en").contains("Completion Signal"));
    }

    @Test
    void testUnknownLanguageFallsBackToCnTemplate() {
        PromptSection section = TaskCompletionSection.buildCompletionSignalSection("fr", "TASK_DONE");
        assertTrue(section.getContent().get("fr").contains("完成信号"));
    }
}
