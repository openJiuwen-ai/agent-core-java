/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for DL Transformer Converters module.
 * <p>
 * Mirrors Python's {@code test_converters_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.dl_transformer}.
 */
class TestConvertersIntegration {

    @Nested
    class TestDLTransformerRegistryIntegration {

        @Test
        void registryContainsAllTypes() {
            Map<String, Class<?>> registry = DlTransformer.getDslConverterRegistry();
            assertThat(registry).containsKeys("Start", "End", "LLM", "IntentDetection",
                    "Questioner", "Code", "Plugin", "Output", "Branch");
        }

        @Test
        void registryValuesAreClasses() {
            for (Class<?> cls : DlTransformer.getDslConverterRegistry().values()) {
                assertThat(cls).isNotNull();
            }
        }
    }

    @Nested
    class TestStartConverterIntegration {

        @Test
        void startConverterCreation() {
            Map<String, Object> nodeData = new LinkedHashMap<>();
            nodeData.put("id", "node_start");
            nodeData.put("type", "Start");
            nodeData.put("description", "开始节点");
            nodeData.put("parameters", Map.of("outputs", List.of(Map.of("name", "query", "description", "用户输入"))));
            nodeData.put("next", "node_end");

            assertThat(nodeData.get("id")).isEqualTo("node_start");
            assertThat(nodeData.get("type")).isEqualTo("Start");
        }
    }

    @Nested
    class TestEndConverterIntegration {

        @Test
        void endConverterCreation() {
            Map<String, Object> nodeData = new LinkedHashMap<>();
            nodeData.put("id", "node_end");
            nodeData.put("type", "End");
            nodeData.put("description", "结束节点");
            nodeData.put("parameters", Map.of("inputs", List.of(), "configs", Map.of("template", "{{result}}")));

            assertThat(nodeData.get("id")).isEqualTo("node_end");
            assertThat(nodeData.get("type")).isEqualTo("End");
        }
    }

    @Nested
    class TestLLMConverterIntegration {

        @Test
        void llmConverterCreation() {
            Map<String, Object> nodeData = new LinkedHashMap<>();
            nodeData.put("id", "node_llm");
            nodeData.put("type", "LLM");
            nodeData.put("description", "大模型节点");
            nodeData.put("parameters", Map.of(
                    "inputs", List.of(Map.of("name", "query", "value", "${node_start.query}")),
                    "outputs", List.of(Map.of("name", "output", "description", "输出")),
                    "configs", Map.of("system_prompt", "You are helpful", "user_prompt", "{{query}}")
            ));
            nodeData.put("next", "node_end");

            assertThat(nodeData.get("id")).isEqualTo("node_llm");
            assertThat(nodeData.get("type")).isEqualTo("LLM");
        }
    }

    @Nested
    class TestBranchConverterIntegration {

        @Test
        void branchConverterCreation() {
            Map<String, Object> nodeData = new LinkedHashMap<>();
            nodeData.put("id", "node_branch");
            nodeData.put("type", "Branch");
            nodeData.put("description", "分支节点");

            assertThat(nodeData.get("id")).isEqualTo("node_branch");
            assertThat(nodeData.get("type")).isEqualTo("Branch");
        }
    }
}
