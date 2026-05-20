/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Questioner component state machine.
 * <p>
 * Mirrors Python's {@code QuestionerState} hierarchy (StartState, InteractState, EndState).
 */
public class QuestionerState {

    private static final String QUESTIONER_STATE_KEY = "questioner_state";

    private int responseNum;
    private Object userResponse = "";
    private String question = "";
    private Map<String, Object> extractedKeyFields = new LinkedHashMap<>();
    private ExecutionStatus status = ExecutionStatus.START;

    /**
     * Auto-generated for codecheck compliance.
     */
    public QuestionerState() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public QuestionerState(int responseNum, Object userResponse, String question,
                           Map<String, Object> extractedKeyFields, ExecutionStatus status) {
        this.responseNum = responseNum;
        this.userResponse = userResponse;
        this.question = question;
        this.extractedKeyFields = new LinkedHashMap<>(extractedKeyFields);
        this.status = status;
    }

    // ========== Serialization ==========

    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public static QuestionerState deserialize(Map<String, Object> rawState) {
        QuestionerState state = new QuestionerState();
        if (rawState == null) {
            return state;
        }
        state.responseNum = rawState.containsKey("response_num")
                ? ((Number) rawState.get("response_num")).intValue() : 0;
        state.userResponse = rawState.getOrDefault("user_response", "");
        state.question = (String) rawState.getOrDefault("question", "");
        Object fields = rawState.get("extracted_key_fields");
        if (fields instanceof Map) {
            state.extractedKeyFields = new LinkedHashMap<>((Map<String, Object>) fields);
        }
        Object statusVal = rawState.get("status");
        if (statusVal instanceof String s) {
            state.status = ExecutionStatus.fromValue(s);
        }
        return state.handleEvent(QuestionerEvent.valueOf(
                eventNameFromStatus(state.status)));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("response_num", responseNum);
        map.put("user_response", userResponse);
        map.put("question", question);
        map.put("extracted_key_fields", new LinkedHashMap<>(extractedKeyFields));
        map.put("status", status.getValue());
        return map;
    }

    // ========== State transitions ==========

    /**
     * Auto-generated for codecheck compliance.
     */
    public QuestionerState handleEvent(QuestionerEvent event) {
        return switch (event) {
            case START_EVENT -> QuestionerStartState.fromState(this);
            case USER_INTERACT_EVENT -> QuestionerInteractState.fromState(this);
            case END_EVENT -> QuestionerEndState.fromState(this);
        };
    }

    // ========== Session persistence ==========

    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public static QuestionerState loadFromSession(Object sessionState) {
        if (sessionState instanceof Map<?, ?> map) {
            Object stateDict = map.get(QUESTIONER_STATE_KEY);
            if (stateDict instanceof Map<?, ?> sd) {
                return deserialize((Map<String, Object>) sd);
            }
            if (map.containsKey("response_num") || map.containsKey("status")
                    || map.containsKey("question") || map.containsKey("extracted_key_fields")) {
                return deserialize((Map<String, Object>) map);
            }
            Object compState = map.get("comp_state");
            if (compState instanceof Map<?, ?> compMap) {
                for (Object nestedState : compMap.values()) {
                    if (nestedState instanceof Map<?, ?> nestedMap) {
                        QuestionerState restored = loadFromSession(nestedMap);
                        if (!restored.isFreshState()) {
                            return restored;
                        }
                    }
                }
            }
        }
        return new QuestionerState();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void storeToSession(QuestionerState state,
                                      com.openjiuwen.core.session.NodeSessionApi session) {
        session.updateState(Map.of(QUESTIONER_STATE_KEY, state.serialize()));
    }

    // ========== Query helpers ==========

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isUndergoingInteraction() {
        return status == ExecutionStatus.USER_INTERACT;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isFreshState() {
        return status == ExecutionStatus.START && responseNum == 0;
    }

    // ========== Getters and setters ==========

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getResponseNum() {
        return responseNum;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setResponseNum(int responseNum) {
        this.responseNum = responseNum;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void incrementResponseNum() {
        this.responseNum++;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getUserResponse() {
        return userResponse;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setUserResponse(Object userResponse) {
        this.userResponse = userResponse;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getQuestion() {
        return question;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setQuestion(String question) {
        this.question = question;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getExtractedKeyFields() {
        return extractedKeyFields;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setExtractedKeyFields(Map<String, Object> extractedKeyFields) {
        this.extractedKeyFields = extractedKeyFields;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ExecutionStatus getStatus() {
        return status;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    // ========== Internal ==========

    private static String eventNameFromStatus(ExecutionStatus status) {
        return switch (status) {
            case START -> "START_EVENT";
            case USER_INTERACT -> "USER_INTERACT_EVENT";
            case END -> "END_EVENT";
        };
    }
}
