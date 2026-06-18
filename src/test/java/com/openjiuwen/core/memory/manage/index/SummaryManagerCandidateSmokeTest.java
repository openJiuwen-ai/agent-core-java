/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.foundation.store.StorageCodec;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.SummaryUnit;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Focused isolated smoke test for {@link SummaryManager}.
 *
 * <p>Mirrors Python's {@code SummaryManager} behavior in
 * {@code openjiuwen/core/memory/manage/index/summary_manager.py}.</p>
 */
public final class SummaryManagerCandidateSmokeTest {

    private static final String USER_ID = "user-1";
    private static final String SCOPE_ID = "scope-1";
    private static final String SUMMARY_TYPE = MemoryType.SUMMARY.getValue();

    private SummaryManagerCandidateSmokeTest() {
    }

    public static void main(String[] args) {
        verifiesAddMemoriesConvertsOnlySummaryUnits();
        verifiesNoValidSummaryDocsReturnsEmptyList();
        verifiesUpdateGetDeleteAndSearch();
        verifiesListUserSummarySortsDescendingByTimestamp();
        verifiesTimestampParsing();
        verifiesValidationAndExceptionWrapping();
        System.out.println("PASS SummaryManagerCandidateSmokeTest");
    }

    private static void verifiesAddMemoriesConvertsOnlySummaryUnits() {
        RecordingMemoryIndex index = new RecordingMemoryIndex();
        SummaryManager manager = new SummaryManager(index, new byte[]{1, 2, 3});

        SummaryUnit summaryUnit = new SummaryUnit("sum-1", "summary text", "message-1",
                "2026-01-02 03-04-05");
        List<BaseMemoryUnit> summaryUnits = new ArrayList<>();
        summaryUnits.add(new BaseMemoryUnit(MemoryType.USER_PROFILE, "ignored"));
        summaryUnits.add(summaryUnit);

        Map<String, List<BaseMemoryUnit>> memories = new LinkedHashMap<>();
        memories.put(MemoryType.USER_PROFILE.getValue(), List.of(new BaseMemoryUnit(MemoryType.USER_PROFILE, "u1")));
        memories.put(SUMMARY_TYPE, summaryUnits);

        List<BaseMemoryUnit> returned = manager.addMemories(USER_ID, SCOPE_ID, memories, null)
                .toCompletableFuture()
                .join();

        assertSame(summaryUnits, returned, "add_memories returns the original summary list");
        assertEquals(1, index.added.size(), "only one SummaryUnit should become a MemoryDoc");
        MemoryDoc added = index.added.get(0);
        assertEquals("sum-1", added.getId(), "doc id");
        assertEquals("summary text", added.getText(), "doc text");
        assertEquals(SUMMARY_TYPE, added.getType(), "doc type");
        assertEquals("message-1", added.getFields().get("source_id"), "source_id field");
        assertEquals(Map.of(), added.getFields().get("metadata"), "metadata field");
        assertEquals(ZoneOffset.UTC, added.getTimestamp().getOffset(), "fixed-format timestamp is UTC");
    }

    private static void verifiesNoValidSummaryDocsReturnsEmptyList() {
        RecordingMemoryIndex index = new RecordingMemoryIndex();
        SummaryManager manager = new SummaryManager(index);
        Map<String, List<BaseMemoryUnit>> memories = Map.of(
                SUMMARY_TYPE, List.of(new BaseMemoryUnit(MemoryType.SUMMARY, "not-summary-unit"))
        );

        List<BaseMemoryUnit> returned = manager.addMemories(USER_ID, SCOPE_ID, memories, null)
                .toCompletableFuture()
                .join();

        assertEquals(List.of(), returned, "no valid docs returns []");
        assertEquals(0, index.added.size(), "memory index add must not be called");
    }

