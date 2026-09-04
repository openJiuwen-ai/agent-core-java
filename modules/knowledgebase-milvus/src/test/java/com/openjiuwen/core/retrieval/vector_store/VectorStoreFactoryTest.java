/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

import io.milvus.v2.client.MilvusClientV2;

import org.junit.jupiter.api.Test;

import java.util.Map;

/** Tests vector store factory compatibility behavior. */
class VectorStoreFactoryTest {
    @Test
    void createFactoryReturnsCompatibilityStores() {
        assertInstanceOf(ChromaVectorStore.class,
                VectorStoreFactory.createVectorStore(new VectorStoreConfig(StoreType.CHROMA, "kb_chunks")));
    }

    @Test
    void createMilvusStoreAcceptsProvidedClient() {
        MilvusClientV2 client = mock(MilvusClientV2.class);

        assertInstanceOf(MilvusVectorStore.class, VectorStoreFactory.createVectorStore(
                new VectorStoreConfig(StoreType.MILVUS, "kb_chunks"), Map.of("milvus_client", client)));
    }

    @Test
    void createMilvusStoreRequiresUriOrClient() {
        assertThrows(BaseError.class,
                () -> VectorStoreFactory.createVectorStore(new VectorStoreConfig(StoreType.MILVUS, "kb_chunks")));
    }

}
