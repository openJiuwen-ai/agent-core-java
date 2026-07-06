/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.teamworkspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Public class TeamWorkspaceManager used by the Java parity implementation.
 *
 * @since 1.0
 */
public class TeamWorkspaceManager {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final int ERROR_PRIVILEGE_NOT_HELD = 1314;

    private final TeamWorkspaceConfig config;
    private final String workspacePath;
    private final String teamName;
    private final com.openjiuwen.agentteams.teamworkspace.WorkspaceMode mode;
    private final Map<String, WorkspaceFileLock> locks = new LinkedHashMap<>();
    private final List<String> cleanupPaths = new ArrayList<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamWorkspaceManager(TeamWorkspaceConfig config, String workspacePath, String teamName) {
        this(config, workspacePath, teamName, com.openjiuwen.agentteams.teamworkspace.WorkspaceMode.LOCAL);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamWorkspaceManager(TeamWorkspaceConfig config, String workspacePath, String teamName, com.openjiuwen.agentteams.teamworkspace.WorkspaceMode mode) {
        this.config = config != null ? config : TeamWorkspaceConfig.builder().build();
        this.workspacePath = workspacePath;
        this.teamName = teamName;
        this.mode = mode != null ? mode : com.openjiuwen.agentteams.teamworkspace.WorkspaceMode.LOCAL;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void initialize() throws IOException {
        Files.createDirectories(Path.of(workspacePath));
        for (String dir : config.getArtifactDirs()) {
            Files.createDirectories(Path.of(workspacePath).resolve(dir));
        }
        Files.createDirectories(Path.of(workspacePath).resolve("skills"));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void mountIntoWorkspace(String workspaceRoot) throws IOException {
        Path teamDir = Path.of(workspaceRoot).resolve(".team");
        Files.createDirectories(teamDir);
        Path linkPath = teamDir.resolve(teamName);
        if (!Files.exists(linkPath)) {
            mountDirectory(Path.of(workspacePath), linkPath);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void mountIntoWorktree(String worktreePath) throws IOException {
        Path linkPath = Path.of(worktreePath).resolve(".team");
        if (!Files.exists(linkPath)) {
            mountDirectory(Path.of(workspacePath), linkPath);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized boolean acquireLock(String filePath, String holderId, String holderName, int timeoutSeconds) {
        WorkspaceFileLock existing = locks.get(filePath);
        if (existing != null && !existing.isExpired()) {
            return false;
        }
        locks.put(filePath, WorkspaceFileLock.builder()
                .filePath(filePath)
                .holderId(holderId)
                .holderName(holderName)
                .acquiredAt(Instant.now().toString())
                .timeoutSeconds(timeoutSeconds)
                .build());
        return true;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized boolean releaseLock(String filePath, String holderId) {
        WorkspaceFileLock existing = locks.get(filePath);
        if (existing == null) {
            return false;
        }
        if (holderId != null && !holderId.equals(existing.getHolderId())) {
            return false;
        }
        locks.remove(filePath);
        return true;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized WorkspaceFileLock getLock(String filePath) {
        WorkspaceFileLock lock = locks.get(filePath);
        if (lock != null && lock.isExpired()) {
            locks.remove(filePath);
            return null;
        }
        return lock;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized List<WorkspaceFileLock> listLocks() {
        locks.entrySet().removeIf(entry -> entry.getValue().isExpired());
        return List.copyOf(locks.values());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WorkspaceLockResponse handleLockRequest(WorkspaceLockRequest request) {
        if (request == null) {
            return WorkspaceLockResponse.builder()
                    .teamName(teamName)
                    .memberName("")
                    .filePath("")
                    .isGranted(false)
                    .build();
        }
        boolean isGranted = false;
        String memberName = request.getMemberName() != null ? request.getMemberName() : "";
        if ("acquire".equals(request.getAction())) {
            isGranted = acquireLock(
                    request.getFilePath(),
                    memberName,
                    request.getHolderName() != null ? request.getHolderName() : memberName,
                    request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 300
            );
        } else if ("release".equals(request.getAction())) {
            isGranted = releaseLock(request.getFilePath(), memberName);
        }

        Map<String, Object> holder = null;
        if (!isGranted) {
            WorkspaceFileLock existing = getLock(request.getFilePath());
            if (existing != null) {
                holder = lockToMap(existing);
            }
        }
        return WorkspaceLockResponse.builder()
                .teamName(teamName)
                .memberName(memberName)
                .filePath(request.getFilePath())
                .isGranted(isGranted)
                .holder(holder)
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Map<String, Object>> getHistory(String relativePath) {
        return getHistory(relativePath, 10);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Map<String, Object>> getHistory(String relativePath, int limit) {
        if (!config.isVersionControl()) {
            return List.of();
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    "git",
                    "log",
                    "--max-count=" + Math.max(1, limit),
                    "--format=%H|%an|%ai|%s",
                    "--",
                    relativePath != null ? relativePath : ""
            );
            builder.directory(Path.of(workspacePath).toFile());
            Process process = builder.start();
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readProcessStream(process));
            int exit = process.onExit().join().exitValue();
            String stdout = stdoutFuture.join();
            if (exit != 0 || stdout.isBlank()) {
                return List.of();
            }
            List<Map<String, Object>> history = new ArrayList<>();
            for (String line : stdout.split("\\R")) {
                String[] parts = line.split("\\|", 4);
                if (parts.length == 4) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("commit", parts[0]);
                    item.put("author", parts[1]);
                    item.put("date", parts[2]);
                    item.put("message", parts[3]);
                    history.add(item);
                }
            }
            return history;
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String readProcessStream(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void registerCleanupPath(String path) {
        if (path != null && !path.isBlank()) {
            cleanupPaths.add(path);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void removeCleanupPaths() throws IOException {
        cleanupPaths.stream()
                .sorted(Comparator.<String>comparingInt(path -> Path.of(path).getNameCount()).reversed())
                .map(path -> Path.of(path))
                .filter(Files::exists)
                .forEach(path -> {
                    try {
                        if (Files.isDirectory(path)) {
                            try (var stream = Files.walk(path)) {
                                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                                    try {
                                        Files.deleteIfExists(p);
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                });
                            }
                        } else {
                            Files.deleteIfExists(path);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    private void mountDirectory(Path targetPath, Path linkPath) throws IOException {
        try {
            Files.createSymbolicLink(linkPath, targetPath);
        } catch (UnsupportedOperationException | IOException ex) {
            if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                throw ex;
            }
            throw ex;
        }
    }

    private Map<String, Object> lockToMap(WorkspaceFileLock lock) {
        Map<String, Object> holder = new LinkedHashMap<>();
        holder.put("file_path", lock.getFilePath());
        holder.put("holder_id", lock.getHolderId());
        holder.put("holder_name", lock.getHolderName());
        holder.put("acquired_at", lock.getAcquiredAt());
        holder.put("timeout_seconds", lock.getTimeoutSeconds());
        return holder;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamWorkspaceConfig getConfig() {
        return config;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getWorkspacePath() {
        return workspacePath;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTeamName() {
        return teamName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public com.openjiuwen.agentteams.teamworkspace.WorkspaceMode getMode() {
        return mode;
    }
}
