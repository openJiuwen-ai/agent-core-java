/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.store.query.QueryExpr;

import java.util.List;

/**
 * Config for searching memory.
 * <p>
 * Mirrors Python's {@code SearchConfig} in
 * {@code openjiuwen/core/memory/config/graph.py}.
 * </p>
 */
public class SearchConfig extends BaseStrategy {

    @JsonProperty("bfs_k")
    private int bfsK = 3;
    @JsonProperty("bfs_depth")
    private int bfsDepth;
    @JsonProperty("filter_expr")
    private QueryExpr filterExpr;
    @JsonProperty("output_fields")
    private List<String> outputFields;
    private boolean rerank;
    private String language = "en";

    public int getBfsK() {
        return bfsK;
    }

    public void setBfsK(int bfsK) {
        if (bfsK < 1) {
            throw new IllegalArgumentException("bfs_k must be >= 1");
        }
        this.bfsK = bfsK;
    }

    public int getBfsDepth() {
        return bfsDepth;
    }

    public void setBfsDepth(int bfsDepth) {
        if (bfsDepth < 0) {
            throw new IllegalArgumentException("bfs_depth must be >= 0");
        }
        this.bfsDepth = bfsDepth;
    }

    public QueryExpr getFilterExpr() {
        return filterExpr;
    }

    public void setFilterExpr(QueryExpr filterExpr) {
        this.filterExpr = filterExpr;
    }

    public List<String> getOutputFields() {
        return outputFields;
    }

    public void setOutputFields(List<String> outputFields) {
        this.outputFields = outputFields;
    }

    public boolean isRerank() {
        return rerank;
    }

    public void setRerank(boolean rerank) {
        this.rerank = rerank;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        if (!"cn".equals(language) && !"en".equals(language)) {
            throw new IllegalArgumentException("language must be either 'cn' or 'en'");
        }
        this.language = language;
    }
}
