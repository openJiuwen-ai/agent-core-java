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
 * Unit tests for {@link Clarifier}.
 * <p>
 * Mirrors Python's {@code test_clarifier.py} in
 * {@code tests.unit_tests.dev_tools.agent_builder.builders.llm_agent.test_clarifier}.
 */
class TestClarifier {

    @Nested
    class TestResourceConfig {

        @Test
        void resourceConfigHasPlugin() {
            assertThat(Clarifier.RESOURCE_CONFIG).containsKey("plugin");
            assertThat(Clarifier.RESOURCE_CONFIG.get("plugin")).containsEntry("label", "插件");
            assertThat(Clarifier.RESOURCE_CONFIG.get("plugin")).containsEntry("id_key", "tool_id");
            assertThat(Clarifier.RESOURCE_CONFIG.get("plugin")).containsEntry("name_key", "tool_name");
            assertThat(Clarifier.RESOURCE_CONFIG.get("plugin")).containsEntry("desc_key", "tool_desc");
        }

        @Test
        void resourceConfigHasKnowledge() {
            assertThat(Clarifier.RESOURCE_CONFIG).containsKey("knowledge");
            assertThat(Clarifier.RESOURCE_CONFIG.get("knowledge")).containsEntry("label", "知识库");
        }

        @Test
        void resourceConfigHasWorkflow() {
            assertThat(Clarifier.RESOURCE_CONFIG).containsKey("workflow");
            assertThat(Clarifier.RESOURCE_CONFIG.get("workflow")).containsEntry("label", "工作流");
        }
    }

    @Nested
    class TestClarifierParseResourceOutput {

        @Test
        void parseResourceOutputNoSection() {
            Map<String, Object> result = Clarifier.parseResourceOutput(
                    "Some text without resource planning section",
                    Map.of("plugins", List.of())
            );

            assertThat(result.get("display_content")).isEqualTo("");
            assertThat(result.get("id_dict")).isEqualTo(Map.of());
        }

        @Test
        void parseResourceOutputWithPlugins() {
            String resourceOutput = """
                    ## Agent资源规划

                    【选择的插件】
                    [{"tool_id": "tool_001", "tool_name": "Test Tool", "tool_desc": "A test tool"}]
                    """;
            Map<String, Object> result = Clarifier.parseResourceOutput(
                    resourceOutput,
                    Map.of("plugins", List.of(Map.of(
                            "tool_id", "tool_001",
                            "tool_name", "Test Tool",
                            "tool_desc", "A test tool"
                    )))
            );

            assertThat((String) result.get("display_content")).contains("插件");
            assertThat(((Map<?, ?>) result.get("id_dict")).get("plugin")).isEqualTo(List.of("tool_001"));
        }

        @Test
        void parseResourceOutputWithInvalidToolId() {
            String resourceOutput = """
                    ## Agent资源规划

                    【选择的插件】
                    [{"tool_id": "invalid_tool", "tool_name": "Invalid", "tool_desc": "Invalid tool"}]
                    """;
            Map<String, Object> result = Clarifier.parseResourceOutput(
                    resourceOutput,
                    Map.of("plugins", List.of(Map.of(
                            "tool_id", "tool_001",
                            "tool_name", "Valid Tool",
                            "tool_desc", "A valid tool"
                    )))
            );

            assertThat(((Map<?, ?>) result.get("id_dict")).containsKey("plugin")).isFalse();
        }

        @Test
        void parseResourceOutputWithKnowledge() {
            String resourceOutput = """
                    ## Agent资源规划

                    【选择的知识库】
                    [{"knowledge_id": "kb_001", "knowledge_name": "Test KB", "knowledge_desc": "A test KB"}]
                    """;
            Map<String, Object> result = Clarifier.parseResourceOutput(
                    resourceOutput,
                    Map.of("knowledge", List.of(Map.of(
                            "knowledge_id", "kb_001",
                            "knowledge_name", "Test KB",
                            "knowledge_desc", "A test KB"
                    )))
            );

            assertThat((String) result.get("display_content")).contains("知识库");
            assertThat(((Map<?, ?>) result.get("id_dict")).get("knowledge")).isEqualTo(List.of("kb_001"));
        }

        @Test
        void parseResourceOutputWithWorkflow() {
            String resourceOutput = """
                    ## Agent资源规划

                    【选择的工作流】
                    [{"workflow_id": "wf_001", "workflow_name": "Test WF", "workflow_desc": "A test WF"}]
                    """;
            Map<String, Object> result = Clarifier.parseResourceOutput(
                    resourceOutput,
                    Map.of("workflow", List.of(Map.of(
                            "workflow_id", "wf_001",
                            "workflow_name", "Test WF",
                            "workflow_desc", "A test WF"
                    )))
            );

            assertThat((String) result.get("display_content")).contains("工作流");
            assertThat(((Map<?, ?>) result.get("id_dict")).get("workflow")).isEqualTo(List.of("wf_001"));
        }

        @Test
        void parseResourceOutputEmptyList() {
            String resourceOutput = """
                    ## Agent资源规划

                    【选择的插件】
                    []
                    """;
            Map<String, Object> result = Clarifier.parseResourceOutput(resourceOutput, Map.of("plugins", List.of()));

            assertThat(((Map<?, ?>) result.get("id_dict")).containsKey("plugin")).isFalse();
        }
    }
}
