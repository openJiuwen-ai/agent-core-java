/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.List;
import java.util.Map;

/**
 * Public class SearchToolsTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class SearchToolsTool {
    private final SearchHandler handler;

    /**
 * Public interface SearchHandler used by the Java parity implementation.
 *
 * @since 1.0
 */
    @FunctionalInterface
public interface SearchHandler {
        List<Map<String, Object>> search(String query, int limit, int detailLevel) throws Exception;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SearchToolsTool(SearchHandler handler) {
        this.handler = handler;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolOutput invoke(String query, Integer limit, Integer detailLevel) {
        if (query == null || query.isBlank()) {
            return ToolOutput.builder().success(false).error("query is required").build();
        }
        try {
            int normalizedLimit = Math.max(1, Math.min(limit != null ? limit : 10, 20));
            int normalizedDetail = detailLevel != null ? detailLevel : 1;
            List<Map<String, Object>> matches = handler.search(query, normalizedLimit, normalizedDetail);
            return ToolOutput.builder()
                    .success(true)
                    .data(Map.of(
                            "query", query,
                            "matches", matches,
                            "count", matches.size()
                    ))
                    .build();
        } catch (Exception ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }
}
