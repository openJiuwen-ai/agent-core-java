/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for generator integration.
 * <p>
 * Mirrors Python's {@code test_generator_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.llm_agent}.
 */
class TestGeneratorIntegration {

    private final Generator generator = new Generator(null);

    @Test
    void generatorInitialization() {
        assertThat(generator.getLlm()).isNull();
    }

    @Test
    void extractElementsConstant() {
        assertThat(Generator.EXTRACT_ELEMENTS)
                .containsKeys("name", "description", "prompt", "opening_remarks", "question");
    }

    @Test
    void parseInfoEmpty() {
        Map<String, Object> result = Generator.parseInfo("");
        assertThat(result).containsKeys("name", "description", "prompt");
    }

    @Test
    void parseInfoWithContent() {
        Map<String, Object> result = Generator.parseInfo("""
                <角色名称>Test Agent</角色名称>
                <角色描述>Test Description</角色描述>
                <提示词>Test Prompt</提示词>
                <智能体开场白>Hello</智能体开场白>
                <预置问题>Question?</预置问题>
                """);

        assertThat(result).containsEntry("name", "Test Agent");
        assertThat(result).containsEntry("description", "Test Description");
        assertThat(result).containsEntry("prompt", "Test Prompt");
        assertThat(result).containsEntry("opening_remarks", "Hello");
        assertThat(result).containsEntry("question", "Question?");
    }

    @Test
    void parseInfoWithQuotes() {
        Map<String, Object> result = Generator.parseInfo("<角色名称>\"Quoted Name\"</角色名称>");
        assertThat(result).containsEntry("name", "Quoted Name");
    }

    @Test
    void nameElement() {
        assertThat(Generator.EXTRACT_ELEMENTS.get("name")).isEqualTo("角色名称");
    }

    @Test
    void descriptionElement() {
        assertThat(Generator.EXTRACT_ELEMENTS.get("description")).isEqualTo("角色描述");
    }

    @Test
    void promptElement() {
        assertThat(Generator.EXTRACT_ELEMENTS.get("prompt")).isEqualTo("提示词");
    }

    @Test
    void openingRemarksElement() {
        assertThat(Generator.EXTRACT_ELEMENTS.get("opening_remarks")).isEqualTo("智能体开场白");
    }

    @Test
    void questionElement() {
        assertThat(Generator.EXTRACT_ELEMENTS.get("question")).isEqualTo("预置问题");
    }
}
