/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.common.KvPrefixRegistry;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Focused smoke tests for the isolated VariableManager candidate.
 *
 * <p>Mirrors Python's {@code VariableManager} in
 * {@code openjiuwen/core/memory/manage/index/variable_manager.py}.</p>
 */
public final class VariableManagerCandidateSmokeTest {

    private VariableManagerCandidateSmokeTest() {
    }

    public static void main(String[] args) {
        registersPrefixes();
        addMemoriesStoresOnlyVariableUnitsAndReturnsVariableList();
        queryVariableSupportsAllUserVariablesAndSessions();
        updateAndDeleteUserVariableRespectExistenceChecks();
        deleteByUserIdRemovesUserAndSessionPrefixes();
        notImplementedMethodsMirrorPythonPass();
        System.out.println("PASS VariableManagerCandidateSmokeTest");
    }

    private static void registersPrefixes() {
        KvPrefixRegistry registry = KvPrefixRegistry.getInstance();
        registry.unregister(VariableManager.USER_VAR_PREFIX);
        registry.unregister(VariableManager.SESSION_VAR_PREFIX);

        new VariableManager(new InMemoryKVStore(), new byte[0]);

        assertTrue(registry.getAllPrefixes().contains(VariableManager.USER_VAR_PREFIX), "user prefix registered");
        assertTrue(registry.getAllPrefixes().contains(VariableManager.SESSION_VAR_PREFIX), "session prefix registered");
        registry.unregister(VariableManager.USER_VAR_PREFIX);
        registry.unregister(VariableManager.SESSION_VAR_PREFIX);
    }

    private static void addMemoriesStoresOnlyVariableUnitsAndReturnsVariableList() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        VariableManager manager = new VariableManager(kvStore, new byte[0]);
        VariableUnit unit = new VariableUnit("theme", "dark");
        BaseMemoryUnit nonVariableUnit = new BaseMemoryUnit(MemoryType.VARIABLE, "bad");
        BaseMemoryUnit ignoredType = new BaseMemoryUnit(MemoryType.USER_PROFILE, "profile");

        Map<String, List<BaseMemoryUnit>> memories = new LinkedHashMap<>();
        memories.put(MemoryType.USER_PROFILE.getValue(), List.of(ignoredType));
        memories.put(MemoryType.VARIABLE.getValue(), List.of(nonVariableUnit, unit));

        List<BaseMemoryUnit> returned = manager.addMemories("user1", "scope1", memories, null, Map.of())
                .toCompletableFuture()
                .join();

        assertEquals(memories.get(MemoryType.VARIABLE.getValue()), returned, "returns original variable memory list");
        assertEquals("dark", kvStore.get("user_var/user1/scope1/theme").join(), "variable stored");
        assertEquals(null, kvStore.get("user_var/user1/scope1/bad").join(), "non VariableUnit skipped");
    }

    private static void queryVariableSupportsAllUserVariablesAndSessions() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        VariableManager manager = new VariableManager(kvStore, new byte[0]);
        kvStore.set("user_var/u/s/alpha", "one").join();
        kvStore.set("user_var/u/s/beta", "two").join();
        kvStore.set("session_var/u/s/session-1/alpha", "session-one").join();

        Map<String, String> allUserVariables = manager.queryVariable("u", "s", "   ", null)
                .toCompletableFuture()
                .join();
        assertEquals("one", allUserVariables.get("alpha"), "query all alpha");
        assertEquals("two", allUserVariables.get("beta"), "query all beta");

        Map<String, String> single = manager.queryVariable("u", "s", "alpha", null)
                .toCompletableFuture()
                .join();
        assertEquals("one", single.get("alpha"), "query one user variable");

        Map<String, String> session = manager.queryVariable("u", "s", "alpha", "session-1")
                .toCompletableFuture()
                .join();
        assertEquals("session-one", session.get("alpha"), "query one session variable");
    }

    private static void updateAndDeleteUserVariableRespectExistenceChecks() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        VariableManager manager = new VariableManager(kvStore, new byte[0]);

        manager.updateUserVariable("u", "s", "missing", "ignored").toCompletableFuture().join();
        assertEquals(null, kvStore.get("user_var/u/s/missing").join(), "missing variable is not created");

        kvStore.set("user_var/u/s/theme", "dark").join();
        manager.updateUserVariable("u", "s", "theme", "light").toCompletableFuture().join();
        assertEquals("light", kvStore.get("user_var/u/s/theme").join(), "existing variable updated");

        manager.deleteUserVariable("u", "s", "theme").toCompletableFuture().join();
        assertEquals(null, kvStore.get("user_var/u/s/theme").join(), "existing variable deleted");
    }

    private static void deleteByUserIdRemovesUserAndSessionPrefixes() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        VariableManager manager = new VariableManager(kvStore, new byte[0]);
        kvStore.set("user_var/u/s/a", "one").join();
        kvStore.set("session_var/u/s/session/a", "two").join();
        kvStore.set("user_var/u/other/a", "keep").join();

        Object result = manager.deleteByUserId("u", "s", Map.of()).toCompletableFuture().join();

        assertEquals(null, result, "delete_by_user_id mirrors Python None return");
        assertTrue(kvStore.getByPrefix("user_var/u/s/").join().isEmpty(), "user prefix deleted");
        assertTrue(kvStore.getByPrefix("session_var/u/s/").join().isEmpty(), "session prefix deleted");
        assertEquals("keep", kvStore.get("user_var/u/other/a").join(), "other scope retained");
    }

    private static void notImplementedMethodsMirrorPythonPass() {
        VariableManager manager = new VariableManager(new InMemoryKVStore(), new byte[0]);

        assertEquals(null, manager.update("u", "s", "m1", "new", Map.of()).toCompletableFuture().join(),
                "update returns None/null");
        assertEquals(null, manager.delete("u", "s", "m1", Map.of()).toCompletableFuture().join(),
                "delete returns None/null");
        assertEquals(null, manager.get("u", "s", "m1").toCompletableFuture().join(),
                "get returns None/null");
        assertEquals(null, manager.search("u", "s", "query", 3, Map.of()).toCompletableFuture().join(),
                "search returns None/null");

        VariableManager.VariablePair emptyPair = manager.makeVariablePairs("u", false, "s", null, null, "v", null);
        assertEquals("", emptyPair.key(), "missing var name keeps empty key");
        assertEquals("", emptyPair.value(), "missing var name keeps empty value");

        assertTrue(!VariableManager.checkExist(Map.of("name", ""), "name"), "empty value is not existing");
        assertTrue(VariableManager.checkExist(Map.of("name", "value"), "name"), "non-empty value exists");
    }

    private static void assertTrue(boolean actual, String message) {
        if (!actual) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if ((expected == null && actual != null) || (expected != null && !expected.equals(actual))) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
