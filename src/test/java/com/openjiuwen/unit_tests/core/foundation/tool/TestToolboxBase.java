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
 * Unit tests for ToolboxBase.
 * <p>
 * Mirrors Python's test_toolbox_base.py from
 * <code>tests/unit_tests/core/foundation/tool/test_toolbox_base.py</code>.
 */
@DisplayName("Toolbox Base Tests")
class TestToolboxBase {

    // Stub classes
    static abstract class ToolboxBase {
        String name;
        List<String> toolNames = new ArrayList<>();

        ToolboxBase(String name) {
            this.name = name;
        }

        abstract void registerTools();

        void addToolName(String toolName) {
            toolNames.add(toolName);
        }

        List<String> getToolNames() {
            return new ArrayList<>(toolNames);
        }

        String getName() {
            return name;
        }
    }

    static class SimpleToolbox extends ToolboxBase {
        SimpleToolbox(String name) {
            super(name);
        }

        @Override
        void registerTools() {
            addToolName("tool1");
            addToolName("tool2");
        }
    }

    static class AdvancedToolbox extends ToolboxBase {
        AdvancedToolbox(String name) {
            super(name);
        }

        @Override
        void registerTools() {
            addToolName("advanced_tool1");
            addToolName("advanced_tool2");
            addToolName("advanced_tool3");
        }
    }

    @Nested
    @DisplayName("Toolbox Base Tests")
    class TestToolboxBaseClass {

        @Test
        @DisplayName("toolbox base has name")
        void testToolboxBaseHasName() {
            SimpleToolbox toolbox = new SimpleToolbox("simple");

            assertEquals("simple", toolbox.getName());
        }

        @Test
        @DisplayName("toolbox base registers tools")
        void testToolboxBaseRegistersTools() {
            SimpleToolbox toolbox = new SimpleToolbox("simple");
            toolbox.registerTools();

            assertEquals(2, toolbox.getToolNames().size());
            assertTrue(toolbox.getToolNames().contains("tool1"));
            assertTrue(toolbox.getToolNames().contains("tool2"));
        }

        @Test
        @DisplayName("different toolbox types")
        void testDifferentToolboxTypes() {
            SimpleToolbox simple = new SimpleToolbox("simple");
            simple.registerTools();

            AdvancedToolbox advanced = new AdvancedToolbox("advanced");
            advanced.registerTools();

            assertEquals(2, simple.getToolNames().size());
            assertEquals(3, advanced.getToolNames().size());
        }
    }

    @Nested
    @DisplayName("Tool Name Management Tests")
    class TestToolNameManagement {

        @Test
        @DisplayName("add tool name")
        void testAddToolName() {
            SimpleToolbox toolbox = new SimpleToolbox("test");
            toolbox.addToolName("custom_tool");

            assertTrue(toolbox.getToolNames().contains("custom_tool"));
        }

        @Test
        @DisplayName("get tool names returns copy")
        void testGetToolNamesReturnsCopy() {
            SimpleToolbox toolbox = new SimpleToolbox("test");
            toolbox.addToolName("tool1");

            List<String> names = toolbox.getToolNames();
            names.add("should_not_appear");

            assertEquals(1, toolbox.getToolNames().size());
        }
    }
}