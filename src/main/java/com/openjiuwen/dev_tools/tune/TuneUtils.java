/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility methods for prompt tuning.
 *
 * <p>Mirrors Python's {@code TuneUtils} in
 * {@code openjiuwen/dev_tools/tune/utils.py}.</p>
 */
public final class TuneUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Pattern JSON_BLOCK_RE = Pattern.compile("```json(.*?)```", Pattern.DOTALL);
    private static final Pattern LIST_BLOCK_RE = Pattern.compile("```list(.*?)```", Pattern.DOTALL);

    private TuneUtils() {
    }

    public static void validateDigitalParameter(double param, String paramName, double lower, double upper) {
        if (param < lower || param > upper) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR,
                    "error_msg",
                    paramName + " should be between " + lower + " and " + upper
            );
        }
    }

    public static void validate_digital_parameter(double param, String paramName, double lower, double upper) {
        validateDigitalParameter(param, paramName, lower, upper);
    }

    public static String getInputStringFromCase(Case caseValue) {
        Objects.requireNonNull(caseValue, "caseValue");
        List<BaseMessage> messages = readMessages(caseValue);
        List<String> messageContents = new ArrayList<>();
        for (BaseMessage message : messages) {
            String content;
            if (message instanceof AssistantMessage assistant
                    && assistant.getToolCalls() != null
                    && !assistant.getToolCalls().isEmpty()) {
                content = assistant.getToolCalls().stream()
                        .map(TuneUtils::serializeToolCallFull)
                        .collect(Collectors.joining());
            } else {
                content = message.getContentAsString();
            }
            messageContents.add("[" + message.getRole() + "]: " + content);
        }
        String inputString = String.join("\n", messageContents);
        Map<String, Object> variables = readVariables(caseValue);
        if (variables != null && !variables.isEmpty()) {
            inputString += "\nvariables: " + variables + "\n";
        }
        return inputString;
    }

    public static String get_input_string_from_case(Case caseValue) {
        return getInputStringFromCase(caseValue);
    }

    public static String getOutputStringFromMessage(BaseMessage message) {
        BaseMessage resolvedMessage = Objects.requireNonNull(message, "message");
        if (resolvedMessage instanceof AssistantMessage assistant
                && assistant.getToolCalls() != null
                && !assistant.getToolCalls().isEmpty()) {
            return assistant.getToolCalls().stream()
                    .map(TuneUtils::serializeToolCallNameAndArguments)
                    .collect(Collectors.joining());
        }
        return resolvedMessage.getContentAsString();
    }

    public static String get_output_string_from_message(BaseMessage message) {
        return getOutputStringFromMessage(message);
    }

    public static String getContentStringFromTemplate(PromptTemplate template) {
        return Objects.requireNonNull(template, "template").toMessages().stream()
                .map(BaseMessage::getContentAsString)
                .collect(Collectors.joining("\n"));
    }

    public static String get_content_string_from_template(PromptTemplate template) {
        return getContentStringFromTemplate(template);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseJsonFromLlmResponse(String jsonLikeString) {
        Object data = parseLlmResponse(jsonLikeString, JSON_BLOCK_RE, false);
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return (Map<String, Object>) data;
    }

    public static Map<String, Object> parse_json_from_llm_response(String jsonLikeString) {
        return parseJsonFromLlmResponse(jsonLikeString);
    }

    public static List<Object> parseListFromLlmResponse(String listLikeString) {
        Object data = parseLlmResponse(listLikeString, LIST_BLOCK_RE, true);
        if (!(data instanceof List<?> list)) {
            return null;
        }
        return new ArrayList<>(list);
    }

    public static List<Object> parse_list_from_llm_response(String listLikeString) {
        return parseListFromLlmResponse(listLikeString);
    }

    public static String convertCasesToExamples(List<?> cases) {
        if (cases == null || cases.isEmpty()) {
            return "";
        }
        List<String> examples = new ArrayList<>();
        for (int index = 0; index < cases.size(); index++) {
            Object item = cases.get(index);
            examples.add("example " + (index + 1) + ":\n"
                    + "[question]: " + convertDictToString(caseInputs(item)) + "\n"
                    + "[expected answer]: " + convertDictToString(caseLabel(item)));
        }
        return String.join("\n", examples);
    }

    public static String convert_cases_to_examples(List<?> cases) {
        return convertCasesToExamples(cases);
    }

    public static String convertDictToString(Map<?, ?> data) {
        return data.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(" | "));
    }

    private static Object parseLlmResponse(String value, Pattern pattern, boolean pythonLiteralList) {
        if (value == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        String matched = matcher.group(1).strip();
        if (matched.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(matched, Object.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            if (!pythonLiteralList) {
                return null;
            }
            return parsePythonListLiteral(matched);
        }
    }

    private static Object parsePythonListLiteral(String matched) {
        String normalized = matched
                .replace("None", "null")
                .replace("True", "true")
                .replace("False", "false")
                .replace('\'', '"');
        try {
            return OBJECT_MAPPER.readValue(normalized, Object.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static String serializeToolCallFull(ToolCall toolCall) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", toolCall.getId());
        data.put("type", toolCall.getType());
        data.put("name", toolCall.getName());
        data.put("arguments", toolCall.getArguments());
        data.put("index", toolCall.getIndex());
        return writeJson(data);
    }

    private static String serializeToolCallNameAndArguments(ToolCall toolCall) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", toolCall.getName());
        data.put("arguments", toolCall.getArguments());
        return writeJson(data);
    }

    private static String writeJson(Map<String, Object> data) {
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            return String.valueOf(data);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<BaseMessage> readMessages(Case caseValue) {
        Object value = readNoArg(caseValue, "getMessages");
        if (value == null) {
            value = readNoArg(caseValue, "messages");
        }
        if (value instanceof List<?> list) {
            return (List<BaseMessage>) list;
        }
        throw new IllegalArgumentException("case.messages is required");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readVariables(Case caseValue) {
        Object value = readNoArg(caseValue, "getVariables");
        if (value == null) {
            value = readNoArg(caseValue, "variables");
        }
        if (value == null) {
            throw new IllegalArgumentException("case.variables is required");
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return (Map<String, Object>) value;
    }

    private static Object readNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException exception) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalArgumentException("failed to read " + methodName, exception);
        }
    }

    private static Map<String, Object> caseInputs(Object item) {
        if (item instanceof Case caseValue) {
            return caseValue.getInputs();
        }
        if (item instanceof EvaluatedCase evaluatedCase) {
            return evaluatedCase.getInputs();
        }
        throw new IllegalArgumentException("case item must be Case or EvaluatedCase");
    }

    private static Map<String, Object> caseLabel(Object item) {
        if (item instanceof Case caseValue) {
            return caseValue.getLabel();
        }
        if (item instanceof EvaluatedCase evaluatedCase) {
            return evaluatedCase.getLabel();
        }
        throw new IllegalArgumentException("case item must be Case or EvaluatedCase");
    }
}
