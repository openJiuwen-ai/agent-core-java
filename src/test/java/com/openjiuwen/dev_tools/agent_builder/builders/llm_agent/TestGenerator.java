/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Generator}.
 * <p>
 * Mirrors Python's {@code test_generator.py} in
 * {@code tests.unit_tests.dev_tools.agent_builder.builders.llm_agent.test_generator}.
 */
class TestGenerator {

    @Test
    void extractElementsDefinition() {
        assertThat(Generator.EXTRACT_ELEMENTS)
                .containsKeys("name", "description", "prompt", "opening_remarks", "question");
    }

    @Test
    void parseInfoWithValidContent() {
        String content = """
                <角色名称>测试助手</角色名称>
                <角色描述>这是一个测试助手</角色描述>
                <提示词>你是一个测试助手</提示词>
                <智能体开场白>你好！</智能体开场白>
                <预置问题>什么是测试？</预置问题>
                """;

        Map<String, Object> result = Generator.parseInfo(content);

        assertThat(result).containsEntry("name", "测试助手");
        assertThat(result).containsEntry("description", "这是一个测试助手");
        assertThat(result).containsEntry("prompt", "你是一个测试助手");
        assertThat(result).containsEntry("opening_remarks", "你好！");
        assertThat(result).containsEntry("question", "什么是测试？");
    }

    @Test
    void parseInfoWithQuotedContent() {
        Map<String, Object> result = Generator.parseInfo("<角色名称>\"测试助手\"</角色名称>");
        assertThat(result).containsEntry("name", "测试助手");
    }

    @Test
    void parseInfoWithMissingElement() {
        String content = """
                <角色名称>测试助手</角色名称>
                <角色描述>这是一个测试助手</角色描述>
                """;
        Map<String, Object> result = Generator.parseInfo(content);

        assertThat(result).containsEntry("name", "测试助手");
        assertThat(result).containsEntry("description", "这是一个测试助手");
        assertThat(result).containsEntry("prompt", "");
        assertThat(result).containsEntry("opening_remarks", "");
    }

    @Test
    void parseInfoWithPluginList() {
        Map<String, Object> result = Generator.parseInfo("""
                <角色名称>测试助手</角色名称>
                <选择的插件列表>["plugin_001", "plugin_002"]</选择的插件列表>
                """);
        assertThat(result).containsEntry("plugin", "[\"plugin_001\", \"plugin_002\"]");
    }

    @Test
    void parseInfoWithKnowledgeList() {
        Map<String, Object> result = Generator.parseInfo("<选择的知识库列表>[\"kb_001\"]</选择的知识库列表>");
        assertThat(result).containsEntry("knowledge", "[\"kb_001\"]");
    }

    @Test
    void parseInfoWithWorkflowList() {
        Map<String, Object> result = Generator.parseInfo("<选择的工作流列表>[\"wf_001\"]</选择的工作流列表>");
        assertThat(result).containsEntry("workflow", "[\"wf_001\"]");
    }

    @Test
    void parseInfoEmptyContent() {
        Map<String, Object> result = Generator.parseInfo("");

        assertThat(result).containsEntry("name", "");
        assertThat(result).containsEntry("description", "");
        assertThat(result).containsEntry("prompt", "");
        assertThat(result).containsEntry("plugin", "");
        assertThat(result).containsEntry("knowledge", "");
        assertThat(result).containsEntry("workflow", "");
    }

    @Test
    void parseInfoMultilineContent() {
        Map<String, Object> result = Generator.parseInfo("""
                <提示词>你是一个测试助手。
                你可以帮助用户进行测试。
                请保持友好。</提示词>
                """);

        assertThat((String) result.get("prompt")).contains("你是一个测试助手");
        assertThat((String) result.get("prompt")).contains("你可以帮助用户进行测试");
    }
}
