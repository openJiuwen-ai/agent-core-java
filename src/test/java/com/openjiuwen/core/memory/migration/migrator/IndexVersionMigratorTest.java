/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.foundation.store.StorageCodec;
import com.openjiuwen.core.memory.migration.operation.AddMemoryDocFieldOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.RemoveMemoryDocFieldOperation;
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
import static org.assertj.core.api.Assertions.tuple;

/**
 * <p>Mirrors Python's {@code TestIndexVersionMigrator} in
 * {@code tests/unit_tests/core/memory/migration/migrator/test_index_version_migrator.py}.</p>
 */
class IndexVersionMigratorTest {

    @Test
    void testTryMigrateNoOperations() {
        RecordingMemoryIndex index = new RecordingMemoryIndex();

        boolean result = new IndexVersionMigrator().tryMigrate(index, List.of()).join();

        assertThat(result).isTrue();
        assertThat(index.getSchemaVersionCallCount).isEqualTo(1);
        assertThat(index.createBackupCallCount).isZero();
    }

    @Test
    void testTryMigrateWithOperations() {
        RecordingMemoryIndex index = new RecordingMemoryIndex();
        RenameMemoryDocFieldOperation operation = new RenameMemoryDocFieldOperation(
                new OperationMetadata(1, "Rename memory_text to text"),
                "memory_text",
                "text"
        );

        boolean result = new IndexVersionMigrator().tryMigrate(index, List.of(operation)).join();

        assertThat(result).isTrue();
        assertThat(index.getSchemaVersionCallCount).isEqualTo(1);
        assertThat(index.createBackupCallCount).isEqualTo(1);
        assertThat(index.listUserScopesCallCount).isEqualTo(1);
        assertThat(index.listMemoriesCalls)
                .extracting(ListMemoriesCall::offset, ListMemoriesCall::limit)
                .containsExactly(tuple(0, 100), tuple(100, 100));
        assertThat(index.addMemoriesCalls).hasSize(1);
        assertThat(index.deleteMemoriesCalls).hasSize(1);
        assertThat(index.updatedSchemaVersions).containsExactly(1);
        assertThat(index.cleanedBackupIds).containsExactly("backup123");
    }

    @Test
    void testApplyRenameField() {
        RecordingMemoryIndex index = new RecordingMemoryIndex();
        RenameMemoryDocFieldOperation operation = new RenameMemoryDocFieldOperation(
                new OperationMetadata(1, "Rename memory_text to text"),
                "memory_text",
                "text"
        );

        new IndexVersionMigrator().tryMigrate(index, List.of(operation)).join();

        assertThat(index.listUserScopesCallCount).isEqualTo(1);
        assertThat(index.listMemoriesCalls)
                .extracting(ListMemoriesCall::offset, ListMemoriesCall::limit)
                .containsExactly(tuple(0, 100), tuple(100, 100));
        assertThat(index.deleteMemoriesCalls).hasSize(1);
        assertThat(index.addMemoriesCalls).hasSize(1);
        assertThat(index.addMemoriesCalls.get(0))
                .allSatisfy(document -> {
                    assertThat(document.getFields()).containsKey("text");
                    assertThat(document.getFields()).doesNotContainKey("memory_text");
                    assertThat(document.getFields().get("text")).isEqualTo("Content " + document.getId().substring(3));
                });
    }

    @Test
    void testApplyTransformField() {
        RecordingMemoryIndex index = new RecordingMemoryIndex();
        TransformMemoryDocFieldOperation operation = new TransformMemoryDocFieldOperation(
                new OperationMetadata(2, "Uppercase field1"),
                "field1",
                value -> String.valueOf(value).toUpperCase()
        );

        new IndexVersionMigrator().tryMigrate(index, List.of(operation)).join();

        assertThat(index.listUserScopesCallCount).isEqualTo(1);
        assertThat(index.listMemoriesCalls).hasSize(2);
        assertThat(index.deleteMemoriesCalls).hasSize(1);
        assertThat(index.addMemoriesCalls).hasSize(1);
        assertThat(index.addMemoriesCalls.get(0))
                .allSatisfy(document -> assertThat(document.getFields().get("field1"))
                        .isEqualTo("VALUE" + document.getId().substring(3)));
    }

    @Test
    void testApplyAddField() {
        RecordingMemoryIndex index = new RecordingMemoryIndex();
        AddMemoryDocFieldOperation operation = new AddMemoryDocFieldOperation(
                new OperationMetadata(3, "Add new_field"),
                "new_field",
                "default_value"
        );

        new IndexVersionMigrator().tryMigrate(index, List.of(operation)).join();

        assertThat(index.listUserScopesCallCount).isEqualTo(1);
        assertThat(index.listMemoriesCalls).hasSize(2);
        assertThat(index.deleteMemoriesCalls).hasSize(1);
        assertThat(index.addMemoriesCalls).hasSize(1);
        assertThat(index.addMemoriesCalls.get(0))
                .allSatisfy(document -> assertThat(document.getFields().get("new_field")).isEqualTo("default_value"));
    }

