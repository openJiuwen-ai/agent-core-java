/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.foundation.store.StorageCodec;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.manage.index.BaseMemoryManager;
import com.openjiuwen.core.memory.manage.index.VariableManager;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Focused missing-test coverage for variable lookups through {@link SearchManager}.
 *
 * <p>Mirrors Python's {@code TestSearchManager} in
 * {@code tests/unit_tests/core/memory/test_search_manager.py}.</p>
 */
class SearchManagerMissingTest {
    private static final String USER_ID = "test_user_id";
    private static final String SCOPE_ID = "test_scope_id";

    @Test
    void getUserVariableWithEmptyVarNameReturnsNull() {
        SearchFixture fixture = fixture();

        String result = fixture.searchManager.getUserVariable(USER_ID, SCOPE_ID, "").toCompletableFuture().join();

        assertThat(result).isNull();
    }

    @Test
    void getUserVariableWithWhitespaceVarNameReturnsNull() {
        SearchFixture fixture = fixture();

        String result = fixture.searchManager.getUserVariable(USER_ID, SCOPE_ID, "   ").toCompletableFuture().join();

        assertThat(result).isNull();
    }

    @Test
    void getUserVariableWithValidVarNameReturnsValue() {
        SearchFixture fixture = fixture();
        addVariable(fixture.variableManager, "test_variable", "test_value");

        String result = fixture.searchManager.getUserVariable(USER_ID, SCOPE_ID, "test_variable")
                .toCompletableFuture()
                .join();

        assertThat(result).isEqualTo("test_value");
    }

    @Test
    void longTermMemoryGetVariablesWithEmptyStringNameReturnsEmptyKeyNullValue() {
        SearchFixture fixture = fixture();
        addVariable(fixture.variableManager, "test_variable", "test_value");
        LongTermMemory memory = new LongTermMemory();
        setField(memory, "searchManager", fixture.searchManager);
        setField(memory, "variableManager", fixture.variableManager);

        Map<String, String> result = memory.getVariables("", USER_ID, SCOPE_ID).join();

        assertThat(result).containsKey("");
        assertThat(result.get("")).isNull();
    }

    private static SearchFixture fixture() {
        VariableManager variableManager = new VariableManager(new InMemoryKVStore(), new byte[0]);
        Map<String, BaseMemoryManager> managers = new LinkedHashMap<>();
        managers.put(MemoryType.VARIABLE.getValue(), variableManager);
        SearchManager searchManager = new SearchManager(managers, new byte[0], new NoopMemoryIndex());
        return new SearchFixture(variableManager, searchManager);
    }

    private static void addVariable(VariableManager variableManager, String name, String value) {
        Map<String, List<BaseMemoryUnit>> memories = new LinkedHashMap<>();
        memories.put(MemoryType.VARIABLE.getValue(), List.of(new VariableUnit(name, value)));
        variableManager.addMemories(USER_ID, SCOPE_ID, memories, null, Map.of()).toCompletableFuture().join();
    }

    private static void setField(LongTermMemory memory, String name, Object value) {
        try {
            Field field = LongTermMemory.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(memory, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to configure LongTermMemory test field " + name, exception);
        }
    }

    private record SearchFixture(VariableManager variableManager, SearchManager searchManager) {
    }

    private static final class NoopMemoryIndex extends BaseMemoryIndex {
        @Override
        public void setStorageCodec(StorageCodec codec) {
        }

        @Override
        public CompletableFuture<Void> addMemories(String userId, String scopeId, List<MemoryDoc> memories) {
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
            return CompletableFuture.completedFuture(null);
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
