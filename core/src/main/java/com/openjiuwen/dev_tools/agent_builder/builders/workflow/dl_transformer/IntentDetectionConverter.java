/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IntentDetection node converter.
 *
 * <p>Mirrors Python's {@code IntentDetectionConverter} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/intent_detection_converter.py}.
 * </p>
 */
public class IntentDetectionConverter extends BaseConverter {

    private List<Map<String, String>> intents = new ArrayList<>();

    public IntentDetectionConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict) {
        super(nodeData, nodesDict);
    }

    public IntentDetectionConverter(
            Map<String, Object> nodeData,
            Map<String, Object> nodesDict,
            Map<String, Object> resource) {
        super(nodeData, nodesDict, resource);
    }

    public IntentDetectionConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict, Position position) {
        super(nodeData, nodesDict, position);
    }

    public IntentDetectionConverter(
            Map<String, Object> nodeData,
            Map<String, Object> nodesDict,
            Map<String, Object> resource,
            Position position) {
        super(nodeData, nodesDict, resource, position);
    }

    static List<Map<String, String>> convertIntents(List<Map<String, Object>> conditions) {
        List<Map<String, String>> converted = new ArrayList<>();
        for (Map<String, Object> condition : conditions) {
            String expression = requiredString(condition, "expression");
            if ("default".equals(expression)) {
                continue;
            }
            String[] parts = expression.split(" contain ");
            if (parts.length > 1) {
                Map<String, String> intent = new LinkedHashMap<>();
                intent.put("name", parts[1]);
                Object explicitId = condition.get("intent_id");
                intent.put("id", explicitId != null ? String.valueOf(explicitId) : generateIntentId());
                converted.add(intent);
            }
        }
        return converted;
    }

    static List<Map<String, String>> convertBranches(List<Map<String, Object>> conditions) {
        List<Map<String, String>> branches = new ArrayList<>();
        for (Map<String, Object> condition : conditions) {
            branches.add(Map.of("branchId", requiredString(condition, "branch")));
        }
        return branches;
    }

    @Override
    protected void convertSpecificConfig() {
        Map<String, Object> parameters = mapValue(required(nodeData, "parameters"));
        Map<String, Object> configs = mapValue(required(parameters, "configs"));
        String promptContent = nullableString(configs.getOrDefault("prompt", ""));

        Map<String, Object> llmParam = new LinkedHashMap<>();
        llmParam.put("systemPrompt", Map.of("type", "template", "content", ""));
        llmParam.put("prompt", Map.of("type", "template", "content", promptContent));
        llmParam.put("model", ConverterUtils.LLM_MODEL_CONFIG);

        Map<String, InputVariable> inputVariables = convertInputVariables(mapListValue(required(parameters, "inputs")));
        Map<String, InputVariable> renamedInputVariables = new LinkedHashMap<>();
        for (InputVariable value : inputVariables.values()) {
            renamedInputVariables.put("query", value);
        }

        intents = convertIntents(mapListValue(required(parameters, "conditions")));
        node.getData().setInputs(new InputsField(
                renamedInputVariables,
                llmParam,
                null,
                intents,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        Map<String, Object> outputSpec = new LinkedHashMap<>();
        outputSpec.put("name", "classification_id");
        outputSpec.put("type", "integer");
        outputSpec.put("description", null);
        OutputsField outputs = convertOutputsField(List.of(outputSpec));
        if (outputs.getRequired() == null) {
            outputs.setRequired(new ArrayList<>());
        }
        outputs.getRequired().add("classification_id");
        node.getData().setOutputs(outputs);
    }

    @Override
    public void convertEdges() {
        Map<String, String> intentIdMap = new LinkedHashMap<>();
        List<Map<String, Object>> conditions = mapListValue(required(mapValue(required(nodeData, "parameters")), "conditions"));
        for (Map<String, String> intent : intents) {
            String intentName = intent.get("name");
            String intentId = intent.get("id");
            for (Map<String, Object> condition : conditions) {
                String expression = requiredString(condition, "expression");
                if ("default".equals(expression)) {
                    continue;
                }
                String[] parts = expression.split(" contain ");
                if (parts.length > 1 && parts[1].equals(intentName)) {
                    intentIdMap.put(requiredString(condition, "branch"), intentId);
                    break;
                }
            }
        }

        for (Map<String, Object> condition : conditions) {
            if ("default".equals(requiredString(condition, "expression"))) {
                edges.add(new Edge(requiredString(nodeData, "id"), nullableString(required(condition, "next")), "0"));
            } else {
                String sourcePortId = intentIdMap.get(requiredString(condition, "branch"));
                if (sourcePortId != null) {
                    edges.add(new Edge(requiredString(nodeData, "id"), nullableString(required(condition, "next")), sourcePortId));
                }
            }
        }
    }

    private static String generateIntentId() {
        return "intent_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static Object required(Map<String, Object> source, String key) {
        if (!source.containsKey(key)) {
            throw new IllegalArgumentException("Missing required intent detection converter key: " + key);
        }
        return source.get(key);
    }

    private static String requiredString(Map<String, Object> source, String key) {
        Object value = required(source, key);
        return value == null ? null : String.valueOf(value);
    }

    private static String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
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
