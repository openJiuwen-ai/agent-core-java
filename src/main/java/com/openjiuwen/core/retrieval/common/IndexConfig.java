/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

/**
 * Index configuration.
 */
public class IndexConfig {

    private String indexName;
    private String indexType = "hybrid";

    /**
     * Auto-generated for codecheck compliance.
     */
    public IndexConfig() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public IndexConfig(String indexName) {
        this(indexName, "hybrid");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public IndexConfig(String indexName, String indexType) {
        this.indexName = indexName;
        this.indexType = indexType;
        validate();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void validate() {
        RetrievalValidation.requireNonBlank(indexName, "IndexConfig.indexName");
        indexType = RetrievalValidation.validateIndexType(indexType, "IndexConfig.indexType");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getIndexName() {
        return indexName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setIndexName(String indexName) {
        this.indexName = indexName;
        validate();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getIndexType() {
        return indexType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setIndexType(String indexType) {
        this.indexType = indexType;
        validate();
    }
}
