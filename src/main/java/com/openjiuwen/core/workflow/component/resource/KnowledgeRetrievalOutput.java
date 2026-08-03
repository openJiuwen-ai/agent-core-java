/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Output model for the Knowledge Retrieval component.
 *
 * <p>Mirrors Python's {@code KnowledgeRetrievalOutput} in
 * {@code openjiuwen/core/workflow/components/resource/knowledge_retrieval_comp.py}.</p>
 */
public class KnowledgeRetrievalOutput {

    private List<String> results = new ArrayList<>();
    private String context = "";

    public KnowledgeRetrievalOutput() {
    }

    public KnowledgeRetrievalOutput(List<String> results, String context) {
        setResults(results);
        setContext(context);
    }

    public List<String> getResults() {
        return results;
    }

    public void setResults(List<String> results) {
        this.results = results == null ? new ArrayList<>() : new ArrayList<>(results);
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context == null ? "" : context;
    }

    /**
     * Convert to the same plain dictionary shape as Python's {@code model_dump()}.
     *
     * @return output map with {@code results} and {@code context}
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("results", new ArrayList<>(results));
        map.put("context", context);
        return map;
    }

    /**
     * Create from a map representation.
     *
     * @param map source map
     * @return parsed output model
     */
    public static KnowledgeRetrievalOutput fromMap(Map<String, Object> map) {
        if (map == null) {
            return new KnowledgeRetrievalOutput();
        }
        KnowledgeRetrievalOutput output = new KnowledgeRetrievalOutput();
        Object resultsValue = map.get("results");
        if (resultsValue instanceof List<?> list) {
            List<String> parsed = new ArrayList<>();
            for (Object item : list) {
                parsed.add(item == null ? "" : item.toString());
            }
            output.setResults(parsed);
        }
        Object contextValue = map.get("context");
        if (contextValue instanceof String text) {
            output.setContext(text);
        }
        return output;
    }
}
