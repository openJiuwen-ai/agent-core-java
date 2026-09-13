/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

/**
 * Shared config surface that must match between vector store and index manager.
 * 
 * @since 0.1.7
 */
public interface IndexBackendConfig {
    /**
     * getDatabaseName.
     * 
     * @return the result
     * @since 0.1.7
     */
    String getDatabaseName();

    /**
     * getDistanceMetric.
     * 
     * @return the result
     * @since 0.1.7
     */
    String getDistanceMetric();

    /**
     * getIndexType.
     * 
     * @return the result
     * @since 0.1.7
     */
    String getIndexType();

    /**
     * getTextField.
     * 
     * @return the result
     * @since 0.1.7
     */
    String getTextField();

    /**
     * getVectorField.
     * 
     * @return the result
     * @since 0.1.7
     */
    String getVectorField();

    /**
     * getSparseVectorField.
     * 
     * @return the result
     * @since 0.1.7
     */
    String getSparseVectorField();

    /**
     * getMetadataField.
     * 
     * @return the result
     * @since 0.1.7
     */
    String getMetadataField();

    /**
     * getDocIdField.
     * 
     * @return the result
     * @since 0.1.7
     */
    String getDocIdField();
}
