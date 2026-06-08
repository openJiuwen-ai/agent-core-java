/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/prompts/test_external_memory.py}.
 */
class ExternalMemorySectionTest {

    @Test
    void testBuildWithValidPromptBlock() {
        String promptBlock = "Use memory tools to store and retrieve information.";
        PromptSection section = ExternalMemorySection.buildExternalMemorySection(promptBlock, "en");
        assertNotNull(section);
        assertEquals(SectionName.EXTERNAL_MEMORY, section.getName());
        assertEquals(promptBlock, section.getContent().get("en"));
        assertEquals(55, section.getPriority());
    }

    @Test
    void testBuildWithCnLanguage() {
        String promptBlock = "使用记忆工具存储和检索信息。";
        PromptSection section = ExternalMemorySection.buildExternalMemorySection(promptBlock, "cn");
        assertNotNull(section);
        assertEquals(promptBlock, section.getContent().get("cn"));
    }

    @Test
    void testBuildWithEmptyPromptBlock() {
        assertNull(ExternalMemorySection.buildExternalMemorySection("", "en"));
    }

    @Test
    void testBuildWithNullPromptBlock() {
        assertNull(ExternalMemorySection.buildExternalMemorySection(null, "en"));
    }
}
