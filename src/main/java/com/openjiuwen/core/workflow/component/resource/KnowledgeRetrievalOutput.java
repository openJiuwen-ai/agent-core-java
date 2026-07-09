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
 * <p>
 * Mirrors Python's {@code KnowledgeRetrievalOutput} Pydantic model with fields:
 * {@code results}, {@code context}, {@code results_with_metadata}.
 * 
 * @since 0.1.7
 */
public class KnowledgeRetrievalOutput {
    private List<String> results = new ArrayList<>();
    private String context = "";
    private List<Map<String, Object>> resultsWithMetadata;

    /**
     * KnowledgeRetrievalOutput.
     * 
     * @since 0.1.7
     */
    public KnowledgeRetrievalOutput() {
    }

    /**
     * KnowledgeRetrievalOutput.
     * 
     * @param results results
     * @param context context
     * @param resultsWithMetadata resultsWithMetadata
     * @since 0.1.7
     */
    public KnowledgeRetrievalOutput(List<String> results, String context,
            List<Map<String, Object>> resultsWithMetadata) {
        this.results = results != null ? results : new ArrayList<>();
        this.context = context != null ? context : "";
        this.resultsWithMetadata = resultsWithMetadata;
    }

    /**
     * getResults.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getResults() {
        return results;
    }

    /**
     * setResults.
     * 
     * @param results results
     * @since 0.1.7
     */
    public void setResults(List<String> results) {
        this.results = results != null ? results : new ArrayList<>();
    }

    /**
     * getContext.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getContext() {
        return context;
    }

    /**
     * setContext.
     * 
     * @param context context
     * @since 0.1.7
     */
    public void setContext(String context) {
        this.context = context != null ? context : "";
    }

    /**
     * getResultsWithMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Map<String, Object>> getResultsWithMetadata() {
        return resultsWithMetadata;
    }

    /**
     * setResultsWithMetadata.
     * 
     * @param resultsWithMetadata resultsWithMetadata
     * @since 0.1.7
     */
    public void setResultsWithMetadata(List<Map<String, Object>> resultsWithMetadata) {
        this.resultsWithMetadata = resultsWithMetadata;
    }

    /**
     * Convert to a plain map representation.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("results", results);
        map.put("context", context);
        if (resultsWithMetadata != null) {
            map.put("results_with_metadata", resultsWithMetadata);
        }
        return map;
    }

    /**
     * fromMap.
     * 
     * @param map map
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static KnowledgeRetrievalOutput fromMap(Map<String, Object> map) {
        if (map == null) {
            return new KnowledgeRetrievalOutput();
        }
        KnowledgeRetrievalOutput output = new KnowledgeRetrievalOutput();
        Object results = map.get("results");
        if (results instanceof List<?> list) {
            output.results = new ArrayList<>();
            for (Object item : list) {
                output.results.add(item != null ? item.toString() : "");
            }
        }
        Object context = map.get("context");
        if (context instanceof String s) {
            output.context = s;
        }
        Object metadata = map.get("results_with_metadata");
        if (metadata instanceof List<?> metaList) {
            output.resultsWithMetadata = new ArrayList<>();
            for (Object item : metaList) {
                if (item instanceof Map<?, ?> m) {
                    output.resultsWithMetadata.add((Map<String, Object>) m);
                }
            }
        }
        return output;
    }
}
