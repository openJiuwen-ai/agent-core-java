package com.openjiuwen.core.memory.manage.search;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.foundation.store.StorageCodec;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.manage.index.BaseMemoryManager;
import com.openjiuwen.core.memory.manage.index.FragmentMemoryManager;
import com.openjiuwen.core.memory.manage.index.SummaryManager;
import com.openjiuwen.core.memory.manage.index.VariableManager;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.FragmentMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.UserMemStore;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;
import com.openjiuwen.core.foundation.llm.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchManagerTest {

    private static final byte[] CRYPTO_KEY = "1234567890abcdef1234567890123456".getBytes();

    private SearchManager searchManager;
    private VariableManager variableManager;
    private FragmentMemoryManager fragmentMemoryManager;
    private SummaryManager summaryManager;
    private UserMemStore userMemStore;
    private BaseMemoryIndex memoryIndex;

    @BeforeEach
    void setUp() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        variableManager = new VariableManager(kvStore, CRYPTO_KEY);
        userMemStore = new UserMemStore(kvStore);
        memoryIndex = new TestMemoryIndex(kvStore);
        fragmentMemoryManager = new FragmentMemoryManager(memoryIndex, CRYPTO_KEY);
        summaryManager = new SummaryManager(memoryIndex, CRYPTO_KEY);
        Map<String, BaseMemoryManager> managers = new LinkedHashMap<>();
        managers.put(MemoryType.USER_PROFILE.getValue(), fragmentMemoryManager);
        managers.put(MemoryType.SEMANTIC_MEMORY.getValue(), fragmentMemoryManager);
        managers.put(MemoryType.EPISODIC_MEMORY.getValue(), fragmentMemoryManager);
        managers.put(MemoryType.VARIABLE.getValue(), variableManager);
        managers.put(MemoryType.SUMMARY.getValue(), summaryManager);
        searchManager = new SearchManager(managers, CRYPTO_KEY, memoryIndex);
    }

    @Test
    void getUserVariableReturnsNullForEmptyName() {
        assertNull(searchManager.getUserVariable("user", "scope", "").toCompletableFuture().join());
    }

    @Test
    void getUserVariableReturnsNullForWhitespaceName() {
        assertNull(searchManager.getUserVariable("user", "scope", "   ").toCompletableFuture().join());
    }

    @Test
    void getUserVariableReturnsStoredValue() {
        VariableUnit variableUnit = new VariableUnit("test_variable", "test_value");

        Map<String, List<BaseMemoryUnit>> memories = Map.of(
                MemoryType.VARIABLE.getValue(), List.of(variableUnit)
        );
        variableManager.addMemories("user", "scope", memories, null, Map.of()).toCompletableFuture().join();

        assertEquals("test_value", searchManager.getUserVariable("user", "scope", "test_variable").toCompletableFuture().join());
    }

    @Test
    void listUserProfileAggregatesTypedFragmentMemories() {
        Map<String, List<BaseMemoryUnit>> memories = Map.of(
                MemoryType.USER_PROFILE.getValue(), List.of(
                        new FragmentMemoryUnit(MemoryType.USER_PROFILE, "1", "profile", null, "", null),
                        new FragmentMemoryUnit(MemoryType.SEMANTIC_MEMORY, "2", "semantic", null, "", null),
                        new FragmentMemoryUnit(MemoryType.EPISODIC_MEMORY, "3", "episodic", null, "", null)
                )
        );
        fragmentMemoryManager.addMemories("user", "scope", memories, null, Map.of()).toCompletableFuture().join();

        List<Map<String, Object>> result = searchManager.listUserProfile("user", "scope").toCompletableFuture().join();

        assertEquals(3, result.size());
    }

    @Test
    void searchWithoutTypeTraversesAllUniqueManagersAndAppliesSortThresholdLimit() {
        RecordingManager fragment = new RecordingManager(List.of(
                result("fragment-low", 0.4),
                result("fragment-high", 0.9)
        ));
        RecordingManager summary = new RecordingManager(List.of(result("summary", 0.8)));
        RecordingManager variable = new RecordingManager(null);
        Map<String, BaseMemoryManager> managers = new LinkedHashMap<>();
        managers.put(MemoryType.USER_PROFILE.getValue(), fragment);
        managers.put(MemoryType.SEMANTIC_MEMORY.getValue(), fragment);
        managers.put(MemoryType.EPISODIC_MEMORY.getValue(), fragment);
        managers.put(MemoryType.SUMMARY.getValue(), summary);
        managers.put(MemoryType.VARIABLE.getValue(), variable);
        SearchManager manager = new SearchManager(managers, CRYPTO_KEY, null);

        List<Map<String, Object>> results = manager.search(SearchParams.builder()
                .userId("user")
                .scopeId("scope")
                .query("query")
                .topK(2)
                .threshold(0.5)
                .build()).toCompletableFuture().join();

        assertEquals(List.of("fragment-high", "summary"), results.stream()
                .map(item -> String.valueOf(item.get("id")))
                .toList());
        assertEquals(1, fragment.calls);
        assertEquals(1, summary.calls);
        assertEquals(1, variable.calls);
    }

    @Test
    void searchRejectsInvalidSearchType() {
        assertThrows(BaseError.class, () -> searchManager.search(SearchParams.builder()
                .userId("user")
                .scopeId("scope")
                .query("query")
                .searchType(List.of("invalid"))
                .build()).toCompletableFuture().join());
    }

    @Test
    void searchRejectsKnownTypeWhenManagerNotInitialized() {
        SearchManager manager = new SearchManager(Map.of(), CRYPTO_KEY, null);

        assertThrows(BaseError.class, () -> manager.search(SearchParams.builder()
                .userId("user")
                .scopeId("scope")
                .query("query")
                .searchType(List.of(MemoryType.SUMMARY.getValue()))
                .build()).toCompletableFuture().join());
    }

    @Test
    void listUserSummaryDelegatesToSummaryManager() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        UserMemStore store = new UserMemStore(kvStore);
        BaseMemoryIndex idx = new TestMemoryIndex(kvStore);
        SummaryManager summary = new SummaryManager(idx, new byte[0]);
        SearchManager manager = new SearchManager(Map.of(MemoryType.SUMMARY.getValue(), summary), new byte[0], idx);
        store.write("user", "scope", "000000000000000000000011", Map.of(
                "id", "000000000000000000000011",
                "mem_type", MemoryType.SUMMARY.getValue(),
                "mem", "alpha",
                "timestamp", "2026-05-11T01:00:00Z"
        )).join();
        store.write("user", "scope", "000000000000000000000012", Map.of(
                "id", "000000000000000000000012",
                "mem_type", MemoryType.SUMMARY.getValue(),
                "mem", "zulu",
                "timestamp", "2026-05-11T02:00:00Z"
        )).join();

        List<Map<String, Object>> result = manager.listUserSummary("user", "scope").toCompletableFuture().join();

        assertEquals(List.of("zulu", "alpha"), result.stream()
                .map(item -> String.valueOf(item.get("mem")))
                .toList());
    }

    @Test
    void listUserMemWithoutTypeDelegatesAsUnfilteredQuery() {
        TestMemoryIndex idx = new TestMemoryIndex(new InMemoryKVStore());
        idx.addListedDoc(new MemoryDoc("profile", "profile memory", MemoryType.USER_PROFILE.getValue(), null, Map.of()));
        idx.addListedDoc(new MemoryDoc("semantic", "semantic memory", MemoryType.SEMANTIC_MEMORY.getValue(), null, Map.of()));
        SearchManager manager = new SearchManager(Map.of(), CRYPTO_KEY, idx);

        List<Map<String, Object>> result = manager.listUserMem("user", "scope", 10, 1).toCompletableFuture().join();

        assertNull(idx.lastListMemTypes);
        assertEquals(List.of("profile memory", "semantic memory"), result.stream()
                .map(item -> String.valueOf(item.get("mem")))
                .toList());
    }

    @Test
    void listUserMemWithEmptyTypeDelegatesAsUnfilteredQuery() {
        TestMemoryIndex idx = new TestMemoryIndex(new InMemoryKVStore());
        idx.addListedDoc(new MemoryDoc("profile", "profile memory", MemoryType.USER_PROFILE.getValue(), null, Map.of()));
        idx.addListedDoc(new MemoryDoc("semantic", "semantic memory", MemoryType.SEMANTIC_MEMORY.getValue(), null, Map.of()));
        SearchManager manager = new SearchManager(Map.of(), CRYPTO_KEY, idx);

        List<Map<String, Object>> result = manager.listUserMem("user", "scope", 10, 1, "").toCompletableFuture().join();

        assertNull(idx.lastListMemTypes);
        assertEquals(List.of("profile memory", "semantic memory"), result.stream()
                .map(item -> String.valueOf(item.get("mem")))
                .toList());
    }

    @Test
    void listUserMemWithTypeDelegatesAsSingleTypeFilter() {
        TestMemoryIndex idx = new TestMemoryIndex(new InMemoryKVStore());
        idx.addListedDoc(new MemoryDoc("profile", "profile memory", MemoryType.USER_PROFILE.getValue(), null, Map.of()));
        idx.addListedDoc(new MemoryDoc("semantic", "semantic memory", MemoryType.SEMANTIC_MEMORY.getValue(), null, Map.of()));
        SearchManager manager = new SearchManager(Map.of(), CRYPTO_KEY, idx);

        List<Map<String, Object>> result = manager.listUserMem(
                "user",
                "scope",
                10,
                1,
                MemoryType.USER_PROFILE.getValue()
        ).toCompletableFuture().join();

        assertEquals(List.of(MemoryType.USER_PROFILE.getValue()), idx.lastListMemTypes);
        assertEquals(List.of("profile memory"), result.stream()
                .map(item -> String.valueOf(item.get("mem")))
                .toList());
    }

    private static Map<String, Object> result(String id, double score) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("score", score);
        return item;
    }

    private static final class RecordingManager extends BaseMemoryManager {
        private final List<Map<String, Object>> results;
        private int calls;

        private RecordingManager(List<Map<String, Object>> results) {
            this.results = results;
        }

        @Override
        public CompletionStage<List<BaseMemoryUnit>> addMemories(String userId, String scopeId,
                                                                  Map<String, List<BaseMemoryUnit>> memories,
                                                                  Model llm, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<Boolean> update(String userId, String scopeId, String memId, String newMemory,
                                               Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletionStage<Boolean> delete(String userId, String scopeId, String memId,
                                               Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletionStage<Boolean> deleteByUserId(String userId, String scopeId,
                                                       Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletionStage<Map<String, Object>> get(String userId, String scopeId, String memId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<Map<String, Object>>> search(String userId, String scopeId, String query,
                                                                 int topK, Map<String, Object> kwargs) {
            calls++;
            return CompletableFuture.completedFuture(results == null ? null : new ArrayList<>(results));
        }
    }

    private static final class TestMemoryIndex extends BaseMemoryIndex {
        private final BaseKVStore kvStore;
        private final List<MemoryDoc> listedDocs = new ArrayList<>();
        private List<String> lastListMemTypes;

        TestMemoryIndex(BaseKVStore kvStore) {
            this.kvStore = kvStore;
        }

        private void addListedDoc(MemoryDoc doc) {
            listedDocs.add(doc);
        }

        @Override
        public void setStorageCodec(StorageCodec codec) {}

        @Override
        public CompletableFuture<Void> addMemories(String userId, String scopeId, List<MemoryDoc> memories) {
            listedDocs.addAll(memories);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateMemories(String userId, String scopeId, List<MemoryDoc> memories) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteMemories(String userId, String scopeId, List<String> ids) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteByUser(String userId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteByScope(String scopeId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteByUserAndScope(String userId, String scopeId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<MemorySearchResult>> search(String userId, String scopeId, String query,
                                                                   List<String> memTypes, int topK) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<MemoryDoc> getById(String userId, String scopeId, String memId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<MemoryDoc>> listMemories(String userId, String scopeId, int offset, int limit,
                                                               List<String> memTypes) {
            lastListMemTypes = memTypes;
            List<MemoryDoc> sourceDocs = listedDocs.isEmpty()
                    ? loadDocsFromUserMemStore(userId, scopeId)
                    : listedDocs;
            List<MemoryDoc> docs = sourceDocs.stream()
                    .filter(doc -> memTypes == null || memTypes.contains(doc.getType()))
                    .toList();
            int start = Math.max(0, Math.min(offset, docs.size()));
            int end = limit <= 0 ? docs.size() : Math.min(docs.size(), start + limit);
            return CompletableFuture.completedFuture(new ArrayList<>(docs.subList(start, end)));
        }

        private List<MemoryDoc> loadDocsFromUserMemStore(String userId, String scopeId) {
            List<Map<String, Object>> rows = new UserMemStore(kvStore).getInRange(userId, scopeId, 0, Integer.MAX_VALUE)
                    .join();
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<MemoryDoc> docs = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> fields = new LinkedHashMap<>(row);
                String id = String.valueOf(row.getOrDefault("id", ""));
                String text = String.valueOf(row.getOrDefault("mem", ""));
                String type = String.valueOf(row.getOrDefault("mem_type", ""));
                Object timestamp = row.get("timestamp");
                ZonedDateTime parsedTimestamp = timestamp == null ? null : ZonedDateTime.parse(String.valueOf(timestamp));
                fields.remove("id");
                fields.remove("mem");
                fields.remove("mem_type");
                fields.remove("timestamp");
                docs.add(new MemoryDoc(id, text, type, parsedTimestamp, fields));
            }
            return docs;
        }

        @Override
        public CompletableFuture<Void> cleanupBackup(String backupId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<UserScopeKey>> listUserScopes() {
            return CompletableFuture.completedFuture(List.of());
        }
    }

}
