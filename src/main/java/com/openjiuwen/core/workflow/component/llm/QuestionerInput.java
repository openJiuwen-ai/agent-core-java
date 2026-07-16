/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Input model for the Questioner component.
 * 
 * @since 0.1.7
 */
@Data
public class QuestionerInput {
    private Object query = "";

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> extraFields = new LinkedHashMap<>();

    /**
     * fromMap.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    public static QuestionerInput fromMap(Map<String, Object> inputs) {
        QuestionerInput input = new QuestionerInput();
        if (inputs == null) {
            return input;
        }
        if (inputs.containsKey("query")) {
            input.setQuery(inputs.get("query"));
        }
        Map<String, Object> extra = new LinkedHashMap<>(inputs);
        extra.remove("query");
        input.setExtraFields(extra);
        return input;
    }

    /**
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>(extraFields);
        result.put("query", query);
        return result;
    }
}
