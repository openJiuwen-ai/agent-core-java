/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.lite;

import com.openjiuwen.core.memory.lite.CodingMemoryTools;
import com.openjiuwen.core.memory.lite.WriteMode;
import com.openjiuwen.core.memory.lite.WriteResult;
import com.openjiuwen.core.memory.manage.update.MemoryStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Coding Memory conflict detection.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.memory.lite.test_coding_memory_conflict_detection}.
 */
class TestCodingMemoryConflictDetection {

    @AfterEach
    void tearDown() {
        CodingMemoryTools.clearCodingMemoryRuntime();
    }

    @Test
    void testRedundantSkipResult() {
        WriteResult result = new WriteResult(true, "/test/file.md", WriteMode.SKIP);
        result.setNote("Content is redundant with existing memories");

        Map<String, Object> map = result.toDict();
        assertTrue((Boolean) map.get("success"));
        assertEquals("skip", map.get("mode"));
        assertTrue(map.get("note").toString().toLowerCase().contains("redundant"));
    }

    @Test
    void testConflictDetectedResult() {
        WriteResult result = new WriteResult(true, "/test/file.md", WriteMode.CREATE);
        result.setConflictDetected(true);
        result.setConflictingFiles(Arrays.asList("old1.md", "old2.md"));
        result.setNote("Conflicts with: old1.md, old2.md. Use coding_memory_read to review.");

        Map<String, Object> map = result.toDict();
        assertTrue((Boolean) map.get("success"));
        assertEquals("create", map.get("mode"));
        assertTrue((Boolean) map.get("conflict_detected"));
        assertEquals(Arrays.asList("old1.md", "old2.md"), map.get("conflicting_files"));
        assertTrue(map.get("note").toString().contains("old1.md"));
    }

    @Test
    void testCreateModeNoConflict() {
        WriteResult result = new WriteResult(true, "/test/new.md", WriteMode.CREATE);
        Map<String, Object> map = result.toDict();
        assertTrue((Boolean) map.get("success"));
        assertEquals("create", map.get("mode"));
        assertFalse(map.containsKey("conflict_detected"));
    }

    @Test
    void testAppendModeWithSelfConflict() {
        WriteResult result = new WriteResult(true, "/test/file.md", WriteMode.APPEND);
        result.setConflictDetected(true);
        result.setConflictingFiles(List.of("file.md"));
        result.setNote("Conflicts with: file.md");

        Map<String, Object> map = result.toDict();
        assertEquals("append", map.get("mode"));
        assertEquals(List.of("file.md"), map.get("conflicting_files"));
    }

    @Test
    void testAppendModeWithOtherConflict() {
        WriteResult result = new WriteResult(true, "/test/file.md", WriteMode.APPEND);
        result.setConflictDetected(true);
        result.setConflictingFiles(List.of("other_memory.md"));
        result.setNote("Conflicts with: other_memory.md");

        Map<String, Object> map = result.toDict();
        assertEquals(List.of("other_memory.md"), map.get("conflicting_files"));
    }

    @Test
    void testWriteErrorResult() {
        WriteResult result = new WriteResult(false, "/test/file.md", WriteMode.CREATE);
        result.setError("Invalid frontmatter");

        Map<String, Object> map = result.toDict();
        assertFalse((Boolean) map.get("success"));
        assertEquals("Invalid frontmatter", map.get("error"));
    }

    @Test
    void testScenarioNoOldMemories() {
        WriteResult result = new WriteResult(true, "/test/new.md", WriteMode.CREATE);
        assertEquals(WriteMode.CREATE, result.getMode());
        assertFalse(result.isConflictDetected());
    }

    @Test
    void testScenarioRedundantDetection() {
        WriteResult result = new WriteResult(true, "/test/new.md", WriteMode.SKIP);
        result.setNote("Content is redundant with existing memories");
        assertEquals(WriteMode.SKIP, result.getMode());
        assertTrue(result.getNote().toLowerCase().contains("redundant"));
    }

    @Test
    void testScenarioConflictingDetection() {
        WriteResult result = new WriteResult(true, "/test/new.md", WriteMode.CREATE);
        result.setConflictDetected(true);
        result.setConflictingFiles(List.of("old.md"));
        assertEquals(WriteMode.CREATE, result.getMode());
        assertTrue(result.isConflictDetected());
    }

