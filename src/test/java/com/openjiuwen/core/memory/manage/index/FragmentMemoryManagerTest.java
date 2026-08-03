/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.foundation.store.StorageCodec;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.FragmentMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.OperationType;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class FragmentMemoryManagerTest {

    @Test
    void addSingleNewMemorySkipsCheckerAndWritesDocument() {
        InMemoryIndex index = new InMemoryIndex();
        FragmentMemoryManager manager = new FragmentMemoryManager(index, null);
        FragmentMemoryUnit unit = fragment("m1", "new memory", MemoryType.USER_PROFILE, "msg-1", null);

        List<BaseMemoryUnit> result = manager.addMemories(
                "user-1",
                "scope-1",
                Map.of(MemoryType.USER_PROFILE.getValue(), List.of(unit)),
                null,
                Map.of()
        ).toCompletableFuture().join();

        assertThat(result).containsExactly(unit);
        assertThat(index.addedDocs).hasSize(1);
        MemoryDoc doc = index.addedDocs.get(0);
        assertThat(doc.getId()).isEqualTo("m1");
        assertThat(doc.getText()).isEqualTo("new memory");
        assertThat(doc.getType()).isEqualTo("user_profile");
        assertThat(doc.getFields()).containsEntry("source_id", "msg-1");
    }

    @Test
    void updateAndDeleteOperationsReturnProcessResults() {
        InMemoryIndex index = new InMemoryIndex();
        FragmentMemoryManager manager = new FragmentMemoryManager(index, null);
        FragmentMemoryUnit update = fragment("u1", "updated", MemoryType.SEMANTIC_MEMORY, null, OperationType.UPDATE);
        FragmentMemoryUnit delete = fragment("d1", "", MemoryType.SEMANTIC_MEMORY, null, OperationType.DELETE);

        List<BaseMemoryUnit> result = manager.addMemories(
                "user-1",
                "scope-1",
                Map.of(MemoryType.SEMANTIC_MEMORY.getValue(), List.of(update, delete)),
                null,
                Map.of()
        ).toCompletableFuture().join();

        assertThat(index.updatedDocs).extracting(MemoryDoc::getId).containsExactly("u1");
        assertThat(index.deletedIds).containsExactly("d1");
        assertThat(result).containsExactly(delete, update);
    }

    @Test
    void updateEntryIsRemovedWhenSameMemoryIsDeleted() {
        FragmentMemoryUnit update = fragment("same", "updated", MemoryType.USER_PROFILE, null, OperationType.UPDATE);
        Map<String, FragmentMemoryUnit> processResult = new LinkedHashMap<>();
        processResult.put("same", update);

        FragmentMemoryManager.removeUpdateEntriesFromProcessResult(Set.of("same"), processResult);

        assertThat(processResult).isEmpty();
    }

    @Test
    void searchUsesDefaultFragmentTypesAndSortsByScore() {
        InMemoryIndex index = new InMemoryIndex();
        index.searchResults = List.of(
                new BaseMemoryIndex.MemorySearchResult(
                        new MemoryDoc("low", "low", MemoryType.USER_PROFILE.getValue(), ZonedDateTime.now(), Map.of()),
                        0.2d
                ),
                new BaseMemoryIndex.MemorySearchResult(
                        new MemoryDoc("high", "high", MemoryType.SEMANTIC_MEMORY.getValue(), ZonedDateTime.now(), Map.of()),
                        0.9d
                )
        );
        FragmentMemoryManager manager = new FragmentMemoryManager(index, null);

        List<Map<String, Object>> result = manager.search("user-1", "scope-1", "query", 1, Map.of())
                .toCompletableFuture().join();

        assertThat(index.lastSearchMemTypes).containsExactly(
                MemoryType.USER_PROFILE.getValue(),
                MemoryType.SEMANTIC_MEMORY.getValue(),
                MemoryType.EPISODIC_MEMORY.getValue()
        );
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("id", "high").containsEntry("score", 0.9d);
    }

    @Test
    void listFragmentMemoriesRejectsNonFragmentTypeAndSortsDescending() {
        InMemoryIndex index = new InMemoryIndex();
        index.listDocs = List.of(
                new MemoryDoc("a", "alpha", MemoryType.USER_PROFILE.getValue(),
                        ZonedDateTime.parse("2025-01-01T00:00:00Z"), Map.of()),
                new MemoryDoc("b", "zeta", MemoryType.SEMANTIC_MEMORY.getValue(),
                        ZonedDateTime.parse("2024-01-01T00:00:00Z"), Map.of())
        );
        FragmentMemoryManager manager = new FragmentMemoryManager(index, null);

        assertThat(manager.listFragmentMemories("u", "s", 0, 100, MemoryType.VARIABLE)
                .toCompletableFuture().join()).isEmpty();
        List<Map<String, Object>> result = manager.listFragmentMemories("u", "s", 0, 100, null)
                .toCompletableFuture().join();

        assertThat(result).extracting(item -> item.get("id")).containsExactly("b", "a");
    }

    @Test
    void helperMethodsMirrorPythonShapes() {
        ZonedDateTime parsed = FragmentMemoryManager.parseTimestamp("2025-01-02 03-04-05");
        assertThat(parsed.getZone()).isEqualTo(java.time.ZoneOffset.UTC);
        assertThat(parsed.getYear()).isEqualTo(2025);
        assertThat(parsed.getMinute()).isEqualTo(4);

        List<Map<String, Object>> conflicts = List.of(
                Map.of("id", 0, "text", "new", "event", "add"),
                Map.of("id", 2, "text", "old", "event", "delete")
        );
        List<Map<String, Object>> processed = FragmentMemoryManager.processConflictInfo(conflicts, Map.of(2, "mem-2"));

        assertThat(processed.get(0)).containsEntry("id", "-1");
        assertThat(processed.get(1)).containsEntry("id", "mem-2");
    }

    private static FragmentMemoryUnit fragment(String id,
                                               String content,
                                               MemoryType memoryType,
                                               String sourceId,
                                               OperationType operationType) {
        return new FragmentMemoryUnit(
                memoryType,
                id,
                content,
                sourceId,
                "2025-01-01 00:00:00",
                operationType
        );
    }

    private static final class InMemoryIndex extends BaseMemoryIndex {
        private List<BaseMemoryIndex.MemorySearchResult> searchResults = List.of();
        private List<MemoryDoc> listDocs = List.of();
        private List<String> lastSearchMemTypes = List.of();
        private final List<MemoryDoc> addedDocs = new ArrayList<>();
        private final List<MemoryDoc> updatedDocs = new ArrayList<>();
        private final List<String> deletedIds = new ArrayList<>();
        private final Map<String, MemoryDoc> docsById = new LinkedHashMap<>();

        @Override
        public void setStorageCodec(StorageCodec codec) {
        }

        @Override
        public CompletableFuture<Void> addMemories(String userId, String scopeId, List<MemoryDoc> memories) {
            addedDocs.addAll(memories);
            for (MemoryDoc doc : memories) {
                docsById.put(doc.getId(), doc);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateMemories(String userId, String scopeId, List<MemoryDoc> memories) {
            updatedDocs.addAll(memories);
            for (MemoryDoc doc : memories) {
                docsById.put(doc.getId(), doc);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteMemories(String userId, String scopeId, List<String> ids) {
            deletedIds.addAll(ids);
            for (String id : ids) {
                docsById.remove(id);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteByUser(String userId) {
            docsById.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteByScope(String scopeId) {
            docsById.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteByUserAndScope(String userId, String scopeId) {
            docsById.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<MemorySearchResult>> search(String userId,
                                                                  String scopeId,
                                                                  String query,
                                                                  List<String> memTypes,
                                                                  int topK) {
            lastSearchMemTypes = new ArrayList<>(memTypes);
            return CompletableFuture.completedFuture(searchResults);
        }

        @Override
        public CompletableFuture<MemoryDoc> getById(String userId, String scopeId, String memId) {
            return CompletableFuture.completedFuture(docsById.get(memId));
        }

        @Override
        public CompletableFuture<List<MemoryDoc>> listMemories(String userId,
                                                               String scopeId,
                                                               int offset,
                                                               int limit,
                                                               List<String> memTypes) {
            return CompletableFuture.completedFuture(listDocs);
        }

        @Override
        public CompletableFuture<Void> cleanupBackup(String backupId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<UserScopeKey>> listUserScopes() {
            return CompletableFuture.completedFuture(List.of(new UserScopeKey("u", "s")));
        }
    }
}
