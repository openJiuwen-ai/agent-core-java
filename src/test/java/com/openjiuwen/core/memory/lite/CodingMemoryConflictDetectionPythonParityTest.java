/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.memory.manage.update.MemoryStatus;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.memory.lite.test_coding_memory_conflict_detection} in
 * {@code tests/unit_tests/core/memory/lite/test_coding_memory_conflict_detection.py}.</p>
 *
 * <p>Also mirrors Python's {@code tests.system_tests.memory.test_coding_memory_conflict}
 * in {@code tests/system_tests/memory/test_coding_memory_conflict.py}.</p>
 */
class CodingMemoryConflictDetectionPythonParityTest {

    @TestFactory
    List<DynamicTest> codingMemoryConflictDetectionPythonParity() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "TestWriteResultConflictDetection::test_redundant_skip_result", this::redundantSkipResult);
        add(tests, "TestWriteResultConflictDetection::test_conflict_detected_result",
                this::conflictDetectedResult);
        add(tests, "TestWriteResultConflictDetection::test_create_mode_no_conflict", this::createModeNoConflict);
        add(tests, "TestWriteResultConflictDetection::test_append_mode_with_self_conflict",
                this::appendModeWithSelfConflict);
        add(tests, "TestWriteResultConflictDetection::test_append_mode_with_other_conflict",
                this::appendModeWithOtherConflict);
        add(tests, "TestWriteResultConflictDetection::test_write_error_result", this::writeErrorResult);
        add(tests, "TestConflictLogicScenarios::test_scenario_no_old_memories", this::scenarioNoOldMemories);
        add(tests, "TestConflictLogicScenarios::test_scenario_redundant_detection",
                this::scenarioRedundantDetection);
        add(tests, "TestConflictLogicScenarios::test_scenario_conflicting_detection",
                this::scenarioConflictingDetection);
        add(tests, "TestConflictLogicScenarios::test_scenario_append_self_conflict",
                this::scenarioAppendSelfConflict);
        add(tests, "TestMemoryStatusEnum::test_memory_status_values", this::memoryStatusValues);
        add(tests, "TestOptimisticConcurrency::test_frozenset_snapshot_equality",
                this::frozensetSnapshotEquality);
        add(tests, "TestOptimisticConcurrency::test_frozenset_snapshot_inequality_on_new_file",
                this::frozensetSnapshotInequalityOnNewFile);
        add(tests, "TestOptimisticConcurrency::test_frozenset_snapshot_inequality_on_removed_file",
                this::frozensetSnapshotInequalityOnRemovedFile);
        add(tests, "TestOptimisticConcurrency::test_basename_in_snapshot_detects_existing_file",
                this::basenameInSnapshotDetectsExistingFile);
        add(tests, "TestOptimisticConcurrency::test_max_conflict_retries_value", this::maxConflictRetriesValue);
        add(tests, "TestOptimisticConcurrency::test_concurrent_snapshot_reads", this::concurrentSnapshotReads);
        add(tests, "TestLockMechanismBasic::test_asyncio_lock_basic", this::asyncioLockBasic);
        add(tests, "TestLockMechanismBasic::test_asyncio_lock_acquire_release", this::asyncioLockAcquireRelease);
        add(tests, "TestLockMechanismBasic::test_lock_dict_pattern", this::lockDictPattern);
        add(tests, "TestMemoryIndexLockMechanism::test_memory_index_lock_exists", this::memoryIndexLockExists);
        add(tests, "TestMemoryIndexLockMechanism::test_memory_index_lock_separate_from_file_locks",
                this::memoryIndexLockSeparateFromFileLocks);
        add(tests, "TestMemoryIndexLockMechanism::test_memory_index_lock_concurrent_protection",
                this::memoryIndexLockConcurrentProtection);
        add(tests, "TestEditLockProtection::test_edit_uses_same_file_lock_as_write",
                this::editUsesSameFileLockAsWrite);
        add(tests, "TestEditLockProtection::test_different_files_use_different_locks",
                this::differentFilesUseDifferentLocks);
        add(tests, "TestEditLockProtection::test_concurrent_edit_and_write_same_file_serialized",
                this::concurrentEditAndWriteSameFileSerialized);
        add(tests, "TestConflictNoteFormat::test_note_format_single_conflict", this::noteFormatSingleConflict);
        add(tests, "TestConflictNoteFormat::test_note_format_multiple_conflicts", this::noteFormatMultipleConflicts);
        add(tests, "TestFileLockRegistry::test_same_path_returns_same_lock_instance",
                this::samePathReturnsSameLockInstance);
        add(tests, "TestFileLockRegistry::test_concurrent_get_file_lock_same_instance",
                this::concurrentGetFileLockSameInstance);
        add(tests, "TestFileLockRegistry::test_lock_entry_retained_after_async_with",
                this::lockEntryRetainedAfterAsyncWith);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String pythonName, Executable executable) {
        tests.add(DynamicTest.dynamicTest(pythonName, executable));
    }

    private void redundantSkipResult() {
        WriteResult result = result(true, "/test/file.md", WriteMode.SKIP, false, List.of(),
                "Content is redundant with existing memories", null);

        Map<String, Object> out = result.toDict();

        assertEquals(true, out.get("success"));
        assertEquals("skip", out.get("mode"));
        assertTrue(String.valueOf(out.get("note")).toLowerCase().contains("redundant"));
    }

    private void conflictDetectedResult() {
        WriteResult result = result(true, "/test/file.md", WriteMode.CREATE, true,
                List.of("old1.md", "old2.md"),
                "Conflicts with: old1.md, old2.md. Use coding_memory_read to review.", null);

        Map<String, Object> out = result.toDict();

        assertEquals(true, out.get("success"));
        assertEquals("create", out.get("mode"));
        assertEquals(true, out.get("conflict_detected"));
        assertEquals(List.of("old1.md", "old2.md"), out.get("conflicting_files"));
        assertTrue(String.valueOf(out.get("note")).contains("old1.md"));
    }

    private void createModeNoConflict() {
        WriteResult result = result(true, "/test/new.md", WriteMode.CREATE, false, List.of(), null, null);

        Map<String, Object> out = result.toDict();

        assertEquals(true, out.get("success"));
        assertEquals("create", out.get("mode"));
        assertFalse(out.containsKey("conflict_detected"));
    }

    private void appendModeWithSelfConflict() {
        WriteResult result = result(true, "/test/file.md", WriteMode.APPEND, true,
                List.of("file.md"), "Conflicts with: file.md", null);

        Map<String, Object> out = result.toDict();

        assertEquals("append", out.get("mode"));
        assertTrue(((List<?>) out.get("conflicting_files")).contains("file.md"));
    }

    private void appendModeWithOtherConflict() {
        WriteResult result = result(true, "/test/file.md", WriteMode.APPEND, true,
                List.of("other_memory.md"), "Conflicts with: other_memory.md", null);

        assertEquals(List.of("other_memory.md"), result.toDict().get("conflicting_files"));
    }

    private void writeErrorResult() {
        WriteResult result = result(false, "/test/file.md", WriteMode.CREATE, false, List.of(), null,
                "Invalid frontmatter");

        Map<String, Object> out = result.toDict();

        assertEquals(false, out.get("success"));
        assertEquals("Invalid frontmatter", out.get("error"));
    }

    private void scenarioNoOldMemories() {
        WriteResult result = result(true, "/test/new.md", WriteMode.CREATE, false, List.of(), null, null);

        assertEquals(WriteMode.CREATE, result.getMode());
        assertFalse(result.isConflictDetected());
    }

    private void scenarioRedundantDetection() {
        WriteResult result = result(true, "/test/new.md", WriteMode.SKIP, false, List.of(),
                "Content is redundant with existing memories", null);

        assertEquals(WriteMode.SKIP, result.getMode());
        assertTrue(result.getNote().toLowerCase().contains("redundant"));
    }

    private void scenarioConflictingDetection() {
        WriteResult result = result(true, "/test/new.md", WriteMode.CREATE, true, List.of("old.md"), null, null);

        assertEquals(WriteMode.CREATE, result.getMode());
        assertTrue(result.isConflictDetected());
    }

    private void scenarioAppendSelfConflict() {
        WriteResult result = result(true, "/test/file.md", WriteMode.APPEND, true, List.of("file.md"), null, null);

        assertTrue(result.getConflictingFiles().contains("file.md"));
    }

    private void memoryStatusValues() {
        assertEquals("add", MemoryStatus.ADD.getValue());
        assertEquals("delete", MemoryStatus.DELETE.getValue());
    }

    private void frozensetSnapshotEquality() {
        Set<String> left = Set.of("a.md", "b.md");
        Set<String> right = Set.of("b.md", "a.md");

        assertEquals(left, right);
    }

    private void frozensetSnapshotInequalityOnNewFile() {
        assertFalse(Set.of("a.md", "b.md").equals(Set.of("a.md", "b.md", "c.md")));
    }

    private void frozensetSnapshotInequalityOnRemovedFile() {
        assertFalse(Set.of("a.md", "b.md", "c.md").equals(Set.of("a.md", "b.md")));
    }

    private void basenameInSnapshotDetectsExistingFile() {
        Set<String> snapshot = Set.of("existing.md", "other.md");

        assertTrue(snapshot.contains("existing.md"));
        assertFalse(snapshot.contains("new_file.md"));
    }

    private void maxConflictRetriesValue() throws ReflectiveOperationException {
        int retries = getPrivateStaticInt("MAX_CONFLICT_RETRIES");

        assertTrue(retries >= 1);
        assertTrue(retries <= 5);
    }

    private void concurrentSnapshotReads() {
        CodingMemoryToolContext context = new CodingMemoryToolContext("");
        List<CompletableFuture<Set<String>>> futures = List.of(
                CompletableFuture.supplyAsync(() -> snapshotMemoryFiles(context, "coding_memory")),
                CompletableFuture.supplyAsync(() -> snapshotMemoryFiles(context, "coding_memory")),
                CompletableFuture.supplyAsync(() -> snapshotMemoryFiles(context, "coding_memory"))
        );

        List<Set<String>> results = futures.stream().map(CompletableFuture::join).toList();

        assertTrue(results.stream().allMatch(Set::isEmpty));
    }

    private void asyncioLockBasic() {
        ReentrantLock lock = new ReentrantLock();

        assertFalse(lock.isLocked());
    }

    private void asyncioLockAcquireRelease() {
        ReentrantLock lock = new ReentrantLock();

        lock.lock();
        try {
            assertTrue(lock.isLocked());
        } finally {
            lock.unlock();
        }
        assertFalse(lock.isLocked());
    }

    private void lockDictPattern() {
        Map<String, ReentrantLock> locks = new HashMap<>();
        String path = "/test/file.md";

        locks.computeIfAbsent(path, ignored -> new ReentrantLock());

        assertTrue(locks.containsKey(path));
        ReentrantLock sameLock = locks.get(path);
        assertSame(sameLock, locks.get(path));
    }

    private void memoryIndexLockExists() throws ReflectiveOperationException {
        assertTrue(getMemoryIndexLock() instanceof ReentrantLock);
    }

    private void memoryIndexLockSeparateFromFileLocks() throws ReflectiveOperationException {
        ReentrantLock indexLock = getMemoryIndexLock();
        ConcurrentHashMap<String, ReentrantLock> fileLocks = getFileLocks();
        String path = "/coding_memory/index-separate-" + UUID.randomUUID() + ".md";
        ReentrantLock fileLock = getFileLock(path);

        assertFalse(fileLocks.containsKey("_memory_index_lock"));
        assertNotSame(indexLock, fileLock);
    }

    private void memoryIndexLockConcurrentProtection() throws Exception {
        ReentrantLock indexLock = getMemoryIndexLock();
        List<String> order = new CopyOnWriteArrayList<>();

        runTwoSerialized(indexLock, order, "A", "B");

        assertNotInterleaved(order, "A", "B");
    }

    private void editUsesSameFileLockAsWrite() throws ReflectiveOperationException {
        String path = "/coding_memory/test_file.md";

        assertSame(getFileLock(path), getFileLock(path));
    }

    private void differentFilesUseDifferentLocks() throws ReflectiveOperationException {
        assertNotSame(getFileLock("/coding_memory/a.md"), getFileLock("/coding_memory/b.md"));
    }

    private void concurrentEditAndWriteSameFileSerialized() throws Exception {
        ReentrantLock lock = getFileLock("/coding_memory/same_file_" + UUID.randomUUID() + ".md");
        List<String> order = new CopyOnWriteArrayList<>();

        runTwoSerialized(lock, order, "write", "edit");

        assertNotInterleaved(order, "write", "edit");
    }

    private void noteFormatSingleConflict() {
        WriteResult result = result(true, "/test/new.md", WriteMode.CREATE, true, List.of("old.md"),
                "Conflicts with: old.md. Use coding_memory_read to review, then coding_memory_edit to update.", null);

        assertTrue(result.getNote().contains("old.md"));
        assertTrue(result.getNote().contains("coding_memory_read"));
        assertTrue(result.getNote().contains("coding_memory_edit"));
    }

    private void noteFormatMultipleConflicts() {
        List<String> files = List.of("old1.md", "old2.md", "old3.md");
        WriteResult result = result(true, "/test/new.md", WriteMode.CREATE, true, files,
                "Conflicts with: " + String.join(", ", files) + ". Use coding_memory_read to review.", null);

        assertTrue(result.getNote().contains("old1.md"));
        assertTrue(result.getNote().contains("old2.md"));
        assertTrue(result.getNote().contains("old3.md"));
    }

    private void samePathReturnsSameLockInstance() throws ReflectiveOperationException {
        String path = "/coding_memory/same_" + UUID.randomUUID() + ".md";
        ReentrantLock left = getFileLock(path);
        ReentrantLock right = getFileLock(path);

        assertSame(left, right);
        assertTrue(getFileLocks().containsKey(path));
    }

    private void concurrentGetFileLockSameInstance() {
        String path = "/coding_memory/gather_" + UUID.randomUUID() + ".md";
        List<CompletableFuture<ReentrantLock>> futures = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return getFileLock(path);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            }));
        }

        List<ReentrantLock> locks = futures.stream().map(CompletableFuture::join).toList();

        assertTrue(locks.stream().allMatch(lock -> lock == locks.getFirst()));
    }

    private void lockEntryRetainedAfterAsyncWith() throws ReflectiveOperationException {
        String path = "/coding_memory/retain_" + UUID.randomUUID() + ".md";
        ReentrantLock lock = getFileLock(path);
        lock.lock();
        try {
            assertTrue(lock.isLocked());
        } finally {
            lock.unlock();
        }

        assertTrue(getFileLocks().containsKey(path));
        assertSame(lock, getFileLocks().get(path));
    }

    private static WriteResult result(
            boolean success,
            String path,
            WriteMode mode,
            boolean conflictDetected,
            List<String> conflictingFiles,
            String note,
            String error
    ) {
        return new WriteResult(success, path, mode, conflictDetected, conflictingFiles, note, error, null);
    }

    private static int getPrivateStaticInt(String fieldName) throws ReflectiveOperationException {
        Field field = CodingMemoryToolOps.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static ReentrantLock getMemoryIndexLock() throws ReflectiveOperationException {
        Field field = CodingMemoryToolOps.class.getDeclaredField("MEMORY_INDEX_LOCK");
        field.setAccessible(true);
        return (ReentrantLock) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, ReentrantLock> getFileLocks() throws ReflectiveOperationException {
        Field field = CodingMemoryToolOps.class.getDeclaredField("FILE_LOCKS");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, ReentrantLock>) field.get(null);
    }

    private static ReentrantLock getFileLock(String path) throws ReflectiveOperationException {
        Method method = CodingMemoryToolOps.class.getDeclaredMethod("getFileLock", String.class);
        method.setAccessible(true);
        return (ReentrantLock) method.invoke(null, path);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> snapshotMemoryFiles(CodingMemoryToolContext context, String memoryDir) {
        try {
            Method method = CodingMemoryToolOps.class.getDeclaredMethod(
                    "snapshotMemoryFiles", CodingMemoryToolContext.class, String.class);
            method.setAccessible(true);
            return (Set<String>) method.invoke(null, context, memoryDir);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void runTwoSerialized(ReentrantLock lock, List<String> order, String left, String right)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> first = CompletableFuture.runAsync(() -> serializedBlock(lock, order, left),
                    executor);
            CompletableFuture<Void> second = CompletableFuture.runAsync(() -> serializedBlock(lock, order, right),
                    executor);
            CompletableFuture.allOf(first, second).get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void serializedBlock(ReentrantLock lock, List<String> order, String tag) {
        lock.lock();
        try {
            order.add(tag + "_start");
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            order.add(tag + "_end");
        } finally {
            lock.unlock();
        }
    }

    private static void assertNotInterleaved(List<String> order, String left, String right) {
        Set<Integer> leftRange = rangeFor(order, left);
        Set<Integer> rightRange = rangeFor(order, right);
        Set<Integer> intersection = new HashSet<>(leftRange);
        intersection.retainAll(rightRange);

        assertTrue(intersection.isEmpty(), "Operations interleaved: " + order);
    }

    private static Set<Integer> rangeFor(List<String> order, String tag) {
        int start = order.indexOf(tag + "_start");
        int end = order.indexOf(tag + "_end");
        Set<Integer> range = new HashSet<>();
        for (int i = start; i <= end; i++) {
            range.add(i);
        }
        return range;
    }
}
