/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
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

        @Test
        void testPreprocessMultiplePlugins() {
            Map<String, Object> plugin1 = Map.of(
                    "plugin_id", "plugin_001",
                    "plugin_name", "Plugin 1",
                    "plugin_desc", "First plugin",
                    "tools", List.of(Map.of("tool_id", "tool_001", "tool_name", "Tool 1", "desc", "Tool 1")));
            Map<String, Object> plugin2 = Map.of(
                    "plugin_id", "plugin_002",
                    "plugin_name", "Plugin 2",
                    "plugin_desc", "Second plugin",
                    "tools", List.of(Map.of("tool_id", "tool_002", "tool_name", "Tool 2", "desc", "Tool 2")));

            PluginProcessor.PreprocessResult result = PluginProcessor.preprocess(List.of(plugin1, plugin2));

            assertEquals(2, result.pluginDict().size());
            assertEquals(2, result.toolPluginIdMap().size());
            assertEquals("plugin_001", result.toolPluginIdMap().get("tool_001"));
            assertEquals("plugin_002", result.toolPluginIdMap().get("tool_002"));
        }

        @Test
        void testPreprocessPluginWithoutId() {
            PluginProcessor.PreprocessResult result = PluginProcessor.preprocess(List.of(Map.of(
                    "plugin_name", "No ID Plugin",
                    "tools", List.of())));

            assertTrue(result.pluginDict().isEmpty());
            assertTrue(result.toolPluginIdMap().isEmpty());
        }

        @Test
        void testPreprocessToolWithoutId() {
            PluginProcessor.PreprocessResult result = PluginProcessor.preprocess(List.of(Map.of(
                    "plugin_id", "plugin_001",
                    "plugin_name", "Test Plugin",
                    "tools", List.of(Map.of("tool_name", "No ID Tool")))));

            assertTrue(result.pluginDict().containsKey("plugin_001"));
            assertTrue(result.toolPluginIdMap().isEmpty());
        }

        @Test
        void testFormatForPrompt() {
            Map<String, Map<String, Object>> pluginDict = pluginDictWithTwoTools();

            List<Map<String, Object>> result = PluginProcessor.formatForPrompt(pluginDict);

            assertEquals(1, result.size());
            assertEquals("plugin_001", result.get(0).get("plugin_id"));
            assertEquals("Test Plugin", result.get(0).get("plugin_name"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get(0).get("tools");
            assertEquals(2, tools.size());
            Map<String, Object> tool = tools.get(0);
            assertEquals("tool_001", tool.get("tool_id"));
            assertEquals("Test Tool", tool.get("tool_name"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> inputs = (List<Map<String, Object>>) tool.get("input_parameters");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> outputs = (List<Map<String, Object>>) tool.get("output_parameters");
            assertEquals("string", inputs.get(0).get("type"));
            assertEquals("integer", outputs.get(0).get("type"));
        }

        @Test
        void testFormatForPromptEmpty() {
            assertEquals(List.of(), PluginProcessor.formatForPrompt(Map.of()));
        }

        @Test
        void testGetRetrievedInfo() {
            PluginProcessor.RetrievedInfo result = PluginProcessor.getRetrievedInfo(
                    List.of("tool_001"),
                    pluginDictWithTwoTools(),
                    Map.of("tool_001", "plugin_001", "tool_002", "plugin_001"),
                    true);

            assertEquals(1, result.toolList().size());
            assertEquals("tool_001", result.toolList().get(0).get("tool_id"));
            assertEquals("Test Tool", result.toolList().get(0).get("tool_name"));
            assertTrue(result.toolList().get(0).containsKey("inputs"));
            assertTrue(result.toolList().get(0).containsKey("outputs"));
        }

        @Test
        void testGetRetrievedInfoWithoutInputsOutputs() {
            PluginProcessor.RetrievedInfo result = PluginProcessor.getRetrievedInfo(
                    List.of("tool_001"),
                    pluginDictWithTwoTools(),
                    Map.of("tool_001", "plugin_001"),
                    false);

            assertFalse(result.toolList().get(0).containsKey("inputs"));
            assertFalse(result.toolList().get(0).containsKey("outputs"));
        }

        @Test
        void testGetRetrievedInfoInvalidToolId() {
            PluginProcessor.RetrievedInfo result = PluginProcessor.getRetrievedInfo(
                    List.of("invalid_tool_id"),
                    pluginDictWithTwoTools(),
                    Map.of());

            assertTrue(result.toolList().isEmpty());
            assertTrue(result.retrievedPluginDict().isEmpty());
            assertTrue(result.retrievedToolIdMap().isEmpty());
        }

        private Map<String, Map<String, Object>> pluginDictWithTwoTools() {
            Map<String, Object> tool1 = new LinkedHashMap<>();
            tool1.put("tool_id", "tool_001");
            tool1.put("tool_name", "Test Tool");
            tool1.put("tool_desc", "A test tool");
            tool1.put("code", "print('hello')");
            tool1.put("language", "python");
            tool1.put("input_parameters", List.of(Map.of("name", "param1", "desc", "First param", "type", 1)));
            tool1.put("output_parameters", List.of(Map.of("name", "result", "desc", "Result", "type", 2)));
            tool1.put("inputs_for_dl_gen", List.of(Map.of("name", "p1", "description", "param", "type", "string")));
            tool1.put("outputs_for_dl_gen", List.of(Map.of("name", "r1", "description", "result", "type", "string")));

            Map<String, Object> tool2 = new LinkedHashMap<>();
            tool2.put("tool_id", "tool_002");
            tool2.put("tool_name", "Tool 2");
            tool2.put("tool_desc", "Another tool");
            tool2.put("input_parameters", List.of());
            tool2.put("output_parameters", List.of());
            tool2.put("inputs_for_dl_gen", List.of());
            tool2.put("outputs_for_dl_gen", List.of());

            Map<String, Object> plugin = new LinkedHashMap<>();
            plugin.put("plugin_id", "plugin_001");
            plugin.put("plugin_name", "Test Plugin");
            plugin.put("plugin_desc", "A test plugin");
            plugin.put("tools", Map.of("tool_001", tool1, "tool_002", tool2));

            return Map.of("plugin_001", plugin);
        }
    }
}
