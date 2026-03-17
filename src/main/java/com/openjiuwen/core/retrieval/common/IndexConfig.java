/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.common;

/**
 * Index configuration.
 */
public class IndexConfig {

    private String indexName;
    private String indexType = "hybrid";

    public IndexConfig() {
    }

    public IndexConfig(String indexName) {
        this(indexName, "hybrid");
    }

    public IndexConfig(String indexName, String indexType) {
        this.indexName = indexName;
        this.indexType = indexType;
        validate();
    }

    public void validate() {
        RetrievalValidation.requireNonBlank(indexName, "IndexConfig.indexName");
        indexType = RetrievalValidation.validateIndexType(indexType, "IndexConfig.indexType");
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
        validate();
    }

    public String getIndexType() {
        return indexType;
    }

    public void setIndexType(String indexType) {
        this.indexType = indexType;
        validate();
    }
}
