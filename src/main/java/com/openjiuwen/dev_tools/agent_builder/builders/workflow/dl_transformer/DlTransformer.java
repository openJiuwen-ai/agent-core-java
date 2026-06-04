/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * DL transformer — converts design language to workflow DSL.
 * <p>
 * Mirrors Python's {@code DlTransformer} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.dl_transformer}.
 */
public class DlTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(DlTransformer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, Class<?>> DSL_CONVERTER_REGISTRY = new LinkedHashMap<>();

    static {
        DSL_CONVERTER_REGISTRY.put("Start", StartConverter.class);
        DSL_CONVERTER_REGISTRY.put("End", EndConverter.class);
        DSL_CONVERTER_REGISTRY.put("LLM", LlmConverter.class);
        DSL_CONVERTER_REGISTRY.put("IntentDetection", IntentDetectionConverter.class);
        DSL_CONVERTER_REGISTRY.put("Questioner", QuestionerConverter.class);
        DSL_CONVERTER_REGISTRY.put("Code", CodeConverter.class);
        DSL_CONVERTER_REGISTRY.put("Plugin", PluginConverter.class);
        DSL_CONVERTER_REGISTRY.put("Output", OutputConverter.class);
        DSL_CONVERTER_REGISTRY.put("Branch", BranchConverter.class);
    }

    public static Map<String, Class<?>> getDslConverterRegistry() {
        return new LinkedHashMap<>(DSL_CONVERTER_REGISTRY);
    }

    public static String transformToMermaid(String dlContent) {
        List<Map<String, Object>> nodes = parseNodeArray(dlContent);
        return SimpleirToMermaid.transformToMermaid(nodes);
    }

    public String transformToDsl(String dlContent) {
        return transformToDsl(dlContent, new LinkedHashMap<>());
    }

    public String transformToDsl(String dlContent, Map<String, Object> resource) {
        List<Map<String, Object>> nodes = parseNodeArray(dlContent);
        List<Map<String, Object>> convertedNodes = new ArrayList<>();
        List<Map<String, Object>> convertedEdges = new ArrayList<>();

        for (Map<String, Object> nodeData : nodes) {
            ConversionResult result = convertNode(nodeData, nodes, resource);
            convertedNodes.add(convertNodeToMap(result.node()));
            for (Edge edge : result.edges()) {
                convertedEdges.add(convertEdgeToMap(edge));
            }
        }

        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("nodes", convertedNodes);
        workflow.put("edges", convertedEdges);
        try {
            return MAPPER.writeValueAsString(workflow);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize transformed DSL", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> collectPlugin(List<String> toolIdList,
                                                          Map<String, Object> pluginDict,
                                                          Map<String, String> toolIdMap) {
        if (toolIdList == null || toolIdList.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> plugins = new ArrayList<>();
        for (String toolId : toolIdList) {
            String pluginId = toolIdMap == null ? null : toolIdMap.get(toolId);
            if (pluginId == null || pluginDict == null || !(pluginDict.get(pluginId) instanceof Map<?, ?> rawPlugin)) {
                continue;
            }
            Map<String, Object> plugin = (Map<String, Object>) rawPlugin;
            Object toolsObj = plugin.get("tools");
            if (!(toolsObj instanceof Map<?, ?> tools) || !(tools.get(toolId) instanceof Map<?, ?> rawTool)) {
                continue;
            }
            Map<String, Object> tool = (Map<String, Object>) rawTool;
            Map<String, Object> pluginInfo = new LinkedHashMap<>();
            pluginInfo.put("plugin_id", pluginId);
            pluginInfo.put("plugin_name", plugin.getOrDefault("plugin_name", ""));
            pluginInfo.put("plugin_version", plugin.getOrDefault("plugin_version", ""));
            pluginInfo.put("tool_id", toolId);
            pluginInfo.put("tool_name", tool.getOrDefault("tool_name", ""));
            pluginInfo.put("inputs", tool.getOrDefault("ori_inputs", List.of()));
            pluginInfo.put("outputs", tool.getOrDefault("ori_outputs", List.of()));
            copyOptional(tool, pluginInfo, "language");
            copyOptional(tool, pluginInfo, "code");
            copyOptional(tool, pluginInfo, "path");
            copyOptional(tool, pluginInfo, "method");
            plugins.add(pluginInfo);
        }
        return plugins;
    }

    private static void copyOptional(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null && !value.toString().isEmpty()) {
            target.put(key, value);
        }
    }

    /** Transform a DL design to workflow DSL. */
    public Map<String, Object> transform(Map<String, Object> design) {
        LOG.info("[DlTransformer] Transforming DL design");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workflow", design);
        result.put("transformed", true);
        return result;
    }

    private static List<Map<String, Object>> parseNodeArray(String dlContent) {
        try {
            Object parsed = MAPPER.readValue(dlContent, Object.class);
            if (!(parsed instanceof List<?>)) {
                throw new IllegalArgumentException("DL content must be a JSON array");
            }
            return MAPPER.convertValue(parsed, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid DL JSON", e);
        }
    }

    private ConversionResult convertNode(Map<String, Object> nodeData,
                                         List<Map<String, Object>> nodes,
                                         Map<String, Object> resource) {
        Map<String, Object> nodesDict = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            nodesDict.put(String.valueOf(node.get("id")), node);
        }
        String type = String.valueOf(nodeData.get("type"));
        return switch (type) {
            case "Start" -> {
                StartConverter converter = new StartConverter(nodeData, nodesDict);
                converter.convert();
                yield new ConversionResult(converter.getNode(), converter.getEdges());
            }
            case "End" -> {
                EndConverter converter = new EndConverter(nodeData, nodesDict);
                converter.convert();
                yield new ConversionResult(converter.getNode(), converter.getEdges());
            }
            case "LLM" -> {
                LlmConverter converter = new LlmConverter(nodeData, nodesDict);
                converter.convert();
                yield new ConversionResult(converter.getNode(), converter.getEdges());
            }
            case "IntentDetection" -> {
                IntentDetectionConverter converter = new IntentDetectionConverter(nodeData, nodesDict);
                converter.convert();
                yield new ConversionResult(converter.getNode(), converter.getEdges());
            }
            case "Questioner" -> {
                QuestionerConverter converter = new QuestionerConverter(nodeData, nodesDict);
                converter.convert();
                yield new ConversionResult(converter.getNode(), converter.getEdges());
            }
            case "Code" -> {
                CodeConverter converter = new CodeConverter(nodeData, nodesDict);
                converter.convert();
                yield new ConversionResult(converter.getNode(), converter.getEdges());
            }
            case "Plugin" -> {
                PluginConverter converter = new PluginConverter(nodeData, nodesDict);
                converter.convert();
                yield new ConversionResult(converter.getNode(), converter.getEdges());
            }
            case "Output" -> {
                OutputConverter converter = new OutputConverter(nodeData, nodesDict);
                converter.convert();
                yield new ConversionResult(converter.getNode(), converter.getEdges());
            }
            case "Branch" -> {
                BranchConverter converter = new BranchConverter(nodeData, nodesDict);
                converter.convert();
                yield new ConversionResult(converter.getNode(), converter.getEdges());
            }
            default -> throw new IllegalArgumentException("Unknown node type: " + type);
        };
    }

    private Map<String, Object> convertNodeToMap(Node node) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", node.getId());
        result.put("type", node.getType());
        result.put("meta", ConverterUtils.convertToDict(node.getMeta()));
        result.put("data", ConverterUtils.convertToDict(node.getData()));
        return result;
    }

    private Map<String, Object> convertEdgeToMap(Edge edge) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source_node_id", edge.getSourceNodeId());
        result.put("target_node_id", edge.getTargetNodeId());
        if (edge.getSourcePortId() != null) {
            result.put("source_port_id", edge.getSourcePortId());
        }
        return result;
    }

    private record ConversionResult(Node node, List<? extends Edge> edges) {
    }
}
