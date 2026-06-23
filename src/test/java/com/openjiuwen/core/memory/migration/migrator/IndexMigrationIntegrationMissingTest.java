/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.foundation.store.StorageCodec;
import com.openjiuwen.core.memory.migration.operation.AddMemoryDocFieldOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.RenameMemoryDocFieldOperation;
import com.openjiuwen.core.memory.migration.operation.TransformMemoryDocFieldOperation;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code TestIndexMigrationIntegration} in
 * {@code tests/unit_tests/core/memory/migration/migrator/test_index_migration_integration.py}.</p>
 */
class IndexMigrationIntegrationMissingTest {

    @Test
    void testVersionMigrationRenameField() {
        RecordingMemoryIndex index = RecordingMemoryIndex.withTestDocs();

        List<MemoryDoc> initialDocs = index.listMemories("user1", "scope1", 0, 10, null).join();
        assertThat(initialDocs).hasSize(3);
        assertThat(initialDocs)
                .allSatisfy(document -> {
                    assertThat(document.getFields()).containsKey("memory_text");
                    assertThat(document.getFields()).doesNotContainKey("text");
                });

        RenameMemoryDocFieldOperation renameOperation = new RenameMemoryDocFieldOperation(
                new OperationMetadata(1, "Rename memory_text to text"),
                "memory_text",
                "text"
        );

        boolean result = new IndexVersionMigrator().tryMigrate(index, List.of(renameOperation)).join();

        assertThat(result).isTrue();
        assertThat(index.getSchemaVersion()).isEqualTo(1);
        List<MemoryDoc> migratedDocs = index.listMemories("user1", "scope1", 0, 10, null).join();
        assertThat(migratedDocs).hasSize(3);
        assertThat(migratedDocs)
                .allSatisfy(document -> {
                    assertThat(document.getFields()).doesNotContainKey("memory_text");
                    assertThat(document.getFields()).containsKey("text");
                    assertThat(String.valueOf(document.getFields().get("text"))).startsWith("Content ");
                });
    }

    @Test
    void testVersionMigrationMultipleOperations() {
        RecordingMemoryIndex index = RecordingMemoryIndex.withTestDocs();
        List<com.openjiuwen.core.memory.migration.operation.BaseOperation> operations = List.of(
                new RenameMemoryDocFieldOperation(
                        new OperationMetadata(1, "Rename memory_text to content"),
                        "memory_text",
                        "content"
                ),
                new AddMemoryDocFieldOperation(
                        new OperationMetadata(2, "Add processed field"),
                        "processed",
                        true
                ),
                new TransformMemoryDocFieldOperation(
                        new OperationMetadata(3, "Increment count by 10"),
                        "count",
                        value -> ((Number) value).intValue() + 10
                )
        );

        boolean result = new IndexVersionMigrator().tryMigrate(index, operations).join();

        assertThat(result).isTrue();
        assertThat(index.getSchemaVersion()).isEqualTo(3);
        List<MemoryDoc> migratedDocs = index.listMemories("user1", "scope1", 0, 10, null).join();
        assertThat(migratedDocs).hasSize(3);
        assertThat(migratedDocs)
                .allSatisfy(document -> {
                    assertThat(document.getFields()).doesNotContainKey("memory_text");
                    assertThat(document.getFields()).containsKey("content");
                    assertThat(String.valueOf(document.getFields().get("content"))).startsWith("Content ");
                    assertThat(document.getFields()).containsEntry("processed", true);

                    int expectedCount = Integer.parseInt(String.valueOf(document.getFields().get("content")).split(" ")[1]) + 10;
                    assertThat(document.getFields().get("count")).isEqualTo(expectedCount);
                });
    }

    private static final class RecordingMemoryIndex extends BaseMemoryIndex {
        private final List<MemoryDoc> documents = new ArrayList<>();
        private final List<MemoryDoc> backup = new ArrayList<>();
        private int schemaVersion;

        private static RecordingMemoryIndex withTestDocs() {
            RecordingMemoryIndex index = new RecordingMemoryIndex();
            for (int number = 1; number <= 3; number++) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("memory_text", "Content " + number);
                fields.put("category", "test");
                fields.put("count", number);
                index.documents.add(new MemoryDoc(
                        "%024d".formatted(number),
                        "Test document " + number,
                        "fragment",
                        ZonedDateTime.parse("2026-01-01T00:00:00Z").plusSeconds(number),
                        fields
                ));
            }
            return index;
        }

        @Override
        public void setStorageCodec(StorageCodec codec) {
        }

        @Override
        public CompletableFuture<Void> addMemories(String userId, String scopeId, List<MemoryDoc> memories) {
            documents.addAll(copyDocs(memories));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateMemories(String userId, String scopeId, List<MemoryDoc> memories) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteMemories(String userId, String scopeId, List<String> ids) {
            documents.removeIf(document -> ids.contains(document.getId()));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteByUser(String userId) {
            documents.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteByScope(String scopeId) {
            documents.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteByUserAndScope(String userId, String scopeId) {
            documents.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<MemorySearchResult>> search(
                String userId,
                String scopeId,
                String query,
                List<String> memTypes,
                int topK
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<MemoryDoc> getById(String userId, String scopeId, String memId) {
            return CompletableFuture.completedFuture(documents.stream()
                    .filter(document -> memId.equals(document.getId()))
                    .findFirst()
                    .orElse(null));
        }

        @Override
        public CompletableFuture<List<MemoryDoc>> listMemories(
                String userId,
                String scopeId,
                int offset,
                int limit,
                List<String> memTypes
        ) {
            int start = Math.max(0, Math.min(offset, documents.size()));
            int end = Math.min(documents.size(), start + limit);
            return CompletableFuture.completedFuture(copyDocs(documents.subList(start, end)));
        }

        @Override
        public int getSchemaVersion() {
            return schemaVersion;
        }

        @Override
        public void updateSchemaVersion(int version) {
            schemaVersion = version;
        }

        @Override
        public CompletableFuture<String> createBackup() {
            backup.clear();
            backup.addAll(copyDocs(documents));
            return CompletableFuture.completedFuture("backup-1");
        }

        @Override
        public CompletableFuture<Void> restoreBackup(String backupId) {
            documents.clear();
            documents.addAll(copyDocs(backup));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> cleanupBackup(String backupId) {
            backup.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<UserScopeKey>> listUserScopes() {
            return CompletableFuture.completedFuture(List.of(new UserScopeKey("user1", "scope1")));
        }

        private static List<MemoryDoc> copyDocs(List<MemoryDoc> source) {
            List<MemoryDoc> copied = new ArrayList<>();
            for (MemoryDoc document : source) {
                copied.add(new MemoryDoc(
                        document.getId(),
                        document.getText(),
                        document.getType(),
                        document.getTimestamp(),
                        new LinkedHashMap<>(document.getFields())
                ));
            }
            return copied;
        }
    }
}
