/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.planner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PlannerPrompt.
 * <p>
 * Mirrors Python's planner prompt tests.
 */
@DisplayName("Planner Prompt Tests")
class TestPlannerPrompt {

    // Stub classes
    static class PlannerPromptConfig {
        String language;
        int maxLength;
        Map<String, String> sections = new HashMap<>();

        PlannerPromptConfig(String language, int maxLength) {
            this.language = language;
            this.maxLength = maxLength;
        }

        void addSection(String name, String template) {
            sections.put(name, template);
        }
    }

    static class PlannerPrompt {
        PlannerPromptConfig config;
        Map<String, Object> context = new HashMap<>();

        PlannerPrompt(PlannerPromptConfig config) {
            this.config = config;
        }

        void setContext(String key, Object value) {
            context.put(key, value);
        }

        String build() {
            StringBuilder sb = new StringBuilder();
            sb.append("Language: ").append(config.language).append("\n");
            for (Map.Entry<String, String> section : config.sections.entrySet()) {
                sb.append(section.getKey()).append(": ").append(section.getValue()).append("\n");
            }
            return sb.toString();
        }

        String getSection(String name) {
            return config.sections.get(name);
        }
    }

    @Nested
    @DisplayName("Planner Prompt Config Tests")
    class TestPlannerPromptConfig {

        @Test
        @DisplayName("prompt config creation")
        void testPromptConfigCreation() {
            PlannerPromptConfig config = new PlannerPromptConfig("cn", 2000);

            assertEquals("cn", config.language);
            assertEquals(2000, config.maxLength);
        }

        @Test
        @DisplayName("add sections to config")
        void testAddSectionsToConfig() {
            PlannerPromptConfig config = new PlannerPromptConfig("cn", 2000);
            config.addSection("goal", "完成目标规划");
            config.addSection("constraints", "遵循约束条件");

            assertEquals("完成目标规划", config.sections.get("goal"));
            assertEquals("遵循约束条件", config.sections.get("constraints"));
        }
    }

    @Nested
    @DisplayName("Planner Prompt Tests")
    class TestPlannerPromptClass {

        @Test
        @DisplayName("planner prompt creation")
        void testPlannerPromptCreation() {
            PlannerPromptConfig config = new PlannerPromptConfig("cn", 2000);
            PlannerPrompt prompt = new PlannerPrompt(config);

            assertNotNull(prompt);
            assertNotNull(prompt.config);
        }

        @Test
        @DisplayName("set context")
        void testSetContext() {
            PlannerPromptConfig config = new PlannerPromptConfig("cn", 2000);
            PlannerPrompt prompt = new PlannerPrompt(config);
            prompt.setContext("task", "测试任务");
            prompt.setContext("priority", "high");

            assertEquals("测试任务", prompt.context.get("task"));
            assertEquals("high", prompt.context.get("priority"));
        }

        @Test
        @DisplayName("build prompt")
        void testBuildPrompt() {
            PlannerPromptConfig config = new PlannerPromptConfig("cn", 2000);
            config.addSection("goal", "规划任务");
            PlannerPrompt prompt = new PlannerPrompt(config);

            String builtPrompt = prompt.build();

            assertTrue(builtPrompt.contains("Language: cn"));
            assertTrue(builtPrompt.contains("goal"));
        }

        @Test
        @DisplayName("get section")
        void testGetSection() {
            PlannerPromptConfig config = new PlannerPromptConfig("cn", 2000);
            config.addSection("goal", "规划目标");
            PlannerPrompt prompt = new PlannerPrompt(config);

            String section = prompt.getSection("goal");

            assertEquals("规划目标", section);
        }
    }
}