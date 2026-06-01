/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for clarifier integration.
 * <p>
 * Mirrors Python's {@code test_clarifier_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.llm_agent}.
 */
class TestClarifierIntegration {

    private final Clarifier clarifier = new Clarifier(null);

    @Nested
    class TestClarifierIntegrationInner {

        @Test
        void clarifierInitialization() {
            assertThat(clarifier.getLlm()).isNull();
        }

        @Test
        void resourceConfigStructure() {
            assertThat(Clarifier.RESOURCE_CONFIG).containsKeys("plugin", "knowledge", "workflow");
            Clarifier.RESOURCE_CONFIG.values().forEach(config ->
                    assertThat(config).containsKeys("label", "id_key", "name_key", "desc_key"));
        }

        @Test
        void parseResourceOutputEmpty() {
            Map<String, Object> result = Clarifier.parseResourceOutput("", Map.of());
            assertThat(result.get("display_content")).isInstanceOf(String.class);
            assertThat(result.get("id_dict")).isInstanceOf(Map.class);
        }

        @Test
        void parseResourceOutputWithPlugins() {
            Map<String, Object> result = Clarifier.parseResourceOutput(
                    """
                            ## Agent资源规划

                            【选择的插件】
                            [{"tool_id": "test_plugin", "tool_name": "Test Plugin"}]
                            """,
                    Map.of("plugins", List.of(Map.of("tool_id", "test_plugin", "tool_name", "Test Plugin")))
            );

            assertThat(result.get("display_content")).isInstanceOf(String.class);
            assertThat(result.get("id_dict")).isInstanceOf(Map.class);
        }
    }

    @Nested
    class TestClarifierResourceConfig {

        @Test
        void pluginConfig() {
            assertThat(Clarifier.RESOURCE_CONFIG.get("plugin"))
                    .containsEntry("label", "插件")
                    .containsEntry("id_key", "tool_id")
                    .containsEntry("name_key", "tool_name")
                    .containsEntry("desc_key", "tool_desc");
        }

        @Test
        void knowledgeConfig() {
            assertThat(Clarifier.RESOURCE_CONFIG.get("knowledge"))
                    .containsEntry("label", "知识库")
                    .containsEntry("id_key", "knowledge_id")
                    .containsEntry("name_key", "knowledge_name")
                    .containsEntry("desc_key", "knowledge_desc");
        }

        @Test
        void workflowConfig() {
            assertThat(Clarifier.RESOURCE_CONFIG.get("workflow"))
                    .containsEntry("label", "工作流")
                    .containsEntry("id_key", "workflow_id")
                    .containsEntry("name_key", "workflow_name")
                    .containsEntry("desc_key", "workflow_desc");
        }
    }
}
