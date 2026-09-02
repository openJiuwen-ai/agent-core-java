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

import org.junit.jupiter.api.Test;

import java.util.Map;

import javax.sql.DataSource;

class PGVectorStoreFactoryTest {
    @Test
    void createPgVectorStoreRequiresJdbcUrlOrDataSource() {
        assertThrows(BaseError.class,
                () -> VectorStoreFactory.createVectorStore(new VectorStoreConfig(StoreType.PGVECTOR, "kb_chunks")));
    }

    @Test
    void createPgVectorStoreAcceptsJdbcUrlAndDataSourceOptions() {
        assertInstanceOf(PGVectorStore.class,
                VectorStoreFactory.createVectorStore(new VectorStoreConfig(StoreType.PGVECTOR, "kb_chunks"),
                        Map.of("jdbc_url", "jdbc:postgresql://localhost:5432/test_db")));

        assertInstanceOf(PGVectorStore.class,
                VectorStoreFactory.createVectorStore(new VectorStoreConfig(StoreType.PGVECTOR, "kb_chunks"),
                        Map.of("pg_uri", "jdbc:postgresql://localhost:5432/test_db", "user", "postgres", "password",
                                "secret")));

        DataSource dataSource = mock(DataSource.class);
        assertInstanceOf(PGVectorStore.class,
                VectorStoreFactory.createVectorStore(new VectorStoreConfig(StoreType.PGVECTOR, "kb_chunks"),
                        Map.of("dataSource", dataSource, "vector_field", "embedding", "jdbcUrl",
                                "jdbc:postgresql://localhost:5432/test_db")));
    }
}
