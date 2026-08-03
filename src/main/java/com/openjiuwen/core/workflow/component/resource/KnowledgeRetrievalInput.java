/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Input model for the Knowledge Retrieval component.
 *
 * <p>Mirrors Python's {@code KnowledgeRetrievalInput} in
 * {@code openjiuwen/core/workflow/components/resource/knowledge_retrieval_comp.py}.</p>
 */
@Data
public class KnowledgeRetrievalInput {
    private String query;
    private Map<String, Object> extraFields = new LinkedHashMap<>();

    public static KnowledgeRetrievalInput fromMap(Map<String, Object> inputs) {
        KnowledgeRetrievalInput input = new KnowledgeRetrievalInput();
        if (inputs == null) {
            return input;
        }
        if (inputs.containsKey("query")) {
            Object q = inputs.get("query");
            input.setQuery(q != null ? q.toString() : "");
        }
        inputs.forEach((key, value) -> {
            if (!"query".equals(key)) {
                input.extraFields.put(key, value);
            }
        });
        return input;
    }
}
