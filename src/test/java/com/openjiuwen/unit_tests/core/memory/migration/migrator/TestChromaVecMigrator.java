/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.migration.migrator;

import com.openjiuwen.core.memory.manage.mem_model.SemanticStore;
import com.openjiuwen.core.memory.migration.migrator.VectorMigrator;
import com.openjiuwen.core.memory.migration.operation.AddScalarFieldOperation;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.RenameScalarFieldOperation;
import com.openjiuwen.core.memory.migration.operation.UpdateEmbeddingDimensionOperation;
import com.openjiuwen.core.memory.migration.operation.UpdateScalarFieldTypeOperation;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Chroma vector migration.
 *
 * <p>Mirrors Python's {@code test_chroma_vec_migrator.py} from
 * {@code tests/unit_tests/core/memory/migration/migrator/test_chroma_vec_migrator.py}.
 */
class TestChromaVecMigrator {

    private static final String SUMMARY_COLLECTION = "user1_scope1_summary";
    private static final String PROFILE_COLLECTION = "user2_scope2_user_profile";

    private InMemoryVectorStore vectorStore;
    private VectorMigrator migrator;

    @BeforeEach
    void setUp() {
        vectorStore = new InMemoryVectorStore(
                new VectorStoreConfig("chroma", "testdb" + UUID.randomUUID().toString().replace("-", ""), "bootstrap", "cosine"),
                "hybrid"
        );
        migrator = new VectorMigrator(new SemanticStore(vectorStore));
        seedCollection(SUMMARY_COLLECTION, 0);
        seedCollection(PROFILE_COLLECTION, 0);
    }

    @Test
    void testOperationMetadataCreation() {
        OperationMetadata metadata = new OperationMetadata(1, "Add new field");
        assertEquals(1, metadata.getSchemaVersion());
        assertEquals("Add new field", metadata.getDescription());
    }

    @Test
    void testAddScalarFieldOperationCreation() {
        AddScalarFieldOperation operation = new AddScalarFieldOperation(
                new OperationMetadata(1, "Add field"),
                "vector_summary",
                "new_field",
                "VARCHAR",
                "default"
        );
        assertEquals("vector_summary", operation.getDataType());
        assertEquals("new_field", operation.getFieldName());
        assertEquals("VARCHAR", operation.getFieldType());
        assertEquals("default", operation.getDefaultValue());
    }

    @Test
    void testRenameScalarFieldOperationCreation() {
        RenameScalarFieldOperation operation = new RenameScalarFieldOperation(
                new OperationMetadata(2, "Rename field"),
                "vector_summary",
                "old_field",
                "new_field"
        );
        assertEquals("vector_summary", operation.getDataType());
        assertEquals("old_field", operation.getOldFieldName());
        assertEquals("new_field", operation.getNewFieldName());
    }

    @Test
    void testUpdateEmbeddingDimensionCreation() {
        UpdateEmbeddingDimensionOperation operation = new UpdateEmbeddingDimensionOperation(
                new OperationMetadata(3, "Update dimension"),
                "vector_summary",
                "embedding",
                6,
                64
        );
        assertEquals("vector_summary", operation.getDataType());
        assertEquals("embedding", operation.getFieldName());
        assertEquals(6, operation.getNewDimension());
        assertEquals(64, operation.getBatchSize());
    }

    @Test
    void testUpdateScalarFieldTypeOperationCreation() {
        UpdateScalarFieldTypeOperation operation = new UpdateScalarFieldTypeOperation(
                new OperationMetadata(2, "Update count"),
                "vector_summary",
                "count",
                "double"
        );
        assertEquals("vector_summary", operation.getDataType());
        assertEquals("count", operation.getFieldName());
        assertEquals("double", operation.getNewFieldType());
    }

    @Test
    void testTryMigrateSameVersionMultipleOperations() {
        vectorStore.updateCollectionMetadata(SUMMARY_COLLECTION, Map.of("schema_version", 1));

        List<BaseOperation> operations = List.of(
                new AddScalarFieldOperation(new OperationMetadata(2, "Add category"),
                        "vector_summary", "category", "varchar", "general"),
                new AddScalarFieldOperation(new OperationMetadata(2, "Add author"),
                        "vector_summary", "author", "varchar", "unknown"),
                new RenameScalarFieldOperation(new OperationMetadata(2, "Rename count"),
                        "vector_summary", "count", "view_count")
        );

        assertTrue(migrator.tryMigrate("vector_summary", operations));

        Map<String, Object> metadata = metadataFor(SUMMARY_COLLECTION, "doc_1");
        assertEquals("general", metadata.get("category"));
        assertEquals("unknown", metadata.get("author"));
        assertEquals(1, metadata.get("view_count"));
        assertFalse(metadata.containsKey("count"));
        assertEquals(2, collectionVersion(SUMMARY_COLLECTION));
        assertEquals(0, collectionVersion(PROFILE_COLLECTION));
    }

