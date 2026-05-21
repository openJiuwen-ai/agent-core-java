/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test SimpleirToMermaid functionality.
 * <p>
 * Mirrors Python's {@code test_simpleir_to_mermaid.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/dl_transformer/test_simpleir_to_mermaid.py}.
 */
class TestSimpleirToMermaid {

    /**
     * Test SimpleirToMermaid.edgeTransform method.
     * <p>
     * Mirrors Python's {@code TestSimpleIrToMermaidEdgeTransform} class.
     */
    static class TestEdgeTransform {

        @Test
        void testEdgeTransformWithNext() {
            // Test edge transform with next field
            List<Map<String, Object>> nodes = new ArrayList<>();
            Map<String, Object> node1 = new LinkedHashMap<>();
            node1.put("id", "node_1");
            node1.put("type", "Start");
            node1.put("next", "node_2");
            nodes.add(node1);
            
            Map<String, Object> node2 = new LinkedHashMap<>();
            node2.put("id", "node_2");
            node2.put("type", "End");
            nodes.add(node2);

            List<Map<String, Object>> edges = SimpleirToMermaid.edgeTransform(nodes);

            Assertions.assertEquals(1, edges.size());
            Assertions.assertEquals("node_1", edges.get(0).get("source"));
            Assertions.assertEquals("node_2", edges.get(0).get("target"));
        }

        @Test
        void testEdgeTransformWithConditions() {
            // Test edge transform with conditions
            List<Map<String, Object>> nodes = new ArrayList<>();
            
            Map<String, Object> node1 = new LinkedHashMap<>();
            node1.put("id", "node_1");
            node1.put("type", "Branch");
            Map<String, Object> parameters = new LinkedHashMap<>();
            List<Map<String, Object>> conditions = new ArrayList<>();
            
            Map<String, Object> cond1 = new LinkedHashMap<>();
            cond1.put("branch", "branch_1");
            cond1.put("description", "condition 1");
            cond1.put("next", "node_2");
            conditions.add(cond1);
            
            Map<String, Object> cond2 = new LinkedHashMap<>();
            cond2.put("branch", "branch_2");
            cond2.put("description", "condition 2");
            cond2.put("next", "node_3");
            conditions.add(cond2);
            
            parameters.put("conditions", conditions);
            node1.put("parameters", parameters);
            nodes.add(node1);
            
            Map<String, Object> node2 = new LinkedHashMap<>();
            node2.put("id", "node_2");
            node2.put("type", "End");
            nodes.add(node2);
            
            Map<String, Object> node3 = new LinkedHashMap<>();
            node3.put("id", "node_3");
            node3.put("type", "End");
            nodes.add(node3);

            List<Map<String, Object>> edges = SimpleirToMermaid.edgeTransform(nodes);

            Assertions.assertEquals(2, edges.size());
            Assertions.assertEquals("node_1", edges.get(0).get("source"));
            Assertions.assertEquals("node_2", edges.get(0).get("target"));
            Assertions.assertEquals("branch_1", edges.get(0).get("branch"));
            Assertions.assertEquals("node_3", edges.get(1).get("target"));
        }

        @Test
        void testEdgeTransformEmptyNodes() {
            // Test edge transform with empty nodes
            List<Map<String, Object>> edges = SimpleirToMermaid.edgeTransform(new ArrayList<>());
            Assertions.assertEquals(0, edges.size());
        }

        @Test
        void testEdgeTransformEndNodeNoEdge() {
            // Test edge transform with End node
            List<Map<String, Object>> nodes = new ArrayList<>();
            Map<String, Object> node1 = new LinkedHashMap<>();
            node1.put("id", "node_1");
            node1.put("type", "End");
            nodes.add(node1);

            List<Map<String, Object>> edges = SimpleirToMermaid.edgeTransform(nodes);
            Assertions.assertEquals(0, edges.size());
        }
    }

    /**
     * Test SimpleirToMermaid.transToMermaid method.
     * <p>
     * Mirrors Python's {@code TestSimpleIrToMermaidTransToMermaid} class.
     */
    static class TestTransToMermaid {

