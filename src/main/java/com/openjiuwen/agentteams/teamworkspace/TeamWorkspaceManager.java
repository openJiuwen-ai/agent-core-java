/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.teamworkspace;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Public class TeamWorkspaceManager used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class TeamWorkspaceManager {
    /**
     * ERROR_PRIVILEGE_NOT_HELD.
     * 
     * @since 0.1.7
     */
    public static final int ERROR_PRIVILEGE_NOT_HELD = 1314;

    private final TeamWorkspaceConfig config;
    private final String workspacePath;
    private final String teamName;
    private final WorkspaceMode mode;

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, WorkspaceFileLock> locks = new LinkedHashMap<>();

    /**
     * TeamWorkspaceManager.
     * 
     * @param config config
     * @param workspacePath workspacePath
     * @param teamName teamName
     * @since 0.1.7
     */
    public TeamWorkspaceManager(TeamWorkspaceConfig config, String workspacePath, String teamName) {
        this(config, workspacePath, teamName, WorkspaceMode.LOCAL);
    }

    /**
     * TeamWorkspaceManager.
     * 
     * @param config config
     * @param workspacePath workspacePath
     * @param teamName teamName
     * @param mode mode
     * @since 0.1.7
     */
    public TeamWorkspaceManager(TeamWorkspaceConfig config, String workspacePath, String teamName, WorkspaceMode mode) {
        this.config = config != null ? config : TeamWorkspaceConfig.builder().build();
        this.workspacePath = workspacePath;
        this.teamName = teamName;
        this.mode = mode != null ? mode : WorkspaceMode.LOCAL;
    }

    /**
     * initialize.
     * 
     * @throws IOException IOException
     * @since 0.1.7
     */
    public void initialize() throws IOException {
        Files.createDirectories(Path.of(workspacePath));
        for (String dir : config.getArtifactDirs()) {
            Files.createDirectories(Path.of(workspacePath).resolve(dir));
        }
        Files.createDirectories(Path.of(workspacePath).resolve("skills"));
    }

    /**
     * acquireLock.
     * 
     * @param filePath filePath
     * @param holderId holderId
     * @param holderName holderName
     * @param timeoutSeconds timeoutSeconds
     * @return the result
     * @since 0.1.7
     */
    public synchronized boolean acquireLock(String filePath, String holderId, String holderName, int timeoutSeconds) {
        WorkspaceFileLock existing = locks.get(filePath);
        if (existing != null && !existing.isExpired()) {
            return false;
        }
        locks.put(filePath, WorkspaceFileLock.builder().filePath(filePath).holderId(holderId).holderName(holderName)
                .acquiredAt(Instant.now().toString()).timeoutSeconds(timeoutSeconds).build());
        return true;
    }

    /**
     * releaseLock.
     * 
     * @param filePath filePath
     * @param holderId holderId
     * @return the result
     * @since 0.1.7
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
     * getLock.
     *
     * @param filePath filePath
     * @return an {@link Optional} containing the lock, or {@link Optional#empty()}
     *     if no lock exists or it has expired
     * @since 0.1.7
     */
    public synchronized Optional<WorkspaceFileLock> getLock(String filePath) {
        WorkspaceFileLock lock = locks.get(filePath);
        if (lock != null && lock.isExpired()) {
            locks.remove(filePath);
            return Optional.empty();
        }
        return Optional.ofNullable(lock);
    }

    /**
     * listLocks.
     * 
     * @return the result
     * @since 0.1.7
     */
    public synchronized List<WorkspaceFileLock> listLocks() {
        locks.entrySet().removeIf(entry -> entry.getValue().isExpired());
        return List.copyOf(locks.values());
    }

    /**
     * handleLockRequest.
     * 
     * @param request request
     * @return the result
     * @since 0.1.7
     */
    public WorkspaceLockResponse handleLockRequest(WorkspaceLockRequest request) {
        if (request == null) {
            return WorkspaceLockResponse.builder().teamName(teamName).memberName("").filePath("").isGranted(false)
                    .build();
        }
        boolean isGranted = false;
        String memberName = request.getMemberName() != null ? request.getMemberName() : "";
        if ("acquire".equals(request.getAction())) {
            isGranted = acquireLock(request.getFilePath(), memberName,
                    request.getHolderName() != null ? request.getHolderName() : memberName,
                    request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 300);
        } else if ("release".equals(request.getAction())) {
            isGranted = releaseLock(request.getFilePath(), memberName);
        }

        Map<String, Object> holder = null;
        if (!isGranted) {
            Optional<WorkspaceFileLock> existingOpt = getLock(request.getFilePath());
            if (existingOpt.isPresent()) {
                holder = lockToMap(existingOpt.get());
            }
        }
        return WorkspaceLockResponse.builder().teamName(teamName).memberName(memberName).filePath(request.getFilePath())
                .isGranted(isGranted).holder(holder).build();
    }

    /**
     * getHistory.
     * 
     * @param relativePath relativePath
     * @return the result
     * @since 0.1.7
     */
    public List<Map<String, Object>> getHistory(String relativePath) {
        return getHistory(relativePath, 10);
    }

    /**
     * getHistory.
     * 
     * @param relativePath relativePath
     * @param limit limit
     * @return the result
     * @since 0.1.7
     */
    public List<Map<String, Object>> getHistory(String relativePath, int limit) {
        if (!config.isVersionControl()) {
            return List.of();
        }
        String safeRelativePath = resolveSafeHistoryPath(relativePath);
        try {
            ProcessBuilder builder = new ProcessBuilder("git", "log", "--max-count=" + Math.max(1, limit),
                    "--format=%H|%an|%ai|%s", "--", safeRelativePath);
            builder.directory(Path.of(workspacePath).toFile());
            Process process = builder.start();
            CompletableFuture<String> stdoutFuture = OpenJiuwenExecutors.supplyBackgroundAsync(
                    () -> readProcessStream(process));
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

    /**
     * Resolves a history path against the configured workspace and converts it
     * back to a safe relative path for the git command.
     *
     * @param relativePath requested path
     * @return normalized workspace-relative path
     */
    String resolveSafeHistoryPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        Path workspaceRoot = Path.of(workspacePath).toAbsolutePath().normalize();
        Path requestedPath = Path.of(relativePath);
        Path resolvedPath = requestedPath.isAbsolute()
                ? requestedPath.toAbsolutePath().normalize()
                : workspaceRoot.resolve(requestedPath).normalize();
        if (!resolvedPath.startsWith(workspaceRoot)) {
            throw new SecurityException("History path must remain within the team workspace");
        }
        String safeRelativePath = workspaceRoot.relativize(resolvedPath).toString();
        if (safeRelativePath.startsWith("-")) {
            throw new SecurityException("History path must not start with '-'");
        }
        return safeRelativePath;
    }

    /**
     * readProcessStream.
     * 
     * @param process process
     * @return the result
     * @since 0.1.7
     */
    private static String readProcessStream(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * lockToMap.
     * 
     * @param lock lock
     * @return the result
     * @since 0.1.7
     */
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
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TeamWorkspaceConfig getConfig() {
        return config;
    }

    /**
     * getWorkspacePath.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getWorkspacePath() {
        return workspacePath;
    }

    /**
     * getTeamName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTeamName() {
        return teamName;
    }

    /**
     * getMode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public WorkspaceMode getMode() {
        return mode;
    }
}
