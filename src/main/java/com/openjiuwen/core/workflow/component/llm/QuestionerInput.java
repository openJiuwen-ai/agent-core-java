  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.workflow.component.llm;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Input model for the Questioner component.
 */
@Data
public class QuestionerInput {
    private Object query = "";
    private Map<String, Object> extraFields = new LinkedHashMap<>();

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

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>(extraFields);
        result.put("query", query);
        return result;
    }
}
