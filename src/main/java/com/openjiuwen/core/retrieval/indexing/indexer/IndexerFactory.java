/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

/**
 * Factory for pairing a vector store with its index manager implementation.
 */
public final class IndexerFactory {

    private IndexerFactory() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Indexer createIndexer(VectorStore vectorStore) {
        if (vectorStore == null) {
            throw RetrievalExceptions.validation("VectorStore is required");
        }
        if (vectorStore instanceof MilvusVectorStore milvusVectorStore) {
            VectorStoreConfig config = VectorStoreConfig.builder()
                    .storeProvider(com.openjiuwen.core.retrieval.common.StoreType.MILVUS)
                    .collectionName(milvusVectorStore.getCollectionName() != null
                            ? milvusVectorStore.getCollectionName() : "default")
                    .databaseName(milvusVectorStore.getDatabaseName() != null
                            ? milvusVectorStore.getDatabaseName() : "")
                    .distanceMetric(milvusVectorStore.getDistanceMetric() != null
                            ? milvusVectorStore.getDistanceMetric() : "cosine")
                    .build();
            return new MilvusIndexer(config, milvusVectorStore.getMilvusUri(), milvusVectorStore.getMilvusToken());
        }
        return new InMemoryIndexer(vectorStore);
    }
}
