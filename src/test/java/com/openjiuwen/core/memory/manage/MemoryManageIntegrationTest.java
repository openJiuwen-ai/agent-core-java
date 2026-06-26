/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.foundation.store.StorageCodec;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.manage.index.BaseMemoryManager;
import com.openjiuwen.core.memory.manage.index.FragmentMemoryManager;
import com.openjiuwen.core.memory.manage.index.VariableManager;
import com.openjiuwen.core.memory.manage.index.WriteManager;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.FragmentMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code TestManage} in
 * {@code tests/unit_tests/core/memory/manage/test_manage.py}.</p>
 */
class MemoryManageIntegrationTest {

    @Test
    void basicMemoryManageFlowMatchesPythonTest() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        MockMemoryIndex memoryIndex = new MockMemoryIndex();
        FragmentMemoryManager profileManager = new FragmentMemoryManager(memoryIndex, new byte[0]);
        VariableManager variableManager = new VariableManager(kvStore, new byte[0]);
        Map<String, BaseMemoryManager> managers = Map.of(
                MemoryType.USER_PROFILE.getValue(), profileManager,
                MemoryType.VARIABLE.getValue(), variableManager
        );
        WriteManager writeManager = new WriteManager(managers, memoryIndex);

        for (Seed seed : user2025Seeds()) {
            addFragmentAndVariable(writeManager, "usrZH2025", "fitnesstrackerv3", seed);
        }
        for (Seed seed : user2026Seeds()) {
            addFragmentAndVariable(writeManager, "usrZH2026", "fitnesstrackerv3", seed);
        }

        String query = "用户的职业";
        List<Map<String, Object>> results = profileManager.search("usrZH2025", "fitnesstrackerv3", query, 5, Map.of())
                .toCompletableFuture()
                .join();
        assertThat(results).hasSize(5);

        String firstId = String.valueOf(results.get(0).get("id"));
        profileManager.update("usrZH2025", "fitnesstrackerv3", firstId, "用户不是软件工程师，是系统", Map.of())
                .toCompletableFuture()
                .join();
        Map<String, Object> updated = profileManager.get("usrZH2025", "fitnesstrackerv3", firstId)
                .toCompletableFuture()
                .join();
        assertThat(updated).containsEntry("mem", "用户不是软件工程师，是系统");

        List<Map<String, Object>> listed = profileManager.listFragmentMemories("usrZH2025", "fitnesstrackerv3", 0, 10, null)
                .toCompletableFuture()
                .join();
        assertThat(listed).hasSize(6);
        for (Map<String, Object> item : listed.subList(0, 2)) {
            writeManager.deleteMemById("usrZH2025", "fitnesstrackerv3", String.valueOf(item.get("id")))
                    .toCompletableFuture()
                    .join();
        }

