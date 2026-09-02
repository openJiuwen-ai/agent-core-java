/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.provider.pgvector;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.provider.VectorStoreProvider;
import com.openjiuwen.core.retrieval.vector_store.PGVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ServiceLoader;

import javax.sql.DataSource;

class PGVectorProviderTest {
    @Test
    void serviceLoaderDiscoversPgVectorProvider() {
        assertTrue(ServiceLoader.load(VectorStoreProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .anyMatch(provider -> "pgvector".equals(provider.storeType())));
    }

    @Test
    void providerCreatesPgVectorStore() {
        VectorStore store = new PGVectorRetrievalStoreProvider().create(
                new VectorStoreConfig("pgvector", "kb_chunks"), "vector",
                Map.of("dataSource", mock(DataSource.class)));

        assertInstanceOf(PGVectorStore.class, store);
    }
}
