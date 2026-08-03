/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import com.openjiuwen.agent_teams.schema.BaseEventMessage;
import com.openjiuwen.agent_teams.schema.WorkspaceLockRequestEvent;
import com.openjiuwen.agent_teams.schema.WorkspaceLockResponseEvent;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.harness.tools.worktree.Git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Manages team shared workspace metadata and version control.
 *
 * <p>Mirrors Python's {@code TeamWorkspaceManager} in
 * {@code openjiuwen/agent_teams/team_workspace/manager.py}.</p>
 */
public class TeamWorkspaceManager {

    static final int ERROR_PRIVILEGE_NOT_HELD = 1314;

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TeamWorkspaceConfig config;
    private final String workspacePath;
    private final String teamName;
    private final WorkspaceMode mode;
    private final PublishEventCallback publishEvent;
    private final Object messager;
    private final String leaderId;
    private final String nodeId;
    private final Map<String, WorkspaceFileLock> locks = new ConcurrentHashMap<>();
    private final ReentrantLock lockMutex = new ReentrantLock();
    private final Map<String, CompletableFuture<WorkspaceLockResponseEvent>> pendingLockRequests =
            new ConcurrentHashMap<>();

    public TeamWorkspaceManager(
            TeamWorkspaceConfig config,
            String workspacePath,
            String teamName
    ) {
        this(config, workspacePath, teamName, WorkspaceMode.LOCAL, null, null, null, null);
    }

    public TeamWorkspaceManager(
            TeamWorkspaceConfig config,
            String workspacePath,
            String teamName,
            WorkspaceMode mode,
            Object messager,
            String leaderId,
            String nodeId,
            PublishEventCallback publishEvent
    ) {
        this.config = config == null ? new TeamWorkspaceConfig() : config;
        this.workspacePath = Objects.requireNonNull(workspacePath, "workspacePath");
        this.teamName = Objects.requireNonNull(teamName, "teamName");
        this.mode = mode == null ? WorkspaceMode.LOCAL : mode;
        this.messager = messager;
        this.leaderId = leaderId;
        this.nodeId = nodeId;
        this.publishEvent = publishEvent;
    }

    public TeamWorkspaceConfig getConfig() {
        return config;
    }

    public String getWorkspacePath() {
        return workspacePath;
    }

    public String getTeamName() {
        return teamName;
    }

    public WorkspaceMode getMode() {
        return mode;
    }

    public PublishEventCallback getPublishEvent() {
        return publishEvent;
    }

    public CompletableFuture<Void> initialize() {
        return initialize(null);
    }

