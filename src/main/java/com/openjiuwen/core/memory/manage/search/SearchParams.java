/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.search;

import java.util.List;

/**
 * Search request parameters.
 *
 * <p>Mirrors Python's {@code SearchParams} in
 * {@code openjiuwen/core/memory/manage/search/search_manager.py}.</p>
 */
public class SearchParams {
    private String userId;
    private String scopeId;
    private String query;
    private int topK = 5;
    private double threshold = 0.3d;
    private List<String> searchType;

    public SearchParams() {
    }

    public SearchParams(String userId, String scopeId, String query) {
        this.userId = userId;
        this.scopeId = scopeId;
        this.query = query;
    }

    public SearchParams(String userId,
                        String scopeId,
                        String query,
                        int topK,
                        double threshold,
                        List<String> searchType) {
        this.userId = userId;
        this.scopeId = scopeId;
        this.query = query;
        this.topK = topK;
        this.threshold = threshold;
        this.searchType = searchType;
    }

    public static Builder builder() {
        return new Builder();
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

    public List<String> getSearchType() {
        return searchType;
    }

    public void setSearchType(List<String> searchType) {
        this.searchType = searchType;
    }

    /**
     * Builder for {@link SearchParams}.
     *
     * <p>Mirrors Python's {@code SearchParams} pydantic model in
     * {@code openjiuwen/core/memory/manage/search/search_manager.py}.</p>
     */
    public static final class Builder {
        private String userId;
        private String scopeId;
        private String query;
        private int topK = 5;
        private double threshold = 0.3d;
        private List<String> searchType;

        private Builder() {
        }

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

        public Builder searchType(List<String> searchType) {
            this.searchType = searchType;
            return this;
        }

        public SearchParams build() {
            return new SearchParams(userId, scopeId, query, topK, threshold, searchType);
        }
    }
}