    private static void verifiesUpdateGetDeleteAndSearch() {
        RecordingMemoryIndex index = new RecordingMemoryIndex();
        SummaryManager manager = new SummaryManager(index);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("source_id", "message-1");
        fields.put("metadata", Map.of("origin", "unit-test"));
        ZonedDateTime oldTimestamp = ZonedDateTime.parse("2026-01-02T03:04:05Z");
        index.getByIdResult = new MemoryDoc("sum-1", "old summary", SUMMARY_TYPE, oldTimestamp, fields);

        assertTrue(manager.update(USER_ID, SCOPE_ID, "sum-1", "new summary")
                .toCompletableFuture()
                .join(), "existing doc update returns true");
        assertEquals(1, index.updated.size(), "one updated doc");
        assertEquals("new summary", index.updated.get(0).getText(), "updated text");
        assertEquals(fields, index.updated.get(0).getFields(), "updated fields are preserved");

        Map<String, Object> got = manager.get(USER_ID, SCOPE_ID, "sum-1").toCompletableFuture().join();
        assertEquals("sum-1", got.get("id"), "get id");
        assertEquals("old summary", got.get("mem"), "get mem");
        assertEquals(SUMMARY_TYPE, got.get("mem_type"), "get mem_type");
        assertEquals("message-1", got.get("source_id"), "get source_id");
        assertEquals(Map.of("origin", "unit-test"), got.get("metadata"), "get metadata");

        index.searchResults = List.of(new BaseMemoryIndex.MemorySearchResult(index.getByIdResult, 0.75));
        List<Map<String, Object>> searched = manager.search(USER_ID, SCOPE_ID, "query", 3)
                .toCompletableFuture()
                .join();
        assertEquals(List.of(SUMMARY_TYPE), index.lastSearchMemTypes, "search mem_types");
        assertEquals(3, index.lastTopK, "search top_k");
        assertEquals(0.75, searched.get(0).get("score"), "search score");

        assertTrue(manager.delete(USER_ID, SCOPE_ID, "sum-1").toCompletableFuture().join(),
                "delete returns true");
        assertEquals(List.of("sum-1"), index.deletedIds, "delete ids");

        assertTrue(manager.deleteByUserId(USER_ID, SCOPE_ID).toCompletableFuture().join(),
                "delete_by_user_id returns true");
        assertTrue(index.deleteByUserAndScopeCalled, "delete_by_user_and_scope called");

        index.getByIdResult = null;
        assertFalse(manager.update(USER_ID, SCOPE_ID, "missing", "new").toCompletableFuture().join(),
                "missing doc update returns false");
        assertEquals(null, manager.get(USER_ID, SCOPE_ID, "missing").toCompletableFuture().join(),
                "missing get returns null");
    }

    private static void verifiesListUserSummarySortsDescendingByTimestamp() {
        RecordingMemoryIndex index = new RecordingMemoryIndex();
        SummaryManager manager = new SummaryManager(index);
        index.listResult = List.of(
                doc("older", "older text", "2026-01-01T00:00:00Z"),
                doc("newer", "newer text", "2026-01-03T00:00:00Z")
        );

        List<Map<String, Object>> listed = manager.listUserSummary(USER_ID, SCOPE_ID, 5, 10)
                .toCompletableFuture()
                .join();

        assertEquals(5, index.lastOffset, "list offset");
        assertEquals(10, index.lastLimit, "list limit");
        assertEquals(List.of(SUMMARY_TYPE), index.lastListMemTypes, "list mem_types");
        assertEquals("newer", listed.get(0).get("id"), "newest summary first");
        assertEquals("older", listed.get(1).get("id"), "oldest summary last");
    }

    private static void verifiesTimestampParsing() {
        assertEquals(ZonedDateTime.parse("2026-01-02T03:04:05Z"),
                SummaryManager.parseTimestamp("2026-01-02 03-04-05"), "dash timestamp");
        assertEquals(ZonedDateTime.parse("2026-01-02T03:04:05Z"),
                SummaryManager.parseTimestamp("2026-01-02 03:04:05"), "colon timestamp");
        assertEquals(ZonedDateTime.parse("2026-01-02T03:04:05Z"),
                SummaryManager.parseTimestamp("2026-01-02T03:04:05Z"), "ISO timestamp");
        assertTrue(SummaryManager.parseTimestamp("").isBefore(ZonedDateTime.now().plusSeconds(5)),
                "empty timestamp falls back to now");
    }

