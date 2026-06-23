/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's tests in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/llm_agent/test_clarifier.py}.
 */
class ClarifierPythonParityTest {

    @Test
    void resourceConfigHasPlugin() throws Exception {
        Object plugin = resourceConfig().get("plugin");

        assertThat(plugin).isNotNull();
        assertThat(component(plugin, "label")).isEqualTo("插件");
        assertThat(component(plugin, "idKey")).isEqualTo("tool_id");
        assertThat(component(plugin, "nameKey")).isEqualTo("tool_name");
        assertThat(component(plugin, "descKey")).isEqualTo("tool_desc");
    }

    @Test
    void resourceConfigHasKnowledge() throws Exception {
        Object knowledge = resourceConfig().get("knowledge");

        assertThat(knowledge).isNotNull();
        assertThat(component(knowledge, "label")).isEqualTo("知识库");
    }

    @Test
    void resourceConfigHasWorkflow() throws Exception {
        Object workflow = resourceConfig().get("workflow");

        assertThat(workflow).isNotNull();
        assertThat(component(workflow, "label")).isEqualTo("工作流");
    }

    @Test
    void parseResourceOutputNoSection() {
        Clarifier.ParseResult result = Clarifier.parseResourceOutput(
                "Some text without resource planning section",
                Map.of("plugins", List.of()));

        assertThat(result.displayContent()).isEmpty();
        assertThat(result.resourceIdDict()).isEmpty();
    }

    @Test
    void parseResourceOutputWithPlugins() {
        Clarifier.ParseResult result = Clarifier.parseResourceOutput("""
                ## Agent资源规划

                【选择的插件】
                [{"tool_id": "tool_001", "tool_name": "Test Tool", "tool_desc": "A test tool"}]
                """, Map.of("plugins", List.of(
                Map.of("tool_id", "tool_001", "tool_name", "Test Tool", "tool_desc", "A test tool"))));

        assertThat(result.displayContent()).contains("插件");
        assertThat(result.resourceIdDict()).containsEntry("plugin", List.of("tool_001"));
    }

    @Test
    void parseResourceOutputWithInvalidToolId() {
        Clarifier.ParseResult result = Clarifier.parseResourceOutput("""
                ## Agent资源规划

                【选择的插件】
                [{"tool_id": "invalid_tool", "tool_name": "Invalid", "tool_desc": "Invalid tool"}]
                """, Map.of("plugins", List.of(
                Map.of("tool_id", "tool_001", "tool_name", "Valid Tool", "tool_desc", "A valid tool"))));

        assertThat(result.resourceIdDict()).doesNotContainKey("plugin");
        assertThat(result.resourceIdDict().getOrDefault("plugin", List.of())).doesNotContain("invalid_tool");
    }

    @Test
    void parseResourceOutputWithKnowledge() {
        Clarifier.ParseResult result = Clarifier.parseResourceOutput("""
                ## Agent资源规划

                【选择的知识库】
                [{"knowledge_id": "kb_001", "knowledge_name": "Test KB", "knowledge_desc": "A test KB"}]
                """, Map.of("knowledge", List.of(
                Map.of("knowledge_id", "kb_001", "knowledge_name", "Test KB", "knowledge_desc", "A test KB"))));

        assertThat(result.displayContent()).contains("知识库");
        assertThat(result.resourceIdDict()).containsEntry("knowledge", List.of("kb_001"));
    }

    @Test
    void parseResourceOutputWithWorkflow() {
        Clarifier.ParseResult result = Clarifier.parseResourceOutput("""
                ## Agent资源规划

                【选择的工作流】
                [{"workflow_id": "wf_001", "workflow_name": "Test WF", "workflow_desc": "A test WF"}]
                """, Map.of("workflow", List.of(
                Map.of("workflow_id", "wf_001", "workflow_name", "Test WF", "workflow_desc", "A test WF"))));

        assertThat(result.displayContent()).contains("工作流");
        assertThat(result.resourceIdDict()).containsEntry("workflow", List.of("wf_001"));
    }

    @Test
    void parseResourceOutputEmptyList() {
        Clarifier.ParseResult result = Clarifier.parseResourceOutput("""
                ## Agent资源规划

                【选择的插件】
                []
                """, Map.of("plugins", List.of()));

        assertThat(result.resourceIdDict()).doesNotContainKey("plugin");
        assertThat(result.resourceIdDict().getOrDefault("plugin", List.of())).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resourceConfig() throws Exception {
        Field field = Clarifier.class.getDeclaredField("RESOURCE_CONFIG");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(null);
    }

    private static Object component(Object record, String accessor) throws Exception {
        Method method = record.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return method.invoke(record);
    }
}
