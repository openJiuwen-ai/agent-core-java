/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.lite;

import com.openjiuwen.core.memory.lite.WriteMode;
import com.openjiuwen.core.memory.lite.WriteResult;
import com.openjiuwen.core.memory.manage.update.MemoryStatus;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Coding Memory conflict detection.
 * <p>
 * 1. WriteResult/WriteMode data model
 * 2. frontmatter helper functions
 * 3. Snapshots and optimistic concurrency
 * 4. End-to-end behavior covered in system tests with full dependencies
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.memory.lite.test_coding_memory_conflict_detection}.
 */
class TestCodingMemoryConflictDetection {

    // ==================== TestWriteResultConflictDetection ====================

    @Nested
    class TestWriteResultConflictDetection {

        @Test
        @Tag("level0")
        void testRedundantSkipResult() {
            /** Test redundant scenario returns skip mode */
            WriteResult result = new WriteResult(true, "/test/file.md", WriteMode.SKIP);
            result.setNote("Content is redundant with existing memories");

            Map<String, Object> d = result.toDict();
            assertTrue((Boolean) d.get("success"));
            assertEquals("skip", d.get("mode"));
            assertTrue(((String) d.get("note")).toLowerCase().contains("redundant"));
        }

        @Test
        @Tag("level0")
        void testConflictDetectedResult() {
            /** Test conflict scenario returns conflict information */
            WriteResult result = new WriteResult(true, "/test/file.md", WriteMode.CREATE);
            result.setConflictDetected(true);
            result.setConflictingFiles(Arrays.asList("old1.md", "old2.md"));
            result.setNote("Conflicts with: old1.md, old2.md. Use coding_memory_read to review.");

            Map<String, Object> d = result.toDict();
            assertTrue((Boolean) d.get("success"));
            assertEquals("create", d.get("mode"));
            assertTrue((Boolean) d.get("conflict_detected"));
            assertEquals(Arrays.asList("old1.md", "old2.md"), d.get("conflicting_files"));
            assertTrue(((String) d.get("note")).contains("old1.md"));
        }

        @Test
        @Tag("level0")
        void testCreateModeNoConflict() {
            /** Test create mode with no conflict */
            WriteResult result = new WriteResult(true, "/test/new.md", WriteMode.CREATE);

            Map<String, Object> d = result.toDict();
            assertTrue((Boolean) d.get("success"));
            assertEquals("create", d.get("mode"));
            assertFalse(d.containsKey("conflict_detected")); // Not included when no conflict
        }

        @Test
        @Tag("level0")
        void testAppendModeWithSelfConflict() {
            /** Test append mode with self-conflict */
            WriteResult result = new WriteResult(true, "/test/file.md", WriteMode.APPEND);
            result.setConflictDetected(true);
            result.setConflictingFiles(Arrays.asList("file.md")); // __self__ converted to filename
            result.setNote("Conflicts with: file.md");

            Map<String, Object> d = result.toDict();
            assertEquals("append", d.get("mode"));
            assertTrue(((List<String>) d.get("conflicting_files")).contains("file.md"));
        }

        @Test
        @Tag("level0")
        void testAppendModeWithOtherConflict() {
            /** Test append mode with conflict from other files */
            WriteResult result = new WriteResult(true, "/test/file.md", WriteMode.APPEND);
            result.setConflictDetected(true);
            result.setConflictingFiles(Arrays.asList("other_memory.md"));
            result.setNote("Conflicts with: other_memory.md");

            Map<String, Object> d = result.toDict();
            assertEquals(Arrays.asList("other_memory.md"), d.get("conflicting_files"));
        }

        @Test
        @Tag("level0")
        void testWriteErrorResult() {
            /** Test write error */
            WriteResult result = new WriteResult(false, "/test/file.md", WriteMode.CREATE);
            result.setError("Invalid frontmatter");

            Map<String, Object> d = result.toDict();
            assertFalse((Boolean) d.get("success"));
            assertEquals("Invalid frontmatter", d.get("error"));
        }
    }

    // ==================== TestConflictLogicScenarios ====================

    @Nested
    class TestConflictLogicScenarios {

        @Test
        @Tag("level0")
        void testScenarioNoOldMemories() {
            /** Scenario 1: No similar old memories → create directly */
            WriteResult result = new WriteResult(true, "/test/new.md", WriteMode.CREATE);
            assertEquals(WriteMode.CREATE, result.getMode());
            assertFalse(result.isConflictDetected());
        }

        @Test
        @Tag("level0")
        void testScenarioRedundantDetection() {
            /** Scenario 2: Detected redundant → skip */
            WriteResult result = new WriteResult(true, "/test/new.md", WriteMode.SKIP);
            result.setNote("Content is redundant with existing memories");
            assertEquals(WriteMode.SKIP, result.getMode());
            assertTrue(result.getNote().toLowerCase().contains("redundant"));
        }