        results = profileManager.search("usrZH2025", "fitnesstrackerv3", query, 5, Map.of())
                .toCompletableFuture()
                .join();
        assertThat(results).hasSize(4);
        writeManager.deleteMemByUserId("usrZH2026", "fitnesstrackerv3").toCompletableFuture().join();
        results = profileManager.search("usrZH2026", "fitnesstrackerv3", query, 5, Map.of())
                .toCompletableFuture()
                .join();
        assertThat(results).isEmpty();
    }

    private static void addFragmentAndVariable(WriteManager writeManager, String userId, String scopeId, Seed seed) {
        FragmentMemoryUnit fragment = new FragmentMemoryUnit(
                seed.memoryType(),
                seed.memoryId(),
                seed.content(),
                null,
                "",
                null
        );
        writeManager.addMemories(
                userId,
                scopeId,
                Map.of(seed.memoryType().getValue(), List.of(fragment)),
                null
        ).toCompletableFuture().join();

        VariableUnit variable = new VariableUnit(seed.memoryType().name(), seed.content());
        writeManager.addMemories(
                userId,
                scopeId,
                Map.of(variable.getMemType().getValue(), List.of(variable)),
                null
        ).toCompletableFuture().join();
    }

    private static List<Seed> user2025Seeds() {
        return List.of(
                new Seed("1000", MemoryType.USER_PROFILE, "用户非常喜欢川菜，尤其是水煮鱼和麻婆豆腐"),
                new Seed("1001", MemoryType.USER_PROFILE, "用户的职业是软件工程师，居住在北京市"),
                new Seed("1002", MemoryType.USER_PROFILE, "用户的副业是抖音直播"),
                new Seed("1003", MemoryType.USER_PROFILE, "用户的银行账户余额为10000元"),
                new Seed("1004", MemoryType.USER_PROFILE, "用户的朋友圈中有50个好友"),
                new Seed("1005", MemoryType.USER_PROFILE, "用户的宠物是一只金毛犬")
        );
    }

    private static List<Seed> user2026Seeds() {
        return List.of(
                new Seed("019e0ad3b5acb22c931f1010", MemoryType.USER_PROFILE, "用户喜欢打篮球和阅读历史小说"),
                new Seed("019e0ad3b5acb22c931f1011", MemoryType.USER_PROFILE, "用户的生日是1990年1月1日"),
                new Seed("019e0ad3b5acb22c931f1012", MemoryType.USER_PROFILE, "用户的汽车型号是特斯拉Model 3"),
                new Seed("019e0ad3b5acb22c931f1013", MemoryType.USER_PROFILE, "用户在Twitter上有200个关注者")
        );
    }

    private record Seed(String memoryId, MemoryType memoryType, String content) {
    }

    private static final class MockMemoryIndex extends BaseMemoryIndex {
        private final Map<String, Map<String, Map<String, MemoryDoc>>> data = new LinkedHashMap<>();
        private final Map<String, Map<String, Object>> backups = new LinkedHashMap<>();
        private int schemaVersion;

        @Override
        public void setStorageCodec(StorageCodec codec) {
        }

        @Override
        public CompletableFuture<Void> addMemories(String userId, String scopeId, List<MemoryDoc> memories) {
            ensureUserScope(userId, scopeId);
            for (MemoryDoc doc : memories) {
                data.get(userId).get(scopeId).put(doc.getId(), doc);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateMemories(String userId, String scopeId, List<MemoryDoc> memories) {
            if (memories.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            List<String> ids = memories.stream().map(MemoryDoc::getId).toList();
            deleteMemories(userId, scopeId, ids).join();
            return addMemories(userId, scopeId, memories);
        }

        @Override
        public CompletableFuture<Void> deleteMemories(String userId, String scopeId, List<String> ids) {
            if (data.containsKey(userId) && data.get(userId).containsKey(scopeId)) {
                for (String id : ids) {
                    data.get(userId).get(scopeId).remove(id);
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteByUser(String userId) {
            data.remove(userId);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteByScope(String scopeId) {
            for (Map<String, Map<String, MemoryDoc>> scopeData : data.values()) {
                scopeData.remove(scopeId);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteByUserAndScope(String userId, String scopeId) {
            if (data.containsKey(userId)) {
                data.get(userId).remove(scopeId);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<MemorySearchResult>> search(String userId,
                                                                  String scopeId,
                                                                  String query,
                                                                  List<String> memTypes,
                                                                  int topK) {
            if (!data.containsKey(userId) || !data.get(userId).containsKey(scopeId)) {
                return CompletableFuture.completedFuture(List.of());
            }
            List<MemorySearchResult> results = new ArrayList<>();
            for (MemoryDoc doc : data.get(userId).get(scopeId).values()) {
                if (memTypes != null && !memTypes.isEmpty() && !memTypes.contains(doc.getType())) {
                    continue;
                }
                double score = doc.getText().contains(query) ? 1.0d : 0.5d;
                results.add(new MemorySearchResult(doc, score));
            }
            results.sort(Comparator.comparingDouble(MemorySearchResult::score).reversed());
            return CompletableFuture.completedFuture(results.subList(0, Math.min(topK, results.size())));
        }

        @Override
        public CompletableFuture<MemoryDoc> getById(String userId, String scopeId, String memId) {
            if (data.containsKey(userId) && data.get(userId).containsKey(scopeId)) {
                return CompletableFuture.completedFuture(data.get(userId).get(scopeId).get(memId));
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<MemoryDoc>> listMemories(String userId,
                                                               String scopeId,
                                                               int offset,
                                                               int limit,
                                                               List<String> memTypes) {
            if (!data.containsKey(userId) || !data.get(userId).containsKey(scopeId)) {
                return CompletableFuture.completedFuture(List.of());
            }
            List<MemoryDoc> docs = new ArrayList<>(data.get(userId).get(scopeId).values());
            docs.removeIf(doc -> memTypes != null && !memTypes.isEmpty() && !memTypes.contains(doc.getType()));
            docs.sort(Comparator.comparing(MemoryDoc::getTimestamp).reversed());
            int from = Math.min(offset, docs.size());
            int to = Math.min(from + limit, docs.size());
            return CompletableFuture.completedFuture(docs.subList(from, to));
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
            String backupId = UUID.randomUUID().toString();
            backups.put(backupId, Map.of("schema_version", schemaVersion));
            return CompletableFuture.completedFuture(backupId);
        }

        @Override
        public CompletableFuture<Void> restoreBackup(String backupId) {
            if (!backups.containsKey(backupId)) {
                throw new IllegalArgumentException("Backup " + backupId + " not found");
            }
            schemaVersion = (int) backups.get(backupId).get("schema_version");
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> cleanupBackup(String backupId) {
            backups.remove(backupId);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<UserScopeKey>> listUserScopes() {
            List<UserScopeKey> scopes = new ArrayList<>();
            for (Map.Entry<String, Map<String, Map<String, MemoryDoc>>> userEntry : data.entrySet()) {
                for (String scopeId : userEntry.getValue().keySet()) {
                    scopes.add(new UserScopeKey(userEntry.getKey(), scopeId));
                }
            }
            return CompletableFuture.completedFuture(scopes);
        }

        private void ensureUserScope(String userId, String scopeId) {
            data.computeIfAbsent(userId, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(scopeId, ignored -> new LinkedHashMap<>());
        }
    }
}
