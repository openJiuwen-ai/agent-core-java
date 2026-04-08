// coding: utf-8
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility methods for prompt tuning operations.
 *
 * <p>Mirrors Python's {@code TuneUtils} in {@code openjiuwen.dev_tools.tune.utils}.
 */
public final class TuneUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern JSON_PATTERN = Pattern.compile("```json(.*?)```", Pattern.DOTALL);
    private static final Pattern LIST_PATTERN = Pattern.compile("```list(.*?)```", Pattern.DOTALL);

    private TuneUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Validates that a numeric parameter is within the specified bounds.
     *
     * @param param the parameter value to validate
     * @param paramName the parameter name for error messages
     * @param lower the lower bound (inclusive)
     * @param upper the upper bound (inclusive)
     * @throws IllegalArgumentException if parameter is out of bounds
     */
    public static void validateDigitalParameter(double param, String paramName, double lower, double upper) {
        if (param < lower || param > upper) {
            throw new IllegalArgumentException(
                    String.format("%s should be between %s and %s", paramName, lower, upper)
            );
        }
    }

    /**
     * Extracts input string from a Case object.
     *
     * @param case_ the case to extract from
     * @return formatted input string
     */
    public static String getInputStringFromCase(Case case_) {
        StringBuilder sb = new StringBuilder();
        List<BaseMessage> messages = case_.getInputs() != null 
                ? (List<BaseMessage>) case_.getInputs().get("messages")
                : null;
        
        if (messages != null) {
            for (BaseMessage message : messages) {
                String content;
                if (message instanceof AssistantMessage && ((AssistantMessage) message).getToolCalls() != null) {
                    try {
                        content = OBJECT_MAPPER.writeValueAsString(((AssistantMessage) message).getToolCalls());
                    } catch (Exception e) {
                        content = message.getContentAsString();
                    }
                } else {
                    content = message.getContentAsString();
                }
                sb.append(String.format("[%s]: %s%n", message.getRole(), content));
            }
        }
        
        Map<String, Object> variables = case_.getInputs() != null
                ? (Map<String, Object>) case_.getInputs().get("variables")
                : null;
        if (variables != null && !variables.isEmpty()) {
            sb.append(String.format("variables: %s%n", variables));
        }
        
        return sb.toString();
    }

    /**
     * Extracts output string from a BaseMessage.
     *
     * @param message the message to extract from
     * @return formatted output string
     */
    public static String getOutputStringFromMessage(BaseMessage message) {
        if (message instanceof AssistantMessage && ((AssistantMessage) message).getToolCalls() != null) {
            try {
                return OBJECT_MAPPER.writeValueAsString(
                        ((AssistantMessage) message).getToolCalls().stream()
                                .map(tc -> Map.of("name", tc.getName(), "arguments", tc.getArguments()))
                                .collect(Collectors.toList())
                );
            } catch (Exception e) {
                return message.getContentAsString();
            }
        }
        return message.getContentAsString();
    }

    /**
     * Extracts content string from a PromptTemplate.
     *
     * @param template the template to extract from
     * @return formatted content string
     */
    public static String getContentStringFromTemplate(PromptTemplate template) {
        if (template == null) {
            return "";
        }
        // Simplified implementation - actual depends on PromptTemplate structure
        return template.toString();
    }

    /**
     * Parses JSON from an LLM response string.
     *
     * @param jsonLikeString the response string containing JSON
     * @return parsed JSON map, or empty Optional if parsing fails
     */
    public static Optional<Map<String, Object>> parseJsonFromLlmResponse(String jsonLikeString) {
        if (jsonLikeString == null || jsonLikeString.isEmpty()) {
            return Optional.empty();
        }

        Matcher matcher = JSON_PATTERN.matcher(jsonLikeString);
        if (!matcher.find()) {
            Loggers.AGENT.warn("Failed to extract json string from response: {}", jsonLikeString);
            return Optional.empty();
        }

        String matchedJsonString = matcher.group(1).trim();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonData = OBJECT_MAPPER.readValue(matchedJsonString, Map.class);
            return Optional.of(jsonData);
        } catch (Exception e) {
            Loggers.AGENT.warn("Failed to decode json string: {}", jsonLikeString);
            return Optional.empty();
        }
    }

    /**
     * Parses a list from an LLM response string.
     *
     * @param listLikeString the response string containing a list
     * @return parsed list, or empty Optional if parsing fails
     */
    public static Optional<List<Object>> parseListFromLlmResponse(String listLikeString) {
        if (listLikeString == null || listLikeString.isEmpty()) {
            return Optional.empty();
        }

        Matcher matcher = LIST_PATTERN.matcher(listLikeString);
        if (!matcher.find()) {
            Loggers.AGENT.warn("Failed to extract list string from response: {}", listLikeString);
            return Optional.empty();
        }

        String matchedListString = matcher.group(1).trim();
        try {
            @SuppressWarnings("unchecked")
            List<Object> listData = OBJECT_MAPPER.readValue(matchedListString, List.class);
            return Optional.of(listData);
        } catch (Exception e) {
            Loggers.AGENT.warn("Failed to convert list string to Java list: {}", listLikeString);
            return Optional.empty();
        }
    }

    /**
     * Converts a list of cases to example format string.
     *
     * @param cases the cases to convert
     * @return formatted examples string
     */
    public static String convertCasesToExamples(List<?> cases) {
        if (cases == null || cases.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cases.size(); i++) {
            Object caseObj = cases.get(i);
            Case case_;
            if (caseObj instanceof EvaluatedCase) {
                case_ = ((EvaluatedCase) caseObj).getCase();
            } else if (caseObj instanceof Case) {
                case_ = (Case) caseObj;
            } else {
                continue;
            }

            sb.append(String.format("example %d:%n", i + 1));
            sb.append(String.format("[question]: %s%n", convertDictToString(case_.getInputs())));
            sb.append(String.format("[expected answer]: %s%n", convertDictToString(
                    case_.getLabel() instanceof Map ? (Map<String, Object>) case_.getLabel() : Map.of()
            )));
        }
        
        return sb.toString();
    }

    /**
     * Converts a map to a formatted string.
     *
     * @param data the map to convert
     * @return formatted string
     */
    private static String convertDictToString(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "";
        }
        return data.entrySet().stream()
                .map(e -> String.format("%s:%s", e.getKey(), e.getValue()))
                .collect(Collectors.joining(" | "));
    }
}