/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM node converter.
 *
 * <p>Mirrors Python's {@code LLMConverter} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/llm_converter.py}.</p>
 */
public class LLMConverter extends BaseConverter {

    public LLMConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict) {
        super(nodeData, nodesDict);
    }

    public LLMConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict, Map<String, Object> resource) {
        super(nodeData, nodesDict, resource);
    }

    public LLMConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict, Position position) {
        super(nodeData, nodesDict, position);
    }

    public LLMConverter(
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

        List<Map<String, Object>> outputsList = parameters.containsKey("outputs")
                ? mapListValue(parameters.get("outputs"))
                : List.of();
        int outputsCount = outputsList.size();

        String outputFormat = nullableString(configs.getOrDefault("output_format", "text"));
        if (outputsCount > 1 && ("text".equals(outputFormat) || "markdown".equals(outputFormat))) {
            outputFormat = "json";
        }
        if (outputsCount == 1 && !List.of("text", "markdown", "json").contains(outputFormat)) {
            outputFormat = "text";
        }

        node.getData().setOutputFormat(outputFormat);
        Map<String, Object> llmParam = new LinkedHashMap<>(ConverterUtils.convertLlmParam(
                nullableString(required(configs, "system_prompt")),
                nullableString(required(configs, "user_prompt"))
        ));
        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", outputFormat);
        llmParam.put("response_format", responseFormat);

        node.getData().setInputs(new InputsField(
                convertInputVariables(mapListValue(required(parameters, "inputs"))),
                llmParam,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
        node.getData().setOutputs(convertOutputsField(mapListValue(required(parameters, "outputs"))));
    }

    private static Object required(Map<String, Object> source, String key) {
        if (!source.containsKey(key)) {
            throw new IllegalArgumentException("Missing required LLM converter key: " + key);
        }
        return source.get(key);
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
