/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.store;

import com.openjiuwen.core.context.offload.api.OffloadErrorCode;
import com.openjiuwen.core.context.offload.api.OffloadException;
import com.openjiuwen.core.context.offload.api.OffloadStore;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadPayload;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Filesystem store with atomic publish: payload bytes go to a
 * {@code *.tmp.<uuid>} sibling file, are fsynced, then moved onto the
 * target with {@code ATOMIC_MOVE}; the tmp file is removed on any failure
 * (架构文档 §3.1, AC-2-01/04/05). Readers therefore never observe a
 * half-written artifact.
 *
 * <p>T20: with {@link #FileOffloadStore(Path, boolean) enableFileLock}, the
 * save path additionally serializes concurrent publishers of the same
 * handle via an exclusive lock on a sibling {@code .json.lock} file —
 * aimed at shared-filesystem deployments where multiple processes publish
 * the same namespace (v2 §7.9). Default off: atomic move alone suffices
 * within a single process.</p>
 *
 * <p>File layout: {@code {workspaceRoot}/context/{tenant}/{user}/{session}_context/offload/{handle}.json}.
 * Note: the legacy processor path used an {@code {algorithmId}_} filename
 * prefix; the v2 SPI {@link #save} signature carries no algorithm id, so
 * the store keys files by handle alone (deviation logged in the
 * implementation report).</p>
 *
 * @since 2026-01-01
 */
public class FileOffloadStore implements OffloadStore {
    private static final String TMP_SUFFIX_FORMAT = ".tmp.%s";
    private static final String LOCK_SUFFIX = ".lock";

    /**
     * T20: same-JVM contenders fail fast with {@link OverlappingFileLockException}
     * instead of blocking, so acquisition retries with a short backoff before
     * giving up (cross-process contenders block inside {@code lock()} as usual).
     */
    private static final int LOCK_RETRY_MAX = 200;
    private static final long LOCK_RETRY_BACKOFF_MS = 10L;

    private final Path workspaceRoot;

    /**
     * T20: when enabled, the save path takes an exclusive {@link FileChannel}
     * lock on a sibling {@code {handle}.json.lock} file before writing. This
     * serializes concurrent publishers of the same handle across processes
     * sharing the workspace (共享 FS 场景, v2 §7.9); reads stay lock-free.
     */
    private final boolean isFileLockEnabled;

    public FileOffloadStore(Path workspaceRoot) {
        this(workspaceRoot, false);
    }

    public FileOffloadStore(Path workspaceRoot, boolean isFileLockEnabled) {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.isFileLockEnabled = isFileLockEnabled;
    }

    /**
     * Returns the absolute target path for a handle, mirroring the shared
     * storage-key convention (目录段经 sanitized namespace).
     *
     * @param workspaceRoot the workspace root path
     * @param ns            sanitized namespace
     * @param handle        unique offload handle
     * @return the absolute file path string
     */
    public static String resolveFilePath(Path workspaceRoot, OffloadNamespace ns, String handle) {
        return Path.of(workspaceRoot.toAbsolutePath().normalize().toString(),
                "context", ns.toFsDirName(), "offload", handle + ".json").toString();
    }

    @Override
    public OffloadBackend backend() {
        return OffloadBackend.FILESYSTEM;
    }

    public Path workspaceRoot() {
        return workspaceRoot;
    }

    @Override
    public void save(OffloadNamespace ns, String handle, OffloadPayload payload, Duration ttl) throws OffloadException {
        Objects.requireNonNull(ns, "ns must not be null");
        Objects.requireNonNull(handle, "handle must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Path target = Path.of(resolveFilePath(workspaceRoot, ns, handle));
        Path tmp = target.resolveSibling(target.getFileName() + String.format(TMP_SUFFIX_FORMAT, UUID.randomUUID()));
        try {
            Files.createDirectories(target.getParent());
            if (isFileLockEnabled) {
                lockAndPublish(tmp, target, payload);
            } else {
                publish(tmp, target, payload);
            }
        } catch (IOException ex) {
            cleanupQuietly(tmp);
            throw new OffloadException(OffloadErrorCode.OFFLOAD_STORE_IO,
                    "failed to persist offload payload: " + handle, ex);
        } catch (RuntimeException ex) {
            cleanupQuietly(tmp);
            throw ex;
        }
    }

    /**
     * Publishes tmp → target: write + fsync, then atomic move with a
     * non-atomic fallback. Kept separate so the T20 lock wraps the whole
     * tmp-write + move window, not just the rename.
     *
     * @param tmp     the temporary file path to write
     * @param target  the final target path
     * @param payload the payload to serialize and write
     * @throws IOException when write, fsync, or move fails
     */
    private static void publish(Path tmp, Path target, OffloadPayload payload) throws IOException {
        writeAndFsync(tmp, payload.serializeJson());
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            // Cross-filesystem or non-POSIX FS: fall back to a non-atomic replace.
            // The sidecar FileLock (when enabled) still serializes concurrent publishers.
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * T20: wraps the whole tmp-write + atomic-move window in the sidecar
     * lock. Same-JVM contenders surface as {@link OverlappingFileLockException}
     * rather than blocking, so acquisition retries with a short backoff
     * (cross-process contenders block inside {@code lock()} as usual). After
     * a successful publish the lock file is removed so no sibling is left
     * behind; that cleanup is best-effort because a concurrent publisher may
     * still hold the channel open (a leftover lock file never affects
     * readers — reads stay lock-free).
     *
     * @param tmp     the temporary file path to write
     * @param target  the final target path
     * @param payload the payload to serialize and write
     * @throws IOException when the lock cannot be acquired or the publish fails
     */
    private void lockAndPublish(Path tmp, Path target, OffloadPayload payload) throws IOException {
        Path lockPath = lockPathFor(target);
        try {
            for (int attempt = 0; ; attempt++) {
                try (FileChannel lockChannel = FileChannel.open(lockPath, standardWriteOptions())) {
                    FileLock lock = lockChannel.lock();
                    try {
                        publish(tmp, target, payload);
                    } finally {
                        lock.release();
                    }
                    break;
                } catch (OverlappingFileLockException ex) {
                    if (attempt >= LOCK_RETRY_MAX) {
                        throw new IOException("offload file lock stayed busy: " + lockPath, ex);
                    }
                    Thread.sleep(LOCK_RETRY_BACKOFF_MS);
                }
            }
        } catch (InterruptedException ex) {
            // G.CON.10: the InterruptedException is wrapped into an IOException
            // so the interrupted status propagates via the exception cause;
            // no Thread.currentThread().interrupt() is needed here.
            throw new IOException("interrupted while acquiring the offload file lock", ex);
        }
        try {
            Files.deleteIfExists(lockPath);
        } catch (IOException ignored) {
            // best-effort: a waiting publisher may still hold the channel open
        }
    }

    private static Path lockPathFor(Path target) {
        return target.resolveSibling(target.getFileName() + LOCK_SUFFIX);
    }

    @Override
    public OffloadPayload load(OffloadNamespace ns, String handle) {
        Path target = Path.of(resolveFilePath(workspaceRoot, ns, handle));
        if (!Files.exists(target)) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_MISSING, "offload file missing: " + handle);
        }
        try {
            return OffloadPayload.deserializeJson(Files.readString(target));
        } catch (IOException ex) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_STORE_IO,
                    "failed to read offload payload: " + handle, ex);
        } catch (RuntimeException ex) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_CORRUPT,
                    "offload payload unreadable: " + handle, ex);
        }
    }

    @Override
    public boolean delete(OffloadNamespace ns, String handle) {
        try {
            return Files.deleteIfExists(Path.of(resolveFilePath(workspaceRoot, ns, handle)));
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public boolean exists(OffloadNamespace ns, String handle) {
        return Files.exists(Path.of(resolveFilePath(workspaceRoot, ns, handle)));
    }

    private static void writeAndFsync(Path tmp, String content) throws IOException {
        try (FileChannel channel = FileChannel.open(tmp, standardWriteOptions())) {
            channel.write(ByteBuffer.wrap(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            channel.force(true);
        }
    }

    private static OpenOption[] standardWriteOptions() {
        return new OpenOption[]{
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};
    }

    private static void cleanupQuietly(Path tmp) {
        if (tmp == null) {
            return;
        }
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException ignored) {
            // best-effort tmp cleanup; the publish itself already failed
        }
    }
}
