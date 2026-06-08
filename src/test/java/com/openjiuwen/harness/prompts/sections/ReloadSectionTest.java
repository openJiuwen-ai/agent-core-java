/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's reload prompt-section behavior for DeepAgent.
 */
class ReloadSectionTest {

    @Test
    void testBuildReloadSectionCn() {
        PromptSection section = ReloadSection.buildReloadSection("cn");
        assertEquals("offload", section.getName());
        assertEquals(90, section.getPriority());
        assertTrue(section.getContent().get("cn").contains("上下文压缩"));
        assertTrue(section.getContent().get("cn").contains("reload_original_context_messages"));
    }

    @Test
    void testBuildReloadSectionNonCnUsesEnglishHint() {
        PromptSection section = ReloadSection.buildReloadSection("fr");
        assertTrue(section.getContent().get("fr").contains("Context Compression"));
        assertTrue(section.getContent().get("fr").contains("offload_handle"));
    }
}
