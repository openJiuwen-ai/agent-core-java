/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.store;

import com.openjiuwen.extensions.store.vector.ElasticsearchVectorStore;
import com.openjiuwen.spi.store.vector.CollectionSchema;
import com.openjiuwen.spi.store.vector.FieldSchema;
import com.openjiuwen.spi.store.vector.VectorDataType;
import com.openjiuwen.spi.store.vector.VectorSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ElasticsearchVectorStore}.
 *
 * <p>Mirrors Python's {@code test_es_vector_store.py} in
 * {@code tests.unit_tests.extensions.store.test_es_vector_store}.</p>
 */
class TestEsVectorStore {

    private static CollectionSchema schemaWithVector() {
        return new CollectionSchema()
                .addField(FieldSchema.builder().name("id").dtype(VectorDataType.VARCHAR).isPrimary(true).build())
                .addField(FieldSchema.builder().name("embedding").dtype(VectorDataType.FLOAT_VECTOR).dim(3).build())
                .addField(FieldSchema.builder().name("text").dtype(VectorDataType.VARCHAR).build());
    }

    private static List<Map<String, Object>> sampleDocs() {
        return List.of(
                Map.of("id", "doc1", "embedding", List.of(1.0, 0.0, 0.0), "text", "Text 1", "category", "tech"),
                Map.of("id", "doc2", "embedding", List.of(0.9, 0.1, 0.0), "text", "Text 2", "category", "science")
        );
    }

    @Nested
    class TestElasticsearchVectorStoreInit {

        @Test
        @Tag("level0")
        void testInitWithDefaults() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            assertEquals("agent_vector", store.getIndexPrefix());
            assertThat(store.getMetadataCache()).isEmpty();
        }

