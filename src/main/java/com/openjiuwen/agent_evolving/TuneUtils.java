/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.agent_evolving;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collection of static utility methods for self-evolving operations.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.utils.TuneUtils}.
 */
public final class TuneUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TuneUtils() {
        // Utility class
    }

    /**
     * Validate numeric parameter is within bounds.
     *
     * @param param     Value to validate
     * @param paramName Parameter name for error message
     * @param lower     Minimum allowed value
     * @param upper     Maximum allowed value
     * @throws ValidationError if param is outside [lower, upper]
     */
    public static void validateDigitalParameter(double param, String paramName, double lower, double upper) {
        if (param < lower || param > upper) {
            throw new ValidationError(
                    StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR,
                    Map.of("error_msg", paramName + " should be between " + lower + " and " + upper)
            );
        }
    }

    /**
     * Extract readable input string from Case.
     *
     * @param caseData Case to extract input from
     * @return Formatted input string
     */
    public static String getInputStringFromCase(Case caseData) {
        return convertDictToString(caseData.getInputs());
    }

    /**
     * Convert BaseMessage to string for logging/comparison.
     *
     * @param message Message to convert
     * @return Serialized message content; tool_calls included if present
     */
    public static String getOutputStringFromMessage(BaseMessage message) {
        if (message == null) {
            return "";
        }
        if (message instanceof AssistantMessage assistantMessage
                && assistantMessage.getToolCalls() != null
                && !assistantMessage.getToolCalls().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (var toolCall : assistantMessage.getToolCalls()) {
                try {
                    sb.append(OBJECT_MAPPER.writeValueAsString(
                            Map.of(
                                    "name", toolCall.getName(),
                                    "arguments", toolCall.getArguments()
                            )
                    ));
                } catch (Exception e) {
                    sb.append(message.getContentAsString());
                }
            }
            return sb.toString();
        }
        return message.getContentAsString();
    }

    /**
     * Convert PromptTemplate to multi-line text.
     *
     * @param template PromptTemplate to convert
     * @return Concatenated message contents separated by newlines
     */
    public static String getContentStringFromTemplate(PromptTemplate template) {
        if (template == null) {
            return "";
        }
        return template.toMessages().stream()
                .map(BaseMessage::getContentAsString)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    /**
     * Format Case/EvaluatedCase list as few-shot example text.
     *
     * @param cases List of cases to format
     * @return Formatted examples with question and expected answer
     */
    public static String convertCasesToExamples(List<?> cases) {
        if (cases == null || cases.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cases.size(); i++) {
            Object caseObj = cases.get(i);
            Case c;
            if (caseObj instanceof EvaluatedCase evaluatedCase) {
                c = evaluatedCase.getCase();
            } else if (caseObj instanceof Case caseData) {
                c = caseData;
            } else {
                continue;
            }
            sb.append("example ").append(i + 1).append(":\n");
            sb.append("[question]: ").append(convertDictToString(c.getInputs())).append("\n");
            sb.append("[expected answer]: ").append(convertDictToString(c.getLabel()));
            if (i + 1 < cases.size()) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Extract and parse JSON from ```json ... ``` block.
     *
     * @param jsonLikeString String containing JSON block
     * @return Parsed JSON value, or null on failure
     */
    public static Object parseJsonFromLlmResponse(String jsonLikeString) {
        Pattern pattern = Pattern.compile("```json(.*?)```", Pattern.DOTALL);
        return parseLlmResponseRaw(jsonLikeString, pattern);
    }

    /**
     * Extract and parse JSON object from ```json ... ``` block.
     *
     * @param jsonLikeString String containing JSON block
     * @return Parsed JSON map, or null on failure / non-object payload
     */
    public static Map<String, Object> parseJsonObjectFromLlmResponse(String jsonLikeString) {
        Object data = parseJsonFromLlmResponse(jsonLikeString);
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return null;
    }

    /**
     * Extract and parse list from ```list ... ``` block.
     *
     * @param listLikeString String containing list block
     * @return Parsed list, or null on failure
     */
    public static List<Object> parseListFromLlmResponse(String listLikeString) {
        Pattern pattern = Pattern.compile("```list(.*?)```", Pattern.DOTALL);
        Object data = parseLlmResponseRaw(listLikeString, pattern);
        if (!(data instanceof List)) {
            Loggers.AGENT.warn("Parsed data is not a list-type");
            return null;
        }
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) data;
        return result;
    }

    /**
     * Convert dict to single-line string.
     *
     * @param data Map to convert
     * @return String in format "k1:v1 | k2:v2"
     */
    public static String convertDictToString(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "";
        }
        StringJoiner sj = new StringJoiner(" | ");
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            sj.add(entry.getKey() + ":" + entry.getValue());
        }
        return sj.toString();
    }

    private static Object parseLlmResponseRaw(String string, Pattern pattern) {
        if (string == null || string.isEmpty()) {
            return null;
        }
        String matchedString = string;
        if (pattern != null) {
            Matcher matcher = pattern.matcher(string);
            if (!matcher.find()) {
                Loggers.AGENT.warn("Failed to extract string like `{}` from response", pattern);
                return null;
            }
            matchedString = matcher.group(1).trim();
        }

        try {
            return OBJECT_MAPPER.readValue(matchedString, new TypeReference<Object>() {});
        } catch (Exception e) {
            Loggers.AGENT.warn("Failed to convert string to object: {}", e.getMessage());
            return null;
        }
    }
}
