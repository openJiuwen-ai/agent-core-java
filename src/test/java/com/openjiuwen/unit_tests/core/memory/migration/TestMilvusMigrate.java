/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.migration;

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
 * Unit tests for vector migration flow aligned with Milvus migration scenarios.
 *
 * <p>Mirrors Python's {@code test_milvus_migrate.py} from
 * {@code tests/unit_tests/core/memory/migration/test_milvus_migrate.py}.
 */
class TestMilvusMigrate {

    private static final String SUMMARY_COLLECTION = "test_user_scope_summary";

    private InMemoryVectorStore vectorStore;
    private VectorMigrator migrator;

    @BeforeEach
    void setUp() {
        vectorStore = new InMemoryVectorStore(
                new VectorStoreConfig("chroma", "testdb" + UUID.randomUUID().toString().replace("-", ""), "milvus-bootstrap", "cosine"),
                "hybrid"
        );
        migrator = new VectorMigrator(new SemanticStore(vectorStore));
        seedSummaryCollection(SUMMARY_COLLECTION, 0);
    }

    @Test
    void testAddScalarFieldOperationCreation() {
        AddScalarFieldOperation operation = new AddScalarFieldOperation(
                new OperationMetadata(1, "Add field"),
                "vector_summary",
                "added_field",
                "string",
                "default"
        );
        assertEquals("added_field", operation.getFieldName());
        assertEquals("string", operation.getFieldType());
        assertEquals("default", operation.getDefaultValue());
    }

    @Test
    void testRenameScalarFieldOperationCreation() {
        RenameScalarFieldOperation operation = new RenameScalarFieldOperation(
                new OperationMetadata(1, "Rename field"),
                "vector_summary",
                "old_field_name",
                "new_field_name"
        );
        assertEquals("old_field_name", operation.getOldFieldName());
        assertEquals("new_field_name", operation.getNewFieldName());
    }

    @Test
    void testUpdateScalarFieldTypeOperationCreation() {
        UpdateScalarFieldTypeOperation operation = new UpdateScalarFieldTypeOperation(
                new OperationMetadata(1, "Update type"),
                "vector_summary",
                "type_change_field",
                "int64"
        );
        assertEquals("type_change_field", operation.getFieldName());
        assertEquals("int64", operation.getNewFieldType());
    }

    @Test
    void testUpdateEmbeddingDimensionOperationCreation() {
        UpdateEmbeddingDimensionOperation operation = new UpdateEmbeddingDimensionOperation(
                new OperationMetadata(1, "Update dimension"),
                "vector_summary",
                "vector",
                8,
                128
        );
        assertEquals("vector", operation.getFieldName());
        assertEquals(8, operation.getNewDimension());
        assertEquals(128, operation.getBatchSize());
    }

    @Test
    void testSchemaUpdatesAndMigration() {
        List<BaseOperation> operations = List.of(
                new AddScalarFieldOperation(new OperationMetadata(1, "Add field"),
                        "vector_summary", "added_field", "string", "default"),
                new RenameScalarFieldOperation(new OperationMetadata(2, "Rename field"),
                        "vector_summary", "old_field_name", "new_field_name"),
                new UpdateScalarFieldTypeOperation(new OperationMetadata(3, "Update type"),
                        "vector_summary", "type_change_field", "int64"),
                new UpdateEmbeddingDimensionOperation(new OperationMetadata(4, "Update dimension"),
                        "vector_summary", "vector", 8, 64)
        );

        assertTrue(migrator.tryMigrate("vector_summary", operations));

        Map<String, Object> metadata = metadataFor(SUMMARY_COLLECTION, "doc_1");
        assertEquals("default", metadata.get("added_field"));
        assertFalse(metadata.containsKey("old_field_name"));
        assertEquals("value", metadata.get("new_field_name"));
        assertInstanceOf(Long.class, metadata.get("type_change_field"));
        assertEquals(100L, metadata.get("type_change_field"));
        assertEquals(8, vectorFor(SUMMARY_COLLECTION, "doc_1").size());
        assertEquals(4, collectionVersion(SUMMARY_COLLECTION));
    }

    @Test
    void testMultiCollectionMigration() {
        seedSummaryCollection("test_user_scope_multicol_a_summary", 0);
        seedSummaryCollection("test_user_scope_multicol_b_summary", 0);
        seedSummaryCollection("test_user_scope_multicol_c_summary", 0);

        List<BaseOperation> operations = List.of(
                new AddScalarFieldOperation(new OperationMetadata(1, "Add field"),
                        "vector_summary", "added_multi_field", "string", "default_multi"),
                new RenameScalarFieldOperation(new OperationMetadata(2, "Rename shared"),
                        "vector_summary", "shared_field", "renamed_shared_field"),
                new AddScalarFieldOperation(new OperationMetadata(3, "Add another field"),
                        "vector_summary", "another_new_field", "int64", 0L)
        );

        assertTrue(migrator.tryMigrate("vector_summary", operations));

        for (String collectionName : vectorStore.listCollectionNames()) {
            if (!collectionName.endsWith("_summary")) {
                continue;
            }
            Map<String, Object> metadata = metadataFor(collectionName, "doc_1");
            assertEquals("default_multi", metadata.get("added_multi_field"));
            assertEquals("shared", metadata.get("renamed_shared_field"));
            assertFalse(metadata.containsKey("shared_field"));
            assertEquals(0L, metadata.get("another_new_field"));
            assertEquals(3, collectionVersion(collectionName));
        }
    }

    @Test
    void testMigrationWithEmptyOperations() {
        vectorStore.updateCollectionMetadata(SUMMARY_COLLECTION, Map.of("schema_version", 2));
        assertTrue(migrator.tryMigrate("vector_summary", List.of()));
        assertEquals(2, collectionVersion(SUMMARY_COLLECTION));
    }

