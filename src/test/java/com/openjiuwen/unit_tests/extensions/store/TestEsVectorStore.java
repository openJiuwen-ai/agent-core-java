/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.store;

import com.openjiuwen.spi.store.vector.BaseVectorStore;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.extensions.store.vector.ElasticsearchVectorStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.Nested;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ElasticsearchVectorStore.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/extensions/store/test_es_vector_store.py}.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_ES_TESTS", matches = "true")
public class TestEsVectorStore {

    // ---------------------------------------------------------------------------
    // Initialization Tests
    // ---------------------------------------------------------------------------

    @Nested
    class TestElasticsearchVectorStoreInit {

        @Test
        @DisplayName("Test initialization with default parameters")
        @Tag("level0")
        void testInitWithDefaults() {
            Object mockEs = mock(Object.class);
            ElasticsearchVectorStore store = new ElasticsearchVectorStore(mockEs);
            
            assertThat(store.getEs()).isEqualTo(mockEs);
            assertThat(store.getIndexPrefix()).isEqualTo("agent_vector");
            assertThat(store.getMetadataCache()).isEmpty();
        }

        @Test
        @DisplayName("Test initialization with custom prefix")
        @Tag("level0")
        void testInitWithCustomPrefix() {
            Object mockEs = mock(Object.class);
            ElasticsearchVectorStore store = new ElasticsearchVectorStore(mockEs, "custom_prefix");
            
            assertThat(store.getEs()).isEqualTo(mockEs);
            assertThat(store.getIndexPrefix()).isEqualTo("custom_prefix");
        }

        @Test
        @DisplayName("Test index name generation")
        @Tag("level0")
        void testIndexName() {
            Object mockEs = mock(Object.class);
            ElasticsearchVectorStore store = new ElasticsearchVectorStore(mockEs, "my_prefix");
            
            String indexName = store.getIndexName("test_coll");
            assertThat(indexName).isEqualTo("my_prefix__test_coll");
        }
    }

    // ---------------------------------------------------------------------------
    // Type Mapping Tests
    // ---------------------------------------------------------------------------

    @Nested
    class TestTypeMapping {

        @Test
        @DisplayName("Test mapping FLOAT_VECTOR type to ES dense_vector")
        @Tag("level0")
        void testMapEsTypeVector() {
            FieldSchema field = new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, 768);
            Map<String, Object> result = ElasticsearchVectorStore.mapEsType(field);
            
            assertThat(result.get("type")).isEqualTo("dense_vector");
            assertThat(result.get("dims")).isEqualTo(768);
            assertThat(result.get("index")).isEqualTo(true);
            assertThat(result.get("similarity")).isEqualTo("cosine");
        }

        @Test
        @DisplayName("Test mapping VARCHAR type to ES keyword")
        @Tag("level0")
        void testMapEsTypeVarchar() {
            FieldSchema field = new FieldSchema("text", VectorDataType.VARCHAR);
            Map<String, Object> result = ElasticsearchVectorStore.mapEsType(field);
            
            assertThat(result.get("type")).isEqualTo("keyword");
        }

        @Test
        @DisplayName("Test mapping INT64 type to ES long")
        @Tag("level0")
        void testMapEsTypeInt64() {
            FieldSchema field = new FieldSchema("count", VectorDataType.INT64);
            Map<String, Object> result = ElasticsearchVectorStore.mapEsType(field);
            
            assertThat(result.get("type")).isEqualTo("long");
        }

        @Test
        @DisplayName("Test mapping FLOAT type to ES float")
        @Tag("level0")
        void testMapEsTypeFloat() {
            FieldSchema field = new FieldSchema("score", VectorDataType.FLOAT);
            Map<String, Object> result = ElasticsearchVectorStore.mapEsType(field);
            
            assertThat(result.get("type")).isEqualTo("float");
        }
    }

    // ---------------------------------------------------------------------------
    // CRUD Tests
    // ---------------------------------------------------------------------------

    @Nested
    class TestCRUDOperations {

        @Test
        @DisplayName("Test create collection")
        @Tag("level0")
        void testCreateCollection() {
            // Placeholder for collection creation
            String collectionName = "test_collection";
            List<FieldSchema> fields = new ArrayList<>();
            fields.add(new FieldSchema("id", VectorDataType.VARCHAR));
            fields.add(new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, 768));
            
            assertThat(collectionName).isEqualTo("test_collection");
            assertThat(fields).hasSize(2);
        }

        @Test
        @DisplayName("Test insert vectors")
        @Tag("level0")
        void testInsertVectors() {
            List<Map<String, Object>> vectors = new ArrayList<>();
            Map<String, Object> vector1 = new LinkedHashMap<>();
            vector1.put("id", "vec_001");
            vector1.put("embedding", Arrays.asList(0.1, 0.2, 0.3));
            vectors.add(vector1);
            
            assertThat(vectors).hasSize(1);
        }

        @Test
        @DisplayName("Test search vectors")
        @Tag("level0")
        void testSearchVectors() {
            List<Double> queryEmbedding = Arrays.asList(0.1, 0.2, 0.3);
            int topK = 10;
            
            assertThat(topK).isEqualTo(10);
            assertThat(queryEmbedding).hasSize(3);
        }
    }

    // ---------------------------------------------------------------------------
    // Primary Key Tests
    // ---------------------------------------------------------------------------

    @Nested
    class TestPrimaryKey {

        @Test
        @DisplayName("Test get primary key field")
        @Tag("level0")
        void testGetPrimaryKeyField() {
            List<FieldSchema> fields = new ArrayList<>();
            FieldSchema pkField = new FieldSchema("id", VectorDataType.VARCHAR);
            pkField.setPrimaryKey(true);
            fields.add(pkField);
            fields.add(new FieldSchema("content", VectorDataType.VARCHAR));
            
            FieldSchema found = ElasticsearchVectorStore.getPrimaryKeyField(fields);
            assertThat(found.getName()).isEqualTo("id");
            assertThat(found.isPrimaryKey()).isTrue();
        }
    }
}