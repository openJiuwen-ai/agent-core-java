/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Read and write the team-level {@code TEAM_MEMORY.md} file.
 *
 * <p>Mirrors Python's {@code SharedMemoryManager} in
 * {@code openjiuwen/agent_teams/memory/shared_memory.py}.</p>
 */
public class SharedMemoryManager implements TeamMemoryManager.SharedMemoryManagerView {

    public static final String TEAM_MEMORY_FILENAME = "TEAM_MEMORY.md";
    public static final int TEAM_MEMORY_MAX_READ_LINES = 200;
    private static final ConcurrentHashMap<Path, Object> LOCAL_WRITE_LOCKS = new ConcurrentHashMap<>();

    private final Path directory;
    private final TeamMemoryExtractor.FileSystemView sysOperation;

    public SharedMemoryManager(String teamMemoryDir) {
        this(teamMemoryDir, null);
    }

    public SharedMemoryManager(String teamMemoryDir, TeamMemoryExtractor.FileSystemView sysOperation) {
        this.directory = Path.of(teamMemoryDir);
        this.sysOperation = sysOperation;
    }

    /**
     * Ensure the team-memory directory exists.
     *
     * <p>Mirrors Python's {@code ensure_dir} in
     * {@code openjiuwen/agent_teams/memory/shared_memory.py}.</p>
     *
     * @return completion signal
     */
    @Override
    public CompletionStage<Void> ensureDir() {
        try {
            Files.createDirectories(directory);
            return CompletableFuture.completedFuture(null);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /**
     * Read the team summary as an optional value for manager prompt injection.
     *
     * <p>Mirrors Python's {@code read_team_summary} in
     * {@code openjiuwen/agent_teams/memory/shared_memory.py}.</p>
     *
     * @return optional content, empty when missing or unreadable
     */
    @Override
    public CompletionStage<Optional<String>> readTeamSummary() {
        return readTeamSummaryText().thenApply(content -> content.isBlank()
                ? Optional.empty()
                : Optional.of(content));
    }

    public CompletionStage<String> readTeamSummaryText() {
        Path target = targetPath();
        if (sysOperation != null) {
            return sysOperation.readFile(target.toString())
                    .thenApply(content -> content.map(SharedMemoryManager::limitAndTrim).orElse(""))
                    .exceptionally(throwable -> "");
        }

        try {
            if (!Files.exists(target)) {
                return CompletableFuture.completedFuture("");
            }
            String content = Files.readString(target);
            return CompletableFuture.completedFuture(limitAndTrim(content));
        } catch (IOException exception) {
            return CompletableFuture.completedFuture("");
        }
    }

    /**
     * Overwrite the team summary.
     *
     * <p>Mirrors Python's {@code write_team_summary} in
     * {@code openjiuwen/agent_teams/memory/shared_memory.py}.</p>
     *
     * @param content full file content
     * @return completion signal
     */
    public CompletionStage<Void> writeTeamSummary(String content) {
        return ensureDir().thenCompose(ignored -> {
            if (sysOperation == null) {
                return writeLocalAtomic(content);
            }
            return sysOperation.writeFile(targetPath().toString(), content == null ? "" : content, true)
                    .handle((success, throwable) -> throwable == null && Boolean.TRUE.equals(success))
                    .thenCompose(success -> success
                            ? CompletableFuture.completedFuture(null)
                            : writeLocalAtomic(content));
        });
    }

    /**
     * Append one entry through a read-modify-write sequence.
     *
     * <p>Mirrors Python's {@code append_entry} in
     * {@code openjiuwen/agent_teams/memory/shared_memory.py}.</p>
     *
     * @param entry entry content to append
     * @return completion signal
     */
    public CompletionStage<Void> appendEntry(String entry) {
        return readTeamSummaryText().thenCompose(existing -> {
            String newContent = existing.isBlank()
                    ? nullToEmpty(entry)
                    : existing + "\n\n---\n\n" + nullToEmpty(entry);
            return writeTeamSummary(newContent);
        });
    }

    public Path targetPath() {
        return directory.resolve(TEAM_MEMORY_FILENAME).normalize();
    }

    private CompletionStage<Void> writeLocalAtomic(String content) {
        Object lock = LOCAL_WRITE_LOCKS.computeIfAbsent(targetPath(), ignored -> new Object());
        synchronized (lock) {
            return writeLocalAtomicLocked(content);
        }
    }

    private CompletionStage<Void> writeLocalAtomicLocked(String content) {
        Path tmpPath = null;
        try {
            Files.createDirectories(directory);
            tmpPath = Files.createTempFile(directory, "team_memory_", ".tmp");
            Files.writeString(tmpPath, content == null ? "" : content);
            moveReplacing(tmpPath, targetPath());
            return CompletableFuture.completedFuture(null);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        } finally {
            if (tmpPath != null) {
                try {
                    Files.deleteIfExists(tmpPath);
                } catch (IOException ignored) {
                    // Python best-effort removes the temp file in finally.
                }
            }
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String limitAndTrim(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return Arrays.stream(content.split("\\R", -1))
                .limit(TEAM_MEMORY_MAX_READ_LINES)
                .collect(Collectors.joining("\n"))
                .strip();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
