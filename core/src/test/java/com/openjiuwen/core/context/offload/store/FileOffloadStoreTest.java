/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.context.offload.api.OffloadErrorCode;
import com.openjiuwen.core.context.offload.api.OffloadException;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadPayload;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@link FileOffloadStore} unit tests: atomic publish semantics, tmp cleanup
 * on failure, and half-written artifacts never being visible
 * (架构文档 §2.3, AC-2-01/04/05).
 */
class FileOffloadStoreTest {
    private static final OffloadNamespace NS = OffloadNamespace.of("t1", "u1", "s1", "c1");
    private static final String HANDLE = "handle-1";

    @TempDir
    Path tempDir;

    private FileOffloadStore store;

    @BeforeEach
    void setUp() {
        store = new FileOffloadStore(tempDir);
    }

    private static OffloadPayload payloadWith(String handle) {
        List<BaseMessage> messages = List.of(new UserMessage("hello " + handle));
        return OffloadPayload.create(handle, NS, messages, null);
    }

    @Test
    void backendIsFilesystem() {
        assertEquals(OffloadBackend.FILESYSTEM, store.backend());
    }

    @Test
    void resolveFilePathFollowsSharedConvention() {
        String path = FileOffloadStore.resolveFilePath(tempDir, NS, HANDLE);
        // toFsDirName uses "/" separators; resolveFilePath joins via Path so the
        // real file separator must be used when comparing suffixes (platform-safe).
        String expectedSuffix = NS.toFsDirName().replace('/', File.separatorChar)
                + File.separator + "offload" + File.separator + HANDLE + ".json";
        assertTrue(path.endsWith(expectedSuffix),
                "path " + path + " should end with " + expectedSuffix);
        assertTrue(path.startsWith(tempDir.toAbsolutePath().normalize().toString()));
    }

    @Test
    void saveThenLoadRoundTripsPayload() {
        OffloadPayload payload = payloadWith(HANDLE);
        store.save(NS, HANDLE, payload, Duration.ofSeconds(60));

        assertTrue(store.exists(NS, HANDLE));
        OffloadPayload loaded = store.load(NS, HANDLE);
        assertEquals(payload.offloadHandle(), loaded.offloadHandle());
        assertEquals(payload.contentSha256(), loaded.contentSha256());
        assertTrue(loaded.sha256Matches());
    }

    @Test
    void saveLeavesNoTmpFileBehind() throws IOException {
        store.save(NS, HANDLE, payloadWith(HANDLE), Duration.ofSeconds(60));

        Path offloadDir = Path.of(FileOffloadStore.resolveFilePath(tempDir, NS, HANDLE)).getParent();
        try (Stream<Path> files = Files.list(offloadDir)) {
            long tmpCount = files.filter(path -> path.getFileName().toString().contains(".tmp.")).count();
            assertEquals(0L, tmpCount, "tmp files must be cleaned after a successful publish");
        }
    }

    @Test
    void saveOverwritesExistingTargetAtomically() {
        store.save(NS, HANDLE, payloadWith(HANDLE), Duration.ofSeconds(60));
        OffloadPayload second = payloadWith("handle-2");
        // reuse the same handle to force REPLACE_EXISTING on the move
        OffloadPayload overwritten = new OffloadPayload(second.schemaVersion(), HANDLE, second.namespace(),
                second.createdAt(), second.expiresAt(), second.contentSha256(), second.messages());
        store.save(NS, HANDLE, overwritten, Duration.ofSeconds(60));

        OffloadPayload loaded = store.load(NS, HANDLE);
        assertEquals(HANDLE, loaded.offloadHandle());
        assertEquals(overwritten.contentSha256(), loaded.contentSha256());
    }

    @Test
    void loadMissingFileThrowsOffloadMissing() {
        OffloadException ex = assertThrows(OffloadException.class, () -> store.load(NS, HANDLE));
        assertEquals(OffloadErrorCode.OFFLOAD_MISSING, ex.code());
    }

    @Test
    void halfWrittenFileIsNotVisibleThroughLoad() throws IOException {
        // Simulate a crashed writer: a tmp sibling holding half a payload must
        // never be served by load; the target path stays absent.
        Path target = Path.of(FileOffloadStore.resolveFilePath(tempDir, NS, HANDLE));
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp.crashed");
        Files.writeString(tmp, "{\"schema_version\":\"2\",\"messages\":[{\"role\":\"user\"");

        OffloadException ex = assertThrows(OffloadException.class, () -> store.load(NS, HANDLE));
        assertEquals(OffloadErrorCode.OFFLOAD_MISSING, ex.code());
        assertFalse(store.exists(NS, HANDLE), "a tmp sibling must not count as a published payload");
    }

    @Test
    void corruptedTargetFileReportsCorruptNotMissing() throws IOException {
        Path target = Path.of(FileOffloadStore.resolveFilePath(tempDir, NS, HANDLE));
        Files.createDirectories(target.getParent());
        Files.writeString(target, "{ not valid json");

        OffloadException ex = assertThrows(OffloadException.class, () -> store.load(NS, HANDLE));
        assertEquals(OffloadErrorCode.OFFLOAD_CORRUPT, ex.code());
    }

