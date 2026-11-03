/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder;

import com.openjiuwen.dev_tools.DevToolsPackageMarker;
import com.openjiuwen.dev_tools.agent_builder.resource.PluginProcessor;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderConstants;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentBuilderUtilitiesTest {

    @Test
    void constantsMatchPythonModule() {
        assertEquals(
                "Please provide your desired workflow description so I can generate "
                        + "the corresponding flowchart for you. If unclear, you can reply 'unclear' "
                        + "and I will plan the process for you.",
                AgentBuilderConstants.WORKFLOW_REQUEST_CONTENT);
        assertEquals("Workflow design content:\n", AgentBuilderConstants.WORKFLOW_DESIGN_RESPONSE_CONTENT);
        assertEquals(50, AgentBuilderConstants.DEFAULT_MAX_HISTORY_SIZE);
        assertEquals(3, AgentBuilderConstants.DEFAULT_MAX_RETRIES);
        assertEquals(30, AgentBuilderConstants.DEFAULT_TIMEOUT);
        assertEquals("plugin", AgentBuilderConstants.RESOURCE_TYPE_PLUGIN);
        assertEquals("knowledge", AgentBuilderConstants.RESOURCE_TYPE_KNOWLEDGE);
        assertEquals("workflow", AgentBuilderConstants.RESOURCE_TYPE_WORKFLOW);
        assertEquals("```(?:json)?\\s*([\\s\\S]*?)\\s*```", AgentBuilderConstants.JSON_EXTRACT_PATTERN);
        assertEquals("/api/v1", AgentBuilderConstants.API_BASE_PATH);
        assertEquals(0.1d, AgentBuilderConstants.PROGRESS_UPDATE_INTERVAL);
        assertEquals(30.0d, AgentBuilderConstants.PROGRESS_HEARTBEAT_INTERVAL);
        assertEquals(5000, AgentBuilderConstants.MAX_QUERY_LENGTH);
        assertEquals(1000, AgentBuilderConstants.MAX_HISTORY_SIZE);
    }

    @Test
    void packageMarkerRemainsMarkerOnly() throws Exception {
        Constructor<?> constructor = DevToolsPackageMarker.class.getDeclaredConstructor();
        assertThat(Modifier.isFinal(DevToolsPackageMarker.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }

    @Test
    void pluginProcessorMirrorsFormattingBehavior() {
        List<Map<String, Object>> rawPlugins = List.of(
                Map.of(
                        "plugin_id", "plugin-a",
                        "plugin_name", "Plugin A",
                        "plugin_desc", "Description A",
                        "plugin_version", "v1",
                        "tools", List.of(
                                Map.of(
                                        "tool_id", "tool-a",
                                        "tool_name", "Tool A",
                                        "desc", "Tool description",
                                        "code", "print(1)",
                                        "language", "python",
                                        "input_parameters", List.of(
                                                Map.of("name", "count", "description", "how many", "type", 2),
                                                Map.of("name", "raw", "desc", "raw value", "type", "string")),
                                        "output_parameters", List.of(
                                                Map.of("name", "ok", "description", "result", "type", 4))))));

        PluginProcessor.PreprocessResult preprocess = PluginProcessor.preprocess(rawPlugins);
        Map<String, Map<String, Object>> pluginDict = preprocess.pluginDict();
        Map<String, Object> plugin = pluginDict.get("plugin-a");
        Map<String, Object> tool = castTools(plugin.get("tools")).get("tool-a");

        assertEquals("integer", PluginProcessor.convertType(2));
        assertEquals("custom", PluginProcessor.convertType("custom"));
        assertEquals("string", PluginProcessor.convertType(null));
        assertEquals("plugin-a", preprocess.toolPluginIdMap().get("tool-a"));
        assertEquals("Plugin A", plugin.get("plugin_name"));
        assertEquals("draft", PluginProcessor.preprocess(List.of(Map.of("plugin_id", "p2"))).pluginDict().get("p2").get("plugin_version"));

        List<Map<String, Object>> inputsForDl = castList(tool.get("inputs_for_dl_gen"));
        assertEquals("count", inputsForDl.get(0).get("name"));
        assertEquals("how many", inputsForDl.get(0).get("description"));
        assertEquals("integer", inputsForDl.get(0).get("type"));
        assertEquals("raw value", inputsForDl.get(1).get("description"));

        List<Map<String, Object>> prompt = PluginProcessor.formatForPrompt(pluginDict);
        assertEquals("plugin-a", prompt.get(0).get("plugin_id"));
        List<Map<String, Object>> promptTools = castList(prompt.get(0).get("tools"));
        assertEquals("tool-a", promptTools.get(0).get("tool_id"));
        assertEquals("integer", castList(promptTools.get(0).get("input_parameters")).get(0).get("type"));

        PluginProcessor.RetrievedInfo retrieved = PluginProcessor.getRetrievedInfo(
                List.of("tool-a", "missing"),
                pluginDict,
                preprocess.toolPluginIdMap());
        assertEquals(1, retrieved.toolList().size());
        assertEquals("tool-a", retrieved.toolList().get(0).get("tool_id"));
        assertEquals("plugin-a", retrieved.retrievedToolIdMap().get("tool-a"));
        assertEquals(1, castList(retrieved.toolList().get(0).get("outputs")).size());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> castTools(Object value) {
        return (Map<String, Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
