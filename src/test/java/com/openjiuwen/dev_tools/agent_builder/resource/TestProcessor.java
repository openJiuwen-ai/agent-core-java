/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test processor functionality.
 * <p>
 * Mirrors Python's {@code test_processor.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/resource/test_processor.py}.
 *
 */
class TestProcessor {

    /**
     * Test TYPE_MAP constant.
     * <p>
     * Mirrors Python's {@code TestTypeMap} class.
     */
    @Nested
    class TestTypeMap {

        @Test
        void testTypeMapValues() {
            assertEquals("string", PluginProcessor.TYPE_MAP.get(1));
            assertEquals("integer", PluginProcessor.TYPE_MAP.get(2));
            assertEquals("number", PluginProcessor.TYPE_MAP.get(3));
            assertEquals("boolean", PluginProcessor.TYPE_MAP.get(4));
            assertEquals("array", PluginProcessor.TYPE_MAP.get(5));
            assertEquals("object", PluginProcessor.TYPE_MAP.get(6));
        }
    }

    /**
     * Test convertType function.
     * <p>
     * Mirrors Python's {@code TestConvertType} class.
     */
    @Nested
    class TestConvertType {

        @Test
        void testConvertIntType() {
            assertEquals("string", PluginProcessor.convertType(1));
            assertEquals("integer", PluginProcessor.convertType(2));
            assertEquals("number", PluginProcessor.convertType(3));
            assertEquals("boolean", PluginProcessor.convertType(4));
            assertEquals("array", PluginProcessor.convertType(5));
            assertEquals("object", PluginProcessor.convertType(6));
        }

        @Test
        void testConvertStringType() {
            assertEquals("string", PluginProcessor.convertType("string"));
            assertEquals("integer", PluginProcessor.convertType("integer"));
            assertEquals("custom", PluginProcessor.convertType("custom"));
        }

        @Test
        void testConvertUnknownIntType() {
            assertEquals("string", PluginProcessor.convertType(999));
        }

        @Test
        void testConvertNullType() {
            assertEquals("string", PluginProcessor.convertType(null));
        }

        @Test
        void testConvertOtherType() {
            assertEquals("string", PluginProcessor.convertType(List.of(1, 2, 3)));
            assertEquals("string", PluginProcessor.convertType(Map.of("key", "value")));
        }
    }

    /**
     * Test PluginProcessor.
     * <p>
     * Mirrors Python's {@code TestPluginProcessor} class.
     */
    @Nested
    class TestPluginProcessor {

        @Test
        void testPreprocessEmptyList() {
            PluginProcessor.PreprocessResult result = PluginProcessor.preprocess(List.of());

            assertTrue(result.pluginDict().isEmpty());
            assertTrue(result.toolPluginIdMap().isEmpty());
        }

        @Test
        void testPreprocessNull() {
            PluginProcessor.PreprocessResult result = PluginProcessor.preprocess(null);

            assertTrue(result.pluginDict().isEmpty());
            assertTrue(result.toolPluginIdMap().isEmpty());
        }

        @Test
        void testPreprocessSinglePlugin() {
            Map<String, Object> tool = Map.of(
                    "tool_id", "tool_001",
                    "tool_name", "Test Tool",
                    "desc", "A test tool",
                    "code", "print('hello')",
                    "language", "python",
                    "input_parameters", List.of(Map.of("name", "param1", "desc", "First param", "type", 1)),
                    "output_parameters", List.of(Map.of("name", "result", "desc", "Result", "type", 1)));
            Map<String, Object> plugin = Map.of(
                    "plugin_id", "plugin_001",
                    "plugin_name", "Test Plugin",
                    "plugin_desc", "A test plugin",
                    "plugin_version", "1.0.0",
                    "tools", List.of(tool));

            PluginProcessor.PreprocessResult result = PluginProcessor.preprocess(List.of(plugin));

            assertTrue(result.pluginDict().containsKey("plugin_001"));
            assertEquals("plugin_001", result.toolPluginIdMap().get("tool_001"));
            Map<String, Object> formattedPlugin = result.pluginDict().get("plugin_001");
            assertEquals("Test Plugin", formattedPlugin.get("plugin_name"));
            assertEquals("A test plugin", formattedPlugin.get("plugin_desc"));
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> tools =
                    (Map<String, Map<String, Object>>) formattedPlugin.get("tools");
            assertTrue(tools.containsKey("tool_001"));
            assertEquals("Test Tool", tools.get("tool_001").get("tool_name"));
            assertEquals("A test tool", tools.get("tool_001").get("tool_desc"));
            assertEquals("print('hello')", tools.get("tool_001").get("code"));
            assertEquals("python", tools.get("tool_001").get("language"));
        }
    }
}
