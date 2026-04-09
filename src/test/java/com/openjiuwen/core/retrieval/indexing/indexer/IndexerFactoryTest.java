/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;
import com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class IndexerFactoryTest {

    @Test
    void createIndexerReturnsMilvusIndexerForMilvusStore() {
        MilvusClientV2 client = mock(MilvusClientV2.class);

        Indexer indexer = IndexerFactory.createIndexer(
                new MilvusVectorStore(client, new VectorStoreConfig("milvus", "kb_base"), "vector"));

        assertInstanceOf(MilvusIndexer.class, indexer);
    }

    @Test
    void createIndexerFallsBackToGenericVectorStoreIndexer() {
        Indexer indexer = IndexerFactory.createIndexer(new InMemoryVectorStore("kb_base"));

        assertInstanceOf(InMemoryIndexer.class, indexer);
    }
}