        @Test
        void testTransToMermaidBasic() {
            // Test basic mermaid transformation
            Map<String, Object> data = new LinkedHashMap<>();
            List<Map<String, Object>> nodes = new ArrayList<>();
            
            Map<String, Object> node1 = new LinkedHashMap<>();
            node1.put("id", "node_1");
            node1.put("description", "Start Node");
            nodes.add(node1);
            
            Map<String, Object> node2 = new LinkedHashMap<>();
            node2.put("id", "node_2");
            node2.put("description", "End Node");
            nodes.add(node2);
            
            data.put("nodes", nodes);
            
            List<Map<String, Object>> edges = new ArrayList<>();
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", "node_1");
            edge.put("target", "node_2");
            edges.add(edge);
            data.put("edges", edges);

            String result = SimpleirToMermaid.transToMermaid(data);

            Assertions.assertTrue(result.contains("graph TD"));
            Assertions.assertTrue(result.contains("node_1"));
            Assertions.assertTrue(result.contains("node_2"));
            Assertions.assertTrue(result.contains("-->"));
        }

        @Test
        void testTransToMermaidWithDescription() {
            // Test mermaid transformation with description
            Map<String, Object> data = new LinkedHashMap<>();
            List<Map<String, Object>> nodes = new ArrayList<>();
            
            Map<String, Object> node1 = new LinkedHashMap<>();
            node1.put("id", "node_1");
            node1.put("description", "Start Node");
            nodes.add(node1);
            
            Map<String, Object> node2 = new LinkedHashMap<>();
            node2.put("id", "node_2");
            node2.put("description", "End Node");
            nodes.add(node2);
            
            data.put("nodes", nodes);
            
            List<Map<String, Object>> edges = new ArrayList<>();
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", "node_1");
            edge.put("target", "node_2");
            edge.put("description", "condition");
            edges.add(edge);
            data.put("edges", edges);

            String result = SimpleirToMermaid.transToMermaid(data);

            Assertions.assertTrue(result.contains("Start Node"));
            Assertions.assertTrue(result.contains("End Node"));
        }

        @Test
        void testTransToMermaidWithBranch() {
            // Test mermaid transformation with branch
            Map<String, Object> data = new LinkedHashMap<>();
            List<Map<String, Object>> nodes = new ArrayList<>();
            
            Map<String, Object> node1 = new LinkedHashMap<>();
            node1.put("id", "node_1");
            node1.put("description", "Branch Node");
            nodes.add(node1);
            
            Map<String, Object> node2 = new LinkedHashMap<>();
            node2.put("id", "node_2");
            node2.put("description", "End 1");
            nodes.add(node2);
            
            Map<String, Object> node3 = new LinkedHashMap<>();
            node3.put("id", "node_3");
            node3.put("description", "End 2");
            nodes.add(node3);
            
            data.put("nodes", nodes);
            
            List<Map<String, Object>> edges = new ArrayList<>();
            Map<String, Object> edge1 = new LinkedHashMap<>();
            edge1.put("source", "node_1");
            edge1.put("target", "node_2");
            edge1.put("branch", "branch_1");
            edge1.put("description", "cond1");
            edges.add(edge1);
            
            Map<String, Object> edge2 = new LinkedHashMap<>();
            edge2.put("source", "node_1");
            edge2.put("target", "node_3");
            edge2.put("branch", "branch_2");
            edge2.put("description", "cond2");
            edges.add(edge2);
            
            data.put("edges", edges);

            String result = SimpleirToMermaid.transToMermaid(data);

            Assertions.assertTrue(result.contains("graph TD"));
            Assertions.assertTrue(result.indexOf("node_1") != result.lastIndexOf("node_1") 
                    || result.contains("node_1"));
        }

        @Test
        void testTransToMermaidEmptyData() {
            // Test mermaid transformation with empty data
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("nodes", new ArrayList<>());
            data.put("edges", new ArrayList<>());

            String result = SimpleirToMermaid.transToMermaid(data);

            Assertions.assertTrue(result.contains("graph TD"));
        }
    }

