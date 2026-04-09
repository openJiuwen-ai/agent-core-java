  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.workflow.component.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Input model for IntentDetection component.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.intent_detection_comp.IntentDetectionInput}.
 */
public class IntentDetectionInput {

    private String query = "";
    private final Map<String, Object> extraFields = new LinkedHashMap<>();

    public IntentDetectionInput() {
    }

    public IntentDetectionInput(String query) {
        this.query = query != null ? query : "";
    }

    public static IntentDetectionInput fromMap(Map<String, Object> map) {
        IntentDetectionInput input = new IntentDetectionInput();
        if (map == null) {
            return input;
        }
        if (map.containsKey("query")) {
            Object q = map.get("query");
            input.query = q != null ? q.toString() : "";
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!"query".equals(entry.getKey())) {
                input.extraFields.put(entry.getKey(), entry.getValue());
            }
        }
        return input;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Map<String, Object> getExtraFields() {
        return extraFields;
    }
}
