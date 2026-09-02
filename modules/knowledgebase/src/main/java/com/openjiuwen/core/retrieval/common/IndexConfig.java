/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

/**
 * Index configuration.
 * 
 * @since 0.1.7
 */
public class IndexConfig {
    private String indexName;
    private String indexType = "hybrid";

    /**
     * IndexConfig.
     * 
     * @since 0.1.7
     */
    public IndexConfig() {
    }

    /**
     * IndexConfig.
     * 
     * @param indexName indexName
     * @since 0.1.7
     */
    public IndexConfig(String indexName) {
        this(indexName, "hybrid");
    }

    /**
     * IndexConfig.
     * 
     * @param indexName indexName
     * @param indexType indexType
     * @since 0.1.7
     */
    public IndexConfig(String indexName, String indexType) {
        this.indexName = indexName;
        this.indexType = indexType;
        validate();
    }

    /**
     * validate.
     * 
     * @since 0.1.7
     */
    public void validate() {
        RetrievalValidation.requireNonBlank(indexName, "IndexConfig.indexName");
        indexType = RetrievalValidation.validateIndexType(indexType, "IndexConfig.indexType");
    }

    /**
     * getIndexName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getIndexName() {
        return indexName;
    }

    /**
     * setIndexName.
     * 
     * @param indexName indexName
     * @since 0.1.7
     */
    public void setIndexName(String indexName) {
        this.indexName = indexName;
        validate();
    }

    /**
     * getIndexType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getIndexType() {
        return indexType;
    }

    /**
     * setIndexType.
     * 
     * @param indexType indexType
     * @since 0.1.7
     */
    public void setIndexType(String indexType) {
        this.indexType = indexType;
        validate();
    }
}
