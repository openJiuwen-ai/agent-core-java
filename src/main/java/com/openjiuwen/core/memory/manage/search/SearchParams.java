/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Parameters for memory search operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchParams {
    private String userId;
    private String scopeId;
    private String query;
    @Builder.Default
    private int topK = 5;
    @Builder.Default
    private double threshold = 0.3;
    private String searchType;

    public static SearchParamsBuilder builder() {
        return new SearchParamsBuilder();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public String getSearchType() {
        return searchType;
    }

    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }

    public static final class SearchParamsBuilder {
        private String userId;
        private String scopeId;
        private String query;
        private int topK = 5;
        private double threshold = 0.3;
        private String searchType;

        public SearchParamsBuilder userId(String userId) { this.userId = userId; return this; }
        public SearchParamsBuilder scopeId(String scopeId) { this.scopeId = scopeId; return this; }
        public SearchParamsBuilder query(String query) { this.query = query; return this; }
        public SearchParamsBuilder topK(int topK) { this.topK = topK; return this; }
        public SearchParamsBuilder threshold(double threshold) { this.threshold = threshold; return this; }
        public SearchParamsBuilder searchType(String searchType) { this.searchType = searchType; return this; }

        public SearchParams build() {
            return new SearchParams(userId, scopeId, query, topK, threshold, searchType);
        }
    }
}
