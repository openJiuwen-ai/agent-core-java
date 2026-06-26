/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.vector.ChromaVectorStore;
import com.openjiuwen.core.memory.migration.operation.AddScalarFieldOperation;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.RenameScalarFieldOperation;
import com.openjiuwen.core.memory.migration.operation.UpdateEmbeddingDimensionOperation;
import com.openjiuwen.core.memory.migration.operation.UpdateScalarFieldTypeOperation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Mirrors Python's {@code test_chroma_vec_migrator} in
 * {@code tests/unit_tests/core/memory/migration/migrator/test_chroma_vec_migrator.py}.
 */
class ChromaVectorMigratorMissingTest {

    private static final String SUMMARY_COLLECTION = "user1_scope1_summary";
    private static final String USER_PROFILE_COLLECTION = "user2_scope2_user_profile";

    @Test
    void tryMigrateSameVersionMultipleOperations() {
        ChromaVectorStore store = newStoreWithCollections();
        VectorMigrator migrator = new VectorMigrator(store);

        BaseOperation version1 = new AddScalarFieldOperation(
                version(1), "vector_summary", "version1_field", "varchar", "v1");
        assertThat(migrator.tryMigrate("vector_summary", List.of(version1)).join()).isTrue();

        assertThat(store.getCollectionMetadata(SUMMARY_COLLECTION).join()).containsEntry("schema_version", 1);
        assertThat(store.getCollectionMetadata(USER_PROFILE_COLLECTION).join()).containsEntry("schema_version", 0);

        List<BaseOperation> operations = List.of(
                new AddScalarFieldOperation(version(2), "vector_summary", "category", "varchar", "general"),
                new AddScalarFieldOperation(version(2), "vector_summary", "author", "varchar", "unknown"),
                new RenameScalarFieldOperation(version(2), "vector_summary", "count", "view_count")
        );

        assertThat(migrator.tryMigrate("vector_summary", operations).join()).isTrue();

        CollectionSchema schema = schema(store);
        assertThat(schema.hasField("category")).isTrue();
        assertThat(schema.hasField("author")).isTrue();
        assertThat(schema.hasField("view_count")).isTrue();
        assertThat(schema.hasField("count")).isFalse();

        for (Map<String, Object> doc : docs(store)) {
            assertThat(doc).containsEntry("category", "general");
            assertThat(doc).containsEntry("author", "unknown");
            assertThat(doc).containsKey("view_count");
            assertThat(doc).doesNotContainKey("count");
        }
        assertThat(store.getCollectionMetadata(SUMMARY_COLLECTION).join()).containsEntry("schema_version", 2);
    }

    @Test
    void tryMigrateMultiVersionMultiOperations() {
        ChromaVectorStore store = newStoreWithCollections();
        VectorMigrator migrator = new VectorMigrator(store);

        List<BaseOperation> operations = new ArrayList<>();
        operations.add(new AddScalarFieldOperation(version(1), "vector_summary", "version1_field1", "varchar",
                "v1_f1"));
        operations.add(new AddScalarFieldOperation(version(1), "vector_summary", "version1_field2", "int32", 1));
        operations.add(new RenameScalarFieldOperation(version(2), "vector_summary", "count", "view_count"));
        operations.add(new AddScalarFieldOperation(version(2), "vector_summary", "version2_field", "double", 2.0d));
        operations.add(new UpdateScalarFieldTypeOperation(version(2), "vector_summary", "version1_field2", "int64"));
        operations.add(new UpdateEmbeddingDimensionOperation(version(3), "vector_summary", "embedding", 6,
                doc -> concat(embedding(doc), List.of(0.1d, 0.2d)), 1000));
        operations.add(new AddScalarFieldOperation(version(3), "vector_summary", "version3_field", "bool", true));

        assertThat(migrator.tryMigrate("vector_summary", operations).join()).isTrue();

        assertThat(store.getCollectionMetadata(SUMMARY_COLLECTION).join()).containsEntry("schema_version", 3);
        CollectionSchema updatedSchema = schema(store);
        assertThat(updatedSchema.hasField("version1_field1")).isTrue();
        assertThat(updatedSchema.hasField("version1_field2")).isTrue();
        assertThat(updatedSchema.hasField("view_count")).isTrue();
        assertThat(updatedSchema.hasField("count")).isFalse();
        assertThat(updatedSchema.hasField("version2_field")).isTrue();
        assertThat(updatedSchema.getField("version1_field2").getDtype()).isEqualTo(VectorDataType.INT64);
        assertThat(updatedSchema.getField("embedding").getDim()).isEqualTo(6);
        assertThat(updatedSchema.hasField("version3_field")).isTrue();

        for (Map<String, Object> doc : docs(store)) {
            assertThat(doc).containsEntry("version1_field1", "v1_f1");
            assertThat(doc.get("version1_field2")).isInstanceOf(Integer.class);
            assertThat(doc).containsKey("view_count");
            assertThat(doc).doesNotContainKey("count");
            assertThat(doc).containsEntry("version2_field", 2.0d);
            assertThat(embedding(doc)).hasSize(6);
            assertThat(doc).containsEntry("version3_field", true);
        }
    }

