/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TestTypeMap}, {@code TestConvertType}, and {@code TestPluginProcessor} in
 * {@code tests/unit_tests/dev_tools/agent_builder/resource/test_processor.py}.
 *
 * <p>Also mirrors Python's {@code tests.system_tests.dev_tools.agent_builder.resource.test_resource_integration}
 * in {@code tests/system_tests/dev_tools/agent_builder/resource/test_resource_integration.py}.</p>
 */
class PluginProcessorTest {

    @Test
    void typeMapValuesMatchPythonConstants() {
        assertEquals("string", PluginProcessor.TYPE_MAP.get(1));
        assertEquals("integer", PluginProcessor.TYPE_MAP.get(2));
        assertEquals("number", PluginProcessor.TYPE_MAP.get(3));
        assertEquals("boolean", PluginProcessor.TYPE_MAP.get(4));
        assertEquals("array", PluginProcessor.TYPE_MAP.get(5));
        assertEquals("object", PluginProcessor.TYPE_MAP.get(6));
    }

    @Test
    void convertIntTypeUsesTypeMap() {
        assertEquals("string", PluginProcessor.convertType(1));
        assertEquals("integer", PluginProcessor.convertType(2));
        assertEquals("number", PluginProcessor.convertType(3));
        assertEquals("boolean", PluginProcessor.convertType(4));
        assertEquals("array", PluginProcessor.convertType(5));
        assertEquals("object", PluginProcessor.convertType(6));
    }

    @Test
    void convertStringTypeReturnsOriginalValue() {
        assertEquals("string", PluginProcessor.convertType("string"));
        assertEquals("integer", PluginProcessor.convertType("integer"));
        assertEquals("custom", PluginProcessor.convertType("custom"));
    }

    @Test
    void convertUnknownIntTypeFallsBackToString() {
        assertEquals("string", PluginProcessor.convertType(999));
    }

    @Test
    void convertNullTypeFallsBackToString() {
        assertEquals("string", PluginProcessor.convertType(null));
    }

    @Test
    void convertOtherTypeFallsBackToString() {
        assertEquals("string", PluginProcessor.convertType(List.of(1, 2, 3)));
        assertEquals("string", PluginProcessor.convertType(Map.of("key", "value")));
    }

    @Test
    void preprocessEmptyListReturnsEmptyMaps() {
        PluginProcessor.PreprocessResult result = PluginProcessor.preprocess(List.of());

        assertTrue(result.pluginDict().isEmpty());
        assertTrue(result.toolPluginIdMap().isEmpty());
    }

    @Test
    void preprocessNullReturnsEmptyMaps() {
        PluginProcessor.PreprocessResult result = PluginProcessor.preprocess(null);

        assertTrue(result.pluginDict().isEmpty());
        assertTrue(result.toolPluginIdMap().isEmpty());
    }

    @Test
    void preprocessSinglePluginBuildsPluginAndToolMaps() {
        List<Map<String, Object>> rawPlugins = List.of(map(
                "plugin_id", "plugin_001",
                "plugin_name", "Test Plugin",
                "plugin_desc", "A test plugin",
                "plugin_version", "1.0.0",
                "tools", List.of(map(
                        "tool_id", "tool_001",
                        "tool_name", "Test Tool",
                        "desc", "A test tool",
                        "code", "print('hello')",
                        "language", "python",
                        "input_parameters", List.of(map("name", "param1", "desc", "First param", "type", 1)),
                        "output_parameters", List.of(map("name", "result", "desc", "Result", "type", 1))))));

        PluginProcessor.PreprocessResult result = PluginProcessor.preprocess(rawPlugins);

        assertTrue(result.pluginDict().containsKey("plugin_001"));
        assertTrue(result.toolPluginIdMap().containsKey("tool_001"));
        assertEquals("plugin_001", result.toolPluginIdMap().get("tool_001"));

        Map<String, Object> plugin = result.pluginDict().get("plugin_001");
        assertEquals("Test Plugin", plugin.get("plugin_name"));
        assertEquals("A test plugin", plugin.get("plugin_desc"));
        assertTrue(asToolMap(plugin.get("tools")).containsKey("tool_001"));

        Map<String, Object> tool = asToolMap(plugin.get("tools")).get("tool_001");
        assertEquals("Test Tool", tool.get("tool_name"));
        assertEquals("A test tool", tool.get("tool_desc"));
        assertEquals("print('hello')", tool.get("code"));
        assertEquals("python", tool.get("language"));
    }

