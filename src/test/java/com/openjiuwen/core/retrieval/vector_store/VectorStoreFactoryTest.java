/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class VectorStoreFactoryTest {

    @Test
    void createFactoryReturnsCompatibilityStores() {
        assertInstanceOf(
                ChromaVectorStore.class,
                VectorStoreFactory.createVectorStore(new VectorStoreConfig(StoreType.CHROMA, "kb_chunks")));
    }

    @Test
    void createMilvusStoreAcceptsProvidedClient() {
        MilvusClientV2 client = mock(MilvusClientV2.class);

        assertInstanceOf(
                MilvusVectorStore.class,
                VectorStoreFactory.createVectorStore(
                        new VectorStoreConfig(StoreType.MILVUS, "kb_chunks"),
                        Map.of("milvus_client", client)));
    }

    @Test
    void createMilvusStoreRequiresUriOrClient() {
        assertThrows(
                BaseError.class,
                () -> VectorStoreFactory.createVectorStore(new VectorStoreConfig(StoreType.MILVUS, "kb_chunks")));
    }

    @Test
    void createPgVectorStoreRequiresJdbcUrlOrDataSource() {
        assertThrows(
                BaseError.class,
                () -> VectorStoreFactory.createVectorStore(new VectorStoreConfig(StoreType.PGVECTOR, "kb_chunks")));
    }

    @Test
    void createPgVectorStoreAcceptsJdbcUrlAndDataSourceOptions() {
        assertInstanceOf(
                PGVectorStore.class,
                VectorStoreFactory.createVectorStore(
                        new VectorStoreConfig(StoreType.PGVECTOR, "kb_chunks"),
                        Map.of("jdbc_url", "jdbc:postgresql://localhost:5432/test_db")));

        assertInstanceOf(
                PGVectorStore.class,
                VectorStoreFactory.createVectorStore(
                        new VectorStoreConfig(StoreType.PGVECTOR, "kb_chunks"),
                        Map.of(
                                "pg_uri", "jdbc:postgresql://localhost:5432/test_db",
                                "user", "postgres",
                                "password", "secret")));

        DataSource dataSource = mock(DataSource.class);
        assertInstanceOf(
                PGVectorStore.class,
                VectorStoreFactory.createVectorStore(
                        new VectorStoreConfig(StoreType.PGVECTOR, "kb_chunks"),
                        Map.of(
                                "dataSource", dataSource,
                                "vector_field", "embedding",
                                "jdbcUrl", "jdbc:postgresql://localhost:5432/test_db")));
    }
}
