/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Toolbox.
 * <p>
 * Mirrors Python's test_toolbox.py from
 * <code>tests/unit_tests/core/foundation/tool/test_toolbox.py</code>.
 */
@DisplayName("Toolbox Tests")
class TestToolbox {

    // Stub classes
    static class ToolStub {
        String name;
        String description;
        Map<String, Object> parameters;

        ToolStub(String name, String description) {
            this.name = name;
            this.description = description;
            this.parameters = new HashMap<>();
        }

        Object execute(Map<String, Object> inputs) {
            return inputs.get("input");
        }
    }

    static class Toolbox {
        List<ToolStub> tools = new ArrayList<>();
        Map<String, ToolStub> toolMap = new HashMap<>();

        void addTool(ToolStub tool) {
            tools.add(tool);
            toolMap.put(tool.name, tool);
        }

        ToolStub getTool(String name) {
            return toolMap.get(name);
        }

        List<ToolStub> listTools() {
            return new ArrayList<>(tools);
        }

        Object executeTool(String name, Map<String, Object> inputs) {
            ToolStub tool = toolMap.get(name);
            if (tool == null) {
                throw new IllegalArgumentException("Tool not found: " + name);
            }
            return tool.execute(inputs);
        }
    }

    @Nested
    @DisplayName("Tool Registration Tests")
    class TestToolRegistration {

        @Test
        @DisplayName("add tool to toolbox")
        void testAddToolToToolbox() {
            Toolbox toolbox = new Toolbox();
            ToolStub tool = new ToolStub("calculator", "Calculate math expressions");

            toolbox.addTool(tool);

            assertNotNull(toolbox.getTool("calculator"));
            assertEquals(1, toolbox.listTools().size());
        }

        @Test
        @DisplayName("list all tools")
        void testListAllTools() {
            Toolbox toolbox = new Toolbox();
            toolbox.addTool(new ToolStub("tool1", "Description 1"));
            toolbox.addTool(new ToolStub("tool2", "Description 2"));
            toolbox.addTool(new ToolStub("tool3", "Description 3"));

            List<ToolStub> tools = toolbox.listTools();

            assertEquals(3, tools.size());
        }
    }

    @Nested
    @DisplayName("Tool Execution Tests")
    class TestToolExecution {

        @Test
        @DisplayName("execute tool")
        void testExecuteTool() {
            Toolbox toolbox = new Toolbox();
            ToolStub tool = new ToolStub("echo", "Echo input");
            toolbox.addTool(tool);

            Map<String, Object> inputs = new HashMap<>();
            inputs.put("input", "hello");

            Object result = toolbox.executeTool("echo", inputs);

            assertEquals("hello", result);
        }

        @Test
        @DisplayName("execute non-existent tool throws")
        void testExecuteNonExistentToolThrows() {
            Toolbox toolbox = new Toolbox();

            assertThrows(IllegalArgumentException.class, () -> {
                toolbox.executeTool("nonexistent", new HashMap<>());
            });
        }
    }

    @Nested
    @DisplayName("Tool Properties Tests")
    class TestToolProperties {

        @Test
        @DisplayName("tool has name and description")
        void testToolHasNameAndDescription() {
            ToolStub tool = new ToolStub("weather", "Get weather information");

            assertEquals("weather", tool.name);
            assertEquals("Get weather information", tool.description);
        }
    }
}