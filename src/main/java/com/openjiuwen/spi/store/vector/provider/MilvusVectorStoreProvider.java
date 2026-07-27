/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector.provider;

import com.openjiuwen.core.foundation.store.vector.MilvusVectorStore;
import com.openjiuwen.spi.store.vector.BaseVectorStore;
import com.openjiuwen.spi.store.vector.VectorStoreProvider;

import java.util.Map;

/**
 * Built-in vector store provider for Milvus.
 * <p>
 * Creates vector store instances backed by Milvus, a high-performance
 * open-source vector database designed for scalable similarity search
 * and AI application workloads.
 *
 * @since 0.1.12
 * @see VectorStoreProvider
 * @see com.openjiuwen.core.foundation.store.vector.MilvusVectorStore
 */
public final class MilvusVectorStoreProvider implements VectorStoreProvider {
    /**
     * Returns the Milvus vector store type name.
     *
     * @return the type name "milvus"
     */
    @Override
    public String typeName() {
        return "milvus";
    }

    /**
     * Creates a new Milvus vector store instance.
     *
     * @param conf the configuration map for Milvus connection
     * @return a new MilvusVectorStore instance
     */
    @Override
    public BaseVectorStore create(Map<String, Object> conf) {
        MilvusVectorStore asyncStore = new MilvusVectorStore(conf);
        return BaseVectorStore.fromAsync(asyncStore);
    }
}
