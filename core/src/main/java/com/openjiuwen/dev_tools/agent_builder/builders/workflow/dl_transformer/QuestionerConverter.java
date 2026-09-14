/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Questioner node converter.
 *
 * <p>Mirrors Python's {@code QuestionerConverter} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/questioner_converter.py}.</p>
 */
public class QuestionerConverter extends BaseConverter {

    public QuestionerConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict) {
        super(nodeData, nodesDict);
    }

    public QuestionerConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict, Map<String, Object> resource) {
        super(nodeData, nodesDict, resource);
    }

    public QuestionerConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict, Position position) {
        super(nodeData, nodesDict, position);
    }

    public QuestionerConverter(
            Map<String, Object> nodeData,
            Map<String, Object> nodesDict,
            Map<String, Object> resource,
            Position position) {
        super(nodeData, nodesDict, resource, position);
    }

    @Override
    protected void convertSpecificConfig() {
        Map<String, Object> parameters = mapValue(required(nodeData, "parameters"));
        Map<String, Object> configs = mapValue(required(parameters, "configs"));
        Map<String, Object> llmParam = ConverterUtils.convertLlmParam(nullableString(required(configs, "prompt")), "");

        OutputsField outputs = convertOutputsField(mapListValue(required(parameters, "outputs")));
        if (!hasProperty(outputs, "user_response")) {
            outputs.putProperty("user_response", new OutputsField("string", "用户响应输出变量"));
        }
        if (!hasProperty(outputs, "output")) {
            String firstOutputKey = firstOutputKey(outputs);
            if (firstOutputKey != null) {
                outputs.putProperty("output", new OutputsField("string", "输出变量"));
            }
        }

        Map<String, InputVariable> inputParameters = convertInputVariables(mapListValue(required(parameters, "inputs")));
        if (inputParameters.isEmpty()) {
            inputParameters.put(
                    "input",
                    new InputVariable(SourceType.ref.getValue(), "node_start.query", Map.of("index", 0))
            );
        }

        node.getData().setInputs(new InputsField(
                inputParameters,
                llmParam,
                stringMapValue(required(llmParam, "systemPrompt")),
                null,
                null,
                null,
                null,
                null,
                false,
                3
        ));
        node.getData().setOutputs(outputs);
        outputs.setRequired(requiredKeys(outputs));
    }

    private static boolean hasProperty(OutputsField outputs, String name) {
        return outputs.getProperties() != null && outputs.getProperties().containsKey(name);
    }

    private static String firstOutputKey(OutputsField outputs) {
        if (outputs.getProperties() == null || outputs.getProperties().isEmpty()) {
            return null;
        }
        return outputs.getProperties().keySet().iterator().next();
    }

    private static List<String> requiredKeys(OutputsField outputs) {
        List<String> keys = new ArrayList<>();
        if (outputs.getProperties() == null) {
            return keys;
        }
        for (String key : outputs.getProperties().keySet()) {
            if (!"output".equals(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static Object required(Map<String, Object> source, String key) {
        if (!source.containsKey(key)) {
            throw new IllegalArgumentException("Missing required questioner converter key: " + key);
        }
        return source.get(key);
    }

    private static String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, String> stringMapValue(Object value) {
        Map<String, Object> rawMap = mapValue(value);
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
            result.put(entry.getKey(), nullableString(entry.getValue()));
        }
        return result;
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
