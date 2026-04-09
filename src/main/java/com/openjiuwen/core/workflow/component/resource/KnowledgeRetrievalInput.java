  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.workflow.component.resource;

import lombok.Data;

import java.util.Map;

/**
 * Input model for the Knowledge Retrieval component.
 */
@Data
public class KnowledgeRetrievalInput {
    private String query;

    public static KnowledgeRetrievalInput fromMap(Map<String, Object> inputs) {
        KnowledgeRetrievalInput input = new KnowledgeRetrievalInput();
        if (inputs != null && inputs.containsKey("query")) {
            Object q = inputs.get("query");
            input.setQuery(q != null ? q.toString() : "");
        }
        return input;
    }
}
