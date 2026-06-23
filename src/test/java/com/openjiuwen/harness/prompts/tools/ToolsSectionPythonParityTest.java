/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.sections.SectionName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code TestBuildToolsSection} in
 * {@code tests/unit_tests/harness/prompts/test_tools_section.py}.</p>
 */
class ToolsSectionPythonParityTest {
    @Test
    void returnsNoneWhenNoDescriptions() {
        assertNull(HarnessPromptToolsPackage.buildToolsSection((Map<String, String>) null, "cn"));
        assertNull(HarnessPromptToolsPackage.buildToolsSection(Map.of(), "cn"));
    }

    @Test
    void returnsSectionWithDescriptions() {
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("todo_create", "Create todos");
        descriptions.put("todo_list", "List todos");

        PromptSection section = HarnessPromptToolsPackage.buildToolsSection(descriptions, "cn");

        assertNotNull(section);
        assertEquals(SectionName.TOOLS, section.getName());
        assertEquals(40, section.getPriority());
        String rendered = section.render("cn");
        assertTrue(rendered.contains("todo_create"));
        assertTrue(rendered.contains("todo_list"));
    }

    @Test
    void enLanguage() {
        PromptSection section = HarnessPromptToolsPackage.buildToolsSection(Map.of("search", "Search the web"), "en");

        assertNotNull(section);
        String rendered = section.render("en");
        assertTrue(rendered.contains("Available Tools"));
        assertTrue(rendered.contains("search"));
    }

    @Test
    void cnLanguageHeader() {
        PromptSection section = HarnessPromptToolsPackage.buildToolsSection(Map.of("tool1", "desc1"), "cn");

        assertNotNull(section);
        assertTrue(section.render("cn").contains("可用工具"));
    }
}