    public CompletableFuture<Void> initialize(String remoteUrl) {
        try {
            Files.createDirectories(Path.of(workspacePath));
            for (String directory : config.getArtifactDirs()) {
                Files.createDirectories(Path.of(workspacePath).resolve(directory));
            }
            Files.createDirectories(Path.of(workspacePath).resolve("skills"));

            if (!config.isVersionControl()) {
                TEAM_LOGGER.info(
                        "Workspace {} initialized as plain shared directory (version_control disabled)",
                        workspacePath);
                return CompletableFuture.completedFuture(null);
            }

            Path gitDir = Path.of(workspacePath).resolve(".git");
            if (Files.isDirectory(gitDir)) {
                TEAM_LOGGER.debug("Workspace already initialized at {}", workspacePath);
                return CompletableFuture.completedFuture(null);
            }

            if (mode == WorkspaceMode.DISTRIBUTED
                    && isNonBlank(remoteUrl)
                    && !Objects.equals(leaderId, nodeId)) {
                Path parent = Path.of(workspacePath).getParent();
                String name = Path.of(workspacePath).getFileName().toString();
                runGit(List.of("clone", remoteUrl, name), parent == null ? null : parent.toString(), true).join();
                TEAM_LOGGER.info("Cloned workspace repo from {}", remoteUrl);
                return CompletableFuture.completedFuture(null);
            }

            runGit(List.of("init"), workspacePath, true).join();
            runGit(List.of("commit", "--allow-empty", "-m", "Initialize team workspace"), workspacePath, true).join();
            TEAM_LOGGER.info("Initialized workspace git repo at {}", workspacePath);

            if (mode == WorkspaceMode.DISTRIBUTED
                    && isNonBlank(remoteUrl)
                    && Objects.equals(leaderId, nodeId)) {
                Git.GitResult existingRemote = runGit(List.of("remote", "get-url", "origin"), workspacePath, false).join();
                if (!existingRemote.ok()) {
                    runGit(List.of("remote", "add", "origin", remoteUrl), workspacePath, false).join();
                    TEAM_LOGGER.info("Added remote origin {}", remoteUrl);
                }
            }

            return CompletableFuture.completedFuture(null);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    protected void mountDirectory(String targetPath, String linkPath) throws IOException {
        try {
            Files.createSymbolicLink(Path.of(linkPath), Path.of(targetPath));
        } catch (IOException exception) {
            if (!isWindows() || !shouldFallbackToJunction(exception)) {
                throw exception;
            }
            createWindowsJunction(targetPath, linkPath);
            TEAM_LOGGER.info(
                    "Symlink privilege unavailable on Windows; mounted {} via junction at {}",
                    targetPath,
                    linkPath);
        }
    }

    protected void createWindowsJunction(String targetPath, String linkPath) throws IOException {
        String systemRoot = System.getenv().getOrDefault("SystemRoot", "C:\\Windows");
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(systemRoot, "System32", "cmd.exe").toString(),
                "/c",
                "mklink",
                "/J",
                linkPath,
                targetPath);
        builder.redirectErrorStream(false);
        Process process;
        try {
            process = builder.start();
            int exitCode = process.waitFor();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (exitCode != 0) {
                String errorOutput = stderr.isBlank() ? stdout : stderr;
                throw new IOException("Failed to create junction " + linkPath + " -> " + targetPath + ": " + errorOutput);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while creating junction " + linkPath, exception);
        }
    }

    boolean isMountedToWorkspace(String linkPath) {
        try {
            return Files.exists(Path.of(linkPath)) && Files.isSameFile(Path.of(linkPath), Path.of(workspacePath));
        } catch (IOException exception) {
            return false;
        }
    }

    void mergeExistingMountContents(String linkPath) {
        Path link = Path.of(linkPath);
        if (!Files.isDirectory(link) || Files.isSymbolicLink(link)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(link)) {
            for (Path source : stream.toList()) {
                Path relative = link.relativize(source);
                Path target = relative.toString().isEmpty() ? Path.of(workspacePath) : Path.of(workspacePath).resolve(relative);
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                    continue;
                }
                if (!Files.exists(target)) {
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        } catch (IOException ignored) {
            // Keep Python's best-effort merge behavior.
        }
    }

    String backupExistingMountPath(String linkPath) throws IOException {
        String stamp = OffsetDateTime.now(ZoneOffset.UTC).format(BACKUP_STAMP);
        Path base = Path.of(linkPath + ".stale-" + stamp);
        Path backup = base;
        int counter = 1;
        while (Files.exists(backup) || Files.isSymbolicLink(backup)) {
            counter += 1;
            backup = Path.of(linkPath + ".stale-" + stamp + "-" + counter);
        }
        Files.move(Path.of(linkPath), backup);
        return backup.toString();
    }

    boolean prepareMountPath(String linkPath) throws IOException {
        Path link = Path.of(linkPath);
        if (!Files.exists(link) && !Files.isSymbolicLink(link)) {
            return true;
        }
        if (isMountedToWorkspace(linkPath)) {
            return false;
        }

        mergeExistingMountContents(linkPath);
        String backupPath = backupExistingMountPath(linkPath);
        TEAM_LOGGER.warning(
                "Replaced stale team workspace mount path {}; previous contents moved to {}",
                linkPath,
                backupPath);
        return true;
    }

    public void mountIntoWorkspace(String workspaceRoot) throws IOException {
        Path teamDir = Path.of(workspaceRoot).resolve(".team");
        Files.createDirectories(teamDir);
        String linkPath = teamDir.resolve(teamName).toString();
        if (prepareMountPath(linkPath)) {
            mountDirectory(workspacePath, linkPath);
            TEAM_LOGGER.debug("Mounted team workspace {} into {}", teamName, linkPath);
        }
    }

    public void mountWorktree(String slug, String worktreePath) throws IOException {
        Path worktreeDir = Path.of(workspacePath).resolve(".worktree");
        Files.createDirectories(worktreeDir);
        Path linkPath = worktreeDir.resolve(slug);
        if (Files.exists(linkPath) || Files.isSymbolicLink(linkPath)) {
            if (Files.isSymbolicLink(linkPath)) {
                Files.deleteIfExists(linkPath);
            } else {
                TEAM_LOGGER.warning(
                        "Worktree mount path '{}' exists and is not a symlink -- skipping",
                        linkPath);
                return;
            }
        }
        mountDirectory(worktreePath, linkPath.toString());
        TEAM_LOGGER.debug("Mounted worktree '{}' at {}", slug, linkPath);
    }

    public void unmountWorktree(String slug) throws IOException {
        Path linkPath = Path.of(workspacePath).resolve(".worktree").resolve(slug);
        if (Files.isSymbolicLink(linkPath)) {
            Files.deleteIfExists(linkPath);
            TEAM_LOGGER.debug("Unmounted worktree '{}' from {}", slug, linkPath);
        }
    }

    public void mountIntoWorktree(String worktreePath) throws IOException {
        String linkPath = Path.of(worktreePath).resolve(".team").toString();
        if (prepareMountPath(linkPath)) {
            mountDirectory(workspacePath, linkPath);
        }

        Path gitignore = Path.of(worktreePath).resolve(".gitignore");
        String existing = Files.exists(gitignore) ? Files.readString(gitignore) : "";
        List<String> additions = new ArrayList<>();
        for (String entry : List.of(".agent/", ".team/")) {
            if (!existing.contains(entry)) {
                additions.add(entry);
            }
        }
        if (additions.isEmpty()) {
            return;
        }
        StringBuilder content = new StringBuilder(existing);
        if (!existing.isEmpty() && !existing.endsWith("\n")) {
            content.append('\n');
        }
        content.append("# Agent Teams managed\n");
        for (String entry : additions) {
            content.append(entry).append('\n');
        }
        Files.writeString(gitignore, content.toString(), StandardCharsets.UTF_8);
    }

    public CompletableFuture<Boolean> pull() {
        if (!config.isVersionControl() || mode != WorkspaceMode.DISTRIBUTED) {
            return CompletableFuture.completedFuture(false);
        }
        Git.GitResult result = runGit(List.of("pull", "--rebase", "--autostash", "origin", "main"), workspacePath, false).join();
        return CompletableFuture.completedFuture(result.ok() && !result.stdout().contains("Already up to date"));
    }

    public CompletableFuture<Boolean> push() {
        if (!config.isVersionControl() || mode != WorkspaceMode.DISTRIBUTED) {
            return CompletableFuture.completedFuture(true);
        }
        Git.GitResult result = runGit(List.of("push", "origin", "main"), workspacePath, false).join();
        if (!result.ok()) {
            TEAM_LOGGER.warning("Workspace push failed: {}. Will retry on next write.", result.stderr());
        }
        return CompletableFuture.completedFuture(result.ok());
    }

    public CompletableFuture<String> autoCommit(String relativePath, String memberName) {
        if (!config.isVersionControl()) {
            return CompletableFuture.completedFuture(null);
        }

        runGit(List.of("add", relativePath), workspacePath, false).join();
        Git.GitResult status = runGit(List.of("diff", "--cached", "--quiet"), workspacePath, false).join();
        if (status.ok()) {
            return CompletableFuture.completedFuture(null);
        }

        Git.GitResult commit = runGit(
                List.of("commit", "-m", "[" + memberName + "] Update " + relativePath),
                workspacePath,
                false).join();
        if (!commit.ok()) {
            return CompletableFuture.completedFuture(null);
        }

        String sha = Git.revParse("HEAD", workspacePath).join();
        if (mode == WorkspaceMode.DISTRIBUTED) {
            boolean pushed = push().join();
            if (!pushed) {
                pull().join();
                if (!push().join()) {
                    TEAM_LOGGER.error("Workspace push failed after retry for {}", relativePath);
                }
            }
        }
        return CompletableFuture.completedFuture(sha);
    }

    public CompletableFuture<List<Map<String, String>>> getHistory(String relativePath) {
        return getHistory(relativePath, 10);
    }

    public CompletableFuture<List<Map<String, String>>> getHistory(String relativePath, int limit) {
        if (!config.isVersionControl()) {
            return CompletableFuture.completedFuture(List.of());
        }
        if (mode == WorkspaceMode.DISTRIBUTED) {
            pull().join();
        }

        Git.GitResult result = runGit(
                List.of("log", "--max-count=" + limit, "--format=%H|%an|%ai|%s", "--", relativePath),
                workspacePath,
                false).join();
        if (!result.ok() || result.stdout().isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<Map<String, String>> history = new ArrayList<>();
        for (String line : result.stdout().split("\\R")) {
            String[] parts = line.split("\\|", 4);
            if (parts.length != 4) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("commit", parts[0]);
            item.put("author", parts[1]);
            item.put("date", parts[2]);
            item.put("message", parts[3]);
            history.add(item);
        }
        return CompletableFuture.completedFuture(history);
    }

    public WorkspaceFileLock getLock(String filePath) {
        WorkspaceFileLock lock = locks.get(filePath);
        if (lock != null && lock.isExpired()) {
            locks.remove(filePath);
            return null;
        }
        return lock;
    }

    public CompletableFuture<Boolean> acquireLock(
            String filePath,
            String memberName,
            String displayName
    ) {
        return acquireLock(filePath, memberName, displayName, 300);
    }

    public CompletableFuture<Boolean> acquireLock(
            String filePath,
            String memberName,
            String displayName,
            int timeoutSeconds
    ) {
        if (mode == WorkspaceMode.DISTRIBUTED && !Objects.equals(leaderId, nodeId)) {
            return remoteAcquireLock(filePath, memberName, displayName, timeoutSeconds);
        }

        lockMutex.lock();
        try {
            WorkspaceFileLock existing = locks.get(filePath);
            if (existing != null && !existing.isExpired() && !Objects.equals(existing.getHolderId(), memberName)) {
                return CompletableFuture.completedFuture(false);
            }
            WorkspaceFileLock lock = new WorkspaceFileLock();
            lock.setFilePath(filePath);
            lock.setHolderId(memberName);
            lock.setHolderName(displayName);
            lock.setAcquiredAt(OffsetDateTime.now(ZoneOffset.UTC).toString());
            lock.setTimeoutSeconds(timeoutSeconds);
            locks.put(filePath, lock);
            return CompletableFuture.completedFuture(true);
        } finally {
            lockMutex.unlock();
        }
    }

    public CompletableFuture<Boolean> releaseLock(String filePath, String memberName) {
        if (mode == WorkspaceMode.DISTRIBUTED && !Objects.equals(leaderId, nodeId)) {
            return remoteReleaseLock(filePath, memberName);
        }

        lockMutex.lock();
        try {
            WorkspaceFileLock existing = locks.get(filePath);
            if (existing == null || !Objects.equals(existing.getHolderId(), memberName)) {
                return CompletableFuture.completedFuture(false);
            }
            locks.remove(filePath);
            return CompletableFuture.completedFuture(true);
        } finally {
            lockMutex.unlock();
        }
    }

    public CompletableFuture<List<WorkspaceFileLock>> listLocks() {
        lockMutex.lock();
        try {
            List<String> expired = new ArrayList<>();
            for (Map.Entry<String, WorkspaceFileLock> entry : locks.entrySet()) {
                if (entry.getValue().isExpired()) {
                    expired.add(entry.getKey());
                }
            }
            expired.forEach(locks::remove);
            return CompletableFuture.completedFuture(new ArrayList<>(locks.values()));
        } finally {
            lockMutex.unlock();
        }
    }

    public CompletableFuture<Boolean> remoteAcquireLock(
            String filePath,
            String memberName,
            String displayName,
            int timeoutSeconds
    ) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Distributed lock acquire is Phase 3"));
    }

    public CompletableFuture<Boolean> remoteReleaseLock(String filePath, String memberName) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Distributed lock release is Phase 3"));
    }

    public CompletableFuture<WorkspaceLockResponseEvent> sendLockRequest(WorkspaceLockRequestEvent request) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Distributed lock messaging is Phase 3"));
    }

    public CompletableFuture<WorkspaceLockResponseEvent> handleLockRequest(WorkspaceLockRequestEvent request) {
        boolean granted = false;
        if ("acquire".equals(request.getAction())) {
            granted = acquireLock(
                    request.getFilePath(),
                    request.getMemberName(),
                    isNonBlank(request.getHolderName()) ? request.getHolderName() : request.getMemberName(),
                    request.getTimeoutSeconds() == null ? 300 : request.getTimeoutSeconds()
            ).join();
        } else if ("release".equals(request.getAction())) {
            granted = releaseLock(request.getFilePath(), request.getMemberName()).join();
        }

        Map<String, Object> holder = null;
        if (!granted) {
            WorkspaceFileLock existing = locks.get(request.getFilePath());
            if (existing != null) {
                holder = new LinkedHashMap<>();
                holder.put("file_path", existing.getFilePath());
                holder.put("holder_id", existing.getHolderId());
                holder.put("holder_name", existing.getHolderName());
                holder.put("acquired_at", existing.getAcquiredAt());
                holder.put("timeout_seconds", existing.getTimeoutSeconds());
            }
        }

        WorkspaceLockResponseEvent response = new WorkspaceLockResponseEvent();
        response.setTeamName(teamName);
        response.setMemberName(request.getMemberName());
        response.setFilePath(request.getFilePath());
        response.setGranted(granted);
        response.setHolder(holder);
        return CompletableFuture.completedFuture(response);
    }

    public void handleLockResponse(WorkspaceLockResponseEvent response) {
        for (String action : List.of("acquire", "release")) {
            String key = action + ":" + response.getFilePath();
            CompletableFuture<WorkspaceLockResponseEvent> future = pendingLockRequests.get(key);
            if (future != null && !future.isDone()) {
                future.complete(response);
                return;
            }
        }
    }

    protected CompletableFuture<Git.GitResult> runGit(List<String> args, String cwd, boolean check) {
        return Git.runGit(args, cwd, check);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean shouldFallbackToJunction(IOException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String lowered = message.toLowerCase();
        return lowered.contains("1314") || lowered.contains("privilege");
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    public interface PublishEventCallback {
        CompletableFuture<Void> publish(String topic, BaseEventMessage event);
    }
}
