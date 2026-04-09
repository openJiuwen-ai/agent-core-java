  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.workflow.component.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Output model for the Questioner component.
 */
public class QuestionerOutput {
    private Object userResponse = "";
    private String question = "";
    private final Map<String, Object> extraFields = new LinkedHashMap<>();

    public Object getUserResponse() {
        return userResponse;
    }

    public void setUserResponse(Object userResponse) {
        this.userResponse = userResponse;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public void putField(String key, Object value) {
        extraFields.put(key, value);
    }

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

    public static QuestionerOutput fromFields(Map<String, Object> fields) {
        QuestionerOutput output = new QuestionerOutput();
        if (fields != null) {
            output.extraFields.putAll(fields);
        }
        return output;
    }
}
