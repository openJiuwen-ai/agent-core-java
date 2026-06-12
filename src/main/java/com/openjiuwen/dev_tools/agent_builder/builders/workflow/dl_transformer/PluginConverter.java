/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Plugin node converter.
 *
 * <p>Mirrors Python's {@code PluginConverter} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/plugin_converter.py}.</p>
 */
public class PluginConverter extends BaseConverter {

    public static final Map<String, Map<String, Object>> PLUGIN_DEFAULT_OUTPUTS = createPluginDefaultOutputs();

    public PluginConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict) {
        super(nodeData, nodesDict);
    }

    public PluginConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict, Map<String, Object> resource) {
        super(nodeData, nodesDict, resource);
    }

    public PluginConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict, Position position) {
        super(nodeData, nodesDict, position);
    }

    public PluginConverter(
            Map<String, Object> nodeData,
            Map<String, Object> nodesDict,
            Map<String, Object> resource,
            Position position) {
        super(nodeData, nodesDict, resource, position);
    }

    public static boolean isLocalCodePlugin(Map<String, Object> pluginInfo) {
        return truthy(pluginInfo.get("language")) || truthy(pluginInfo.get("code"));
    }

    public static boolean isCloudPlugin(Map<String, Object> pluginInfo) {
        return truthy(pluginInfo.get("path")) || truthy(pluginInfo.get("method"));
    }

    static Map<String, String> convertPluginInfo(Map<String, Object> pluginInfo) {
        Map<String, String> converted = new LinkedHashMap<>();
        converted.put("toolID", stringValue(pluginInfo.get("tool_id"), ""));
        converted.put("toolName", stringValue(pluginInfo.get("tool_name"), ""));
        converted.put("pluginID", stringValue(pluginInfo.get("plugin_id"), ""));
        converted.put("pluginName", stringValue(pluginInfo.get("plugin_name"), ""));
        converted.put("pluginVersion", stringValue(pluginInfo.get("plugin_version"), "draft"));
        return converted;
    }

    @Override
    protected void convertSpecificConfig() {
        Map<String, Object> parameters = mapValue(required(nodeData, "parameters"));
        Map<String, Object> configs = parameters.containsKey("configs")
                ? mapValue(parameters.get("configs"))
                : Map.of();
        Object toolId = configs.getOrDefault("tool_id", "");
        Map<String, Object> pluginInfo = findPluginInfo(toolId);

        node.getData().setInputs(new InputsField(
                convertInputVariables(mapListValue(required(parameters, "inputs"))),
                null,
                null,
                null,
                null,
                null,
                convertPluginInfo(pluginInfo),
                null,
                null,
                null
        ));
        node.getData().setOutputs(convertOutputsWithDefaults(mapListValue(required(parameters, "outputs"))));
    }

    private OutputsField convertOutputsWithDefaults(List<Map<String, Object>> outputs) {
        OutputsField result = new OutputsField("object");
        result.setRequired(new ArrayList<>());

        for (Map<String, Object> item : outputs) {
            List<String> variableNames = reversedParts(stringValue(required(item, "name"), ""));
            result.addProperty(new OutputPropertySpec(
                    variableNames,
                    stringValue(required(item, "description"), null),
                    variableIndex,
                    item.get("type") != null ? String.valueOf(item.get("type")) : null,
                    null,
                    null,
                    null
            ));
            variableIndex++;
        }

        for (Map.Entry<String, Map<String, Object>> entry : PLUGIN_DEFAULT_OUTPUTS.entrySet()) {
            String name = entry.getKey();
            if (result.getProperties() != null && result.getProperties().containsKey(name)) {
                continue;
            }
            Map<String, Object> config = entry.getValue();
            result.addProperty(new OutputPropertySpec(
                    List.of(name),
                    null,
                    ((Number) required(config, "index")).intValue(),
                    String.valueOf(required(config, "type")),
                    null,
                    "data".equals(name) ? Map.of() : null,
                    "data".equals(name) ? List.of() : null
            ));
        }

        result.setRequired(List.of("error_code", "error_message", "data"));
        return result;
    }

    private Map<String, Object> findPluginInfo(Object toolId) {
        if (resource == null) {
            return Map.of();
        }
        Object pluginsObject = resource.get("plugins");
        if (!(pluginsObject instanceof List<?> plugins)) {
            return Map.of();
        }
        for (Object pluginObject : plugins) {
            Map<String, Object> plugin = mapValue(pluginObject);
            if (Objects.equals(plugin.get("tool_id"), toolId)) {
                return plugin;
            }
        }
        return Map.of();
    }

    private static Map<String, Map<String, Object>> createPluginDefaultOutputs() {
        Map<String, Map<String, Object>> outputs = new LinkedHashMap<>();
        outputs.put("error_code", defaultOutput("integer", 1));
        outputs.put("error_message", defaultOutput("string", 2));
        outputs.put("data", defaultOutput("object", 3));
        return outputs;
    }

    private static Map<String, Object> defaultOutput(String type, int index) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("type", type);
        output.put("index", index);
        return output;
    }

    private static Object required(Map<String, Object> source, String key) {
        if (!source.containsKey(key)) {
            throw new IllegalArgumentException("Missing required plugin converter key: " + key);
        }
        return source.get(key);
    }

    private static String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0D;
        }
        if (value instanceof String text) {
            return !text.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private static List<String> reversedParts(String name) {
        String[] parts = name.split("_of_");
        List<String> variableNames = new ArrayList<>();
        for (int index = parts.length - 1; index >= 0; index--) {
            variableNames.add(parts[index]);
        }
        return variableNames;
    }

    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected map value: " + value);
        }
        return toStringObjectMap(map);
    }

    private static List<Map<String, Object>> mapListValue(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected list value: " + value);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            result.add(mapValue(item));
        }
        return result;
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return converted;
    }
}