    @Test
    void testScenarioAppendSelfConflict() {
        WriteResult result = new WriteResult(true, "/test/file.md", WriteMode.APPEND);
        result.setConflictDetected(true);
        result.setConflictingFiles(List.of("file.md"));
        assertTrue(result.getConflictingFiles().contains("file.md"));
    }

    @Test
    void testMemoryStatusValues() {
        assertEquals("add", MemoryStatus.ADD.getValue());
        assertEquals("delete", MemoryStatus.DELETE.getValue());
    }

    @Test
    void testSetSnapshotEquality() {
        Set<String> left = new HashSet<>(Arrays.asList("a.md", "b.md"));
        Set<String> right = new HashSet<>(Arrays.asList("b.md", "a.md"));
        assertEquals(left, right);
    }

    @Test
    void testSetSnapshotInequalityOnNewFile() {
        Set<String> before = new HashSet<>(Arrays.asList("a.md", "b.md"));
        Set<String> after = new HashSet<>(Arrays.asList("a.md", "b.md", "c.md"));
        assertNotEquals(before, after);
    }

    @Test
    void testSetSnapshotInequalityOnRemovedFile() {
        Set<String> before = new HashSet<>(Arrays.asList("a.md", "b.md", "c.md"));
        Set<String> after = new HashSet<>(Arrays.asList("a.md", "b.md"));
        assertNotEquals(before, after);
    }

    @Test
    void testBasenameInSnapshotDetectsExistingFile() {
        Set<String> snapshot = new HashSet<>(Arrays.asList("existing.md", "other.md"));
        assertTrue(snapshot.contains("existing.md"));
        assertFalse(snapshot.contains("new_file.md"));
    }

    @Test
    void testMaxConflictRetriesValue() {
        assertTrue(maxConflictRetries() >= 1);
        assertTrue(maxConflictRetries() <= 5);
    }

    @Test
    void testConcurrentSnapshotLikeReadsDoNotRequireRuntime() {
        CodingMemoryTools.clearCodingMemoryRuntime();
        assertEquals(0, CodingMemoryTools.countMemoryFiles());
        assertEquals(0, CodingMemoryTools.countMemoryFiles());
        assertEquals(0, CodingMemoryTools.countMemoryFiles());
    }

    @Test
    void testReentrantLockBasic() {
        ReentrantLock lock = new ReentrantLock();
        assertFalse(lock.isLocked());
    }

    @Test
    void testReentrantLockAcquireRelease() {
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        assertTrue(lock.isLocked());
        lock.unlock();
        assertFalse(lock.isLocked());
    }

    @Test
    void testLockDictPattern() {
        ReentrantLock first = CodingMemoryTools.getFileLock("/test/file.md");
        ReentrantLock second = CodingMemoryTools.getFileLock("/test/file.md");
        assertSame(first, second);
    }

    @Test
    void testMemoryIndexLockExists() {
        assertInstanceOf(ReentrantLock.class, memoryIndexLock());
    }

    @Test
    void testMemoryIndexLockSeparateFromFileLocks() {
        ReentrantLock fileLock = CodingMemoryTools.getFileLock("/coding_memory/test.md");
        assertNotSame(memoryIndexLock(), fileLock);
        assertFalse(fileLocks().containsValue(memoryIndexLock()));
    }

    @Test
    void testMemoryIndexLockConcurrentProtection() throws Exception {
        List<String> order = runSerialized(memoryIndexLock(), "A", "B");
        assertNonInterleaving(order, "A", "B");
    }

    @Test
    void testEditUsesSameFileLockAsWrite() {
        String path = "/coding_memory/test_file.md";
        ReentrantLock lock1 = CodingMemoryTools.getFileLock(path);
        ReentrantLock lock2 = CodingMemoryTools.getFileLock(path);
        assertSame(lock1, lock2);
    }

    @Test
    void testDifferentFilesUseDifferentLocks() {
        ReentrantLock lockA = CodingMemoryTools.getFileLock("/coding_memory/a.md");
        ReentrantLock lockB = CodingMemoryTools.getFileLock("/coding_memory/b.md");
        assertNotSame(lockA, lockB);
    }

    @Test
    void testConcurrentEditAndWriteSameFileSerialized() throws Exception {
        ReentrantLock lock = CodingMemoryTools.getFileLock("/coding_memory/same_file.md");
        List<String> order = runSerialized(lock, "write", "edit");
        assertNonInterleaving(order, "write", "edit");
    }