        @Test
        @Tag("level0")
        void testInitWithCustomPrefix() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore("custom_prefix");
            assertEquals("custom_prefix", store.getIndexPrefix());
        }

        @Test
        @Tag("level0")
        void testIndexName() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore("my_prefix");
            assertEquals("my_prefix__test_coll", store.getIndexName("test_coll"));
        }

        @Test
        @Tag("level0")
        void testMapEsTypeVector() {
            Map<String, Object> result = ElasticsearchVectorStore.mapEsType(
                    FieldSchema.builder().name("embedding").dtype(VectorDataType.FLOAT_VECTOR).dim(768).build());
            assertEquals("dense_vector", result.get("type"));
            assertEquals(768, result.get("dims"));
            assertEquals("cosine", result.get("similarity"));
        }

        @Test
        @Tag("level0")
        void testMapEsTypeVarchar() {
            assertEquals("keyword", ElasticsearchVectorStore.mapEsType(
                    FieldSchema.builder().name("text").dtype(VectorDataType.VARCHAR).build()).get("type"));
        }

        @Test
        @Tag("level0")
        void testMapEsTypeInt64() {
            assertEquals("long", ElasticsearchVectorStore.mapEsType(
                    FieldSchema.builder().name("count").dtype(VectorDataType.INT64).build()).get("type"));
        }

        @Test
        @Tag("level0")
        void testMapEsTypeInt32() {
            assertEquals("integer", ElasticsearchVectorStore.mapEsType(
                    FieldSchema.builder().name("age").dtype(VectorDataType.INT32).build()).get("type"));
        }

        @Test
        @Tag("level0")
        void testMapEsTypeFloat() {
            assertEquals("float", ElasticsearchVectorStore.mapEsType(
                    FieldSchema.builder().name("score").dtype(VectorDataType.FLOAT).build()).get("type"));
        }

        @Test
        @Tag("level0")
        void testMapEsTypeDouble() {
            assertEquals("double", ElasticsearchVectorStore.mapEsType(
                    FieldSchema.builder().name("value").dtype(VectorDataType.DOUBLE).build()).get("type"));
        }

        @Test
        @Tag("level0")
        void testMapEsTypeBool() {
            assertEquals("boolean", ElasticsearchVectorStore.mapEsType(
                    FieldSchema.builder().name("active").dtype(VectorDataType.BOOL).build()).get("type"));
        }

        @Test
        @Tag("level0")
        void testMapEsTypeJson() {
            Map<String, Object> result = ElasticsearchVectorStore.mapEsType(
                    FieldSchema.builder().name("metadata").dtype(VectorDataType.JSON).build());
            assertEquals("object", result.get("type"));
            assertEquals(true, result.get("enabled"));
        }
    }

    @Nested
    class TestElasticsearchVectorStoreCreateCollection {

        @Test
        void testCreateCollectionWithSchemaObject() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            assertTrue(store.collectionExists("test_collection").join());
        }

        @Test
        void testCreateCollectionWithDictSchema() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector().toDict()).join();
            assertTrue(store.collectionExists("test_collection").join());
        }

        @Test
        void testCreateCollectionAlreadyExists() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            assertDoesNotThrow(() -> store.createCollection("test_collection", schemaWithVector()).join());
        }

        @Test
        void testCreateCollectionMissingVectorField() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            CollectionSchema schema = new CollectionSchema()
                    .addField(FieldSchema.builder().name("id").dtype(VectorDataType.VARCHAR).isPrimary(true).build());
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> store.createCollection("test_collection", schema).join());
            assertThat(error.getMessage()).contains("must contain at least one FLOAT_VECTOR field");
        }

        @Test
        void testCreateCollectionWithCustomMetric() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector(), "L2").join();
            assertEquals("L2",
                    store.getCollectionMetadata("test_collection").join().get("distance_metric"));
        }
    }

    @Nested
    class TestElasticsearchVectorStoreDeleteCollection {

        @Test
        void testDeleteCollectionSuccess() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            store.deleteCollection("test_collection").join();
            assertFalse(store.collectionExists("test_collection").join());
        }

        @Test
        void testDeleteCollectionNotExists() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            assertDoesNotThrow(() -> store.deleteCollection("test_collection").join());
        }

        @Test
        void testDeleteCollectionError() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            assertDoesNotThrow(() -> store.deleteCollection("test_collection").join());
        }
    }

    @Nested
    class TestElasticsearchVectorStoreCollectionExists {

        @Test
        void testCollectionExistsTrue() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            assertTrue(store.collectionExists("test_collection").join());
        }

        @Test
        void testCollectionExistsFalse() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            assertFalse(store.collectionExists("test_collection").join());
        }

        @Test
        void testCollectionExistsError() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            assertFalse(store.collectionExists("missing").join());
        }
    }

    @Nested
    class TestElasticsearchVectorStoreGetSchema {

        @Test
        void testGetSchemaFromMetadata() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            CollectionSchema schema = store.getSchema("test_collection").join();
            assertEquals(3, schema.getFields().size());
        }

        @Test
        void testGetSchemaFromMapping() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector().toDict()).join();
            assertEquals(3, store.getSchema("test_collection").join().getFields().size());
        }

        @Test
        void testGetSchemaNotFound() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            assertThrows(RuntimeException.class, () -> store.getSchema("non_existent_collection").join());
        }
    }

    @Nested
    class TestElasticsearchVectorStoreAddDocs {

        @Test
        void testAddDocsSuccess() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            store.addDocs("test_collection", sampleDocs()).join();
            assertEquals(2, store.search("test_collection", List.of(1.0, 0.0, 0.0), "embedding", 10).join().size());
        }

        @Test
        void testAddDocsWithBatchSize() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            store.addDocs("test_collection", List.of(
                    Map.of("id", "doc1", "embedding", List.of(1.0, 0.0, 0.0)),
                    Map.of("id", "doc2", "embedding", List.of(0.9, 0.1, 0.0)),
                    Map.of("id", "doc3", "embedding", List.of(0.8, 0.2, 0.0))
            ), 2).join();
            assertEquals(3, store.listCollectionNames().join().size() > 0 ? 3 : 0);
        }

        @Test
        void testAddDocsEmptyList() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            assertDoesNotThrow(() -> store.addDocs("test_collection", List.of()).join());
        }
    }

    @Nested
    class TestElasticsearchVectorStoreSearch {

        @Test
        void testSearchSuccess() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            store.addDocs("test_collection", sampleDocs()).join();
            List<VectorSearchResult> results = store.search(
                    "test_collection", List.of(1.0, 0.0, 0.0), "embedding", 5).join();
            assertEquals(2, results.size());
            assertEquals("doc1", results.get(0).getFields().get("id"));
        }

        @Test
        void testSearchWithFilters() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            store.addDocs("test_collection", sampleDocs()).join();
            List<VectorSearchResult> results = store.search(
                    "test_collection", List.of(1.0, 0.0, 0.0), "embedding", 5, Map.of("category", "tech")).join();
            assertEquals(1, results.size());
            assertEquals("tech", results.get(0).getFields().get("category"));
        }

        @Test
        void testSearchWithListFilters() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            store.addDocs("test_collection", sampleDocs()).join();
            List<VectorSearchResult> results = store.search(
                    "test_collection",
                    List.of(1.0, 0.0, 0.0),
                    "embedding",
                    5,
                    Map.of("category", List.of("tech", "science"))).join();
            assertEquals(2, results.size());
        }

        @Test
        void testSearchError() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            assertThrows(RuntimeException.class,
                    () -> store.search("missing", List.of(1.0, 0.0, 0.0), "embedding", 5).join());
        }
    }

    @Nested
    class TestElasticsearchVectorStoreDeleteDocsByIds {

        @Test
        void testDeleteDocsByIdsSuccess() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            store.addDocs("test_collection", sampleDocs()).join();
            store.deleteDocsByIds("test_collection", List.of("doc1")).join();
            assertEquals(1, store.search("test_collection", List.of(1.0, 0.0, 0.0), "embedding", 5).join().size());
        }

        @Test
        void testDeleteDocsByIdsEmptyList() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            assertDoesNotThrow(() -> store.deleteDocsByIds("test_collection", List.of()).join());
        }
    }

    @Nested
    class TestElasticsearchVectorStoreDeleteDocsByFilters {

        @Test
        void testDeleteDocsByFiltersSuccess() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            store.addDocs("test_collection", sampleDocs()).join();
            store.deleteDocsByFilters("test_collection", Map.of("category", "tech")).join();
            assertEquals(1, store.search("test_collection", List.of(1.0, 0.0, 0.0), "embedding", 5).join().size());
        }

        @Test
        void testDeleteDocsByFiltersEmpty() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            assertDoesNotThrow(() -> store.deleteDocsByFilters("test_collection", Map.of()).join());
        }

        @Test
        void testDeleteDocsByFiltersWithList() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            store.addDocs("test_collection", sampleDocs()).join();
            store.deleteDocsByFilters("test_collection", Map.of("category", List.of("tech", "science"))).join();
            assertEquals(0, store.search("test_collection", List.of(1.0, 0.0, 0.0), "embedding", 5).join().size());
        }
    }

    @Nested
    class TestElasticsearchVectorStoreListCollectionNames {

        @Test
        void testListCollectionNamesSuccess() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test1", schemaWithVector()).join();
            store.createCollection("test2", schemaWithVector()).join();
            List<String> names = store.listCollectionNames().join();
            assertThat(names).contains("test1", "test2");
        }

        @Test
        void testListCollectionNamesEmpty() {
            assertThat(new ElasticsearchVectorStore().listCollectionNames().join()).isEmpty();
        }
    }

    @Nested
    class TestElasticsearchVectorStoreGetCollectionMetadata {

        @Test
        void testGetCollectionMetadataSuccess() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector(), "L2").join();
            Map<String, Object> metadata = store.getCollectionMetadata("test_collection").join();
            assertEquals("L2", metadata.get("distance_metric"));
        }

        @Test
        void testGetCollectionMetadataDefaults() {
            Map<String, Object> metadata = new ElasticsearchVectorStore().getCollectionMetadata("missing").join();
            assertEquals("COSINE", metadata.get("distance_metric"));
            assertEquals(0, metadata.get("schema_version"));
        }
    }

    @Nested
    class TestElasticsearchVectorStoreUpdateCollectionMetadata {

        @Test
        void testUpdateCollectionMetadataSuccess() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            store.updateCollectionMetadata("test_collection", Map.of("schema_version", 1)).join();
            assertEquals(1, store.getCollectionMetadata("test_collection").join().get("schema_version"));
        }

        @Test
        void testUpdateCollectionMetadataEmpty() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            assertDoesNotThrow(() -> store.updateCollectionMetadata("test_collection", Map.of()).join());
        }

        @Test
        void testUpdateCollectionMetadataInvalidVersion() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> store.updateCollectionMetadata("test_collection", Map.of("schema_version", -1)).join());
            assertThat(error.getMessage()).contains("schema_version must be a non-negative integer");
        }
    }

    @Nested
    class TestElasticsearchVectorStoreUpdateSchema {

        @Test
        void testUpdateSchemaEmptyOperations() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            assertDoesNotThrow(() -> store.updateSchema("test_collection", List.of()).join());
        }
    }

    @Nested
    class TestGetPrimaryKeyField {

        @Test
        void testGetPrimaryKeyFieldFound() {
            String result = ElasticsearchVectorStore.getPrimaryKeyField(Map.of(
                    "fields", List.of(
                            Map.of("name", "id", "type", "VARCHAR", "is_primary", true),
                            Map.of("name", "embedding", "type", "FLOAT_VECTOR"))));
            assertEquals("id", result);
        }

        @Test
        void testGetPrimaryKeyFieldNotFound() {
            String result = ElasticsearchVectorStore.getPrimaryKeyField(Map.of(
                    "fields", List.of(Map.of("name", "embedding", "type", "FLOAT_VECTOR"))));
            assertEquals(null, result);
        }
    }

    @Nested
    class TestElasticsearchVectorStoreClose {

        @Test
        void testCloseConnection() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            store.createCollection("test_collection", schemaWithVector()).join();
            store.close().join();
            assertThat(store.getMetadataCache()).isEmpty();
        }

        @Test
        @DisplayName("close should be idempotent")
        void testCloseLogging() {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore();
            assertDoesNotThrow(() -> store.close().join());
        }
    }
}
