/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorMigratorTest {

    @Test
    void tryMigrateAppliesOnlyHigherVersionOperationsForMatchingCollections() {
        RecordingVectorStore store = new RecordingVectorStore(
                List.of("alice_scope_summary", "alice_scope_user_profile"),
                Map.of(
                        "alice_scope_summary", Map.of("schema_version", 1),
                        "alice_scope_user_profile", Map.of("schema_version", 0)
                )
        );
        VectorMigrator migrator = new VectorMigrator(store);

        Boolean result = migrator.tryMigrate("vector_summary", List.of(
                operation(1),
                operation(2),
                operation(3)
        )).join();

        assertThat(result).isTrue();
        assertThat(store.schemaUpdates)
                .singleElement()
                .satisfies(update -> {
                    assertThat(update.collectionName()).isEqualTo("alice_scope_summary");
                    assertThat(update.schemaVersions()).containsExactly(2, 3);
                });
        assertThat(store.metadataUpdates)
                .singleElement()
                .satisfies(update -> {
                    assertThat(update.collectionName()).isEqualTo("alice_scope_summary");
                    assertThat(update.metadata()).containsEntry("schema_version", 3);
                });
        assertThat(store.metadataByCollection.get("alice_scope_user_profile"))
                .containsEntry("schema_version", 0);
    }

    @Test
    void tryMigrateProcessesEveryMatchingCollectionInCollectionOrder() {
        RecordingVectorStore store = new RecordingVectorStore(
                List.of("first_scope_summary", "other_scope_user_profile", "second_scope_summary"),
                Map.of(
                        "first_scope_summary", Map.of("schema_version", 0),
                        "other_scope_user_profile", Map.of("schema_version", 0),
                        "second_scope_summary", Map.of("schema_version", 2)
                )
        );
        VectorMigrator migrator = new VectorMigrator(store);

        migrator.tryMigrate("summary", List.of(operation(1), operation(2), operation(3))).join();

        assertThat(store.schemaUpdates)
                .extracting(AppliedSchemaUpdate::collectionName)
                .containsExactly("first_scope_summary", "second_scope_summary");
        assertThat(store.schemaUpdates.get(0).schemaVersions()).containsExactly(1, 2, 3);
        assertThat(store.schemaUpdates.get(1).schemaVersions()).containsExactly(3);
        assertThat(store.metadataByCollection.get("first_scope_summary")).containsEntry("schema_version", 3);
        assertThat(store.metadataByCollection.get("second_scope_summary")).containsEntry("schema_version", 3);
    }

    @Test
    void tryMigratePreservesOperationOrderInsideSingleBatch() {
        RecordingVectorStore store = new RecordingVectorStore(
                List.of("scope_summary"),
                Map.of("scope_summary", Map.of("schema_version", 0))
        );
        VectorMigrator migrator = new VectorMigrator(store);

        migrator.tryMigrate("vector_summary", List.of(operation(3), operation(1), operation(2))).join();

        assertThat(store.schemaUpdates)
                .singleElement()
                .extracting(AppliedSchemaUpdate::schemaVersions)
                .isEqualTo(List.of(3, 1, 2));
        assertThat(store.metadataByCollection.get("scope_summary")).containsEntry("schema_version", 3);
    }

    @Test
    void tryMigrateUsesZeroWhenSchemaVersionMetadataIsMissing() {
        RecordingVectorStore store = new RecordingVectorStore(
                List.of("scope_summary"),
                Map.of("scope_summary", Map.of())
        );
        VectorMigrator migrator = new VectorMigrator(store);

        migrator.tryMigrate("vector_summary", List.of(operation(1), operation(2))).join();

        assertThat(store.schemaUpdates)
                .singleElement()
                .extracting(AppliedSchemaUpdate::schemaVersions)
                .isEqualTo(List.of(1, 2));
        assertThat(store.metadataByCollection.get("scope_summary")).containsEntry("schema_version", 2);
    }

    @Test
    void tryMigrateSkipsStoreWritesWhenNoNewerOperationsExist() {
        RecordingVectorStore store = new RecordingVectorStore(
                List.of("scope_summary"),
                Map.of("scope_summary", Map.of("schema_version", 3))
        );
        VectorMigrator migrator = new VectorMigrator(store);

        Boolean result = migrator.tryMigrate("vector_summary", List.of(operation(1), operation(2), operation(3))).join();

        assertThat(result).isTrue();
        assertThat(store.schemaUpdates).isEmpty();
        assertThat(store.metadataUpdates).isEmpty();
    }

    @Test
    void tryMigrateReturnsTrueWhenNoCollectionsMatchRequestedSuffix() {
        RecordingVectorStore store = new RecordingVectorStore(
                List.of("scope_user_profile", "scope_message"),
                Map.of(
                        "scope_user_profile", Map.of("schema_version", 0),
                        "scope_message", Map.of("schema_version", 0)
                )
        );
        VectorMigrator migrator = new VectorMigrator(store);

        Boolean result = migrator.tryMigrate("vector_summary", List.of(operation(1))).join();

        assertThat(result).isTrue();
        assertThat(store.schemaUpdates).isEmpty();
        assertThat(store.metadataUpdates).isEmpty();
    }

    @Test
    void tryMigrateSupportsRawMemoryTypeWithoutVectorPrefix() {
        RecordingVectorStore store = new RecordingVectorStore(
                List.of("scope_user_profile"),
                Map.of("scope_user_profile", Map.of("schema_version", 0))
        );
        VectorMigrator migrator = new VectorMigrator(store);

        migrator.tryMigrate("user_profile", List.of(operation(1))).join();

        assertThat(store.schemaUpdates)
                .singleElement()
                .extracting(AppliedSchemaUpdate::collectionName)
                .isEqualTo("scope_user_profile");
    }

    @Test
    void tryMigrateRejectsUnsupportedMemoryTypeWithMappedStatusCode() {
        VectorMigrator migrator = new VectorMigrator(new RecordingVectorStore(List.of(), Map.of()));

        assertThatThrownBy(() -> migrator.tryMigrate("vector_unknown", List.of(operation(1))))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.MEMORY_MIGRATE_MEMORY_EXECUTION_ERROR);
    }

    @Test
    void tryMigrateListsSupportedTypesInSortedOrderForUnsupportedMemoryType() {
        VectorMigrator migrator = new VectorMigrator(new RecordingVectorStore(List.of(), Map.of()));

        assertThatThrownBy(() -> migrator.tryMigrate("vector_unknown", List.of(operation(1))))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("Supported types: [summary, user_profile]");
    }

    @Test
    void tryMigratePropagatesStoreFailuresWithoutWritingMetadata() {
        RecordingVectorStore store = new RecordingVectorStore(
                List.of("scope_summary"),
                Map.of("scope_summary", Map.of("schema_version", 0))
        );
        store.failUpdateSchemaWith(new IllegalStateException("schema update failed"));
        VectorMigrator migrator = new VectorMigrator(store);

        assertThatThrownBy(() -> migrator.tryMigrate("vector_summary", List.of(operation(1))).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("schema update failed");
        assertThat(store.metadataUpdates).isEmpty();
    }

    private static BaseOperation operation(int schemaVersion) {
        return new TestOperation(schemaVersion);
    }

    private static final class TestOperation extends BaseOperation {

        private TestOperation(int schemaVersion) {
            super(new OperationMetadata(schemaVersion, "v" + schemaVersion));
        }
    }

    private record AppliedSchemaUpdate(String collectionName, List<Integer> schemaVersions) {
    }

    private record MetadataUpdate(String collectionName, Map<String, Object> metadata) {
    }

    private static final class RecordingVectorStore extends BaseVectorStore {

        private final List<String> collectionNames;
        private final Map<String, Map<String, Object>> metadataByCollection;
        private final List<AppliedSchemaUpdate> schemaUpdates = new ArrayList<>();
        private final List<MetadataUpdate> metadataUpdates = new ArrayList<>();
        private RuntimeException updateSchemaFailure;

        private RecordingVectorStore(List<String> collectionNames, Map<String, Map<String, Object>> metadataByCollection) {
            this.collectionNames = new ArrayList<>(collectionNames);
            this.metadataByCollection = new LinkedHashMap<>();
            metadataByCollection.forEach((collectionName, metadata) ->
                    this.metadataByCollection.put(collectionName, new LinkedHashMap<>(metadata))
            );
        }

        private void failUpdateSchemaWith(RuntimeException error) {
            this.updateSchemaFailure = error;
        }

        @Override
        public CompletableFuture<Void> createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> deleteCollection(String collectionName, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Boolean> collectionExists(String collectionName, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<CollectionSchema> getSchema(String collectionName, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> addDocs(String collectionName, List<Map<String, Object>> docs, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<VectorSearchResult>> search(
                String collectionName,
                List<Double> queryVector,
                String vectorField,
                int topK,
                Map<String, Object> filters,
                Map<String, Object> kwargs
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> deleteDocsByFilters(String collectionName, Map<String, Object> filters, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<String>> listCollectionNames() {
            return CompletableFuture.completedFuture(List.copyOf(collectionNames));
        }

        @Override
        public CompletableFuture<Void> updateSchema(String collectionName, List<BaseOperation> operations) {
            if (updateSchemaFailure != null) {
                return CompletableFuture.failedFuture(updateSchemaFailure);
            }
            schemaUpdates.add(new AppliedSchemaUpdate(
                    collectionName,
                    operations.stream().map(BaseOperation::getSchemaVersion).toList()
            ));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
            metadataUpdates.add(new MetadataUpdate(collectionName, Map.copyOf(metadata)));
            metadataByCollection.computeIfAbsent(collectionName, ignored -> new LinkedHashMap<>()).putAll(metadata);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getCollectionMetadata(String collectionName) {
            Map<String, Object> metadata = metadataByCollection.get(collectionName);
            return CompletableFuture.completedFuture(metadata == null ? Map.of() : new LinkedHashMap<>(metadata));
        }
    }
}