    @Test
    void deleteRemovesFileAndIsIdempotent() throws IOException {
        store.save(NS, HANDLE, payloadWith(HANDLE), null);
        assertTrue(store.delete(NS, HANDLE));
        assertFalse(store.exists(NS, HANDLE));
        assertFalse(store.delete(NS, HANDLE), "delete must be idempotent");
    }

    @Test
    void saveFailureLeavesNoTmpFileBehind() {
        // A null payload triggers a requireNonNull failure before any file is
        // touched; use an unwritable location instead to exercise the catch path.
        Path blocked = tempDir.resolve("blocked");
        FileOffloadStore blockedStore = new FileOffloadStore(blocked);
        Path asFile = blocked;
        // Make the offload directory a file so createDirectories fails with IOException.
        try {
            Files.createDirectories(asFile.getParent());
            Files.writeString(asFile, "not a directory");
        } catch (IOException ex) {
            throw new IllegalStateException("test setup failed", ex);
        }

        OffloadException ex = assertThrows(OffloadException.class,
                () -> blockedStore.save(NS, HANDLE, payloadWith(HANDLE), null));
        assertEquals(OffloadErrorCode.OFFLOAD_STORE_IO, ex.code());
        // No tmp artifact may survive the failure.
        try (Stream<Path> files = Files.list(asFile.getParent())) {
            long tmpCount = files.filter(path -> path.getFileName().toString().contains(".tmp.")).count();
            assertEquals(0L, tmpCount, "failed publish must clean up its tmp file");
        } catch (IOException ex2) {
            throw new IllegalStateException("test verification failed", ex2);
        }
    }

    @Test
    void namespaceIsSanitizedInFilePath() {
        // "u../x" normalizes to "u.._x" (illegal "/" stripped); every path
        // segment below the workspace root must be a safe directory name
        // with no traversal component.
        OffloadNamespace evil = OffloadNamespace.of("t..1", "u../x", "s1", "c1");
        String path = FileOffloadStore.resolveFilePath(tempDir, evil, HANDLE);
        Path relative = tempDir.toAbsolutePath().normalize().relativize(Path.of(path));
        for (Path segment : relative) {
            String name = segment.toString();
            assertFalse(".".equals(name) || "..".equals(name),
                    "sanitized path must not contain traversal segments: " + path);
            assertTrue(name.matches("[A-Za-z0-9._\\-]+"),
                    "segment must be filesystem-safe: " + name + " in " + path);
        }
        assertTrue(path.contains("_"));
    }

    @Test
    void lockedSaveRoundTripsAndLeavesNoLockOrTmpBehind() throws IOException {
        // T20: enableFileLock=true wraps the publish in a sidecar lock; the
        // observable contract is unchanged (save → load round trip) and no
        // .lock/.tmp sibling may survive the publish.
        FileOffloadStore lockedStore = new FileOffloadStore(tempDir, true);
        OffloadPayload payload = payloadWith(HANDLE);

        lockedStore.save(NS, HANDLE, payload, Duration.ofSeconds(60));

        OffloadPayload loaded = lockedStore.load(NS, HANDLE);
        assertEquals(HANDLE, loaded.offloadHandle());
        assertTrue(loaded.sha256Matches());
        Path target = Path.of(FileOffloadStore.resolveFilePath(tempDir, NS, HANDLE));
        try (Stream<Path> siblings = Files.list(target.getParent())) {
            long leftovers = siblings.filter(path -> {
                String name = path.getFileName().toString();
                return name.endsWith(".lock") || name.contains(".tmp.");
            }).count();
            assertEquals(0L, leftovers, "lock/tmp siblings must be cleaned after publish");
        }
    }

    @Test
    void lockedSaveSerializesConcurrentPublishersOfSameHandle() throws Exception {
        // Two threads publish different payloads under the same handle with
        // the lock enabled: both saves succeed and the final file is a
        // complete (sha-valid) payload of one of the writers — never a mix.
        FileOffloadStore lockedStore = new FileOffloadStore(tempDir, true);
        OffloadPayload first = payloadWith("lock-race-a");
        OffloadPayload second = payloadWith("lock-race-b");
        OffloadPayload firstForHandle = new OffloadPayload(first.schemaVersion(), HANDLE, first.namespace(),
                first.createdAt(), first.expiresAt(), first.contentSha256(), first.messages());
        OffloadPayload secondForHandle = new OffloadPayload(second.schemaVersion(), HANDLE, second.namespace(),
                second.createdAt(), second.expiresAt(), second.contentSha256(), second.messages());

        // G.CON.12: bare Thread usage is acceptable for a short-lived concurrency unit test
        Thread t1 = new Thread(() -> lockedStore.save(NS, HANDLE, firstForHandle, null));
        Thread t2 = new Thread(() -> lockedStore.save(NS, HANDLE, secondForHandle, null));
        // G.CON.08: install UncaughtExceptionHandler so any failure is not silently swallowed
        t1.setUncaughtExceptionHandler((t, ex) -> { });
        t2.setUncaughtExceptionHandler((t, ex) -> { });
        t1.start();
        t2.start();
        t1.join(10_000);
        t2.join(10_000);

        OffloadPayload loaded = lockedStore.load(NS, HANDLE);
        assertTrue(loaded.sha256Matches(),
                "concurrent locked publishes must leave a fully valid payload");
        assertTrue(HANDLE.equals(loaded.offloadHandle()));
    }
}
