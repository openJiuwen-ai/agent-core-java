/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_simpleir_to_mermaid.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/dl_transformer/test_simpleir_to_mermaid.py}.
 */
class TestSimpleirToMermaid {

    @Nested
    class TestSimpleIrToMermaidEdgeTransform {

        @Test
        void testEdgeTransformWithNext() {
            List<Map<String, Object>> nodes = List.of(
                    mapOf("id", "node_1", "type", "Start", "next", "node_2"),
                    mapOf("id", "node_2", "type", "End"));

            List<Map<String, Object>> edges = SimpleirToMermaid.edgeTransform(nodes);

            assertEquals(1, edges.size());
            assertEquals("node_1", edges.get(0).get("source"));
            assertEquals("node_2", edges.get(0).get("target"));
        }

        @Test
        void testEdgeTransformWithConditions() {
            List<Map<String, Object>> conditions = List.of(
                    mapOf("branch", "branch_1", "description", "condition 1", "next", "node_2"),
                    mapOf("branch", "branch_2", "description", "condition 2", "next", "node_3"));
            List<Map<String, Object>> nodes = List.of(
                    mapOf("id", "node_1", "type", "Branch", "parameters", mapOf("conditions", conditions)),
                    mapOf("id", "node_2", "type", "End"),
                    mapOf("id", "node_3", "type", "End"));

            List<Map<String, Object>> edges = SimpleirToMermaid.edgeTransform(nodes);

            assertEquals(2, edges.size());
            assertEquals("node_1", edges.get(0).get("source"));
            assertEquals("node_2", edges.get(0).get("target"));
            assertEquals("branch_1", edges.get(0).get("branch"));
            assertEquals("node_3", edges.get(1).get("target"));
        }

        @Test
        void testEdgeTransformEmptyNodes() {
            assertEquals(0, SimpleirToMermaid.edgeTransform(List.of()).size());
        }

        @Test
        void testEdgeTransformEndNodeNoEdge() {
            List<Map<String, Object>> nodes = List.of(mapOf("id", "node_1", "type", "End"));

            assertEquals(0, SimpleirToMermaid.edgeTransform(nodes).size());
        }
    }

    @Nested
    class TestSimpleIrToMermaidTransToMermaid {

        @Test
        void testTransToMermaidBasic() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("nodes", List.of(
                    mapOf("id", "node_1", "description", "Start Node"),
                    mapOf("id", "node_2", "description", "End Node")));
            data.put("edges", List.of(mapOf("source", "node_1", "target", "node_2")));

            String result = SimpleirToMermaid.transToMermaid(data);

            assertTrue(result.contains("graph TD"));
            assertTrue(result.contains("node_1"));
            assertTrue(result.contains("node_2"));
            assertTrue(result.contains("-->"));
        }

        @Test
        void testTransToMermaidWithDescription() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("nodes", List.of(
                    mapOf("id", "node_1", "description", "Start Node"),
                    mapOf("id", "node_2", "description", "End Node")));
            data.put("edges", List.of(mapOf("source", "node_1", "target", "node_2", "description", "condition")));

            String result = SimpleirToMermaid.transToMermaid(data);

            assertTrue(result.contains("Start Node"));
            assertTrue(result.contains("End Node"));
        }

        @Test
        void testTransToMermaidWithBranch() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("nodes", List.of(
                    mapOf("id", "node_1", "description", "Branch Node"),
                    mapOf("id", "node_2", "description", "End 1"),
                    mapOf("id", "node_3", "description", "End 2")));
            data.put("edges", List.of(
                    mapOf("source", "node_1", "target", "node_2", "branch", "branch_1", "description", "cond1"),
                    mapOf("source", "node_1", "target", "node_3", "branch", "branch_2", "description", "cond2")));

            String result = SimpleirToMermaid.transToMermaid(data);

            assertTrue(result.contains("graph TD"));
            assertTrue(result.contains("node_1"));
        }

        @Test
        void testTransToMermaidEmptyData() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("nodes", List.of());
            data.put("edges", List.of());

            assertTrue(SimpleirToMermaid.transToMermaid(data).contains("graph TD"));
        }
    }

    @Nested
    class TestSimpleIrToMermaidTransformToMermaid {

        @Test
        void testTransformToMermaidSuccess() {
            List<Map<String, Object>> nodes = new ArrayList<>();
            nodes.add(mapOf(
                    "id", "node_start",
                    "type", "Start",
                    "description", "Start",
                    "parameters", mapOf("outputs", List.of(mapOf("name", "query", "description", "input"))),
                    "next", "node_end"));
            nodes.add(mapOf(
                    "id", "node_end",
                    "type", "End",
                    "description", "End",
                    "parameters", mapOf("inputs", List.of(), "configs", mapOf("template", "{{result}}"))));

            String result = SimpleirToMermaid.transformToMermaid(nodes);

            assertTrue(result.contains("graph TD"));
            assertTrue(result.contains("node_start"));
            assertTrue(result.contains("node_end"));
        }

        @Test
        void testTransformToMermaidWithLlm() {
            List<Map<String, Object>> nodes = List.of(
                    mapOf("id", "node_start", "type", "Start", "description", "Start", "next", "node_llm"),
                    mapOf("id", "node_llm", "type", "LLM", "description", "LLM Node", "next", "node_end"),
                    mapOf("id", "node_end", "type", "End", "description", "End"));

            String result = SimpleirToMermaid.transformToMermaid(nodes);

            assertTrue(result.contains("graph TD"));
            assertTrue(result.contains("node_llm"));
        }

        @Test
        void testTransformToMermaidEmptyNodes() {
            assertTrue(SimpleirToMermaid.transformToMermaid(List.of()).contains("graph TD"));
        }

        @Test
        void testTransformToMermaidSpecialCharacters() {
            List<Map<String, Object>> nodes = List.of(
                    mapOf("id", "node_1", "type", "Start", "description", "Test `special` \"chars\"", "next", "node_2"),
                    mapOf("id", "node_2", "type", "End", "description", "End"));

            String result = SimpleirToMermaid.transformToMermaid(nodes);

            assertTrue(result.contains("graph TD"));
            assertTrue(!result.contains("`") || result.contains("'"));
        }
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }
}
