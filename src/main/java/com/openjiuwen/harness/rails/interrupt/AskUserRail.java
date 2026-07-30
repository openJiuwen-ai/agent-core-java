/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.interrupt.AskUserRequest;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ask-user rail: returns user input without executing the tool.
 * <p>
 * Aligned with Python {@code openjiuwen.harness.rails.interrupt.ask_user_rail.AskUserRail}.
 * Questions come from tool argument {@code questions}; interrupt {@code message} is empty.
 *
 * @since 0.1.7
 */
public class AskUserRail extends BaseInterruptRail {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * AskUserRail.
     *
     * @since 0.1.7
     */
    public AskUserRail() {
        super(List.of("ask_user"));
    }

    /**
     * resolveInterrupt.
     *
     * @param ctx ctx
     * @param toolCall toolCall
     * @param userInput userInput
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected InterruptDecision resolveInterrupt(AgentCallbackContext ctx, ToolCall toolCall, Object userInput) {
        if (userInput == null) {
            return interrupt(buildAskRequest(toolCall));
        }

        try {
            AskUserPayload payload;
            if (userInput instanceof AskUserPayload askUserPayload) {
                payload = askUserPayload;
            } else if (userInput instanceof Map<?, ?> map) {
                payload = parseUserInputDict(castStringObjectMap(map), toolCall);
            } else if (userInput instanceof String text) {
                if (text.isEmpty()) {
                    return interrupt(buildAskRequest(toolCall));
                }
                List<Map<String, Object>> questions = extractQuestions(parseToolArgs(toolCall));
                if (!questions.isEmpty()) {
                    String firstQuestion = stringField(questions.get(0), "question");
                    Map<String, String> answers = new LinkedHashMap<>();
                    answers.put(firstQuestion, text);
                    payload = AskUserPayload.builder().answers(answers).build();
                } else {
                    return interrupt(buildAskRequest(toolCall));
                }
            } else {
                return interrupt(buildAskRequest(toolCall));
            }

            if (payload.getAnswers() == null || payload.getAnswers().isEmpty()) {
                return interrupt(buildAskRequest(toolCall));
            }
            return reject(formatToolResult(toolCall, payload));
        } catch (RuntimeException ignored) {
            return interrupt(buildAskRequest(toolCall));
        }
    }

    /**
     * Build AskUserRequest with questions from tool call arguments.
     *
     * @param toolCall ask_user tool call; may be null
     * @return interrupt request
     * @since 0.1.14
     */
    static AskUserRequest buildAskRequest(ToolCall toolCall) {
        Map<String, Object> args = parseToolArgs(toolCall);
        List<Map<String, Object>> questions = extractQuestions(args);

        String interruptId = toolCall != null && toolCall.getId() != null && !toolCall.getId().isBlank()
                ? toolCall.getId()
                : "ask_user_interrupt";

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("tool_call_id", interruptId);
        context.put("tool_name", "ask_user");

        AskUserRequest request = new AskUserRequest();
        request.setInterruptId(interruptId);
        request.setMessage("");
        request.setPayloadSchema(AskUserPayload.toSchema());
        request.setContext(context);
        request.setQuestions(questions);
        return request;
    }

    /**
     * Parse tool_call.arguments to a map.
     *
     * @param toolCall tool call
     * @return parsed args or empty map
     * @since 0.1.14
     */
    static Map<String, Object> parseToolArgs(ToolCall toolCall) {
        if (toolCall == null || toolCall.getArguments() == null || toolCall.getArguments().isBlank()) {
            return Collections.emptyMap();
        }
        String raw = toolCall.getArguments().trim();
        try {
            Map<String, Object> parsed = MAPPER.readValue(raw, new TypeReference<>() {
            });
            return parsed != null ? parsed : Collections.emptyMap();
        } catch (JsonProcessingException ignored) {
            return Collections.emptyMap();
        }
    }

    static List<Map<String, Object>> extractQuestions(Map<String, Object> args) {
        if (args == null) {
            return Collections.emptyList();
        }
        Object questions = args.get("questions");
        if (!(questions instanceof List<?> list) || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                normalized.add(castStringObjectMap(map));
            } else if (item != null) {
                // tolerate plain string questions from older callers
                Map<String, Object> wrapper = new LinkedHashMap<>();
                wrapper.put("question", String.valueOf(item));
                normalized.add(wrapper);
            }
        }
        return normalized;
    }

    private static AskUserPayload parseUserInputDict(Map<String, Object> userInput, ToolCall toolCall) {
        Object answersObj = userInput.get("answers");
        if (answersObj instanceof Map<?, ?> answersMap) {
            Map<String, String> answers = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : answersMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    answers.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            return AskUserPayload.builder().answers(answers).build();
        }
        List<Map<String, Object>> questions = extractQuestions(parseToolArgs(toolCall));
        if (questions.size() == 1 && userInput.containsKey("answer")) {
            String firstQuestion = stringField(questions.get(0), "question");
            Map<String, String> answers = new LinkedHashMap<>();
            answers.put(firstQuestion, String.valueOf(userInput.get("answer")));
            return AskUserPayload.builder().answers(answers).build();
        }
        return AskUserPayload.builder().build();
    }

    private static String formatToolResult(ToolCall toolCall, AskUserPayload payload) {
        List<Map<String, Object>> questions = extractQuestions(parseToolArgs(toolCall));
        if (questions.isEmpty()) {
            return String.valueOf(payload.getAnswers());
        }
        List<String> answerParts = new ArrayList<>();
        for (Map<String, Object> question : questions) {
            String questionText = stringField(question, "question");
            String answerValue = payload.getAnswers().getOrDefault(questionText, "");
            answerParts.add("\"" + questionText + "\"=\"" + answerValue + "\"");
        }
        return "User has answered your questions: " + String.join(", ", answerParts)
                + ". You can now continue with the user's answers in mind.";
    }

    private static String stringField(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castStringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }
}