    @Test
    void preprocessMultiplePluginsBuildsBothMappings() {
        List<Map<String, Object>> rawPlugins = List.of(
                map(
                        "plugin_id", "plugin_001",
                        "plugin_name", "Plugin 1",
                        "plugin_desc", "First plugin",
                        "tools", List.of(map("tool_id", "tool_001", "tool_name", "Tool 1", "desc", "Tool 1"))),
                map(
                        "plugin_id", "plugin_002",
                        "plugin_name", "Plugin 2",
                        "plugin_desc", "Second plugin",
                        "tools", List.of(map("tool_id", "tool_002", "tool_name", "Tool 2", "desc", "Tool 2"))));

        PluginProcessor.PreprocessResult result = PluginProcessor.preprocess(rawPlugins);

        assertEquals(2, result.pluginDict().size());
        assertEquals(2, result.toolPluginIdMap().size());
        assertEquals("plugin_001", result.toolPluginIdMap().get("tool_001"));
        assertEquals("plugin_002", result.toolPluginIdMap().get("tool_002"));
    }

    @Test
    void preprocessPluginWithoutIdIsSkipped() {
        List<Map<String, Object>> rawPlugins = List.of(map(
                "plugin_name", "No ID Plugin",
                "tools", List.of()));

        PluginProcessor.PreprocessResult result = PluginProcessor.preprocess(rawPlugins);

        assertEquals(0, result.pluginDict().size());
        assertEquals(0, result.toolPluginIdMap().size());
    }

    @Test
    void preprocessToolWithoutIdKeepsPluginAndSkipsToolMapEntry() {
        List<Map<String, Object>> rawPlugins = List.of(map(
                "plugin_id", "plugin_001",
                "plugin_name", "Test Plugin",
                "tools", List.of(map("tool_name", "No ID Tool"))));

        PluginProcessor.PreprocessResult result = PluginProcessor.preprocess(rawPlugins);

        assertTrue(result.pluginDict().containsKey("plugin_001"));
        assertEquals(0, result.toolPluginIdMap().size());
    }

    @Test
    void formatForPromptConvertsParameterTypes() {
        Map<String, Map<String, Object>> pluginDict = new LinkedHashMap<>();
        pluginDict.put("plugin_001", map(
                "plugin_id", "plugin_001",
                "plugin_name", "Test Plugin",
                "plugin_desc", "A test plugin",
                "tools", Map.of("tool_001", map(
                        "tool_id", "tool_001",
                        "tool_name", "Test Tool",
                        "tool_desc", "A test tool",
                        "code", "print('hello')",
                        "language", "python",
                        "input_parameters", List.of(map("name", "param1", "desc", "First param", "type", 1)),
                        "output_parameters", List.of(map("name", "result", "desc", "Result", "type", 2))))));

        List<Map<String, Object>> result = PluginProcessor.formatForPrompt(pluginDict);

        assertEquals(1, result.size());
        assertEquals("plugin_001", result.get(0).get("plugin_id"));
        assertEquals("Test Plugin", result.get(0).get("plugin_name"));
        assertEquals(1, asMapList(result.get(0).get("tools")).size());

        Map<String, Object> tool = asMapList(result.get(0).get("tools")).get(0);
        assertEquals("tool_001", tool.get("tool_id"));
        assertEquals("Test Tool", tool.get("tool_name"));
        assertEquals("string", asMapList(tool.get("input_parameters")).get(0).get("type"));
        assertEquals("integer", asMapList(tool.get("output_parameters")).get(0).get("type"));
    }