    @Test
    void tryMigrateUpdateFieldTypeNormal() {
        ChromaVectorStore store = newStoreWithCollections();
        VectorMigrator migrator = new VectorMigrator(store);

        BaseOperation operation = new UpdateScalarFieldTypeOperation(version(1), "vector_summary", "count", "double");

        assertThat(migrator.tryMigrate("vector_summary", List.of(operation)).join()).isTrue();

        assertThat(schema(store).getField("count").getDtype()).isEqualTo(VectorDataType.DOUBLE);
    }

    @Test
    void tryMigrateUpdateNonexistentFieldType() {
        ChromaVectorStore store = newStoreWithCollections();
        VectorMigrator migrator = new VectorMigrator(store);

        BaseOperation operation = new UpdateScalarFieldTypeOperation(
                version(1), "vector_summary", "nonexistent_field", "float64");

        Throwable thrown = catchThrowable(() -> migrator.tryMigrate("vector_summary", List.of(operation)).join());

        assertThat(rootCauseMessage(thrown).toLowerCase(Locale.ROOT)).contains("does not exist");
    }

    @Test
    void tryMigrateUpdateVectorFieldType() {
        ChromaVectorStore store = newStoreWithCollections();
        VectorMigrator migrator = new VectorMigrator(store);

        BaseOperation operation = new UpdateScalarFieldTypeOperation(
                version(1), "vector_summary", "embedding", "float64");

        Throwable thrown = catchThrowable(() -> migrator.tryMigrate("vector_summary", List.of(operation)).join());

        assertThat(rootCauseMessage(thrown).toLowerCase(Locale.ROOT)).contains("cannot update type of vector field");
    }

    @Test
    void tryMigrateWithExceptionDuringMigration() {
        ChromaVectorStore store = newStoreWithCollections();
        VectorMigrator migrator = new VectorMigrator(store);
        List<Map<String, Object>> initialDocs = docs(store);
        Map<String, Object> initialSchema = schema(store).toDict();
        Object initialVersion = store.getCollectionMetadata(SUMMARY_COLLECTION).join().get("schema_version");

        BaseOperation operation = new UpdateEmbeddingDimensionOperation(
                version(1),
                "vector_summary",
                "embedding",
                8,
                doc -> {
                    throw new IllegalArgumentException("Intentional failure during embedding recomputation");
                },
                1000
        );

        Throwable thrown = catchThrowable(() -> migrator.tryMigrate("vector_summary", List.of(operation)).join());

        assertThat(rootCauseMessage(thrown)).contains("Intentional failure during embedding recomputation");
        assertThat(schema(store).toDict()).isEqualTo(initialSchema);
        assertThat(store.getCollectionMetadata(SUMMARY_COLLECTION).join()).containsEntry("schema_version", initialVersion);
        assertThat(docs(store)).isEqualTo(initialDocs);
        assertThat(store.listCollectionNames().join())
                .noneMatch(collectionName -> collectionName.startsWith(SUMMARY_COLLECTION + "_migration_"));
    }

