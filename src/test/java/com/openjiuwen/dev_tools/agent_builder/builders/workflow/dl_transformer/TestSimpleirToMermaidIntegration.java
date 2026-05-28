/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for SimpleIR to Mermaid module.
 * <p>
 * Mirrors Python's {@code test_simpleir_to_mermaid_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.dl_transformer}.
 */
class TestSimpleirToMermaidIntegration {

    @Nested
    class TestSimpleIrToMermaidIntegration {

        @Test
        void transformToMermaidBasic() {
            List<Map<String, Object>> nodes = List.of(
                    Map.of("id", "node_start", "type", "Start", "description", "Start",
                            "parameters", Map.of("outputs", List.of(Map.of("name", "query", "description", "input"))),
                            "next", "node_end"),
                    Map.of("id", "node_end", "type", "End", "description", "End",
                            "parameters", Map.of("inputs", List.of(), "configs", Map.of("template", "{{result}}")))
            );

            String result = SimpleirToMermaid.toMermaid(nodes);
            assertThat(result).contains("graph TD");
        }

        @Test
        void transformToMermaidWithLlm() {
            List<Map<String, Object>> nodes = List.of(
                    Map.of("id", "node_start", "type", "Start", "description", "Start", "next", "node_llm"),
                    Map.of("id", "node_llm", "type", "LLM", "description", "LLM Node", "next", "node_end"),
                    Map.of("id", "node_end", "type", "End", "description", "End")
            );

            String result = SimpleirToMermaid.toMermaid(nodes);
            assertThat(result).contains("graph TD");
        }

        @Test
        void transformToMermaidEmptyNodes() {
            String result = SimpleirToMermaid.toMermaid(List.of());
            assertThat(result).contains("graph TD");
        }
    }
}
