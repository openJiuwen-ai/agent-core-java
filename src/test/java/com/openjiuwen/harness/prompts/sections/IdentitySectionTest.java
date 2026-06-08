/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's identity prompt-section behavior for DeepAgent.
 */
class IdentitySectionTest {

    @Test
    void testBuildIdentitySection() {
        PromptSection section = IdentitySection.buildIdentitySection("en");
        assertEquals(SectionName.IDENTITY, section.getName());
        assertEquals(10, section.getPriority());
        assertTrue(section.getContent().get("cn").contains("通用 AI 助手"));
        assertTrue(section.getContent().get("en").contains("general-purpose AI assistant"));
    }
}