    /**
     * Test SimpleirToMermaid.transformToMermaid method.
     * <p>
     * Mirrors Python's {@code TestSimpleIrToMermaidTransformToMermaid} class.
     */
    static class TestTransformToMermaid {

        @Test
        void testTransformToMermaidSuccess() {
            // Test successful transformation
            List<Map<String, Object>> nodes = new ArrayList<>();
            
            Map<String, Object> nodeStart = new LinkedHashMap<>();
            nodeStart.put("id", "node_start");
            nodeStart.put("type", "Start");
            nodeStart.put("description", "Start");
            Map<String, Object> params1 = new LinkedHashMap<>();
            List<Map<String, Object>> outputs = new ArrayList<>();
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("name", "query");
            output.put("description", "input");
            outputs.add(output);
            params1.put("outputs", outputs);
            nodeStart.put("parameters", params1);
            nodeStart.put("next", "node_end");
            nodes.add(nodeStart);
            
            Map<String, Object> nodeEnd = new LinkedHashMap<>();
            nodeEnd.put("id", "node_end");
            nodeEnd.put("type", "End");
            nodeEnd.put("description", "End");
            Map<String, Object> params2 = new LinkedHashMap<>();
            params2.put("inputs", new ArrayList<>());
            Map<String, Object> configs = new LinkedHashMap<>();
            configs.put("template", "{{result}}");
            params2.put("configs", configs);
            nodeEnd.put("parameters", params2);
            nodes.add(nodeEnd);

            String result = SimpleirToMermaid.transformToMermaid(nodes);

            Assertions.assertTrue(result.contains("graph TD"));
            Assertions.assertTrue(result.contains("node_start"));
            Assertions.assertTrue(result.contains("node_end"));
        }

        @Test
        void testTransformToMermaidWithLlm() {
            // Test transformation with LLM node
            List<Map<String, Object>> nodes = new ArrayList<>();
            
            Map<String, Object> nodeStart = new LinkedHashMap<>();
            nodeStart.put("id", "node_start");
            nodeStart.put("type", "Start");
            nodeStart.put("description", "Start");
            nodeStart.put("next", "node_llm");
            nodes.add(nodeStart);
            
            Map<String, Object> nodeLlm = new LinkedHashMap<>();
            nodeLlm.put("id", "node_llm");
            nodeLlm.put("type", "LLM");
            nodeLlm.put("description", "LLM Node");
            nodeLlm.put("next", "node_end");
            nodes.add(nodeLlm);
            
            Map<String, Object> nodeEnd = new LinkedHashMap<>();
            nodeEnd.put("id", "node_end");
            nodeEnd.put("type", "End");
            nodeEnd.put("description", "End");
            nodes.add(nodeEnd);

            String result = SimpleirToMermaid.transformToMermaid(nodes);

            Assertions.assertTrue(result.contains("graph TD"));
            Assertions.assertTrue(result.contains("node_llm"));
        }

        @Test
        void testTransformToMermaidEmptyNodes() {
            // Test transformation with empty nodes
            String result = SimpleirToMermaid.transformToMermaid(new ArrayList<>());
            Assertions.assertTrue(result.contains("graph TD"));
        }

        @Test
        void testTransformToMermaidSpecialCharacters() {
            // Test transformation with special characters in description
            List<Map<String, Object>> nodes = new ArrayList<>();
            
            Map<String, Object> node1 = new LinkedHashMap<>();
            node1.put("id", "node_1");
            node1.put("type", "Start");
            node1.put("description", "Test `special` \"chars\"");
            node1.put("next", "node_2");
            nodes.add(node1);
            
            Map<String, Object> node2 = new LinkedHashMap<>();
            node2.put("id", "node_2");
            node2.put("type", "End");
            node2.put("description", "End");
            nodes.add(node2);

            String result = SimpleirToMermaid.transformToMermaid(nodes);

            Assertions.assertTrue(result.contains("graph TD"));
            // Python replaces backticks with single quotes
            Assertions.assertTrue(!result.contains("`") || result.contains("'"));
        }
    }
}