    @Test
    void testMigrationWithNullOperations() {
        vectorStore.updateCollectionMetadata(SUMMARY_COLLECTION, Map.of("schema_version", 2));
        assertTrue(migrator.tryMigrate("vector_summary", null));
        assertEquals(2, collectionVersion(SUMMARY_COLLECTION));
    }

    @Test
    void testMigrationInvalidEntityKey() {
        assertFalse(migrator.tryMigrate("vector_invalid", List.of(
                new AddScalarFieldOperation(new OperationMetadata(1, "noop"),
                        "vector_invalid", "field", "string", "value")
        )));
    }

    @Test
    void testMigrationRollbackOnUpdateSchemaFailureReturnsFalse() {
        VectorMigrator failingMigrator = new VectorMigrator(new SemanticStore(new InMemoryVectorStore("unused")) {
            @Override
            public List<String> listCollectionNames() {
                return List.of(SUMMARY_COLLECTION);
            }

            @Override
            public Map<String, Object> getCollectionMetadata(String collectionName) {
                return Map.of("schema_version", 0);
            }

            @Override
            public boolean updateSchema(String collectionName, List<?> operations) {
                return false;
            }
        });

        assertFalse(failingMigrator.tryMigrate("vector_summary", List.of(
                new AddScalarFieldOperation(new OperationMetadata(1, "Add field"),
                        "vector_summary", "rollback_test_field", "string", "test")
        )));
    }

    @Test
    void testMigrationReturnsFalseWhenMetadataReadFails() {
        VectorMigrator failingMigrator = new VectorMigrator(new SemanticStore(new InMemoryVectorStore("unused")) {
            @Override
            public List<String> listCollectionNames() {
                return List.of(SUMMARY_COLLECTION);
            }

            @Override
            public Map<String, Object> getCollectionMetadata(String collectionName) {
                throw new IllegalStateException("metadata unavailable");
            }
        });

        assertFalse(failingMigrator.tryMigrate("vector_summary", List.of(
                new AddScalarFieldOperation(new OperationMetadata(1, "Add field"),
                        "vector_summary", "field", "string", "value")
        )));
    }

    @Test
    void testMigrationIdempotency() {
        List<BaseOperation> operations = List.of(
                new AddScalarFieldOperation(new OperationMetadata(1, "Add field"),
                        "vector_summary", "idempotent_field", "string", "idempotent")
        );

        assertTrue(migrator.tryMigrate("vector_summary", operations));
        Map<String, Object> afterFirst = metadataFor(SUMMARY_COLLECTION, "doc_1");
        int versionAfterFirst = collectionVersion(SUMMARY_COLLECTION);

        assertTrue(migrator.tryMigrate("vector_summary", operations));
        Map<String, Object> afterSecond = metadataFor(SUMMARY_COLLECTION, "doc_1");

        assertEquals(versionAfterFirst, collectionVersion(SUMMARY_COLLECTION));
        assertEquals(afterFirst.get("idempotent_field"), afterSecond.get("idempotent_field"));
    }

    @Test
    void testNoMatchingCollectionsLeavesStateUnchanged() {
        vectorStore.deleteTable(SUMMARY_COLLECTION);
        assertTrue(migrator.tryMigrate("vector_summary", List.of(
                new AddScalarFieldOperation(new OperationMetadata(1, "noop"),
                        "vector_summary", "field", "string", "value")
        )));
        assertFalse(vectorStore.tableExists(SUMMARY_COLLECTION));
    }

    @Test
    void testPartialMigrationContinueUpgrade() {
        vectorStore.updateCollectionMetadata(SUMMARY_COLLECTION, Map.of("schema_version", 2));

        List<BaseOperation> operations = List.of(
                new AddScalarFieldOperation(new OperationMetadata(1, "Skip"),
                        "vector_summary", "v1_field", "string", "v1"),
                new AddScalarFieldOperation(new OperationMetadata(2, "Skip too"),
                        "vector_summary", "v2_field", "string", "v2"),
                new AddScalarFieldOperation(new OperationMetadata(3, "Apply"),
                        "vector_summary", "v3_field", "string", "v3")
        );

        assertTrue(migrator.tryMigrate("vector_summary", operations));

        Map<String, Object> metadata = metadataFor(SUMMARY_COLLECTION, "doc_1");
        assertFalse(metadata.containsKey("v1_field"));
        assertFalse(metadata.containsKey("v2_field"));
        assertEquals("v3", metadata.get("v3_field"));
        assertEquals(3, collectionVersion(SUMMARY_COLLECTION));
    }

    private void seedSummaryCollection(String collectionName, int schemaVersion) {
        InMemoryVectorStore scoped = (InMemoryVectorStore) vectorStore.withCollection(collectionName);
        List<Map<String, Object>> docs = List.of(
                summaryDoc("doc_1", "data", List.of(0.1f, 0.2f, 0.3f, 0.4f)),
                summaryDoc("doc_2", "data2", List.of(0.5f, 0.6f, 0.7f, 0.8f))
        );
        scoped.add(docs, null, null);
        vectorStore.updateCollectionMetadata(collectionName, Map.of("schema_version", schemaVersion));
    }

    private Map<String, Object> summaryDoc(String id, String text, List<Float> vector) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("old_field_name", "value");
        metadata.put("type_change_field", 100);
        metadata.put("shared_field", "shared");

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", id);
        doc.put("text", text);
        doc.put("vector", vector);
        doc.put("old_field_name", "value");
        doc.put("type_change_field", 100);
        doc.put("shared_field", "shared");
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
