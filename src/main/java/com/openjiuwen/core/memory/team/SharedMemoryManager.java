/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import com.openjiuwen.core.common.logging.Loggers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the team-level {@code TEAM_MEMORY.md} file under a team-memory directory.
 * <p>
 * Write semantics:
 * <ul>
 *   <li>If a {@code sysOperation} is provided, uses its filesystem abstraction.</li>
 *   <li>Otherwise falls back to local atomic write (temp file + atomic move).</li>
 * </ul>
 * <p>
 * Mirrors Python's {@code SharedMemoryManager} from
 * {@code memory/team/shared_memory.py}.
 */
public class SharedMemoryManager {

    public static final String TEAM_MEMORY_FILENAME = "TEAM_MEMORY.md";
    public static final int TEAM_MEMORY_MAX_READ_LINES = 200;

    private final String teamMemoryDir;
    private final Object sysOperation;
    private final Object writeLock = new Object();

    public SharedMemoryManager(String teamMemoryDir, Object sysOperation) {
        this.teamMemoryDir = teamMemoryDir;
        this.sysOperation = sysOperation;
    }

    public SharedMemoryManager(String teamMemoryDir) {
        this(teamMemoryDir, null);
    }

    /**
     * Ensure the team-memory directory exists.
     */
    public CompletableFuture<Void> ensureDir() {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(Paths.get(teamMemoryDir));
            } catch (IOException e) {
                Loggers.MEMORY.warn("[SharedMemoryManager] Failed to create dir {}: {}", teamMemoryDir, e.getMessage());
            }
        });
    }

    /**
     * Read team memory summary (first {@link #TEAM_MEMORY_MAX_READ_LINES} lines).
     */
    public CompletableFuture<String> readTeamSummary() {
        return CompletableFuture.supplyAsync(() -> {
            Path filePath = Paths.get(teamMemoryDir, TEAM_MEMORY_FILENAME);

            // Try sysOperation if available
            if (sysOperation != null) {
                try {
                    // TODO: integrate with SysOperation.fs().readFile() when available
                    // For now, fall through to local file read
                } catch (Exception e) {
                    // Fall through
                }
            }

            // Local file read
            try {
                if (!Files.exists(filePath)) {
                    return "";
                }
                var lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
                var result = new StringBuilder();
                int limit = Math.min(lines.size(), TEAM_MEMORY_MAX_READ_LINES);
                for (int i = 0; i < limit; i++) {
                    if (i > 0) result.append("\n");
                    result.append(lines.get(i));
                }
                return result.toString().trim();
            } catch (Exception e) {
                return "";
            }
        });
    }

    /**
     * Write team memory summary (full overwrite).
     */
    public CompletableFuture<Void> writeTeamSummary(String content) {
        return ensureDir().thenCompose(v -> CompletableFuture.runAsync(() -> {
            Path target = Paths.get(teamMemoryDir, TEAM_MEMORY_FILENAME);

            // Try sysOperation if available
            if (sysOperation != null) {
                try {
                    // TODO: integrate with SysOperation.fs().writeFile() when available
                    // For now, fall through to local file write
                } catch (Exception e) {
                    Loggers.MEMORY.warn("[SharedMemoryManager] sys_operation write failed, fallback: {}", e.getMessage());
                }
            }

            // Python's fallback write has no await between temp-file creation and os.replace,
            // so concurrent asyncio calls complete this critical section serially.
            synchronized (writeLock) {
                Path tmpPath = null;
                try {
                    tmpPath = Files.createTempFile(Paths.get(teamMemoryDir), "team_memory_", ".tmp");
                    Files.writeString(tmpPath, content, StandardCharsets.UTF_8);
                    Files.move(tmpPath, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    Loggers.MEMORY.error("[SharedMemoryManager] Atomic write failed: {}", e.getMessage());
                    throw new RuntimeException("Failed to write team summary", e);
                } finally {
                    if (tmpPath != null && Files.exists(tmpPath)) {
                        try { Files.deleteIfExists(tmpPath); } catch (Exception ignored) {}
                    }
                }
            }
        }));
    }

    /**
     * Append an entry to the team memory.
     */
    public CompletableFuture<Void> appendEntry(String entry) {
        return readTeamSummary().thenCompose(existing ->
                writeTeamSummary(existing.isEmpty() ? entry : existing + "\n\n---\n\n" + entry));
    }
}
