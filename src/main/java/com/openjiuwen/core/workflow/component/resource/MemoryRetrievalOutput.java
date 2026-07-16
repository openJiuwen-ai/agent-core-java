/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.memory.MemResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Output model for the Memory Retrieval component.
 * <p>
 * Mirrors Python's {@code MemoryRetrievalOutput}.
 * 
 * @since 0.1.7
 */
public class MemoryRetrievalOutput {
    private List<MemResult> fragmentMemoryResults = new ArrayList<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<MemResult> summaryResults = new ArrayList<>();

    /**
     * Default constructor.
     * 
     * @since 0.1.7
     */
    public MemoryRetrievalOutput() {
    }

    /**
     * Constructor with parameters.
     * 
     * @param fragmentMemoryResults the fragment memory results
     * @param summaryResults the summary results
     * @since 0.1.7
     */
    public MemoryRetrievalOutput(List<MemResult> fragmentMemoryResults, List<MemResult> summaryResults) {
        this.fragmentMemoryResults = fragmentMemoryResults != null ? fragmentMemoryResults : new ArrayList<>();
        this.summaryResults = summaryResults != null ? summaryResults : new ArrayList<>();
    }

    /**
     * Get fragment memory results.
     * 
     * @return the fragment memory results
     * @since 0.1.7
     */
    public List<MemResult> getFragmentMemoryResults() {
        return fragmentMemoryResults;
    }

    /**
     * Set fragment memory results.
     * 
     * @param fragmentMemoryResults the fragment memory results
     * @since 0.1.7
     */
    public void setFragmentMemoryResults(List<MemResult> fragmentMemoryResults) {
        this.fragmentMemoryResults = fragmentMemoryResults != null ? fragmentMemoryResults : new ArrayList<>();
    }

    /**
     * Get summary results.
     * 
     * @return the summary results
     * @since 0.1.7
     */
    public List<MemResult> getSummaryResults() {
        return summaryResults;
    }

    /**
     * Set summary results.
     * 
     * @param summaryResults the summary results
     * @since 0.1.7
     */
    public void setSummaryResults(List<MemResult> summaryResults) {
        this.summaryResults = summaryResults != null ? summaryResults : new ArrayList<>();
    }

    /**
     * Convert to a plain map representation.
     * 
     * @return the map representation
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fragment_memory_results", fragmentMemoryResults);
        map.put("summary_results", summaryResults);
        return map;
    }
}