    @Test
    void testTryMigrateMultiVersionMultiOperations() {
        List<BaseOperation> operations = new ArrayList<>();
        operations.add(new AddScalarFieldOperation(new OperationMetadata(1, "Add version1_field1"),
                "vector_summary", "version1_field1", "varchar", "v1_f1"));
        operations.add(new AddScalarFieldOperation(new OperationMetadata(1, "Add version1_field2"),
                "vector_summary", "version1_field2", "int32", 1));
        operations.add(new RenameScalarFieldOperation(new OperationMetadata(2, "Rename count"),
                "vector_summary", "count", "view_count"));
        operations.add(new AddScalarFieldOperation(new OperationMetadata(2, "Add version2_field"),
                "vector_summary", "version2_field", "double", 2.0d));
        operations.add(new UpdateScalarFieldTypeOperation(new OperationMetadata(2, "Update type"),
                "vector_summary", "version1_field2", "int64"));
        operations.add(new UpdateEmbeddingDimensionOperation(new OperationMetadata(3, "Expand embedding"),
                "vector_summary", "embedding", 6, 64));
        operations.add(new AddScalarFieldOperation(new OperationMetadata(3, "Add version3_field"),
                "vector_summary", "version3_field", "bool", true));

        assertTrue(migrator.tryMigrate("vector_summary", operations));

        Map<String, Object> metadata = metadataFor(SUMMARY_COLLECTION, "doc_1");
        assertEquals("v1_f1", metadata.get("version1_field1"));
        assertInstanceOf(Long.class, metadata.get("version1_field2"));
        assertEquals(1L, metadata.get("version1_field2"));
        assertEquals(1, metadata.get("view_count"));
        assertEquals(2.0d, metadata.get("version2_field"));
        assertEquals(Boolean.TRUE, metadata.get("version3_field"));
        assertEquals(6, vectorFor(SUMMARY_COLLECTION, "doc_1").size());
        assertEquals(3, collectionVersion(SUMMARY_COLLECTION));
    }

    @Test
    void testTryMigrateUpdateFieldTypeNormal() {
        assertTrue(migrator.tryMigrate("vector_summary", List.of(
                new UpdateScalarFieldTypeOperation(new OperationMetadata(1, "Update count"),
                        "vector_summary", "count", "double")
        )));

        Object value = metadataFor(SUMMARY_COLLECTION, "doc_1").get("count");
        assertInstanceOf(Double.class, value);
        assertEquals(1.0d, value);
    }

    @Test
    void testTryMigrateUpdateEmbeddingDimensionExpansion() {
        assertTrue(migrator.tryMigrate("vector_summary", List.of(
                new UpdateEmbeddingDimensionOperation(new OperationMetadata(1, "Expand embedding"),
                        "vector_summary", "embedding", 8, 32)
        )));

        List<Float> vector = vectorFor(SUMMARY_COLLECTION, "doc_1");
        assertEquals(8, vector.size());
        assertEquals(List.of(0.1f, 0.2f, 0.3f, 0.4f, 0.0f, 0.0f, 0.0f, 0.0f), vector);
    }

    @Test
    void testTryMigrateExistingVersionSkipsLowerVersionOperations() {
        vectorStore.updateCollectionMetadata(SUMMARY_COLLECTION, Map.of("schema_version", 2));

        assertTrue(migrator.tryMigrate("vector_summary", List.of(
                new AddScalarFieldOperation(new OperationMetadata(1, "Skipped op"),
                        "vector_summary", "skipped_field", "varchar", "skip"),
                new AddScalarFieldOperation(new OperationMetadata(3, "Apply op"),
                        "vector_summary", "applied_field", "varchar", "ok")
        )));

        Map<String, Object> metadata = metadataFor(SUMMARY_COLLECTION, "doc_1");
        assertFalse(metadata.containsKey("skipped_field"));
        assertEquals("ok", metadata.get("applied_field"));
        assertEquals(3, collectionVersion(SUMMARY_COLLECTION));
    }

