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
 * IntentDetectionConverter for DL transformer.
 * <p>
 * Mirrors Python's {@code IntentDetectionConverter} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.converters}.
 */
public class IntentDetectionConverter extends BaseConverter {

    public IntentDetectionConverter(Map<String, Object> nodeData, Map<String, Object> context) {
        super(nodeData, context);
    }

    public IntentDetectionConverter(Map<String, Object> nodeData, Map<String, Object> context, Position position) {
        super(nodeData, context, position);
    }

    @Override
    protected void convertSpecificConfig() {
        List<Map<String, Object>> conditions = extractConditions();
        List<Map<String, String>> intents = new ArrayList<>();
        List<Map<String, Object>> branches = new ArrayList<>();

        for (Map<String, Object> condition : conditions) {
            Object branch = condition.get("branch");
            Map<String, Object> branchInfo = new LinkedHashMap<>();
            if (branch != null) {
                branchInfo.put("branchId", branch.toString());
            }
            branches.add(branchInfo);

            String expression = String.valueOf(condition.getOrDefault("expression", ""));
            if ("default".equals(expression)) {
                continue;
            }
            String intentName = extractIntentName(expression);
            if (intentName == null) {
                continue;
            }

            Map<String, String> intent = new LinkedHashMap<>();
            intent.put("name", intentName);
            intent.put("id", String.valueOf(condition.getOrDefault(
                    "intent_id",
                    "intent_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
            )));
            intents.add(intent);
        }

        Map<String, Object> llmParam = new LinkedHashMap<>();
        llmParam.put("systemPrompt", Map.of("type", "template", "content", ""));
        llmParam.put("prompt", Map.of(
                "type",
                "template",
                "content",
                extractPromptContent()
        ));
        llmParam.put("model", ConverterUtils.LLM_MODEL_CONFIG);

        Map<String, InputVariable> inputVariables = convertInputVariables(extractInputs());
        Map<String, InputVariable> renamedInputs = new LinkedHashMap<>();
        for (InputVariable value : inputVariables.values()) {
            renamedInputs.put("query", value);
            break;
        }

        node.getData().setInputs(new InputsField(
                renamedInputs,
                llmParam,
                null,
                castIntentList(intents),
                null,
                null,
                null,
                null,
                null,
                null
        ));
        OutputsField outputs = convertOutputsField(List.of(
                Map.of("name", "classification_id", "type", "integer", "description", "")
        ));
        node.getData().setOutputs(outputs);
        node.getData().setBranches(branches);
    }

    @Override
    public void convertEdges() {
        List<Map<String, Object>> conditions = extractConditions();
        Map<String, String> branchIntentIdMap = new LinkedHashMap<>();
        InputsField inputs = node.getData().getInputs();
        if (inputs != null && inputs.getIntents() != null) {
            for (Map<String, String> intent : inputs.getIntents()) {
                String intentName = intent.get("name");
                String intentId = intent.get("id");
                if (intentName == null || intentId == null) {
                    continue;
                }
                for (Map<String, Object> condition : conditions) {
                    String expression = String.valueOf(condition.getOrDefault("expression", ""));
                    if (!"default".equals(expression) && intentName.equals(extractIntentName(expression))) {
                        Object branch = condition.get("branch");
                        if (branch != null) {
                            branchIntentIdMap.put(branch.toString(), intentId);
                        }
                    }
                }
            }
        }

        for (Map<String, Object> condition : conditions) {
            Object next = condition.get("next");
            if (next == null || next.toString().isEmpty()) {
                continue;
            }
            String expression = String.valueOf(condition.getOrDefault("expression", ""));
            if ("default".equals(expression)) {
                edges.add(new BaseConverter.Edge(String.valueOf(nodeData.get("id")), next.toString(), "0", null));
                continue;
            }
            Object branch = condition.get("branch");
            String sourcePortId = branch == null ? null : branchIntentIdMap.get(branch.toString());
            edges.add(new BaseConverter.Edge(
                    String.valueOf(nodeData.get("id")),
                    next.toString(),
                    sourcePortId,
                    null
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> castIntentList(List<Map<String, String>> intents) {
        return intents;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractConditions() {
        Object parameters = nodeData.get("parameters");
        if (!(parameters instanceof Map<?, ?> parameterMap)) {
            return List.of();
        }
        Object conditions = parameterMap.get("conditions");
        if (!(conditions instanceof List<?> rawConditions)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object rawCondition : rawConditions) {
            if (rawCondition instanceof Map<?, ?> condition) {
                result.add((Map<String, Object>) condition);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractInputs() {
        Object parameters = nodeData.get("parameters");
        if (!(parameters instanceof Map<?, ?> parameterMap)) {
            return List.of();
        }
        Object inputs = parameterMap.get("inputs");
        if (!(inputs instanceof List<?> rawInputs)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object rawInput : rawInputs) {
            if (rawInput instanceof Map<?, ?> input) {
                result.add((Map<String, Object>) input);
            }
        }
        return result;
    }

    private String extractPromptContent() {
        Object parameters = nodeData.get("parameters");
        if (!(parameters instanceof Map<?, ?> parameterMap)) {
            return "";
        }
        Object configs = parameterMap.get("configs");
        if (!(configs instanceof Map<?, ?> configMap)) {
            return "";
        }
        Object prompt = configMap.get("prompt");
        return prompt == null ? "" : prompt.toString();
    }

    private String extractIntentName(String expression) {
        String[] parts = expression.split(" contain ", 2);
        if (parts.length < 2) {
            return null;
        }
        return parts[1].trim();
    }
}
