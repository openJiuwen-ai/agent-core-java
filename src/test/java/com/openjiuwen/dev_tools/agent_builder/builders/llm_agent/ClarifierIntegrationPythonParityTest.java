/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestClarifierIntegration} and {@code TestClarifierResourceConfig} in
 * {@code tests/system_tests/dev_tools/agent_builder/builders/llm_agent/test_clarifier_integration.py}.
 */
class ClarifierIntegrationPythonParityTest {

    @Test
    void testClarifierInitialization() {
        Model mockLlm = mockLlm();

        Clarifier clarifier = new Clarifier(mockLlm);

        assertThat(clarifier.getLlm()).isSameAs(mockLlm);
    }

    @Test
    void testResourceConfigStructure() {
        assertThat(Clarifier.RESOURCE_CONFIG).containsKeys("plugin", "knowledge", "workflow");
        Clarifier.RESOURCE_CONFIG.values().forEach(config -> assertThat(config)
                .containsKeys("label", "id_key", "name_key", "desc_key"));
    }

    @Test
    void testParseResourceOutputEmpty() {
        Clarifier.ParseResult result = Clarifier.parseResourceOutput("", Map.of());

        assertThat(result.displayContent()).isInstanceOf(String.class);
        assertThat(result.resourceIdDict()).isInstanceOf(Map.class);
    }

    @Test
    void testParseResourceOutputWithPlugins() {
        String resourceOutput = "插件: test_plugin";
        Map<String, Object> availableResources = Map.of(
                "plugin",
                List.of(Map.of("tool_id", "test_plugin", "tool_name", "Test Plugin")));

        Clarifier.ParseResult result = Clarifier.parseResourceOutput(resourceOutput, availableResources);

        assertThat(result.displayContent()).isInstanceOf(String.class);
        assertThat(result.resourceIdDict()).isInstanceOf(Map.class);
    }

    @Test
    void testPluginConfig() {
        Map<String, String> config = Clarifier.RESOURCE_CONFIG.get("plugin");

        assertThat(config).containsEntry("label", "插件");
        assertThat(config).containsEntry("id_key", "tool_id");
        assertThat(config).containsEntry("name_key", "tool_name");
        assertThat(config).containsEntry("desc_key", "tool_desc");
    }

    @Test
    void testKnowledgeConfig() {
        Map<String, String> config = Clarifier.RESOURCE_CONFIG.get("knowledge");

        assertThat(config).containsEntry("label", "知识库");
        assertThat(config).containsEntry("id_key", "knowledge_id");
        assertThat(config).containsEntry("name_key", "knowledge_name");
        assertThat(config).containsEntry("desc_key", "knowledge_desc");
    }

    @Test
    void testWorkflowConfig() {
        Map<String, String> config = Clarifier.RESOURCE_CONFIG.get("workflow");

        assertThat(config).containsEntry("label", "工作流");
        assertThat(config).containsEntry("id_key", "workflow_id");
        assertThat(config).containsEntry("name_key", "workflow_name");
        assertThat(config).containsEntry("desc_key", "workflow_desc");
    }

    private static Model mockLlm() {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("mock")));
    }
}
