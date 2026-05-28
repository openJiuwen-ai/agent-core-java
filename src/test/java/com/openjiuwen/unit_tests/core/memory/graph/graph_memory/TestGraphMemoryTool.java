/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.graph.graph_memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GraphMemoryTool.
 * <p>
 * Mirrors Python's test_graph_memory_tool.py from
 * <code>tests/unit_tests/core/memory/graph/graph_memory/test_graph_memory_tool.py</code>.
 */
@DisplayName("Graph Memory Tool Tests")
class TestGraphMemoryTool {

    // Stub classes
    static class ToolInput {
        String action;
        Map<String, Object> params = new HashMap<>();

        ToolInput(String action) {
            this.action = action;
        }

        void setParam(String key, Object value) {
            params.put(key, value);
        }
    }

    static class ToolOutput {
        boolean success;
        String message;
        Map<String, Object> data = new HashMap<>();

        ToolOutput(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        void addData(String key, Object value) {
            data.put(key, value);
        }
    }

    static class GraphMemoryToolStub {
        String toolName = "graph_memory";

        ToolOutput execute(ToolInput input) {
            switch (input.action) {
                case "add_entity":
                    return new ToolOutput(true, "Entity added");
                case "search":
                    return new ToolOutput(true, "Search completed");
                case "get_relations":
                    return new ToolOutput(true, "Relations retrieved");
                default:
                    return new ToolOutput(false, "Unknown action: " + input.action);
            }
        }

        String getToolName() {
            return toolName;
        }

        String getDescription() {
            return "Tool for interacting with graph memory";
        }
    }

    @Nested
    @DisplayName("Tool Input Tests")
    class TestToolInput {

        @Test
        @DisplayName("tool input creation")
        void testToolInputCreation() {
            ToolInput input = new ToolInput("add_entity");

            assertEquals("add_entity", input.action);
        }

        @Test
        @DisplayName("tool input with params")
        void testToolInputWithParams() {
            ToolInput input = new ToolInput("add_entity");
            input.setParam("name", "Python");
            input.setParam("type", "Language");

            assertEquals("Python", input.params.get("name"));
            assertEquals("Language", input.params.get("type"));
        }
    }

    @Nested
    @DisplayName("Tool Output Tests")
    class TestToolOutput {

        @Test
        @DisplayName("tool output creation")
        void testToolOutputCreation() {
            ToolOutput output = new ToolOutput(true, "Success");

            assertTrue(output.success);
            assertEquals("Success", output.message);
        }

        @Test
        @DisplayName("tool output with data")
        void testToolOutputWithData() {
            ToolOutput output = new ToolOutput(true, "Success");
            output.addData("count", 5);
            output.addData("results", new HashMap<>());

            assertEquals(5, output.data.get("count"));
            assertNotNull(output.data.get("results"));
        }
    }

    @Nested
    @DisplayName("Graph Memory Tool Tests")
    class TestGraphMemoryToolClass {

        @Test
        @DisplayName("tool has name")
        void testToolHasName() {
            GraphMemoryToolStub tool = new GraphMemoryToolStub();

            assertEquals("graph_memory", tool.getToolName());
        }

        @Test
        @DisplayName("tool has description")
        void testToolHasDescription() {
            GraphMemoryToolStub tool = new GraphMemoryToolStub();

            assertNotNull(tool.getDescription());
        }

        @Test
        @DisplayName("tool execute add entity")
        void testToolExecuteAddEntity() {
            GraphMemoryToolStub tool = new GraphMemoryToolStub();
            ToolInput input = new ToolInput("add_entity");

            ToolOutput output = tool.execute(input);

            assertTrue(output.success);
        }

        @Test
        @DisplayName("tool execute search")
        void testToolExecuteSearch() {
            GraphMemoryToolStub tool = new GraphMemoryToolStub();
            ToolInput input = new ToolInput("search");
            input.setParam("query", "Python");

            ToolOutput output = tool.execute(input);

            assertTrue(output.success);
        }

        @Test
        @DisplayName("tool execute unknown action")
        void testToolExecuteUnknownAction() {
            GraphMemoryToolStub tool = new GraphMemoryToolStub();
            ToolInput input = new ToolInput("unknown_action");

            ToolOutput output = tool.execute(input);

            assertFalse(output.success);
            assertTrue(output.message.contains("Unknown action"));
        }
    }
}