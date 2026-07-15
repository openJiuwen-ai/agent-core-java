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
 * 
 * @since 0.1.7
 */
public class QuestionerState {
    private static final String QUESTIONER_STATE_KEY = "questioner_state";

    private int responseNum;
    private Object userResponse = "";
    private String question = "";

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> extractedKeyFields = new LinkedHashMap<>();
    private ExecutionStatus status = ExecutionStatus.START;

    /**
     * QuestionerState.
     * 
     * @since 0.1.7
     */
    public QuestionerState() {
    }

    /**
     * QuestionerState.
     * 
     * @param responseNum responseNum
     * @param userResponse userResponse
     * @param question question
     * @param extractedKeyFields extractedKeyFields
     * @param status status
     * @since 0.1.7
     */
    public QuestionerState(int responseNum, Object userResponse, String question,
            Map<String, Object> extractedKeyFields, ExecutionStatus status) {
        this.responseNum = responseNum;
        this.userResponse = userResponse;
        this.question = question;
        this.extractedKeyFields = new LinkedHashMap<>(extractedKeyFields);
        this.status = status;
    }

    /**
     * deserialize.
     * 
     * @param rawState rawState
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static QuestionerState deserialize(Map<String, Object> rawState) {
        QuestionerState state = new QuestionerState();
        if (rawState == null) {
            return state;
        }
        state.responseNum =
            rawState.containsKey("response_num") ? ((Number) rawState.get("response_num")).intValue() : 0;
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
        return state.handleEvent(QuestionerEvent.valueOf(eventNameFromStatus(state.status)));
    }

    /**
     * serialize.
     * 
     * @return the result
     * @since 0.1.7
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
     * handleEvent.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
    public QuestionerState handleEvent(QuestionerEvent event) {
        return switch (event) {
            case START_EVENT -> QuestionerStartState.fromState(this);
            case USER_INTERACT_EVENT -> QuestionerInteractState.fromState(this);
            case END_EVENT -> QuestionerEndState.fromState(this);
        };
    }

    // ========== Session persistence ==========

    /**
     * loadFromSession.
     * 
     * @param sessionState sessionState
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static QuestionerState loadFromSession(Object sessionState) {
        if (sessionState instanceof Map<?, ?> map) {
            Object stateDict = map.get(QUESTIONER_STATE_KEY);
            if (stateDict instanceof Map<?, ?> sd) {
                return deserialize((Map<String, Object>) sd);
            }
            if (map.containsKey("response_num") || map.containsKey("status") || map.containsKey("question")
                    || map.containsKey("extracted_key_fields")) {
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
     * storeToSession.
     * 
     * @param state state
     * @param session session
     * @since 0.1.7
     */
    public static void storeToSession(QuestionerState state, com.openjiuwen.core.session.NodeSessionApi session) {
        session.updateState(Map.of(QUESTIONER_STATE_KEY, state.serialize()));
    }

    // ========== Query helpers ==========

    /**
     * isUndergoingInteraction.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isUndergoingInteraction() {
        return status == ExecutionStatus.USER_INTERACT;
    }

    /**
     * isFreshState.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isFreshState() {
        return status == ExecutionStatus.START && responseNum == 0;
    }

    // ========== Getters and setters ==========

    /**
     * getResponseNum.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getResponseNum() {
        return responseNum;
    }

    /**
     * setResponseNum.
     * 
     * @param responseNum responseNum
     * @since 0.1.7
     */
    public void setResponseNum(int responseNum) {
        this.responseNum = responseNum;
    }

    /**
     * incrementResponseNum.
     * 
     * @since 0.1.7
     */
    public void incrementResponseNum() {
        this.responseNum++;
    }

    /**
     * getUserResponse.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getUserResponse() {
        return userResponse;
    }

    /**
     * setUserResponse.
     * 
     * @param userResponse userResponse
     * @since 0.1.7
     */
    public void setUserResponse(Object userResponse) {
        this.userResponse = userResponse;
    }

    /**
     * getQuestion.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getQuestion() {
        return question;
    }

    /**
     * setQuestion.
     * 
     * @param question question
     * @since 0.1.7
     */
    public void setQuestion(String question) {
        this.question = question;
    }

    /**
     * getExtractedKeyFields.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getExtractedKeyFields() {
        return extractedKeyFields;
    }

    /**
     * setExtractedKeyFields.
     * 
     * @param extractedKeyFields extractedKeyFields
     * @since 0.1.7
     */
    public void setExtractedKeyFields(Map<String, Object> extractedKeyFields) {
        this.extractedKeyFields = extractedKeyFields;
    }

    /**
     * getStatus.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ExecutionStatus getStatus() {
        return status;
    }

    /**
     * setStatus.
     * 
     * @param status status
     * @since 0.1.7
     */
    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    /**
     * eventNameFromStatus.
     * 
     * @param status status
     * @return the result
     * @since 0.1.7
     */
    private static String eventNameFromStatus(ExecutionStatus status) {
        return switch (status) {
            case START -> "START_EVENT";
            case USER_INTERACT -> "USER_INTERACT_EVENT";
            case END -> "END_EVENT";
        };
    }
}
