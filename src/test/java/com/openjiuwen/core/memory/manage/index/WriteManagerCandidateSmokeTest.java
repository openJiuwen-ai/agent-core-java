/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.foundation.store.StorageCodec;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Focused isolated smoke test for {@link WriteManager}.
 *
 * <p>Mirrors Python's {@code WriteManager} behavior in
 * {@code openjiuwen/core/memory/manage/index/write_manager.py}.</p>
 */
public final class WriteManagerCandidateSmokeTest {

    private static final String USER_ID = "user-1";
    private static final String SCOPE_ID = "scope-1";

    private WriteManagerCandidateSmokeTest() {
    }

    public static void main(String[] args) {
        emptyMemoriesReturnEmptyList();
        addMemoriesUsesUniqueManagersAndCombinesResults();
        addMemoriesPropagatesManagerFailure();
        updateAndDeleteResolveManagerThroughMemoryIndex();
        missingOrUnsupportedTypeSkipsUpdateAndDelete();
        deleteByUserIdUsesUniqueManagers();
        System.out.println("PASS WriteManagerCandidateSmokeTest");
    }

    private static void emptyMemoriesReturnEmptyList() {
        RecordingManager manager = new RecordingManager("summary");
        WriteManager writeManager = new WriteManager(Map.of("summary", manager), new RecordingMemoryIndex());

        List<BaseMemoryUnit> result = writeManager.addMemories(USER_ID, SCOPE_ID, Map.of(), null)
                .toCompletableFuture()
                .join();

        assertEquals(List.of(), result, "empty memories returns []");
        assertEquals(0, manager.addCalls, "manager is not called for empty memories");
    }

    private static void addMemoriesUsesUniqueManagersAndCombinesResults() {
        RecordingManager fragmentManager = new RecordingManager("fragment");
        RecordingManager summaryManager = new RecordingManager("summary");
        BaseMemoryUnit fragmentResult = new BaseMemoryUnit(MemoryType.USER_PROFILE, "fragment-1");
        BaseMemoryUnit summaryResult = new BaseMemoryUnit(MemoryType.SUMMARY, "summary-1");
        fragmentManager.addReturn = List.of(fragmentResult);
        summaryManager.addReturn = List.of(summaryResult);
        Map<String, BaseMemoryManager> managers = new LinkedHashMap<>();
        managers.put("user_profile", fragmentManager);
        managers.put("semantic_memory", fragmentManager);
        managers.put("summary", summaryManager);
        WriteManager writeManager = new WriteManager(managers, new RecordingMemoryIndex());

        Map<String, List<BaseMemoryUnit>> memories = new LinkedHashMap<>();
        memories.put("user_profile", List.of(new BaseMemoryUnit(MemoryType.USER_PROFILE, "incoming-1")));
        memories.put("summary", List.of(new BaseMemoryUnit(MemoryType.SUMMARY, "incoming-2")));
        Map<String, Object> kwargs = Map.of("trace_id", "t-1");

        List<BaseMemoryUnit> result = writeManager.addMemories(USER_ID, SCOPE_ID, memories, null, kwargs)
                .toCompletableFuture()
                .join();

        assertEquals(List.of(fragmentResult, summaryResult), result, "combined manager results");
        assertEquals(1, fragmentManager.addCalls, "duplicate manager value is called once");
        assertEquals(1, summaryManager.addCalls, "summary manager called once");
        assertSame(memories, fragmentManager.lastMemories, "entire memories map passed to manager");
        assertSame(kwargs, fragmentManager.lastKwargs, "kwargs map passed through");
    }

    private static void addMemoriesPropagatesManagerFailure() {
        RecordingManager manager = new RecordingManager("summary");
        IllegalStateException failure = new IllegalStateException("add failed");
        manager.addFailure = failure;
        WriteManager writeManager = new WriteManager(Map.of("summary", manager), new RecordingMemoryIndex());

        Throwable cause = expectCompletionFailure(() -> writeManager.addMemories(
                USER_ID,
                SCOPE_ID,
                Map.of("summary", List.of(new BaseMemoryUnit(MemoryType.SUMMARY, "s1"))),
                null
        ).toCompletableFuture().join());

        assertSame(failure, cause, "manager add failure is propagated");
    }

    private static void updateAndDeleteResolveManagerThroughMemoryIndex() {
        RecordingMemoryIndex index = new RecordingMemoryIndex();
        index.getByIdResult = new MemoryDoc(
                "summary-1",
                "old summary",
                "summary",
                ZonedDateTime.parse("2026-01-02T03:04:05Z"),
                Map.of()
        );
        RecordingManager manager = new RecordingManager("summary");
        WriteManager writeManager = new WriteManager(Map.of("summary", manager), index);

        writeManager.updateMemById(USER_ID, SCOPE_ID, "summary-1", "new summary", Map.of("source", "smoke"))
                .toCompletableFuture()
                .join();
        writeManager.deleteMemById(USER_ID, SCOPE_ID, "summary-1", Map.of("source", "smoke"))
                .toCompletableFuture()
                .join();

        assertEquals(2, index.getByIdCalls, "index is consulted for update and delete");
        assertEquals(1, manager.updateCalls, "resolved manager update called");
        assertEquals("new summary", manager.lastNewMemory, "new memory passed as newMemory");
        assertEquals(1, manager.deleteCalls, "resolved manager delete called");
        assertEquals("summary-1", manager.lastMemId, "mem id passed to manager");
    }

