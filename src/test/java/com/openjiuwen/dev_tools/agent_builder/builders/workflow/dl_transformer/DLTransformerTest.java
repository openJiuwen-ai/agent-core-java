/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import com.openjiuwen.core.common.security.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's DL transformer tests in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/dl_transformer/test_dl_transformer.py}.
 */
@DisplayName("DLTransformer Tests")
class DLTransformerTest {

    @Test
    @DisplayName("collect plugin returns plugin information for mapped tools")
    void collectPluginReturnsMappedPluginInfo() {
        Map<String, Object> tool = mapOf(
                "tool_name", "Tool 1",
                "ori_inputs", List.of(mapOf("name", "query")),
                "ori_outputs", List.of(mapOf("name", "answer")),
                "language", "python",
                "code", "print('hello')");
        Map<String, Map<String, Object>> pluginDict = Map.of(
                "plugin_1",
                mapOf(
                        "plugin_name", "Test Plugin",
                        "plugin_version", "1.0",
                        "tools", Map.of("tool_1", tool)));

        List<Map<String, Object>> result = DLTransformer.collectPlugin(
                List.of("tool_1", "tool_2"),
                pluginDict,
                Map.of("tool_1", "plugin_1"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst())
                .containsEntry("plugin_id", "plugin_1")
                .containsEntry("plugin_name", "Test Plugin")
                .containsEntry("plugin_version", "1.0")
                .containsEntry("tool_id", "tool_1")
                .containsEntry("tool_name", "Tool 1")
                .containsEntry("language", "python")
                .containsEntry("code", "print('hello')");
        assertThat(result.getFirst().get("inputs")).asList().hasSize(1);
        assertThat(result.getFirst().get("outputs")).asList().hasSize(1);
    }

    @Test
    @DisplayName("collect plugin skips missing tools")
    void collectPluginSkipsMissingTools() {
        assertThat(DLTransformer.collectPlugin(List.of("tool_missing"), Map.of(), Map.of())).isEmpty();
        assertThat(DLTransformer.collectPlugin(List.of(), Map.of(), Map.of())).isEmpty();
    }

    @Test
    @DisplayName("registry contains all supported DL node types and returns a copy")
    void registryContainsAllSupportedTypesAndReturnsCopy() {
        Map<String, Class<? extends BaseConverter>> registry = DLTransformer.getDslConverterRegistry();

        assertThat(registry).containsKeys("Start", "End", "LLM", "IntentDetection", "Questioner",
                "Code", "Plugin", "Output", "Branch");
        registry.remove("Start");
        assertThat(DLTransformer.getDslConverterRegistry()).containsKey("Start");
    }

    @Test
    @DisplayName("constructor creates transformer instance")
    void constructorCreatesTransformerInstance() {
        assertThat(new DLTransformer()).isNotNull();
    }

    @Test
    @DisplayName("transform to Mermaid extracts JSON and renders graph")
    void transformToMermaidExtractsJsonAndRendersGraph() {
        String result = DLTransformer.transformToMermaid("prefix\n```json\n" + dlJson() + "\n```\nsuffix");

        assertThat(result).contains("graph TD").contains("node_start").contains("node_end");
    }

    @Test
    @DisplayName("transform to Mermaid rejects non-list JSON")
    void transformToMermaidRejectsNonListJson() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DLTransformer.transformToMermaid("{\"id\":\"node_start\"}"));

        assertThat(exception.getMessage()).contains("expected JSON array");
    }

    @Test
    @DisplayName("transform to DSL converts supported nodes to workflow JSON")
    void transformToDslConvertsSupportedNodes() {
        String dsl = new DLTransformer().transformToDsl(dlJson());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonUtils.safeJsonLoads(dsl);

        assertThat(parsed.get("nodes")).asList().hasSize(2);
        assertThat(parsed.get("edges")).asList().hasSize(1);
        assertThat(dsl).contains("node_start").contains("node_end");
    }

    @Test
    @DisplayName("transform to DSL mutates resource plugins to collected plugin info")
    void transformToDslMutatesResourcePlugins() {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("plugins", List.of(Map.of("tool_id", "tool-weather")));
        resource.put("plugin_dict", Map.of(
                "plugin-weather",
                mapOf(
                        "plugin_name", "WeatherPlugin",
                        "plugin_version", "draft",
                        "tools", Map.of(
                                "tool-weather",
                                mapOf("tool_name", "Weather", "ori_inputs", List.of(), "ori_outputs", List.of())))));
        resource.put("tool_id_map", Map.of("tool-weather", "plugin-weather"));

        new DLTransformer().transformToDsl(dlJson(), resource);

        assertThat(resource.get("plugins")).asList().hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> pluginInfo = (Map<String, Object>) ((List<?>) resource.get("plugins")).getFirst();
        assertThat(pluginInfo)
                .containsEntry("tool_id", "tool-weather")
                .containsEntry("plugin_id", "plugin-weather");
    }

    @Test
    @DisplayName("transform to DSL rejects node missing id like Python dict indexing")
    void transformToDslRejectsNodeMissingId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DLTransformer().transformToDsl("[{\"type\":\"Start\"}]"));

        assertThat(exception.getMessage()).contains("Missing required key: id");
    }

    @Test
    @DisplayName("transform to DSL rejects resource plugin missing tool id")
    void transformToDslRejectsResourcePluginMissingToolId() {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("plugins", List.of(Map.of("name", "missing-tool-id")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DLTransformer().transformToDsl(dlJson(), resource));

        assertThat(exception.getMessage()).contains("Missing required key: tool_id");
    }

    private static String dlJson() {
        return """
                [
                  {
                    "id": "node_start",
                    "type": "Start",
                    "description": "Start",
                    "parameters": {
                      "outputs": [
                        {"name": "query", "description": "user query", "type": "string"}
                      ]
                    },
                    "next": "node_end"
                  },
                  {
                    "id": "node_end",
                    "type": "End",
                    "description": "End",
                    "parameters": {
                      "inputs": [
                        {"name": "result", "value": "${node_start.query}", "type": "string"}
                      ]
                    }
                  }
                ]
                """;
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }
}
