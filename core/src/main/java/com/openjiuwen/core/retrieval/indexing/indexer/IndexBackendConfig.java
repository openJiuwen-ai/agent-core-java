/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

/**
 * Shared config surface that must match between vector store and index manager.
 *
 * <p>Mirrors Python's retrieval backend config attributes in
 * {@code openjiuwen/core/retrieval/knowledge_base.py}.</p>
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