    @Test
    void testApplyRemoveField() {
        RecordingMemoryIndex index = new RecordingMemoryIndex();
        RemoveMemoryDocFieldOperation operation = new RemoveMemoryDocFieldOperation(
                new OperationMetadata(4, "Remove field1"),
                "field1"
        );

        new IndexVersionMigrator().tryMigrate(index, List.of(operation)).join();

        assertThat(index.listUserScopesCallCount).isEqualTo(1);
        assertThat(index.listMemoriesCalls).hasSize(2);
        assertThat(index.deleteMemoriesCalls).hasSize(1);
        assertThat(index.addMemoriesCalls).hasSize(1);
        assertThat(index.addMemoriesCalls.get(0))
                .allSatisfy(document -> assertThat(document.getFields()).doesNotContainKey("field1"));
    }

    @Test
    void testMigrationFailureRollback() {
        RecordingMemoryIndex index = new RecordingMemoryIndex();
        index.failAddMemories = true;
        RenameMemoryDocFieldOperation operation = new RenameMemoryDocFieldOperation(
                new OperationMetadata(1, "Rename memory_text to text"),
                "memory_text",
                "text"
        );

        boolean result = new IndexVersionMigrator().tryMigrate(index, List.of(operation)).join();

        assertThat(result).isFalse();
        assertThat(index.createBackupCallCount).isEqualTo(1);
        assertThat(index.listUserScopesCallCount).isEqualTo(1);
        assertThat(index.listMemoriesCalls.size()).isBetween(1, 2);
        assertThat(index.restoredBackupIds).containsExactly("backup123");
        assertThat(index.cleanedBackupIds).containsExactly("backup123");
        assertThat(index.documents)
                .allSatisfy(document -> {
                    assertThat(document.getFields()).containsKey("memory_text");
                    assertThat(document.getFields()).doesNotContainKey("text");
                });
    }

    private static final class RecordingMemoryIndex extends BaseMemoryIndex {
        private final List<MemoryDoc> documents = new ArrayList<>();
        private final List<MemoryDoc> backup = new ArrayList<>();
        private final List<ListMemoriesCall> listMemoriesCalls = new ArrayList<>();
        private final List<List<String>> deleteMemoriesCalls = new ArrayList<>();
        private final List<List<MemoryDoc>> addMemoriesCalls = new ArrayList<>();
        private final List<Integer> updatedSchemaVersions = new ArrayList<>();
        private final List<String> restoredBackupIds = new ArrayList<>();
        private final List<String> cleanedBackupIds = new ArrayList<>();
        private int schemaVersion;
        private int getSchemaVersionCallCount;
        private int createBackupCallCount;
        private int listUserScopesCallCount;
        private boolean failAddMemories;

        private RecordingMemoryIndex() {
            documents.add(newDoc("doc1", "Content 1", "value1"));
            documents.add(newDoc("doc2", "Content 2", "value2"));
        }

        @Override
        public void setStorageCodec(StorageCodec codec) {
        }

        @Override
        public CompletableFuture<Void> addMemories(String userId, String scopeId, List<MemoryDoc> memories) {
            addMemoriesCalls.add(copyDocs(memories));
            if (failAddMemories) {
                return CompletableFuture.failedFuture(new RuntimeException("Migration failed"));
            }
            documents.addAll(memories);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateMemories(String userId, String scopeId, List<MemoryDoc> memories) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteMemories(String userId, String scopeId, List<String> ids) {
            deleteMemoriesCalls.add(new ArrayList<>(ids));
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
            listMemoriesCalls.add(new ListMemoriesCall(userId, scopeId, offset, limit));
            if (offset >= documents.size()) {
                return CompletableFuture.completedFuture(List.of());
            }
            int end = Math.min(offset + limit, documents.size());
            return CompletableFuture.completedFuture(new ArrayList<>(documents.subList(offset, end)));
        }

        @Override
        public int getSchemaVersion() {
            getSchemaVersionCallCount++;
            return schemaVersion;
        }

        @Override
        public void updateSchemaVersion(int version) {
            updatedSchemaVersions.add(version);
            schemaVersion = version;
        }

        @Override
        public CompletableFuture<String> createBackup() {
            createBackupCallCount++;
            backup.clear();
            backup.addAll(copyDocs(documents));
            return CompletableFuture.completedFuture("backup123");
        }

        @Override
        public CompletableFuture<Void> restoreBackup(String backupId) {
            restoredBackupIds.add(backupId);
            documents.clear();
            documents.addAll(copyDocs(backup));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> cleanupBackup(String backupId) {
            cleanedBackupIds.add(backupId);
            backup.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<UserScopeKey>> listUserScopes() {
            listUserScopesCallCount++;
            return CompletableFuture.completedFuture(List.of(new UserScopeKey("user1", "scope1")));
        }

        private static MemoryDoc newDoc(String id, String memoryText, String fieldValue) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("memory_text", memoryText);
            fields.put("field1", fieldValue);
            return new MemoryDoc(
                    id,
                    "Test document " + id.substring(3),
                    "fragment",
                    ZonedDateTime.parse("2009-02-13T23:31:30Z").plusSeconds(Integer.parseInt(id.substring(3)) - 1L),
                    fields
            );
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

    private record ListMemoriesCall(String userId, String scopeId, int offset, int limit) {
    }
}
