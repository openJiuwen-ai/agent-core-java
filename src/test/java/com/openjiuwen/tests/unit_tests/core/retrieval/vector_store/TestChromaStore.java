/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.vector_store.ChromaVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ChromaDB vector store test cases.
 *
 * <p>Mirrors Python's {@code test_chroma_store.py} in
 * {@code tests.unit_tests.core.retrieval.vector_store.test_chroma_store}.</p>
 */
@DisplayName("ChromaVectorStore Tests")
class TestChromaStore {

    @Nested
    @DisplayName("Initialization")
    class InitTests {

        @Test
        @DisplayName("test_init_success - initializes local Chroma-compatible store")
        void testInitSuccess() {
            ChromaVectorStore store = new ChromaVectorStore(vectorStoreConfig());

            assertThat(store.getCollectionName()).isEqualTo("test_collection");
            assertThat(store.getDatabaseName()).isEmpty();
            assertThat(store.getDistanceMetric()).isEqualTo("cosine");
        }
    }

    @Nested
    @DisplayName("Check Vector Field")
    class CheckVectorFieldTests {

        @Test
        @DisplayName("test_check_vector_field_success_matching_config - matching config passes")
        void testCheckVectorFieldSuccessMatchingConfig() {
            Map<String, Object> configured = Map.of(
                    "space", "cosine",
                    "max_neighbors", 16,
                    "ef_construction", 200);
            Map<String, Object> actual = Map.of(
                    "space", "cosine",
                    "max_neighbors", 16,
                    "ef_construction", 200,
                    "ef_search", 100);

            assertThatCode(() -> VectorStore.checkConfigsMatching(configured, actual))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("test_check_vector_field_config_mismatch - max_neighbors mismatch raises")
        void testCheckVectorFieldConfigMismatch() {
            Map<String, Object> configured = Map.of(
                    "space", "cosine",
                    "max_neighbors", 16,
                    "ef_construction", 200);
            Map<String, Object> actual = Map.of(
                    "space", "cosine",
                    "max_neighbors", 32,
                    "ef_construction", 200);

            assertThatThrownBy(() -> VectorStore.checkConfigsMatching(configured, actual))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("database actual config differs from current knowledge base");
        }

        @Test
        @DisplayName("test_check_vector_field_distance_metric_mismatch - distance mismatch raises")
        void testCheckVectorFieldDistanceMetricMismatch() {
            Map<String, Object> configured = Map.of("space", "cosine", "max_neighbors", 16);
            Map<String, Object> actual = Map.of("space", "l2", "max_neighbors", 16);

            assertThatThrownBy(() -> VectorStore.checkConfigsMatching(configured, actual))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("database actual config differs from current knowledge base");
        }

        @Test
        @DisplayName("test_check_vector_field_ignores_ef_search_factor - ignores efSearchFactor")
        void testCheckVectorFieldIgnoresEfSearchFactor() {
            Map<String, Object> configured = Map.of(
                    "space", "cosine",
                    "max_neighbors", 16,
                    "ef_construction", 200,
                    "efSearchFactor", 2.0);
            Map<String, Object> actual = Map.of(
                    "space", "cosine",
                    "max_neighbors", 16,
                    "ef_construction", 200,
                    "efSearchFactor", 9.0);

            assertThatCode(() -> VectorStore.checkConfigsMatching(configured, actual))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("test_check_vector_field_empty_hnsw_config - empty config raises")
        void testCheckVectorFieldEmptyHnswConfig() {
            Map<String, Object> configured = Map.of("space", "cosine", "max_neighbors", 16);

            assertThatThrownBy(() -> VectorStore.checkConfigsMatching(configured, Map.of()))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("database actual config differs from current knowledge base");
        }

        @Test
        @DisplayName("test_check_vector_field_partial_match - reports partial mismatches")
        void testCheckVectorFieldPartialMatch() {
            Map<String, Object> configured = Map.of(
                    "space", "cosine",
                    "max_neighbors", 16,
                    "ef_construction", 200);
            Map<String, Object> actual = Map.of(
                    "space", "cosine",
                    "max_neighbors", 16,
                    "ef_construction", 300);

            assertThatThrownBy(() -> VectorStore.checkConfigsMatching(configured, actual))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("matches")
                    .hasMessageContaining("mismatches");
        }
    }

    private VectorStoreConfig vectorStoreConfig() {
        return new VectorStoreConfig("chroma", "", "test_collection", "cosine");
    }
}
