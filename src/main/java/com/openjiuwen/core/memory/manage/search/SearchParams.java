/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.search;

/**
 * Parameters for memory search operations.
 * <p>
 * Corresponds to Python: manage/search/search_manager.py SearchParams
 */
public class SearchParams {

    private final String userId;
    private final String scopeId;
    private final String query;
    private final int topK;
    private final double threshold;
    private final String searchType;

    private SearchParams(Builder builder) {
        this.userId = builder.userId;
        this.scopeId = builder.scopeId;
        this.query = builder.query;
        this.topK = builder.topK;
        this.threshold = builder.threshold;
        this.searchType = builder.searchType;
    }

    public String getUserId() {
        return userId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getQuery() {
        return query;
    }

    public int getTopK() {
        return topK;
    }

    public double getThreshold() {
        return threshold;
    }

    public String getSearchType() {
        return searchType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String userId;
        private String scopeId;
        private String query;
        private int topK = 5;
        private double threshold = 0.3;
        private String searchType;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder scopeId(String scopeId) {
            this.scopeId = scopeId;
            return this;
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder searchType(String searchType) {
            this.searchType = searchType;
            return this;
        }

        public SearchParams build() {
            return new SearchParams(this);
        }
    }
}

