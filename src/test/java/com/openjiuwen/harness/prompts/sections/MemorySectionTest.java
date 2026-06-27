/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's memory prompt-section behavior for DeepAgent.
 */
class MemorySectionTest {

    @Test
    void buildMemorySectionReadOnlyIncludesCurrentReadOnlyNotice() {
        PromptSection section = MemorySection.buildMemorySection("en", true, true);

        assertEquals(SectionName.MEMORY, section.getName());
        assertEquals(50, section.getPriority());
        assertTrue(section.getContent().get("en").contains("Read-Only Mode"));
        assertTrue(section.getContent().get("en").contains("Writing or modifying memory files is not allowed"));
    }

    @Test
    void buildMemorySectionProactiveIncludesSensitiveInfoRuleAndDateHint() {
        PromptSection section = MemorySection.buildMemorySection("en", false, true);
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();

        assertTrue(section.getContent().get("en").contains("Sensitive Information Conflict Resolution"));
        assertTrue(section.getContent().get("en").contains("Only user-related memory content is allowed"));
        assertTrue(section.getContent().get("en").contains("`" + today + ".md`"));
    }

    @Test
    void buildMemorySectionInactiveIncludesUserMdNotice() {
        PromptSection section = MemorySection.buildMemorySection("en", false, false);

        assertTrue(section.getContent().get("en").contains("Passive Mode"));
        assertTrue(section.getContent().get("en").contains("Only user-related memory content is allowed"));
    }
}
