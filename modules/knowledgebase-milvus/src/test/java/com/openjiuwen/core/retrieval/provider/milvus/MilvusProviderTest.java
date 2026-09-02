/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.provider.milvus;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.indexing.indexer.MilvusIndexer;
import com.openjiuwen.core.retrieval.provider.IndexerProvider;
import com.openjiuwen.core.retrieval.provider.VectorStoreProvider;
import com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import io.milvus.v2.client.MilvusClientV2;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ServiceLoader;

class MilvusProviderTest {
    @Test
    void serviceLoaderDiscoversMilvusProviders() {
        assertTrue(ServiceLoader.load(VectorStoreProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .anyMatch(provider -> "milvus".equals(provider.storeType())));
        assertTrue(ServiceLoader.load(IndexerProvider.class).findFirst().isPresent());
    }

    @Test
    void providersCreateMilvusStoreAndIndexer() {
        MilvusRetrievalVectorStoreProvider storeProvider = new MilvusRetrievalVectorStoreProvider();
        VectorStore store = storeProvider.create(new VectorStoreConfig("milvus", "kb_chunks"), "vector",
                Map.of("milvus_client", mock(MilvusClientV2.class)));

        assertInstanceOf(MilvusVectorStore.class, store);
        assertInstanceOf(MilvusIndexer.class, new MilvusIndexerProvider().create(store));
    }
}
