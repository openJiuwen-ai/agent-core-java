/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Output model for the Questioner component.
 * 
 * @since 0.1.7
 */
public class QuestionerOutput {
    private Object userResponse = "";
    private String question = "";

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Object> extraFields = new LinkedHashMap<>();

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
     * putField.
     * 
     * @param key key
     * @param value value
     * @since 0.1.7
     */
    public void putField(String key, Object value) {
        extraFields.put(key, value);
    }

    /**
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>(extraFields);
        if (userResponse != null && !"".equals(userResponse)) {
            result.put("user_response", userResponse);
        }
        if (question != null && !question.isEmpty()) {
            result.put("question", question);
        }
        return result;
    }

    /**
     * fromFields.
     * 
     * @param fields fields
     * @return the result
     * @since 0.1.7
     */
    public static QuestionerOutput fromFields(Map<String, Object> fields) {
        QuestionerOutput output = new QuestionerOutput();
        if (fields != null) {
            output.extraFields.putAll(fields);
        }
        return output;
    }
}
