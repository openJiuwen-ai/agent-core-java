/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Code node converter.
 *
 * <p>Mirrors Python's {@code CodeConverter} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/code_converter.py}.</p>
 */
public class CodeConverter extends BaseConverter {

    public static final Map<String, Object> CODE_EXCEPTION_CONFIG = createCodeExceptionConfig();

    public CodeConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict) {
        super(nodeData, nodesDict);
    }

    public CodeConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict, Map<String, Object> resource) {
        super(nodeData, nodesDict, resource);
    }

    public CodeConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict, Position position) {
        super(nodeData, nodesDict, position);
    }

    public CodeConverter(
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

        node.getData().setInputs(new InputsField(
                convertInputVariables(mapListValue(required(parameters, "inputs"))),
                null,
                null,
                null,
                "python",
                nullableString(required(configs, "code")),
                null,
                null,
                null,
                null
        ));

        OutputsField outputs = convertOutputsField(mapListValue(required(parameters, "outputs")));
        node.getData().setOutputs(outputs);
        if (outputs.getProperties() != null) {
            outputs.setRequired(new ArrayList<>(outputs.getProperties().keySet()));
        }
        node.getData().setExceptionConfig(CODE_EXCEPTION_CONFIG);
    }

    @Override
    public void convertEdges() {
        if (nodeData.containsKey("next")) {
            edges.add(new Edge(requiredString(nodeData, "id"), nullableString(nodeData.get("next")), "0"));
        }
    }

    private static Map<String, Object> createCodeExceptionConfig() {
        Map<String, Object> executeStep = new LinkedHashMap<>();
        executeStep.put("defaultStep", "0");
        executeStep.put("errorStep", "1");

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("retryTimes", 3);
        config.put("timeoutSeconds", 30);
        config.put("processType", "break");
        config.put("executeStep", executeStep);
        return config;
    }

    private static Object required(Map<String, Object> source, String key) {
        if (!source.containsKey(key)) {
            throw new IllegalArgumentException("Missing required code converter key: " + key);
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