    @Test
    void tryMigrateAddExistingField() {
        ChromaVectorStore store = newStoreWithCollections();
        VectorMigrator migrator = new VectorMigrator(store);

        BaseOperation operation = new AddScalarFieldOperation(version(1), "vector_summary", "count", "int32", 0);

        Throwable thrown = catchThrowable(() -> migrator.tryMigrate("vector_summary", List.of(operation)).join());

        assertThat(rootCauseMessage(thrown).toLowerCase(Locale.ROOT))
                .satisfies(message -> assertThat(message).containsAnyOf("already exists", "duplicate"));
    }

    @Test
    void tryMigrateRenameFieldNormal() {
        ChromaVectorStore store = newStoreWithCollections();
        VectorMigrator migrator = new VectorMigrator(store);

        BaseOperation operation = new RenameScalarFieldOperation(version(1), "vector_summary", "count", "view_count");

        assertThat(migrator.tryMigrate("vector_summary", List.of(operation)).join()).isTrue();

        CollectionSchema updatedSchema = schema(store);
        assertThat(updatedSchema.hasField("count")).isFalse();
        assertThat(updatedSchema.hasField("view_count")).isTrue();

        List<Map<String, Object>> documents = docs(store);
        for (int index = 0; index < documents.size(); index++) {
            assertThat(documents.get(index)).doesNotContainKey("count");
            assertThat(documents.get(index)).containsEntry("view_count", index + 1);
        }
    }

    @Test
    void tryMigrateRenameNonexistentField() {
        ChromaVectorStore store = newStoreWithCollections();
        VectorMigrator migrator = new VectorMigrator(store);

        BaseOperation operation = new RenameScalarFieldOperation(
                version(1), "vector_summary", "nonexistent_field", "new_field");

        Throwable thrown = catchThrowable(() -> migrator.tryMigrate("vector_summary", List.of(operation)).join());

        assertThat(rootCauseMessage(thrown).toLowerCase(Locale.ROOT)).contains("does not exist");
    }

    @Test
    void tryMigrateRenameToExistingField() {
        ChromaVectorStore store = newStoreWithCollections();
        VectorMigrator migrator = new VectorMigrator(store);

        BaseOperation operation = new RenameScalarFieldOperation(version(1), "vector_summary", "count", "text");

        Throwable thrown = catchThrowable(() -> migrator.tryMigrate("vector_summary", List.of(operation)).join());

        assertThat(rootCauseMessage(thrown).toLowerCase(Locale.ROOT)).contains("already exists");
    }

    @Test
    void tryMigrateUpdateEmbeddingDimensionExpansion() {
        ChromaVectorStore store = newStoreWithCollections();
        VectorMigrator migrator = new VectorMigrator(store);

        BaseOperation operation = new UpdateEmbeddingDimensionOperation(version(1), "vector_summary", "embedding", 8,
                doc -> {
                    List<Double> oldEmbedding = embedding(doc);
                    return concat(oldEmbedding, oldEmbedding);
                }, 1000);

        assertThat(migrator.tryMigrate("vector_summary", List.of(operation)).join()).isTrue();

        assertThat(schema(store).getField("embedding").getDim()).isEqualTo(8);
        for (Map<String, Object> doc : docs(store)) {
            List<Double> newEmbedding = embedding(doc);
            List<Double> oldEmbedding = newEmbedding.subList(0, 4);
            assertThat(newEmbedding).hasSize(8).isEqualTo(concat(oldEmbedding, oldEmbedding));
        }
    }

    @Test
    void tryMigrateUpdateEmbeddingDimensionReduction() {
        ChromaVectorStore store = newStoreWithCollections();
        VectorMigrator migrator = new VectorMigrator(store);

        BaseOperation operation = new UpdateEmbeddingDimensionOperation(version(1), "vector_summary", "embedding", 2,
                doc -> embedding(doc).subList(0, 2), 1000);

        assertThat(migrator.tryMigrate("vector_summary", List.of(operation)).join()).isTrue();

        assertThat(schema(store).getField("embedding").getDim()).isEqualTo(2);
        assertThat(docs(store)).extracting(doc -> embedding(doc))
                .containsExactly(List.of(0.1d, 0.2d), List.of(0.5d, 0.6d));
    }

