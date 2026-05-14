/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal output cache used during questioner processing.
 */
@Data
public class OutputCache {
    private Object userResponse = "";
    private String question = "";
    private Map<String, Object> keyFields = new LinkedHashMap<>();

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

    public Map<String, Object> getKeyFields() {
        return keyFields;
    }

    public void setKeyFields(Map<String, Object> keyFields) {
        this.keyFields = keyFields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(keyFields);
    }
}
