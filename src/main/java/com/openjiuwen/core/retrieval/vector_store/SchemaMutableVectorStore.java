/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.spi.store.vector.CollectionSchema;

import java.util.List;
import java.util.Map;

/**
 * Optional extension for vector stores that support schema and collection metadata updates.
 */
public interface SchemaMutableVectorStore extends VectorStore {

    List<String> listCollectionNames();

    Map<String, Object> getCollectionMetadata(String collectionName);

    void updateCollectionMetadata(String collectionName, Map<String, Object> metadata);

    void updateSchema(String collectionName, List<?> operations);

    CollectionSchema getSchema(String collectionName);
}
