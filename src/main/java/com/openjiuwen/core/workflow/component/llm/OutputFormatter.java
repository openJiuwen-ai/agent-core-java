/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.utils.SchemaUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Formats LLM response content according to response type and output configuration.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.llm_comp.OutputFormatter}.
 */
public final class OutputFormatter {

    private OutputFormatter() {
    }

    /**
     * Format the LLM response into a structured output.
     *
     * @param responseContent the raw response content
     * @param responseFormat  response format configuration (with "type" key)
     * @param outputsConfig   output parameter configuration
     * @return formatted output map
     */
    public static Map<String, Object> formatResponse(String responseContent,
                                                      Map<String, Object> responseFormat,
                                                      Map<String, Object> outputsConfig) {
        String responseType = (String) responseFormat.get("type");
        ValidationUtils.validateOutputsConfig(outputsConfig);

        return switch (responseType) {
            case "text", "markdown" -> formatTextResponse(responseContent, outputsConfig);
            case "json" -> formatJsonResponse(responseContent, outputsConfig);
            default -> {
                ValidationUtils.raiseInvalidParamsError("no supported response type: '" + responseType + "'");
                yield Map.of(); // unreachable
            }
        };
    }

    private static Map<String, Object> formatTextResponse(String responseContent, Map<String, Object> outputsConfig) {
        if (outputsConfig.size() != 1) {
            ValidationUtils.raiseInvalidParamsError("text/markdown response type, outputs_config must contain only one field");
        }
        String fieldName = outputsConfig.keySet().iterator().next();
        return Map.of(fieldName, responseContent);
    }

    private static Map<String, Object> formatJsonResponse(String responseContent, Map<String, Object> outputsConfig) {
        if (outputsConfig.isEmpty()) {
            ValidationUtils.raiseInvalidParamsError("json response format, output config should contain at least one field");
        }

        Map<String, Object> parsedJson = JsonParser.parseJsonContent(responseContent);

        Object configType = outputsConfig.get("type");
        if (configType instanceof String && "object".equals(configType)) {
            SchemaUtils.validateWithSchema(parsedJson, outputsConfig);
            return SchemaUtils.formatWithSchema(parsedJson, outputsConfig, false);
        } else {
            Map<String, Object> jsonSchema = SchemaGenerator.generateJsonSchema(outputsConfig);
            try {
                ValidationUtils.validateJsonSchema(parsedJson, jsonSchema);
            } catch (BaseError e) {
                throw e;
            } catch (Exception e) {
                ValidationUtils.raiseInvalidParamsError("json schema validation failed: " + responseContent);
            }
            return extractConfiguredFields(parsedJson, outputsConfig);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractConfiguredFields(Map<String, Object> parsedJson,
                                                                Map<String, Object> outputsConfig) {
        Map<String, Object> output = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : outputsConfig.entrySet()) {
            String fieldName = entry.getKey();
            Map<String, Object> fieldConfig = (Map<String, Object>) entry.getValue();

            if (!parsedJson.containsKey(fieldName)) {
                Object required = fieldConfig.getOrDefault("required", true);
                if (Boolean.TRUE.equals(required)) {
                    ValidationUtils.raiseInvalidParamsError("missing required field: " + fieldName);
                }
            } else {
                Object value = parsedJson.get(fieldName);

                // Filter out unknown keys from object values
                if (value instanceof Map<?, ?> mapVal && fieldConfig.containsKey("properties")) {
                    Map<String, Object> properties = (Map<String, Object>) fieldConfig.get("properties");
                    Map<String, Object> filtered = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : mapVal.entrySet()) {
                        if (e.getKey() instanceof String key && properties.containsKey(key)) {
                            filtered.put(key, e.getValue());
                        }
                    }
                    output.put(fieldName, filtered);
                } else {
                    output.put(fieldName, value);
                }
            }
        }

        return output;
    }
}
