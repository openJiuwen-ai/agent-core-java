/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DL transformer facade for Mermaid flowcharts and workflow DSL JSON.
 *
 * <p>Mirrors Python's {@code DLTransformer} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/dl_transformer.py}.</p>
 */
public class DLTransformer {

    private static final LoggerProtocol LOGGER = LogManager.getLogger("agent_builder");
    private static final Map<String, Class<? extends BaseConverter>> DSL_CONVERTER_REGISTRY = createRegistry();

    public static Map<String, Class<? extends BaseConverter>> getDslConverterRegistry() {
        return new LinkedHashMap<>(DSL_CONVERTER_REGISTRY);
    }

    public static List<Map<String, Object>> collectPlugin(List<String> toolIdList,
                                                          Map<String, Map<String, Object>> pluginDict,
                                                          Map<String, String> toolIdMap) {
        List<Map<String, Object>> collected = new ArrayList<>();
        for (String toolId : toolIdList) {
            if (!toolIdMap.containsKey(toolId)) {
                continue;
            }

            String pluginId = toolIdMap.get(toolId);
            Map<String, Object> plugin = pluginDict.getOrDefault(pluginId, Map.of());
            Map<String, Object> tools = mapValue(plugin.getOrDefault("tools", Map.of()));
            Map<String, Object> tool = mapValue(tools.getOrDefault(toolId, Map.of()));

            Map<String, Object> pluginInfo = new LinkedHashMap<>();
            pluginInfo.put("plugin_id", pluginId);
            pluginInfo.put("plugin_name", stringValue(plugin.get("plugin_name"), ""));
            pluginInfo.put("plugin_version", stringValue(plugin.get("plugin_version"), ""));
            pluginInfo.put("tool_id", toolId);
            pluginInfo.put("tool_name", stringValue(tool.get("tool_name"), ""));
            pluginInfo.put("inputs", tool.getOrDefault("ori_inputs", List.of()));
            pluginInfo.put("outputs", tool.getOrDefault("ori_outputs", List.of()));

            putIfTruthy(pluginInfo, "language", tool.get("language"));
            putIfTruthy(pluginInfo, "code", tool.get("code"));
            putIfTruthy(pluginInfo, "path", tool.get("path"));
            putIfTruthy(pluginInfo, "method", tool.get("method"));
            collected.add(pluginInfo);
        }
        return collected;
    }

    public static String transformToMermaid(String dlContent) {
        List<Map<String, Object>> nodes = parseDlNodes(dlContent);
        String mermaidResult = SimpleirToMermaid.transformToMermaid(nodes);
        LOGGER.debug("Mermaid transformation completed, node_count={}", nodes.size());
        return mermaidResult;
    }

    public String transformToDsl(String dlContent) {
        return transformToDsl(dlContent, null);
    }

    public String transformToDsl(String dlContent, Map<String, Object> resource) {
        if (resource != null && !resource.isEmpty()) {
            List<String> toolIds = new ArrayList<>();
            for (Map<String, Object> item : mapListValue(resource.getOrDefault("plugins", List.of()))) {
                toolIds.add(String.valueOf(requireKey(item, "tool_id")));
            }
            resource.put("plugins", collectPlugin(
                    toolIds,
                    mapOfMaps(resource.getOrDefault("plugin_dict", Map.of())),
                    stringMap(resource.getOrDefault("tool_id_map", Map.of()))));
        }

        List<Map<String, Object>> nodes = parseDlNodes(dlContent);
        Map<String, Object> nodesDict = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            nodesDict.put(String.valueOf(requireKey(node, "id")), node);
        }

        Workflow workflow = new Workflow();
        int x = 0;
        int y = 0;
        for (Map<String, Object> node : nodes) {
            String nodeType = String.valueOf(requireKey(node, "type"));
            Class<? extends BaseConverter> converterClass = DSL_CONVERTER_REGISTRY.get(nodeType);
            if (converterClass == null) {
                LOGGER.warning("Unsupported node type, node_type={}, node_id={}", nodeType, node.get("id"));
                continue;
            }

            BaseConverter converter = instantiateConverter(converterClass, node, nodesDict, resource, new Position(x, y));
            converter.convert();
            workflow.getNodes().add(converter.getNode());
            workflow.getEdges().addAll(converter.getEdges());
            x += 20;
            y += 20;
        }

        LOGGER.debug(
                "DSL transformation completed, node_count={}, edge_count={}",
                workflow.getNodes().size(),
                workflow.getEdges().size());

        return JsonUtils.safeJsonDumps(ConverterUtils.convertToDict(workflow));
    }

    private static Map<String, Class<? extends BaseConverter>> createRegistry() {
        Map<String, Class<? extends BaseConverter>> registry = new LinkedHashMap<>();
        registry.put(NodeType.Start.getDlType(), StartConverter.class);
        registry.put(NodeType.End.getDlType(), EndConverter.class);
        registry.put(NodeType.LLM.getDlType(), LLMConverter.class);
        registry.put(NodeType.IntentDetection.getDlType(), IntentDetectionConverter.class);
        registry.put(NodeType.Questioner.getDlType(), QuestionerConverter.class);
        registry.put(NodeType.Code.getDlType(), CodeConverter.class);
        registry.put(NodeType.Plugin.getDlType(), PluginConverter.class);
        registry.put(NodeType.Output.getDlType(), OutputConverter.class);
        registry.put(NodeType.Branch.getDlType(), BranchConverter.class);
        return registry;
    }

    private static BaseConverter instantiateConverter(Class<? extends BaseConverter> converterClass,
                                                      Map<String, Object> node,
                                                      Map<String, Object> nodesDict,
                                                      Map<String, Object> resource,
                                                      Position position) {
        try {
            if (NodeType.Plugin.getDlType().equals(String.valueOf(node.get("type")))) {
                return converterClass
                        .getConstructor(Map.class, Map.class, Map.class, Position.class)
                        .newInstance(node, nodesDict, resource, position);
            }
            return converterClass
                    .getConstructor(Map.class, Map.class, Position.class)
                    .newInstance(node, nodesDict, position);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                 | NoSuchMethodException ex) {
            throw new IllegalStateException("Failed to instantiate converter: " + converterClass.getSimpleName(), ex);
        }
    }

    private static List<Map<String, Object>> parseDlNodes(String dlContent) {
        String jsonText = AgentBuilderUtils.extractJsonFromText(dlContent);
        Object nodes = JsonUtils.safeJsonLoads(jsonText);
        if (!(nodes instanceof List<?> nodeList)) {
            String typeName = nodes == null ? "null" : nodes.getClass().getName();
            throw new IllegalArgumentException("DL content format error: expected JSON array (list), got " + typeName);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object node : nodeList) {
            result.add(mapValue(node));
        }
        return result;
    }

    private static void putIfTruthy(Map<String, Object> target, String key, Object value) {
        if (truthy(value)) {
            target.put(key, value);
        }
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

    private static String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected map value: " + value);
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return converted;
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

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected string map value: " + value);
        }
        Map<String, String> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return converted;
    }

    private static Map<String, Map<String, Object>> mapOfMaps(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected nested map value: " + value);
        }
        Map<String, Map<String, Object>> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), mapValue(entry.getValue()));
        }
        return converted;
    }

    private static Object requireKey(Map<String, Object> map, String key) {
        if (!map.containsKey(key)) {
            throw new IllegalArgumentException("Missing required key: " + key);
        }
        return map.get(key);
    }
}
