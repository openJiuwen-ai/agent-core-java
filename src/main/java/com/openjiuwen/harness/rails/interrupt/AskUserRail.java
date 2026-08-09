/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.HarnessPromptsPackage;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.tools.AskUserTool;

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

    private AskUserTool askUserTool;

    public AskUserRail() {
        super(List.of("ask_user"));
    }

    @Override
    public void init(DeepAgent agent) {
        super.init(agent);
        if (agent == null) {
            return;
        }
        String language = HarnessPromptsPackage.resolveLanguage(
                agent.deepConfig() == null ? null : agent.deepConfig().getLanguage());
        String agentId = agent.getCard() == null ? null : agent.getCard().getId();
        askUserTool = new AskUserTool(language, agentId);
        agent.registerTool(askUserTool);
    }

    @Override
    public void uninit(DeepAgent agent) {
        if (agent != null && askUserTool != null && askUserTool.getCard() != null) {
            agent.unregisterTool(askUserTool.getCard().getName());
        }
        askUserTool = null;
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
                payload = new AskUserPayload(Map.of(questionText(questions.get(0)), text));
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
            return new AskUserPayload(Map.of(questionText(questions.get(0)), String.valueOf(userInput.get("answer"))));
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
        List<Map<String, Object>> questionList = questions(toolCall);
        AskUserRequest request = new AskUserRequest();
        // Fill message for apps that only read InterruptRequest.message.
        request.setMessage(formatMessageFromQuestions(questionList));
        request.setPayloadSchema(AskUserPayload.toSchema());
        request.setQuestions(questionList);
        return request;
    }

    /**
     * Render structured questions into a human-readable interrupt message.
     *
     * @param questions normalized ask_user questions; may be null/empty
     * @return concatenated message, or empty string when there is no usable content
     */
    static String formatMessageFromQuestions(List<Map<String, Object>> questions) {
        if (questions == null || questions.isEmpty()) {
            return "";
        }
        List<String> blocks = new ArrayList<>();
        for (Map<String, Object> question : questions) {
            if (question == null || question.isEmpty()) {
                continue;
            }
            StringBuilder block = new StringBuilder();
            String header = stringField(question, "header").trim();
            String questionText = stringField(question, "question").trim();
            if (!header.isEmpty() && !questionText.isEmpty()) {
                block.append('[').append(header).append("] ").append(questionText);
            } else if (!questionText.isEmpty()) {
                block.append(questionText);
            } else if (!header.isEmpty()) {
                block.append(header);
            }

            Object optionsObj = question.get("options");
            if (optionsObj instanceof List<?> options && !options.isEmpty()) {
                List<String> optionLines = new ArrayList<>();
                for (Object option : options) {
                    String line = formatOptionLine(option);
                    if (!line.isEmpty()) {
                        optionLines.add(line);
                    }
                }
                if (!optionLines.isEmpty()) {
                    if (block.length() > 0) {
                        block.append('\n');
                    }
                    block.append(String.join("\n", optionLines));
                }
            }

            Object multiSelect = question.get("multi_select");
            if (Boolean.TRUE.equals(multiSelect) || "true".equalsIgnoreCase(String.valueOf(multiSelect))) {
                if (block.length() > 0) {
                    block.append('\n');
                }
                block.append("(multi-select)");
            }

            if (block.length() > 0) {
                blocks.add(block.toString());
            }
        }
        return String.join("\n\n", blocks);
    }

    private static String formatOptionLine(Object option) {
        if (option instanceof Map<?, ?> map) {
            String label = stringField(map, "label").trim();
            String description = stringField(map, "description").trim();
            if (!label.isEmpty() && !description.isEmpty()) {
                return "- " + label + ": " + description;
            }
            if (!label.isEmpty()) {
                return "- " + label;
            }
            if (!description.isEmpty()) {
                return "- " + description;
            }
            return "";
        }
        if (option == null) {
            return "";
        }
        String text = String.valueOf(option).trim();
        return text.isEmpty() ? "" : "- " + text;
    }

    private static String stringField(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
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
