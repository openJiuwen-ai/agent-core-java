/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * System tests for agent_builder resource module.
 * <p>
 * Mirrors Python's {@code test_resource_integration.py} in
 * {@code tests/system_tests/dev_tools/agent_builder/resource/test_resource_integration.py}.
 */
class TestResourceIntegration {

    @Nested
    class TestPluginProcessorIntegration {

        @Test
        void testPluginProcessorFullWorkflow() {
            List<Map<String, Object>> plugins = List.of(
                    Map.of(
                            "plugin_id", "plugin_001",
                            "plugin_name", "Weather Query",
                            "plugin_desc", "Query weather by city",
                            "tools", List.of(Map.of(
                                    "tool_id", "tool_001",
                                    "tool_name", "get_weather",
                                    "desc", "Get weather",
                                    "input_parameters", List.of(Map.of(
                                            "name", "city", "type", 1, "description", "city name")),
                                    "output_parameters", List.of(Map.of(
                                            "name", "weather", "type", 1, "description", "weather text"))))),
                    Map.of(
                            "plugin_id", "plugin_002",
                            "plugin_name", "Calculator",
                            "plugin_desc", "Run math calculations",
                            "tools", List.of(Map.of(
                                    "tool_id", "tool_002",
                                    "tool_name", "calculate",
                                    "desc", "Calculate",
                                    "input_parameters", List.of(Map.of(
                                            "name", "expression", "type", 1, "description", "expression")),
                                    "output_parameters", List.of(Map.of(
                                            "name", "result", "type", 2, "description", "result"))))));

            PluginProcessor.PreprocessResult preprocessResult = PluginProcessor.preprocess(plugins);

            assertEquals(2, preprocessResult.pluginDict().size());
            assertTrue(preprocessResult.pluginDict().containsKey("plugin_001"));
            assertTrue(preprocessResult.pluginDict().containsKey("plugin_002"));
            assertTrue(preprocessResult.toolPluginIdMap().containsKey("tool_001"));
            assertTrue(preprocessResult.toolPluginIdMap().containsKey("tool_002"));

            List<Map<String, Object>> promptText =
                    PluginProcessor.formatForPrompt(preprocessResult.pluginDict());

            assertEquals(2, promptText.size());
            assertTrue(promptText.stream().anyMatch(p -> "Weather Query".equals(p.get("plugin_name"))));
            assertTrue(promptText.stream().anyMatch(p -> "Calculator".equals(p.get("plugin_name"))));
        }

        @Test
        void testPluginProcessorRetrievedInfo() {
            PluginProcessor.PreprocessResult preprocessResult = PluginProcessor.preprocess(List.of(
                    Map.of(
                            "plugin_id", "plugin_001",
                            "plugin_name", "Weather Query",
                            "plugin_desc", "Query weather",
                            "tools", List.of(Map.of(
                                    "tool_id", "tool_001",
                                    "tool_name", "get_weather",
                                    "desc", "Get weather",
                                    "input_parameters", List.of(Map.of("name", "city", "type", 1)),
                                    "output_parameters", List.of(Map.of("name", "weather", "type", 1)))))));

            PluginProcessor.RetrievedInfo retrievedInfo = PluginProcessor.getRetrievedInfo(
                    List.of("tool_001"),
                    preprocessResult.pluginDict(),
                    preprocessResult.toolPluginIdMap(),
                    true);

            assertEquals(1, retrievedInfo.toolList().size());
            assertEquals("get_weather", retrievedInfo.toolList().get(0).get("tool_name"));
        }

        @Test
        void testPluginProcessorEmptyAndNone() {
            PluginProcessor.PreprocessResult resultEmpty = PluginProcessor.preprocess(List.of());
            assertTrue(resultEmpty.pluginDict().isEmpty());
            assertTrue(resultEmpty.toolPluginIdMap().isEmpty());

            PluginProcessor.PreprocessResult resultNull = PluginProcessor.preprocess(null);
            assertTrue(resultNull.pluginDict().isEmpty());
            assertTrue(resultNull.toolPluginIdMap().isEmpty());
        }
    }

    @Nested
    class TestPromptIntegration {

        @Test
        void testRetrieveSystemPromptContent() {
            assertFalse(Prompt.RETRIEVE_SYSTEM_PROMPT.isBlank());
        }

        @Test
        void testRetrieveSystemTemplateFormatting() {
            var formatted = Prompt.RETRIEVE_SYSTEM_TEMPLATE.format(Map.of(
                    "dialog_history", "user: hello\nassistant: hello",
                    "plugin_info_list", "Weather Query, Calculator"));

            assertNotNull(formatted);
        }

        @Test
        void testRetrieveSystemTemplateToMessages() {
            var messages = Prompt.RETRIEVE_SYSTEM_TEMPLATE.format(Map.of(
                    "dialog_history", "user: hello",
                    "plugin_info_list", "Weather Query")).toMessages();

            assertFalse(messages.isEmpty());
        }
    }

    @Nested
    class TestResourceRetrieverIntegration {

        @Test
        void testRetrieverInitialization() {
            Object mockLlm = new Object();

            ResourceRetriever retriever = new ResourceRetriever(mockLlm, List.of());

            assertNotNull(retriever);
            assertSame(mockLlm, retriever.getLlm());
        }

        @Test
        void testRetrieverLoadResources() {
            assertEquals(List.of(), ResourceRetriever.loadResources("missing/plugins.json"));
        }

        @Test
        void testRetrieverRetrieveWorkflow() {
            ResourceRetriever retriever = new StaticRetriever(new Object(), List.of(
                    Map.of(
                            "plugin_id", "plugin_001",
                            "plugin_name", "Weather Query",
                            "plugin_desc", "Query weather",
                            "tools", List.of(Map.of(
                                    "tool_id", "tool_001",
                                    "tool_name", "get_weather",
                                    "desc", "Get weather",
                                    "input_parameters", List.of(Map.of("name", "city", "type", 1)),
                                    "output_parameters", List.of(Map.of("name", "weather", "type", 1)))))));

            Map<String, Object> result = retriever.retrieve(
                    List.of(Map.of("role", "user", "content", "create a weather workflow")),
                    false);

            assertTrue(result.containsKey("plugins"));
            assertTrue(result.containsKey("plugin_dict"));
            assertTrue(result.containsKey("tool_id_map"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> plugins = (List<Map<String, Object>>) result.get("plugins");
            assertEquals(1, plugins.size());
            assertEquals("tool_001", plugins.get(0).get("tool_id"));
            assertFalse(plugins.get(0).containsKey("inputs"));
        }
    }

    private static final class StaticRetriever extends ResourceRetriever {
        private StaticRetriever(Object llm, List<Map<String, Object>> rawPlugins) {
            super(llm, rawPlugins);
        }

        @Override
        protected Map<String, Object> llmRetrieve(List<BaseMessage> messages) {
            return Map.of("tool_id_list", List.of("tool_001"));
        }
    }
}
