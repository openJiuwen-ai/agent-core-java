
package com.openjiuwen.core.memory.manage.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.memory.manage.index.BaseMemoryManager;
import com.openjiuwen.core.memory.manage.index.FragmentMemoryManager;
import com.openjiuwen.core.memory.manage.index.SummaryManager;
import com.openjiuwen.core.memory.manage.index.VariableManager;
import com.openjiuwen.core.memory.manage.mem_model.DataIdManager;
import com.openjiuwen.core.memory.manage.mem_model.FragmentMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.UserMemStore;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;
import com.openjiuwen.core.memory.support.TestInMemoryKVStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class SearchManagerTest {
    private static final byte[] CRYPTO_KEY = "1234567890abcdef1234567890123456".getBytes();

    private SearchManager searchManager;
    private VariableManager variableManager;
    private FragmentMemoryManager fragmentMemoryManager;
    private SummaryManager summaryManager;
    private UserMemStore userMemStore;

    @BeforeEach
    void setUp() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        variableManager = new VariableManager(kvStore, CRYPTO_KEY);
        userMemStore = new UserMemStore(kvStore);
        fragmentMemoryManager = new FragmentMemoryManager(userMemStore, new DataIdManager(), CRYPTO_KEY);
        summaryManager = new SummaryManager(userMemStore, CRYPTO_KEY);
        Map<String, BaseMemoryManager> managers = new LinkedHashMap<>();
        managers.put(MemoryType.USER_PROFILE.getValue(), fragmentMemoryManager);
        managers.put(MemoryType.SEMANTIC_MEMORY.getValue(), fragmentMemoryManager);
        managers.put(MemoryType.EPISODIC_MEMORY.getValue(), fragmentMemoryManager);
        managers.put(MemoryType.VARIABLE.getValue(), variableManager);
        managers.put(MemoryType.SUMMARY.getValue(), summaryManager);
        searchManager = new SearchManager(managers, userMemStore, CRYPTO_KEY);
    }

    @Test
    void getUserVariableReturnsNullForEmptyName() {
        assertNull(searchManager.getUserVariable("user", "scope", ""));
    }

    @Test
    void getUserVariableReturnsNullForWhitespaceName() {
        assertNull(searchManager.getUserVariable("user", "scope", "   "));
    }

    @Test
    void getUserVariableReturnsStoredValue() {
        VariableUnit variableUnit =
            VariableUnit.builder().variableName("test_variable").variableMem("test_value").build();

        variableManager.addMemories("user", "scope", List.of(variableUnit), null, Map.of());

        assertEquals("test_value", searchManager.getUserVariable("user", "scope", "test_variable"));
    }

    @Test
    void listUserProfileAggregatesTypedFragmentMemories() {
        fragmentMemoryManager.addMemories("user", "scope", List.of(
                FragmentMemoryUnit.builder().memId("1").memType(MemoryType.USER_PROFILE).content("profile").build(),
                FragmentMemoryUnit.builder().memId("2").memType(MemoryType.SEMANTIC_MEMORY).content("semantic").build(),
                FragmentMemoryUnit.builder().memId("3").memType(MemoryType.EPISODIC_MEMORY).content("episodic")
                        .build()),
                null,
                Map.of("semantic_store",
                        new com.openjiuwen.core.memory.manage.mem_model.SemanticStore(
                                new com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore(
                                        "search_manager_test"),
                                new com.openjiuwen.core.retrieval.embedding.HashEmbedding())));

        List<Map<String, Object>> result = searchManager.listUserProfile("user", "scope");

        assertEquals(3, result.size());
    }

    @Test
    void searchWithoutTypeTraversesAllUniqueManagersAndAppliesSortThresholdLimit() {
        RecordingManager fragment =
            new RecordingManager(List.of(result("fragment-low", 0.4), result("fragment-high", 0.9)));
        RecordingManager summary = new RecordingManager(List.of(result("summary", 0.8)));
        RecordingManager variable = new RecordingManager(null);
        Map<String, BaseMemoryManager> managers = new LinkedHashMap<>();
        managers.put(MemoryType.USER_PROFILE.getValue(), fragment);
        managers.put(MemoryType.SEMANTIC_MEMORY.getValue(), fragment);
        managers.put(MemoryType.EPISODIC_MEMORY.getValue(), fragment);
        managers.put(MemoryType.SUMMARY.getValue(), summary);
        managers.put(MemoryType.VARIABLE.getValue(), variable);
        SearchManager manager = new SearchManager(managers, userMemStore, CRYPTO_KEY);

        List<Map<String, Object>> results = manager.search(
                SearchParams.builder().userId("user").scopeId("scope").query("query").topK(2).threshold(0.5).build(),
                null);

        assertEquals(List.of("fragment-high", "summary"),
                results.stream().map(item -> String.valueOf(item.get("id"))).toList());
        assertEquals(1, fragment.calls);
        assertEquals(1, summary.calls);
        assertEquals(1, variable.calls);
    }

    @Test
    void searchRejectsInvalidSearchType() {
        assertThrows(BaseError.class, () -> searchManager.search(
                SearchParams.builder().userId("user").scopeId("scope").query("query").searchType("invalid").build(),
                null));
    }

    @Test
    void searchRejectsKnownTypeWhenManagerNotInitialized() {
        SearchManager manager = new SearchManager(Map.of(), userMemStore, CRYPTO_KEY);

        assertThrows(BaseError.class, () -> manager.search(SearchParams.builder().userId("user").scopeId("scope")
                .query("query").searchType(MemoryType.SUMMARY.getValue()).build(), null));
    }

    @Test
    void listUserSummaryDelegatesToSummaryManager() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        UserMemStore store = new UserMemStore(kvStore);
        SummaryManager summary = new SummaryManager(store, new byte[0]);
        SearchManager manager = new SearchManager(Map.of(MemoryType.SUMMARY.getValue(), summary), store, new byte[0]);
        store.write("user", "scope", "000000000000000000000011", Map.of("id", "000000000000000000000011", "mem_type",
                MemoryType.SUMMARY.getValue(), "mem", "alpha", "timestamp", "2026-05-11T01:00:00Z"));
        store.write("user", "scope", "000000000000000000000012", Map.of("id", "000000000000000000000012", "mem_type",
                MemoryType.SUMMARY.getValue(), "mem", "zulu", "timestamp", "2026-05-11T02:00:00Z"));

        List<Map<String, Object>> result = manager.listUserSummary("user", "scope");

        assertEquals(List.of("zulu", "alpha"), result.stream().map(item -> String.valueOf(item.get("mem"))).toList());
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
        public void addMemories(String userId, String scopeId,
                List<? extends com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit> memories,
                Map.Entry<String, Model> llm, Map<String, Object> kwargs) {
        }

        @Override
        public void update(String userId, String scopeId, String memId, String newMemory, Map<String, Object> kwargs) {
        }

        @Override
        public boolean delete(String userId, String scopeId, String memId, Map<String, Object> kwargs) {
            return false;
        }

        @Override
        public boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs) {
            return false;
        }

        @Override
        public Map<String, Object> get(String userId, String scopeId, String memId) {
            return null;
        }

        @Override
        public List<Map<String, Object>> search(String userId, String scopeId, String query, int topK,
                Map<String, Object> kwargs) {
            calls++;
            return results == null ? null : new ArrayList<>(results);
        }
    }
}