        @Test
        @Tag("level0")
        void testScenarioConflictingDetection() {
            /** Scenario 3: Detected conflict → write + return conflict info */
            WriteResult result = new WriteResult(true, "/test/new.md", WriteMode.CREATE);
            result.setConflictDetected(true);
            result.setConflictingFiles(Arrays.asList("old.md"));
            assertEquals(WriteMode.CREATE, result.getMode()); // Still writes
            assertTrue(result.isConflictDetected()); // But returns conflict info
        }

        @Test
        @Tag("level0")
        void testScenarioAppendSelfConflict() {
            /** Scenario 4: Append mode conflicts with self file content */
            WriteResult result = new WriteResult(true, "/test/file.md", WriteMode.APPEND);
            result.setConflictDetected(true);
            result.setConflictingFiles(Arrays.asList("file.md"));
            assertTrue(result.getConflictingFiles().contains("file.md"));
        }
    }

    // ==================== TestMemoryStatusEnum ====================

    @Nested
    class TestMemoryStatusEnum {

        @Test
        @Tag("level0")
        void testMemoryStatusValues() {
            /** Test MemoryStatus enum values */
            assertEquals("add", MemoryStatus.ADD.getValue());
            assertEquals("delete", MemoryStatus.DELETE.getValue());
        }
    }

    // ==================== TestOptimisticConcurrency ====================

    @Nested
    class TestOptimisticConcurrency {

        @Test
        @Tag("level0")
        void testSetSnapshotEquality() {
            /** Snapshots use Set, same file set snapshots should be equal */
            Set<String> s1 = new HashSet<>(Arrays.asList("a.md", "b.md"));
            Set<String> s2 = new HashSet<>(Arrays.asList("b.md", "a.md"));
            assertEquals(s1, s2); // Order independent
        }

        @Test
        @Tag("level0")
        void testSetSnapshotInequalityOnNewFile() {
            /** New file causes snapshot inequality */
            Set<String> sBefore = new HashSet<>(Arrays.asList("a.md", "b.md"));
            Set<String> sAfter = new HashSet<>(Arrays.asList("a.md", "b.md", "c.md"));
            assertNotEquals(sBefore, sAfter);
        }

        @Test
        @Tag("level0")
        void testSetSnapshotInequalityOnRemovedFile() {
            /** Removed file causes snapshot inequality */
            Set<String> sBefore = new HashSet<>(Arrays.asList("a.md", "b.md", "c.md"));
            Set<String> sAfter = new HashSet<>(Arrays.asList("a.md", "b.md"));
            assertNotEquals(sBefore, sAfter);
        }

        @Test
        @Tag("level0")
        void testBasenameInSnapshotDetectsExistingFile() {
            /** Check file existence via basename in snapshot */
            Set<String> snapshot = new HashSet<>(Arrays.asList("existing.md", "other.md"));
            assertTrue(snapshot.contains("existing.md"));
            assertFalse(snapshot.contains("new_file.md"));
        }
    }

    // ==================== TestLockMechanismBasic ====================

    @Nested
    class TestLockMechanismBasic {

        @Test
        @Tag("level0")
        void testReentrantLockBasic() {
            /** Test ReentrantLock basic behavior */
            ReentrantLock lock = new ReentrantLock();
            assertFalse(lock.isLocked());
        }

        @Test
        @Tag("level0")
        void testReentrantLockAcquireRelease() {
            /** Test lock acquire and release */
            ReentrantLock lock = new ReentrantLock();
            lock.lock();
            assertTrue(lock.isLocked());
            lock.unlock();
            assertFalse(lock.isLocked());
        }

        @Test
        @Tag("level0")
        void testLockDictPattern() {
            /** Test lock dictionary pattern */
            Map<String, ReentrantLock> locks = new HashMap<>();
            String path = "/test/file.md";

            // Simulate _get_file_lock logic
            if (!locks.containsKey(path)) {
                locks.put(path, new ReentrantLock());
            }

            assertTrue(locks.containsKey(path));
            assertTrue(locks.get(path) instanceof ReentrantLock);

            // Getting again returns the same lock
            ReentrantLock sameLock = locks.get(path);
            assertEquals(locks.get(path), sameLock);
        }
    }

    // ==================== TestMemoryIndexLockMechanism ====================

    @Nested
    class TestMemoryIndexLockMechanism {

        @Test
        @Tag("level0")
        void testMemoryIndexLockPlaceholder() {
            /** Test memory index lock mechanism - verify ReentrantLock behavior */
            ReentrantLock lock = new ReentrantLock();
            lock.lock();
            assertTrue(lock.isLocked());
            lock.unlock();
            assertFalse(lock.isLocked());
        }
    }
}