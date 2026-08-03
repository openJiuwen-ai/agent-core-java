/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code PluginConverter} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/plugin_converter.py}.
 */
class PluginConverterTest {

    @Test
    void convertBuildsPluginParamInputsOutputsDefaultsAndEdge() {
        PluginConverter converter = new PluginConverter(nodeData(), Map.of(), resource(), new Position(0, 0));

        converter.convert();

        assertThat(converter.getNode().getId()).isEqualTo("node_plugin");
        assertThat(converter.getNode().getType()).isEqualTo(NodeType.Plugin.getDslType());
        assertThat(converter.getNode().getData().getInputs().getInputParameters()).containsKey("query");
        assertThat(converter.getNode().getData().getInputs().getPluginParam())
                .containsEntry("toolID", "tool-weather")
                .containsEntry("toolName", "Weather")
                .containsEntry("pluginID", "plugin-weather")
                .containsEntry("pluginName", "WeatherPlugin")
                .containsEntry("pluginVersion", "draft");

        OutputsField outputs = converter.getNode().getData().getOutputs();
        assertThat(outputs.getProperties()).containsKeys("temperature", "error_code", "error_message", "data");
        assertThat(outputs.getProperties().get("temperature").getDescription()).isEqualTo("current temperature");
        assertThat(outputs.getProperties().get("error_code").getType()).isEqualTo("integer");
        assertThat(outputs.getProperties().get("error_code").getExtra()).containsEntry("index", 1);
        assertThat(outputs.getProperties().get("error_message").getType()).isEqualTo("string");
        assertThat(outputs.getProperties().get("error_message").getExtra()).containsEntry("index", 2);
        assertThat(outputs.getProperties().get("data").getType()).isEqualTo("object");
        assertThat(outputs.getProperties().get("data").getExtra()).containsEntry("index", 3);
        assertThat(outputs.getProperties().get("data").getProperties()).isEmpty();
        assertThat(outputs.getRequired()).containsExactly("error_code", "error_message", "data");
        assertThat(converter.getEdges()).hasSize(1);
        assertThat(converter.getEdges().get(0).getTargetNodeId()).isEqualTo("node_end");
    }

    @Test
    void convertUsesEmptyPluginInfoDefaultsWhenResourceHasNoMatch() {
        PluginConverter converter = new PluginConverter(nodeData(), Map.of(), Map.of("plugins", List.of()), new Position(0, 0));

        converter.convert();

        assertThat(converter.getNode().getData().getInputs().getPluginParam())
                .containsEntry("toolID", "")
                .containsEntry("toolName", "")
                .containsEntry("pluginID", "")
                .containsEntry("pluginName", "")
                .containsEntry("pluginVersion", "draft");
    }

    @Test
    void pluginKindChecksFollowPythonTruthiness() {
        assertThat(PluginConverter.isLocalCodePlugin(Map.of("language", "python"))).isTrue();
        assertThat(PluginConverter.isLocalCodePlugin(Map.of("language", ""))).isFalse();
        assertThat(PluginConverter.isCloudPlugin(Map.of("method", "GET"))).isTrue();
        assertThat(PluginConverter.isCloudPlugin(Map.of("method", ""))).isFalse();
    }

    private static Map<String, Object> nodeData() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "node_plugin");
        node.put("type", "Plugin");
        node.put("description", "plugin node");
        node.put("parameters", Map.of(
                "inputs", List.of(Map.of("name", "query", "value", "${node_start.query}")),
                "outputs", List.of(Map.of(
                        "name", "temperature",
                        "description", "current temperature",
                        "type", "number"
                )),
                "configs", Map.of("tool_id", "tool-weather")
        ));
        node.put("next", "node_end");
        return node;
    }

    private static Map<String, Object> resource() {
        return Map.of("plugins", List.of(Map.of(
                "tool_id", "tool-weather",
                "tool_name", "Weather",
                "plugin_id", "plugin-weather",
                "plugin_name", "WeatherPlugin"
        )));
    }
}
