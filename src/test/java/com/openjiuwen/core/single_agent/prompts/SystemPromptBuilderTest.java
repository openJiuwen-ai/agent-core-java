/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.prompts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemPromptBuilderTest {

    @Test
    @DisplayName("prompt sections render with language fallback")
    void testPromptSectionRenderFallback() {
        PromptSection section = new PromptSection(
                "role",
                Map.of("en", "Hello", "cn", "你好"),
                10
        );
        PromptSection fallbackSection = new PromptSection(
                "fallback",
                Map.of("en", "Hello"),
                20
        );

        assertEquals("你好", section.render("cn"));
        assertEquals("你好".length(), section.charCount("cn"));
        assertEquals("Hello", fallbackSection.render("jp"));
    }

    @Test
    @DisplayName("builder sorts by priority and skips blank sections")
    void testBuilderBuild() {
        SystemPromptBuilder builder = new SystemPromptBuilder("en");
        builder.addSection(new PromptSection("later", Map.of("en", "second"), 20));
        builder.addSection(new PromptSection("blank", Map.of("en", "   "), 30));
        builder.addSection(new PromptSection("first", Map.of("en", "first"), 10));

        assertEquals("first\n\nsecond", builder.build());
    }

    @Test
    @DisplayName("builder keeps insertion order for equal priorities and supports section lookup")
    void testBuilderSectionManagement() {
        SystemPromptBuilder builder = new SystemPromptBuilder();
        Map<String, String> zhOnly = new LinkedHashMap<>();
        zhOnly.put("cn", "甲");
        zhOnly.put("en", "A");

        builder.addSection(new PromptSection("a", zhOnly, 10))
                .addSection(new PromptSection("b", Map.of("cn", "乙", "en", "B"), 10));

        assertTrue(builder.hasSection("a"));
        assertTrue(builder.getSection("b").isPresent());
        assertEquals("甲\n\n乙", builder.build());

        builder.removeSection("a");
        assertFalse(builder.hasSection("a"));
        assertEquals("乙", builder.build());
    }
}