    private static void missingOrUnsupportedTypeSkipsUpdateAndDelete() {
        RecordingMemoryIndex missingIndex = new RecordingMemoryIndex();
        RecordingManager summaryManager = new RecordingManager("summary");
        WriteManager missingWriteManager = new WriteManager(Map.of("summary", summaryManager), missingIndex);

        missingWriteManager.updateMemById(USER_ID, SCOPE_ID, "missing", "ignored").toCompletableFuture().join();
        assertEquals(0, summaryManager.updateCalls, "missing memory skips update");

        RecordingMemoryIndex unsupportedIndex = new RecordingMemoryIndex();
        unsupportedIndex.getByIdResult = new MemoryDoc("m1", "text", "unsupported", ZonedDateTime.now(), Map.of());
        WriteManager unsupportedWriteManager = new WriteManager(Map.of("summary", summaryManager), unsupportedIndex);

        unsupportedWriteManager.deleteMemById(USER_ID, SCOPE_ID, "m1").toCompletableFuture().join();
        assertEquals(0, summaryManager.deleteCalls, "unsupported memory type skips delete");
    }

    private static void deleteByUserIdUsesUniqueManagers() {
        RecordingManager first = new RecordingManager("first");
        RecordingManager second = new RecordingManager("second");
        Map<String, BaseMemoryManager> managers = new LinkedHashMap<>();
        managers.put("user_profile", first);
        managers.put("semantic_memory", first);
        managers.put("summary", second);
        WriteManager writeManager = new WriteManager(managers, new RecordingMemoryIndex());

        writeManager.deleteMemByUserId(USER_ID, SCOPE_ID, Map.of("force", true)).toCompletableFuture().join();

        assertEquals(1, first.deleteByUserIdCalls, "first unique manager called once");
        assertEquals(1, second.deleteByUserIdCalls, "second unique manager called once");
        assertEquals("force", first.lastKwargs.keySet().iterator().next(), "kwargs supplied to delete_by_user_id");
    }

    private static Throwable expectCompletionFailure(Runnable action) {
        try {
            action.run();
            fail("Expected CompletionException");
            return null;
        } catch (CompletionException exception) {
            return exception.getCause();
        }
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            fail(message + ": expected same reference");
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if ((expected == null && actual != null) || (expected != null && !expected.equals(actual))) {
            fail(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }

    private static final class RecordingManager extends BaseMemoryManager {
        private final String memType;
        private int addCalls;
        private int updateCalls;
        private int deleteCalls;
        private int deleteByUserIdCalls;
        private List<BaseMemoryUnit> addReturn = List.of();
        private RuntimeException addFailure;
        private Map<String, List<BaseMemoryUnit>> lastMemories;
        private Map<String, Object> lastKwargs;
        private Model lastLlm;
        private String lastMemId;
        private String lastNewMemory;

        private RecordingManager(String memType) {
            this.memType = memType;
        }

        @Override
        public CompletionStage<List<BaseMemoryUnit>> addMemories(
                String userId,
                String scopeId,
                Map<String, List<BaseMemoryUnit>> memories,
                Model llm,
                Map<String, Object> kwargs
        ) {
            addCalls++;
            lastMemories = memories;
            lastLlm = llm;
            lastKwargs = kwargs;
            if (addFailure != null) {
                return CompletableFuture.failedFuture(addFailure);
            }
            return CompletableFuture.completedFuture(addReturn);
        }

        @Override
        public CompletionStage<Boolean> update(
                String userId,
                String scopeId,
                String memId,
                String newMemory,
                Map<String, Object> kwargs
        ) {
            updateCalls++;
            lastMemId = memId;
            lastNewMemory = newMemory;
            lastKwargs = kwargs;
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletionStage<Boolean> delete(String userId, String scopeId, String memId, Map<String, Object> kwargs) {
            deleteCalls++;
            lastMemId = memId;
            lastKwargs = kwargs;
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletionStage<Boolean> deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs) {
            deleteByUserIdCalls++;
            lastKwargs = kwargs;
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletionStage<Map<String, Object>> get(String userId, String scopeId, String memId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<Map<String, Object>>> search(String userId,
                                                                 String scopeId,
                                                                 String query,
                                                                 int topK,
                                                                 Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private static final class RecordingMemoryIndex extends BaseMemoryIndex {
        private int getByIdCalls;
        private MemoryDoc getByIdResult;

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
        public CompletableFuture<List<MemorySearchResult>> search(String userId,
                                                                  String scopeId,
                                                                  String query,
                                                                  List<String> memTypes,
                                                                  int topK) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<MemoryDoc> getById(String userId, String scopeId, String memId) {
            getByIdCalls++;
            return CompletableFuture.completedFuture(getByIdResult);
        }

        @Override
        public CompletableFuture<Void> cleanupBackup(String backupId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<UserScopeKey>> listUserScopes() {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }
}