    @Test
    void formatForPromptEmptyReturnsEmptyList() {
        assertEquals(List.of(), PluginProcessor.formatForPrompt(Map.of()));
    }

    @Test
    void getRetrievedInfoIncludesInputsAndOutputsByDefault() {
        Map<String, Map<String, Object>> pluginDict = pluginDictForRetrievedInfo(
                map(
                        "tool_id", "tool_001",
                        "tool_name", "Test Tool",
                        "tool_desc", "A test tool",
                        "inputs_for_dl_gen", List.of(map("name", "p1", "desc", "param", "type", 1)),
                        "outputs_for_dl_gen", List.of(map("name", "r1", "desc", "result", "type", 1))),
                map(
                        "tool_id", "tool_002",
                        "tool_name", "Tool 2",
                        "tool_desc", "Another tool",
                        "inputs_for_dl_gen", List.of(),
                        "outputs_for_dl_gen", List.of()));
        Map<String, String> toolIdMap = mapStrings("tool_001", "plugin_001", "tool_002", "plugin_001");

        PluginProcessor.RetrievedInfo result = PluginProcessor.getRetrievedInfo(
                List.of("tool_001"),
                pluginDict,
                toolIdMap);

        assertEquals(1, result.toolList().size());
        assertEquals("tool_001", result.toolList().get(0).get("tool_id"));
        assertEquals("Test Tool", result.toolList().get(0).get("tool_name"));
        assertTrue(result.toolList().get(0).containsKey("inputs"));
        assertTrue(result.toolList().get(0).containsKey("outputs"));
    }

    @Test
    void getRetrievedInfoCanOmitInputsAndOutputs() {
        Map<String, Map<String, Object>> pluginDict = pluginDictForRetrievedInfo(map(
                "tool_id", "tool_001",
                "tool_name", "Test Tool",
                "tool_desc", "A test tool",
                "inputs_for_dl_gen", List.of(),
                "outputs_for_dl_gen", List.of()));
        Map<String, String> toolIdMap = mapStrings("tool_001", "plugin_001");

        PluginProcessor.RetrievedInfo result = PluginProcessor.getRetrievedInfo(
                List.of("tool_001"),
                pluginDict,
                toolIdMap,
                false);

        assertFalse(result.toolList().get(0).containsKey("inputs"));
        assertFalse(result.toolList().get(0).containsKey("outputs"));
    }

    @Test
    void getRetrievedInfoInvalidToolIdReturnsEmptyResults() {
        Map<String, Map<String, Object>> pluginDict = pluginDictForRetrievedInfo();
        Map<String, String> toolIdMap = Map.of();

        PluginProcessor.RetrievedInfo result = PluginProcessor.getRetrievedInfo(
                List.of("invalid_tool_id"),
                pluginDict,
                toolIdMap);

        assertEquals(0, result.toolList().size());
        assertEquals(0, result.retrievedPluginDict().size());
        assertEquals(0, result.retrievedToolIdMap().size());
    }

    @SafeVarargs
    private static Map<String, Map<String, Object>> pluginDictForRetrievedInfo(Map<String, Object>... tools) {
        Map<String, Object> toolMap = new LinkedHashMap<>();
        for (Map<String, Object> tool : tools) {
            toolMap.put(String.valueOf(tool.get("tool_id")), tool);
        }

        Map<String, Map<String, Object>> pluginDict = new LinkedHashMap<>();
        pluginDict.put("plugin_001", map(
                "plugin_id", "plugin_001",
                "plugin_name", "Test Plugin",
                "plugin_desc", "A test plugin",
                "tools", toolMap));
        return pluginDict;
    }

    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return result;
    }

    private static Map<String, String> mapStrings(String... entries) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(entries[i], entries[i + 1]);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> asToolMap(Object value) {
        return (Map<String, Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asMapList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
