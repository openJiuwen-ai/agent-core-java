/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code safety} section contract in
 * {@code openjiuwen/harness/prompts/sections/safety.py}.
 */
class SafetySectionTest {

    @Test
    void buildSafetySectionUsesEnglishPromptWhenRequested() {
        PromptSection section = SafetySection.buildSafetySection("en");

        assertEquals(SectionName.SAFETY, section.getName());
        assertEquals(20, section.getPriority());
        assertTrue(section.render("en").contains("# Safety"));
        assertTrue(section.render("en").contains("Never leak private data"));
    }

    @Test
    void buildSafetySectionFallsBackToChinesePrompt() {
        PromptSection section = SafetySection.buildSafetySection("fr");

        assertEquals(SectionName.SAFETY, section.getName());
        assertTrue(section.getContent().containsKey("cn"));
        assertTrue(section.render("cn").contains("# 瀹夊叏鍘熷垯"));
    }

    @Test
    void buildUsesDefaultChinesePrompt() {
        PromptSection section = SafetySection.build();

        assertEquals(SectionName.SAFETY, section.getName());
        assertEquals(20, section.getPriority());
        assertTrue(section.render("cn").contains("姘歌繙涓嶈娉勯湶闅愮鏁版嵁"));
    }
}
