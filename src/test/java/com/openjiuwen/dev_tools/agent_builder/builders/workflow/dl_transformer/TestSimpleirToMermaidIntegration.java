/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for SimpleIR to Mermaid module.
 * <p>
 * Mirrors Python's {@code test_simpleir_to_mermaid_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.dl_transformer}.
 */
class TestSimpleirToMermaidIntegration {

    @Test
    void testTransformToMermaidBasic() {
        String result = SimpleirToMermaid.transformToMermaid(List.of(
                startNode("node_end"),
                endNode("node_end", "End")
        ));
        assertThat(result).contains("graph TD", "node_start", "node_end");
    }

    @Test
    void testTransformToMermaidWithLlm() {
        String result = SimpleirToMermaid.transformToMermaid(List.of(
                Map.of("id", "node_start", "type", "Start", "description", "Start", "next", "node_llm"),
                Map.of("id", "node_llm", "type", "LLM", "description", "LLM Node", "next", "node_end"),
                Map.of("id", "node_end", "type", "End", "description", "End")
        ));
        assertThat(result).contains("graph TD", "node_llm");
    }

    @Test
    void testTransformToMermaidEmptyNodes() {
        assertThat(SimpleirToMermaid.transformToMermaid(List.of())).contains("graph TD");
    }

    @Test
    void testEdgeTransformWithNext() {
        List<Map<String, Object>> edges = SimpleirToMermaid.edgeTransform(List.of(
                Map.of("id", "node_1", "type", "Start", "next", "node_2"),
                Map.of("id", "node_2", "type", "End")
        ));
        assertThat(edges).hasSize(1);
        assertThat(edges.get(0)).containsEntry("source", "node_1").containsEntry("target", "node_2");
    }

    @Test
    void testEdgeTransformWithConditions() {
        List<Map<String, Object>> edges = SimpleirToMermaid.edgeTransform(List.of(
                Map.of("id", "node_1", "type", "Branch", "parameters", Map.of("conditions", List.of(
                        Map.of("branch", "branch_1", "description", "condition 1", "next", "node_2"),
                        Map.of("branch", "branch_2", "description", "condition 2", "next", "node_3")))),
                Map.of("id", "node_2", "type", "End"),
                Map.of("id", "node_3", "type", "End")
        ));
        assertThat(edges).hasSize(2);
        assertThat(edges.get(0)).containsEntry("source", "node_1").containsEntry("target", "node_2");
        assertThat(edges.get(1)).containsEntry("target", "node_3");
    }

    @Test
    void testEdgeTransformEmptyNodes() {
        assertThat(SimpleirToMermaid.edgeTransform(List.of())).isEmpty();
    }

    @Test
    void testTransToMermaidBasic() {
        String result = SimpleirToMermaid.transToMermaid(Map.of(
                "nodes", List.of(Map.of("id", "node_1", "description", "Start Node"), Map.of("id", "node_2", "description", "End Node")),
                "edges", List.of(Map.of("source", "node_1", "target", "node_2"))));
        assertThat(result).contains("graph TD", "node_1", "node_2", "-->");
    }

    @Test
    void testTransToMermaidWithDescription() {
        String result = SimpleirToMermaid.transToMermaid(Map.of(
                "nodes", List.of(Map.of("id", "node_1", "description", "Start Node"), Map.of("id", "node_2", "description", "End Node")),
                "edges", List.of(Map.of("source", "node_1", "target", "node_2", "description", "condition"))));
        assertThat(result).contains("Start Node", "End Node");
    }

    @Test
    void testTransToMermaidEmptyData() {
        assertThat(SimpleirToMermaid.transToMermaid(Map.of("nodes", List.of(), "edges", List.of()))).contains("graph TD");
    }

    @Test
    void testTransformComplexWorkflow() {
        List<Map<String, Object>> nodes = List.of(
                startNode("node_intent"),
                Map.of("id", "node_intent", "type", "IntentDetection", "description", "意图识别", "parameters", Map.of(
                        "conditions", List.of(
                                Map.of("branch", "branch_1", "description", "查询", "next", "node_llm"),
                                Map.of("branch", "branch_2", "description", "闲聊", "next", "node_end")))),
                Map.of("id", "node_llm", "type", "LLM", "description", "大模型处理", "next", "node_end"),
                Map.of("id", "node_end", "type", "End", "description", "结束")
        );
        String result = SimpleirToMermaid.transformToMermaid(nodes);
        assertThat(result).contains("graph TD", "node_start", "node_intent", "node_llm", "node_end");
    }

    private static Map<String, Object> startNode(String next) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "node_start");
        node.put("type", "Start");
        node.put("description", "Start");
        node.put("parameters", Map.of("outputs", List.of(Map.of("name", "query", "description", "input"))));
        node.put("next", next);
        return node;
    }

    private static Map<String, Object> endNode(String id, String description) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", "End");
        node.put("description", description);
        node.put("parameters", Map.of("inputs", List.of(), "configs", Map.of("template", "{{result}}")));
        return node;
    }
}
