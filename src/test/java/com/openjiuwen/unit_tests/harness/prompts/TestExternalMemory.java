/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.prompts;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.sections.ExternalMemorySection;
import com.openjiuwen.harness.prompts.sections.SectionName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/prompts/test_external_memory.py}.
 */
class TestExternalMemory {

    @Test
    void testBuildWithValidPromptBlock() {
        String promptBlock = "Use memory tools to store and retrieve information.";

        PromptSection section = ExternalMemorySection.buildExternalMemorySection(promptBlock, "en");

        assertThat(section).isNotNull();
        assertThat(section.getName()).isEqualTo(SectionName.EXTERNAL_MEMORY);
        assertThat(section.getContent().get("en")).isEqualTo(promptBlock);
        assertThat(section.getPriority()).isEqualTo(55);
    }

    @Test
    void testBuildWithCnLanguage() {
        String promptBlock = "使用记忆工具存储和检索信息。";

        PromptSection section = ExternalMemorySection.buildExternalMemorySection(promptBlock, "cn");

        assertThat(section).isNotNull();
        assertThat(section.getContent().get("cn")).isEqualTo(promptBlock);
    }

    @Test
    void testBuildWithEmptyPromptBlock() {
        assertThat(ExternalMemorySection.buildExternalMemorySection("", "en")).isNull();
    }

    @Test
    void testBuildWithNullPromptBlock() {
        assertThat(ExternalMemorySection.buildExternalMemorySection(null, "en")).isNull();
    }
}
