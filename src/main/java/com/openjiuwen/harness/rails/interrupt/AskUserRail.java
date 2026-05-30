/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ask-user rail: interrupts tool execution and returns user input without
 * executing the underlying tool.
 * <p>
 * Supports multi-question mode with structured selection.
 * <p>
 * Mirrors Python's {@code AskUserRail} in
 * {@code openjiuwen.harness.rails.interrupt.ask_user_rail}.
 */
public class AskUserRail extends BaseInterruptRail {

    private static final Logger LOG = LoggerFactory.getLogger(AskUserRail.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Default tool names this rail intercepts. */
    private static final Set<String> DEFAULT_TOOL_NAMES = Collections.singleton("ask_user");

    private final List<Object> tools = new ArrayList<>();

    public AskUserRail() {
        this(null);
    }

    public AskUserRail(Iterable<String> toolNames) {
        super(toolNames != null ? toolNames : DEFAULT_TOOL_NAMES);
    }

    @Override
    public void init(Object agent) {
        LOG.info("[AskUserRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        tools.clear();
        LOG.info("[AskUserRail] Uninitialized");
    }

    @Override
    public InterruptDecision resolveInterrupt(Object ctx, Object toolCall, Object userInput,
            Map<String, Object> autoConfirmConfig) {
        if (userInput == null) {
            LOG.debug("[AskUserRail] No user input yet - interrupting to ask user");
            return InterruptDecision.interrupt(buildAskRequest(toolCall));
        }

        AskUserPayload payload;
        try {
            payload = parseUserInput(userInput, toolCall);
        } catch (RuntimeException e) {
            LOG.debug("[AskUserRail] Failed to parse user input - interrupting again", e);
            return InterruptDecision.interrupt(buildAskRequest(toolCall));
        }

        if (payload.getAnswers().isEmpty()) {
            return InterruptDecision.interrupt(buildAskRequest(toolCall));
        }

        String toolResult = formatToolResult(toolCall, payload);
        LOG.debug("[AskUserRail] User answered - rejecting ask_user tool with result");
        return InterruptDecision.reject(toolResult);
    }

    private AskUserPayload parseUserInput(Object userInput, Object toolCall) {
        if (userInput instanceof AskUserPayload payload) {
            return payload;
        }
        if (userInput instanceof Map<?, ?> rawMap) {
            return parseUserInputMap(rawMap, toolCall);
        }
        if (userInput instanceof String answer) {
            if (answer.isEmpty()) {
                return new AskUserPayload();
            }
            List<Map<String, Object>> questions = getQuestions(toolCall);
            if (questions.isEmpty()) {
                return new AskUserPayload();
            }
            String firstQuestion = stringValue(questions.get(0).get("question"));
            return new AskUserPayload(Map.of(firstQuestion, answer));
        }

        LOG.warn("[AskUserRail] Unexpected user input type: {}", userInput.getClass());
        return new AskUserPayload();
    }

    private AskUserPayload parseUserInputMap(Map<?, ?> rawMap, Object toolCall) {
        Object answersObj = rawMap.get("answers");
        if (answersObj instanceof Map<?, ?> answersMap) {
            Map<String, String> answers = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : answersMap.entrySet()) {
                answers.put(stringValue(entry.getKey()), stringValue(entry.getValue()));
            }
            return new AskUserPayload(answers);
        }

        List<Map<String, Object>> questions = getQuestions(toolCall);
        if (questions.size() == 1 && rawMap.containsKey("answer")) {
            String firstQuestion = stringValue(questions.get(0).get("question"));
            return new AskUserPayload(Map.of(firstQuestion, stringValue(rawMap.get("answer"))));
        }
        return new AskUserPayload();
    }

    private String formatToolResult(Object toolCall, AskUserPayload payload) {
        List<Map<String, Object>> questions = getQuestions(toolCall);
        if (questions.isEmpty()) {
            return toPythonDictString(payload.getAnswers());
        }

        List<String> answerParts = new ArrayList<>();
        for (Map<String, Object> question : questions) {
            String questionText = stringValue(question.get("question"));
            String answerValue = payload.getAnswers().getOrDefault(questionText, "");
            answerParts.add("\"" + questionText + "\"=\"" + answerValue + "\"");
        }
        String answersText = String.join(", ", answerParts);
        return "User has answered your questions: " + answersText + ". "
                + "You can now continue with the user's answers in mind.";
    }

    private Map<String, Object> buildAskRequest(Object toolCall) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("message", "");
        request.put("payload_schema", AskUserPayload.toSchema());
        request.put("questions", getQuestions(toolCall));
        return request;
    }

    private List<Map<String, Object>> getQuestions(Object toolCall) {
        Object rawQuestions = parseToolArgs(toolCall).get("questions");
        if (!(rawQuestions instanceof List<?> questionList)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> questions = new ArrayList<>();
        for (Object item : questionList) {
            if (item instanceof Map<?, ?> rawQuestion) {
                questions.add(toStringKeyMap(rawQuestion));
            }
        }
        return questions;
    }

    private Map<String, Object> parseToolArgs(Object toolCall) {
        if (toolCall == null) {
            return Collections.emptyMap();
        }

        Object args;
        if (toolCall instanceof Map<?, ?> map) {
            args = map.get("arguments");
        } else {
            try {
                Method getArguments = toolCall.getClass().getMethod("getArguments");
                args = getArguments.invoke(toolCall);
            } catch (Exception e) {
                return Collections.emptyMap();
            }
        }
        return coerceMap(args);
    }

    private Map<String, Object> coerceMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return toStringKeyMap(map);
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                Object parsed = JSON.readValue(text, Object.class);
                if (parsed instanceof Map<?, ?> map) {
                    return toStringKeyMap(map);
                }
            } catch (Exception e) {
                return Collections.emptyMap();
            }
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            result.put(stringValue(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private String toPythonDictString(Map<String, String> values) {
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            entries.add("'" + escapePythonString(entry.getKey()) + "': '"
                    + escapePythonString(entry.getValue()) + "'");
        }
        return "{" + String.join(", ", entries) + "}";
    }

    private String escapePythonString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : "";
    }

    /**
     * Payload for ask-user response.
     * <p>
     * Mirrors Python's {@code AskUserPayload}.
     */
    public static class AskUserPayload {
        private final Map<String, String> answers = new LinkedHashMap<>();

        public AskUserPayload() {
        }

        public AskUserPayload(Map<String, String> answers) {
            if (answers != null) {
                this.answers.putAll(answers);
            }
        }

        public Map<String, String> getAnswers() {
            return Collections.unmodifiableMap(answers);
        }

        public String toJsonString() {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> e : answers.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append("\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }

        public static AskUserPayload fromMap(Map<String, Object> map) {
            AskUserPayload payload = new AskUserPayload();
            Object answersObj = map.get("answers");
            if (answersObj instanceof Map<?, ?> answersMap) {
                for (Map.Entry<?, ?> entry : answersMap.entrySet()) {
                    payload.answers.put(entry.getKey() != null ? entry.getKey().toString() : "",
                            entry.getValue() != null ? entry.getValue().toString() : "");
                }
            }
            return payload;
        }

        public static Map<String, Object> toSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", Collections.singletonMap("answers",
                    Collections.singletonMap("type", "object")));
            return schema;
        }
    }
}