    private static void verifiesValidationAndExceptionWrapping() {
        try {
            new SummaryManager(null).get(USER_ID, SCOPE_ID, "sum-1");
            fail("Expected missing memory index to raise BaseError");
        } catch (BaseError error) {
            assertEquals(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR, error.getStatus(), "validation status");
            assertEquals(SUMMARY_TYPE, error.getParams().get("memory_type"), "validation memory_type");
        }

        RecordingMemoryIndex index = new RecordingMemoryIndex();
        SummaryManager manager = new SummaryManager(index);
        index.addFailure = new IllegalStateException("add failed");
        BaseError wrapped = expectCompletionBaseError(() -> manager.addMemories(
                USER_ID,
                SCOPE_ID,
                Map.of(SUMMARY_TYPE, List.of(new SummaryUnit("sum-1", "text", null, ""))),
                null
        ).toCompletableFuture().join());
        assertEquals(StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR, wrapped.getStatus(), "wrapped status");
        assertEquals("add failed", wrapped.getParams().get("error_msg"), "wrapped error message");

        BaseError existing = new BaseError(
                StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                Map.of("memory_type", SUMMARY_TYPE, "error_msg", "existing")
        );
        index.addFailure = existing;
        BaseError reraised = expectCompletionBaseError(() -> manager.addMemories(
                USER_ID,
                SCOPE_ID,
                Map.of(SUMMARY_TYPE, List.of(new SummaryUnit("sum-2", "text", null, ""))),
                null
        ).toCompletableFuture().join());
        assertSame(existing, reraised, "BaseError should be re-raised without wrapping");
    }

    private static MemoryDoc doc(String id, String text, String timestamp) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("source_id", "message-" + id);
        fields.put("metadata", Map.of());
        return new MemoryDoc(id, text, SUMMARY_TYPE, ZonedDateTime.parse(timestamp), fields);
    }

    private static BaseError expectCompletionBaseError(Runnable action) {
        try {
            action.run();
            fail("Expected CompletionException with BaseError cause");
            return null;
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof BaseError baseError) {
                return baseError;
            }
            throw new AssertionError("Expected BaseError cause, got " + cause, exception);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            fail(message);
        }
    }

    private static void assertFalse(boolean value, String message) {
        if (value) {
            fail(message);
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

    /**
     * In-memory fake index for SummaryManager candidate verification.
     *
     * <p>Mirrors Python's {@code BaseMemoryIndex} collaborator used by
     * {@code openjiuwen/core/memory/manage/index/summary_manager.py}.</p>
     */
    private static final class RecordingMemoryIndex extends BaseMemoryIndex {
        private final List<MemoryDoc> added = new ArrayList<>();
        private final List<MemoryDoc> updated = new ArrayList<>();
        private List<String> deletedIds = List.of();
        private List<String> lastSearchMemTypes = List.of();
        private List<String> lastListMemTypes = List.of();
        private int lastTopK;
        private int lastOffset;
        private int lastLimit;
        private boolean deleteByUserAndScopeCalled;
        private MemoryDoc getByIdResult;
        private List<MemorySearchResult> searchResults = List.of();
        private List<MemoryDoc> listResult = List.of();
        private RuntimeException addFailure;

        @Override
        public void setStorageCodec(StorageCodec codec) {
        }

        @Override
        public CompletableFuture<Void> addMemories(String userId, String scopeId, List<MemoryDoc> memories) {
            if (addFailure != null) {
                return CompletableFuture.failedFuture(addFailure);
            }
            added.addAll(memories);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateMemories(String userId, String scopeId, List<MemoryDoc> memories) {
            updated.addAll(memories);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteMemories(String userId, String scopeId, List<String> ids) {
            deletedIds = List.copyOf(ids);
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
            deleteByUserAndScopeCalled = true;
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
            lastSearchMemTypes = List.copyOf(memTypes);
            lastTopK = topK;
            return CompletableFuture.completedFuture(searchResults);
        }

        @Override
        public CompletableFuture<MemoryDoc> getById(String userId, String scopeId, String memId) {
            return CompletableFuture.completedFuture(getByIdResult);
        }

        @Override
        public CompletableFuture<List<MemoryDoc>> listMemories(
                String userId,
                String scopeId,
                int offset,
                int limit,
                List<String> memTypes
        ) {
            lastOffset = offset;
            lastLimit = limit;
            lastListMemTypes = List.copyOf(memTypes);
            return CompletableFuture.completedFuture(listResult);
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
