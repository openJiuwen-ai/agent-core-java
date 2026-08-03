/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's coding-memory prompt-section behavior for DeepAgent.
 */
class CodingMemorySectionTest {

    @Test
    void buildCodingMemorySectionUsesWritablePromptByDefault() {
        PromptSection section = CodingMemorySection.buildCodingMemorySection("en", false, "coding_memory/");

        assertEquals(SectionName.MEMORY, section.getName());
        assertEquals(85, section.getPriority());
        assertTrue(section.getContent().get("en").contains("persistent, file-based memory system"));
        assertTrue(section.getContent().get("en").contains("coding_memory/"));
    }

    @Test
    void buildCodingMemorySectionUsesReadOnlyPromptWhenRequested() {
        PromptSection section = CodingMemorySection.buildCodingMemorySection("en", true, "readonly/");

        assertTrue(section.getContent().get("en").contains("read-only"));
        assertTrue(section.getContent().get("en").contains("readonly/"));
    }
}
