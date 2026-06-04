/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.vector_store.ChromaVectorStore;
import com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore;
import com.openjiuwen.core.retrieval.vector_store.PGVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStoreFactory;

import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Vector store factory test cases.
 *
 * <p>Mirrors Python's {@code test_store.py} in
 * {@code tests.unit_tests.core.retrieval.vector_store.test_store}.</p>
 */
@DisplayName("VectorStore Tests")
class TestStore {

    @Nested
    @DisplayName("Create Vector Store")
    class CreateVectorStoreTests {

        @Test
        @DisplayName("test_create_milvus_store - returns MilvusVectorStore")
        void testCreateMilvusStore() {
            VectorStore store = VectorStoreFactory.createVectorStore(
                    milvusConfig(),
                    Map.of("milvusClient", mock(MilvusClientV2.class)));

            assertThat(store).isInstanceOf(MilvusVectorStore.class);
        }

        @Test
        @DisplayName("test_create_chroma_store - returns ChromaVectorStore")
        void testCreateChromaStore() {
            VectorStore store = VectorStoreFactory.createVectorStore(chromaConfig());

            assertThat(store).isInstanceOf(ChromaVectorStore.class);
        }

        @Test
        @DisplayName("test_create_pgvector_store - returns PGVectorStore")
        void testCreatePgvectorStore() {
            VectorStore store = VectorStoreFactory.createVectorStore(
                    pgvectorConfig(),
                    Map.of("dataSource", mock(DataSource.class)));

            assertThat(store).isInstanceOf(PGVectorStore.class);
        }

        @Test
        @DisplayName("test_create_milvus_store_by_string_provider - accepts provider string")
        void testCreateMilvusStoreByStringProvider() {
            VectorStoreConfig config = new VectorStoreConfig("milvus", "test_collection");

            VectorStore store = VectorStoreFactory.createVectorStore(
                    config,
                    Map.of("milvus_client", mock(MilvusClientV2.class)));

            assertThat(store).isInstanceOf(MilvusVectorStore.class);
        }

        @Test
        @DisplayName("test_create_vector_store_invalid_provider - rejects unsupported provider")
        void testCreateVectorStoreInvalidProvider() throws Exception {
            VectorStoreConfig config = new VectorStoreConfig("milvus", "test_collection");
            Field provider = VectorStoreConfig.class.getDeclaredField("storeProvider");
            provider.setAccessible(true);
            provider.set(config, "unsupported_provider");

            assertThatThrownBy(() -> VectorStoreFactory.createVectorStore(config))
                    .isInstanceOf(BaseError.class);
        }

        @Test
        @DisplayName("test_create_vector_store_passes_kwargs_to_store - forwards options")
        void testCreateVectorStorePassesKwargsToStore() {
            VectorStore store = VectorStoreFactory.createVectorStore(
                    pgvectorConfig(),
                    Map.of("dataSource", mock(DataSource.class), "vector_field", "custom_vector"));

            assertThat(store).isInstanceOf(PGVectorStore.class);
            assertThat(store.getVectorField()).isEqualTo("custom_vector");
        }

        @Test
        @DisplayName("test_create_milvus_store_collection_name_preserved - keeps config collection")
        void testCreateMilvusStoreCollectionNamePreserved() {
            VectorStore store = VectorStoreFactory.createVectorStore(
                    milvusConfig(),
                    Map.of("client", mock(MilvusClientV2.class)));

            assertThat(store.getCollectionName()).isEqualTo("test_collection");
        }
    }

    private VectorStoreConfig milvusConfig() {
        return new VectorStoreConfig("milvus", "test_collection");
    }

    private VectorStoreConfig chromaConfig() {
        return new VectorStoreConfig("chroma", "test_collection");
    }

    private VectorStoreConfig pgvectorConfig() {
        return new VectorStoreConfig("pgvector", "test_collection");
    }
}