    @Test
    void tryMigrateUpdateEmbeddingDimensionZeroPadding() {
        ChromaVectorStore store = newStoreWithCollections();
        VectorMigrator migrator = new VectorMigrator(store);

        BaseOperation operation = new UpdateEmbeddingDimensionOperation(version(1), "vector_summary", "embedding", 6);

        assertThat(migrator.tryMigrate("vector_summary", List.of(operation)).join()).isTrue();

        assertThat(schema(store).getField("embedding").getDim()).isEqualTo(6);
        assertThat(docs(store)).extracting(doc -> embedding(doc))
                .containsExactly(List.of(0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d),
                        List.of(0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d));
    }

    @Test
    void tryMigrateUpdateEmbeddingDimensionWrongSize() {
        ChromaVectorStore store = newStoreWithCollections();
        VectorMigrator migrator = new VectorMigrator(store);

        BaseOperation operation = new UpdateEmbeddingDimensionOperation(version(1), "vector_summary", "embedding", 8,
                doc -> concat(embedding(doc), List.of(0.5d, 0.6d)), 1000);

        Throwable thrown = catchThrowable(() -> migrator.tryMigrate("vector_summary", List.of(operation)).join());

        String message = rootCauseMessage(thrown).toLowerCase(Locale.ROOT);
        assertThat(message).contains("vector length");
        assertThat(message).contains("does not match");
    }

    private static ChromaVectorStore newStoreWithCollections() {
        ChromaVectorStore store = new ChromaVectorStore("test-chroma-vec-migrator");
        for (String collectionName : List.of(SUMMARY_COLLECTION, USER_PROFILE_COLLECTION)) {
            store.createCollection(collectionName, initialSchema(), Map.of()).join();
            store.addDocs(collectionName, initialDocs(), Map.of()).join();
        }
        return store;
    }

    private static CollectionSchema initialSchema() {
        return CollectionSchema.fromFields(List.of(
                new FieldSchema("id", VectorDataType.VARCHAR, true, false, 256, null,
                        null, null, null, null),
                new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, false, false, null, 4,
                        null, null, null, null),
                new FieldSchema("text", VectorDataType.VARCHAR, false, false, 65535, null,
                        null, null, null, null),
                new FieldSchema("count", VectorDataType.INT32, false, false, null, null,
                        null, null, null, null)
        ), null, false);
    }

    private static List<Map<String, Object>> initialDocs() {
        return List.of(
                doc("doc_1", List.of(0.1d, 0.2d, 0.3d, 0.4d), "First document", 1),
                doc("doc_2", List.of(0.5d, 0.6d, 0.7d, 0.8d), "Second document", 2)
        );
    }

    private static Map<String, Object> doc(String id, List<Double> embedding, String text, int count) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", id);
        doc.put("embedding", embedding);
        doc.put("text", text);
        doc.put("count", count);
        return doc;
    }

    private static OperationMetadata version(int schemaVersion) {
        return new OperationMetadata(schemaVersion);
    }

    private static CollectionSchema schema(ChromaVectorStore store) {
        return store.getSchema(SUMMARY_COLLECTION, Map.of()).join();
    }

    private static List<Map<String, Object>> docs(ChromaVectorStore store) {
        return store.getAllDocuments(SUMMARY_COLLECTION).join();
    }

    private static List<Double> embedding(Object doc) {
        Object rawValue = doc instanceof Map<?, ?> map ? map.get("embedding") : doc;
        if (!(rawValue instanceof List<?> rawList)) {
            return List.of();
        }
        List<Double> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Number number) {
                result.add(number.doubleValue());
            }
        }
        return result;
    }

    private static List<Double> concat(List<Double> left, List<Double> right) {
        List<Double> result = new ArrayList<>(left);
        result.addAll(right);
        return result;
    }

    private static String rootCauseMessage(Throwable throwable) {
        assertThat(throwable).isNotNull();
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "" : current.getMessage();
    }
}
