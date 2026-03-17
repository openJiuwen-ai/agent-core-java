/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.indexer;

/**
 * Shared config surface that must match between vector store and index manager.
 */
public interface IndexBackendConfig {

    String getDatabaseName();

    String getDistanceMetric();

    String getIndexType();

    String getTextField();

    String getVectorField();

    String getSparseVectorField();

    String getMetadataField();

    String getDocIdField();
}