    @Test
    void testNoteFormatSingleConflict() {
        WriteResult result = new WriteResult(true, "/test/new.md", WriteMode.CREATE);
        result.setConflictDetected(true);
        result.setConflictingFiles(List.of("old.md"));
        result.setNote("Conflicts with: old.md. Use coding_memory_read to review, then coding_memory_edit to update.");
        assertTrue(result.getNote().contains("old.md"));
        assertTrue(result.getNote().contains("coding_memory_read"));
        assertTrue(result.getNote().contains("coding_memory_edit"));
    }

    @Test
    void testNoteFormatMultipleConflicts() {
        List<String> files = List.of("old1.md", "old2.md", "old3.md");
        WriteResult result = new WriteResult(true, "/test/new.md", WriteMode.CREATE);
        result.setConflictDetected(true);
        result.setConflictingFiles(files);
        result.setNote("Conflicts with: old1.md, old2.md, old3.md. Use coding_memory_read to review.");
        assertTrue(result.getNote().contains("old1.md"));
        assertTrue(result.getNote().contains("old2.md"));
        assertTrue(result.getNote().contains("old3.md"));
    }

    @Test
    void testSamePathReturnsSameLockInstance() {
        String path = "/coding_memory/same_" + UUID.randomUUID().toString().replace("-", "") + ".md";
        ReentrantLock first = CodingMemoryTools.getFileLock(path);
        ReentrantLock second = CodingMemoryTools.getFileLock(path);
        assertSame(first, second);
        assertTrue(fileLocks().containsKey(path));
    }

    @Test
    void testConcurrentGetFileLockSameInstance() throws Exception {
        String path = "/coding_memory/gather_" + UUID.randomUUID().toString().replace("-", "") + ".md";
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<ReentrantLock> locks = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    synchronized (locks) {
                        locks.add(CodingMemoryTools.getFileLock(path));
                    }
                });
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
        assertFalse(locks.isEmpty());
        for (ReentrantLock lock : locks) {
            assertSame(locks.get(0), lock);
        }
    }

    @Test
    void testLockEntryRetainedAfterUse() {
        String path = "/coding_memory/retain_" + UUID.randomUUID().toString().replace("-", "") + ".md";
        ReentrantLock lock = CodingMemoryTools.getFileLock(path);
        lock.lock();
        try {
            assertTrue(lock.isLocked());
        } finally {
            lock.unlock();
        }
        assertSame(lock, fileLocks().get(path));
    }

    @SuppressWarnings("unchecked")
    private Map<String, ReentrantLock> fileLocks() {
        try {
            Field field = CodingMemoryTools.class.getDeclaredField("fileLocks");
            field.setAccessible(true);
            return (Map<String, ReentrantLock>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private ReentrantLock memoryIndexLock() {
        try {
            Field field = CodingMemoryTools.class.getDeclaredField("memoryIndexLock");
            field.setAccessible(true);
            return (ReentrantLock) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private int maxConflictRetries() {
        try {
            Field field = CodingMemoryTools.class.getDeclaredField("MAX_CONFLICT_RETRIES");
            field.setAccessible(true);
            return field.getInt(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> runSerialized(ReentrantLock lock, String firstTag, String secondTag) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<String> order = new ArrayList<>();
        try {
            executor.submit(() -> runLocked(lock, ready, start, order, firstTag));
            executor.submit(() -> runLocked(lock, ready, start, order, secondTag));
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
        return order;
    }

    private void runLocked(ReentrantLock lock, CountDownLatch ready, CountDownLatch start,
                           List<String> order, String tag) {
        ready.countDown();
        await(start);
        lock.lock();
        try {
            synchronized (order) {
                order.add(tag + "_start");
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            synchronized (order) {
                order.add(tag + "_end");
            }
        } finally {
            lock.unlock();
        }
    }

    private void assertNonInterleaving(List<String> order, String leftTag, String rightTag) {
        int leftStart = order.indexOf(leftTag + "_start");
        int leftEnd = order.indexOf(leftTag + "_end");
        int rightStart = order.indexOf(rightTag + "_start");
        int rightEnd = order.indexOf(rightTag + "_end");
        Set<Integer> leftRange = Set.of(leftStart, leftEnd);
        Set<Integer> rightRange = Set.of(rightStart, rightEnd);
        assertTrue(leftRange.equals(Set.of(0, 1)) || leftRange.equals(Set.of(2, 3)));
        assertTrue(rightRange.equals(Set.of(0, 1)) || rightRange.equals(Set.of(2, 3)));
        assertTrue(leftEnd < rightStart || rightEnd < leftStart, "Operations interleaved: " + order);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
