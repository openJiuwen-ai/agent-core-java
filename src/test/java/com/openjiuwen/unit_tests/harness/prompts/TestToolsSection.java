/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.prompts;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.tools.ToolDescriptionRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/prompts/test_tools_section.py}.
 */
class TestToolsSection {

    @Test
    void testReturnsNoneWhenNoDescriptions() {
        assertThat(ToolDescriptionRegistry.buildToolsSection(null, "cn")).isNull();
        assertThat(ToolDescriptionRegistry.buildToolsSection(Map.of(), "cn")).isNull();
    }

    @Test
    void testReturnsSectionWithDescriptions() {
        PromptSection section = ToolDescriptionRegistry.buildToolsSection(
                Map.of("todo_create", "Create todos", "todo_list", "List todos"),
                "cn"
        );

        assertThat(section).isNotNull();
        assertThat(section.getName()).isEqualTo("tools");
        assertThat(section.getPriority()).isEqualTo(40);
        String rendered = section.render("cn");
        assertThat(rendered).contains("todo_create");
        assertThat(rendered).contains("todo_list");
    }

    @Test
    void testEnLanguage() {
        PromptSection section = ToolDescriptionRegistry.buildToolsSection(Map.of("search", "Search the web"), "en");

        assertThat(section).isNotNull();
        String rendered = section.render("en");
        assertThat(rendered).contains("Available Tools");
        assertThat(rendered).contains("search");
    }

    @Test
    void testCnLanguageHeader() {
        PromptSection section = ToolDescriptionRegistry.buildToolsSection(Map.of("tool1", "desc1"), "cn");

        assertThat(section).isNotNull();
        assertThat(section.render("cn")).contains("可用工具");
    }
}
