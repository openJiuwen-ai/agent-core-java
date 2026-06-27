/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.harness.rails.CallbackContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles ask-user tool interruptions.
 *
 * <p>Mirrors Python's {@code AskUserRail} and payload classes in
 * {@code openjiuwen/harness/rails/interrupt/ask_user_rail.py}.</p>
 */
public class AskUserRail extends BaseInterruptRail {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    public AskUserRail() {
        super(List.of("ask_user"));
    }

    @Override
    public void beforeToolCall(CallbackContext ctx) {
        if ("ask_user".equals(ctx.get("tool_name"))) {
            ctx.put("interrupt_kind", "ask_user");
        }
        super.beforeToolCall(ctx);
    }

    public InterruptDecision resolveInterrupt(CallbackContext ctx, ToolCall toolCall, Object userInput) {
        if (userInput == null) {
            return interrupt(buildAskRequest(toolCall));
        }

        AskUserPayload payload;
        try {
            if (userInput instanceof AskUserPayload askUserPayload) {
                payload = askUserPayload;
            } else if (userInput instanceof Map<?, ?> inputMap) {
                payload = parseUserInputMap(inputMap, toolCall);
            } else if (userInput instanceof String text) {
                if (text.isEmpty()) {
                    return interrupt(buildAskRequest(toolCall));
                }
                List<Map<String, Object>> questions = questions(toolCall);
                if (questions.isEmpty()) {
                    return interrupt(buildAskRequest(toolCall));
                }
                payload = new AskUserPayload(Map.of(questionText(questions.getFirst()), text));
            } else {
                return interrupt(buildAskRequest(toolCall));
            }
        } catch (RuntimeException exception) {
            return interrupt(buildAskRequest(toolCall));
        }

        if (payload.answers().isEmpty()) {
            return interrupt(buildAskRequest(toolCall));
        }
        return reject(formatToolResult(toolCall, payload));
    }

    private AskUserPayload parseUserInputMap(Map<?, ?> userInput, ToolCall toolCall) {
        Object answers = userInput.get("answers");
        if (answers instanceof Map<?, ?> answerMap) {
            Map<String, String> values = new LinkedHashMap<>();
            answerMap.forEach((key, value) -> values.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
            return new AskUserPayload(values);
        }
        List<Map<String, Object>> questions = questions(toolCall);
        if (questions.size() == 1 && userInput.containsKey("answer")) {
            return new AskUserPayload(Map.of(questionText(questions.getFirst()), String.valueOf(userInput.get("answer"))));
        }
        return new AskUserPayload();
    }

    private String formatToolResult(ToolCall toolCall, AskUserPayload payload) {
        List<Map<String, Object>> questions = questions(toolCall);
        if (questions.isEmpty()) {
            return pythonDictString(payload.answers());
        }

        List<String> answerParts = new ArrayList<>();
        for (Map<String, Object> question : questions) {
            String questionText = questionText(question);
            String answer = payload.answers().getOrDefault(questionText, "");
            answerParts.add("\"" + questionText + "\"=\"" + answer + "\"");
        }
        return "User has answered your questions: "
                + String.join(", ", answerParts)
                + ". You can now continue with the user's answers in mind.";
    }

    private AskUserRequest buildAskRequest(ToolCall toolCall) {
        AskUserRequest request = new AskUserRequest();
        request.setMessage("");
        request.setPayloadSchema(AskUserPayload.toSchema());
        request.setQuestions(questions(toolCall));
        return request;
    }

    private List<Map<String, Object>> questions(ToolCall toolCall) {
        Object value = parseToolArgs(toolCall).get("questions");
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> rawMap) {
                Map<String, Object> question = new LinkedHashMap<>();
                rawMap.forEach((key, rawValue) -> question.put(String.valueOf(key), rawValue));
                result.add(question);
            }
        }
        return result;
    }

    private Map<String, Object> parseToolArgs(ToolCall toolCall) {
        if (toolCall == null || toolCall.getArguments() == null || toolCall.getArguments().isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(toolCall.getArguments(), MAP_TYPE);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private static InterruptResult interrupt(AskUserRequest request) {
        return new InterruptResult(request);
    }

    private static RejectResult reject(String toolResult) {
        return new RejectResult(toolResult);
    }

    private static String questionText(Map<String, Object> question) {
        Object value = question.get("question");
        return value == null ? "" : String.valueOf(value);
    }

    private static String pythonDictString(Map<String, String> answers) {
        List<String> entries = new ArrayList<>();
        answers.forEach((key, value) -> entries.add("'" + key + "': '" + value + "'"));
        return "{" + String.join(", ", entries) + "}";
    }

    /**
     * Mirrors Python's {@code AskUserPayload} in
     * {@code openjiuwen/harness/rails/interrupt/ask_user_rail.py}.
     *
     * @param answers question text to answer mapping
     */
    public record AskUserPayload(Map<String, String> answers) {
        public AskUserPayload() {
            this(Map.of());
        }

        public AskUserPayload {
            answers = answers == null ? Map.of() : new LinkedHashMap<>(answers);
        }

        public static Map<String, Object> toSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "answers", Map.of(
                                    "type", "object",
                                    "additionalProperties", Map.of("type", "string"),
                                    "description", "Question text to answer mapping"
                            )
                    )
            );
        }
    }

    /**
     * Ask-user request configuration with structured questions.
     *
     * <p>Mirrors Python's {@code AskUserRequest} in
     * {@code openjiuwen/harness/rails/interrupt/ask_user_rail.py}.</p>
     */
    public static class AskUserRequest extends InterruptRequest {
        private List<Map<String, Object>> questions = new ArrayList<>();

        public List<Map<String, Object>> getQuestions() {
            return questions;
        }

        public void setQuestions(List<Map<String, Object>> questions) {
            this.questions = questions == null ? new ArrayList<>() : new ArrayList<>(questions);
            putExtraField("questions", this.questions);
        }
    }
}