    @Test
    void testTryMigrateEmptyOperationsReturnsTrue() {
        vectorStore.updateCollectionMetadata(SUMMARY_COLLECTION, Map.of("schema_version", 4));
        assertTrue(migrator.tryMigrate("vector_summary", List.of()));
        assertEquals(4, collectionVersion(SUMMARY_COLLECTION));
    }

    @Test
    void testTryMigrateNullOperationsReturnsTrue() {
        vectorStore.updateCollectionMetadata(SUMMARY_COLLECTION, Map.of("schema_version", 5));
        assertTrue(migrator.tryMigrate("vector_summary", null));
        assertEquals(5, collectionVersion(SUMMARY_COLLECTION));
    }

    @Test
    void testTryMigrateUnsupportedMemoryTypeReturnsFalse() {
        assertFalse(migrator.tryMigrate("vector_unknown", List.of(
                new AddScalarFieldOperation(new OperationMetadata(1, "noop"),
                        "vector_unknown", "x", "varchar", "y")
        )));
    }

    @Test
    void testTryMigrateNoMatchingCollectionsReturnsTrue() {
        vectorStore.deleteTable(SUMMARY_COLLECTION);
        assertTrue(migrator.tryMigrate("vector_summary", List.of(
                new AddScalarFieldOperation(new OperationMetadata(1, "noop"),
                        "vector_summary", "field", "varchar", "value")
        )));
    }

    @Test
    void testTryMigrateOnlyTargetedMemoryTypeCollection() {
        assertTrue(migrator.tryMigrate("vector_user_profile", List.of(
                new AddScalarFieldOperation(new OperationMetadata(1, "Add profile field"),
                        "vector_user_profile", "profile_only", "varchar", "profile")
        )));

        assertEquals("profile", metadataFor(PROFILE_COLLECTION, "doc_1").get("profile_only"));
        assertFalse(metadataFor(SUMMARY_COLLECTION, "doc_1").containsKey("profile_only"));
        assertEquals(1, collectionVersion(PROFILE_COLLECTION));
        assertEquals(0, collectionVersion(SUMMARY_COLLECTION));
    }

    private void seedCollection(String collectionName, int schemaVersion) {
        InMemoryVectorStore scoped = (InMemoryVectorStore) vectorStore.withCollection(collectionName);
        List<Map<String, Object>> docs = List.of(
                doc("doc_1", "First document", List.of(0.1f, 0.2f, 0.3f, 0.4f), 1),
                doc("doc_2", "Second document", List.of(0.5f, 0.6f, 0.7f, 0.8f), 2)
        );
        scoped.add(docs, null, null);
        vectorStore.updateCollectionMetadata(collectionName, Map.of("schema_version", schemaVersion));
    }

    private Map<String, Object> doc(String id, String text, List<Float> vector, int count) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("count", count);
        metadata.put("source", id);

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", id);
        doc.put("text", text);
        doc.put("vector", vector);
        doc.put("count", count);
        doc.put("metadata", metadata);
        return doc;
    }

    private int collectionVersion(String collectionName) {
        Object value = vectorStore.getCollectionMetadata(collectionName).get("schema_version");
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private Map<String, Object> metadataFor(String collectionName, String id) {
        InMemoryVectorStore scoped = (InMemoryVectorStore) vectorStore.withCollection(collectionName);
        for (SearchResult result : scoped.queryByFilters(Map.of(), 10)) {
            if (id.equals(result.getId())) {
                return result.getMetadata();
            }
        }
        throw new IllegalStateException("Missing document: " + id);
    }

    @SuppressWarnings("unchecked")
    private List<Float> vectorFor(String collectionName, String id) {
        try {
            InMemoryVectorStore scoped = (InMemoryVectorStore) vectorStore.withCollection(collectionName);
            Method currentCollection = InMemoryVectorStore.class.getDeclaredMethod("currentCollection");
            currentCollection.setAccessible(true);
            Map<String, Object> records = (Map<String, Object>) currentCollection.invoke(scoped);
            Object record = records.get(id);
            assertNotNull(record);
            Method vectorMethod = record.getClass().getDeclaredMethod("vector");
            vectorMethod.setAccessible(true);
            return (List<Float>) vectorMethod.invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
