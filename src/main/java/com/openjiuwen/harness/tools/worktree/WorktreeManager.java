/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.sysop.Cwd;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Mirrors Python's {@code WorktreeManager} in
 * {@code openjiuwen/harness/tools/worktree/manager.py}.
 */
public class WorktreeManager {

    private static final LoggerProtocol AGENT_LOGGER = Loggers.AGENT;

    private final WorktreeConfig config;
    private final WorktreeBackend backend;
    private final WorktreeEventHandler eventHandler;
    private final List<Object> rails;

    public WorktreeManager(WorktreeConfig config) {
        this(config, null, null, null);
    }

    public WorktreeManager(
            WorktreeConfig config,
            WorktreeBackend backend,
            WorktreeEventHandler eventHandler,
            List<?> rails
    ) {
        this.config = Objects.requireNonNullElseGet(config, WorktreeConfig::new);
        this.backend = backend == null ? WorktreeBackendRegistry.createBackend("git", this.config) : backend;
        this.eventHandler = eventHandler;
        this.rails = rails == null ? List.of() : List.copyOf(rails);
    }

    public WorktreeBackend getBackend() {
        return backend;
    }

    public CompletableFuture<WorktreeSession> enter(String slug) {
        return enter(slug, null, null);
    }

    public CompletableFuture<WorktreeSession> enter(
            String slug,
            String memberName,
            String teamName
    ) {
        try {
            SlugUtils.validateSlug(slug);

            String repoRoot = Git.findCanonicalGitRoot(Cwd.getCwd()).join();
            if (repoRoot == null || repoRoot.isBlank()) {
                throw new RuntimeException("Cannot create worktree: not in a git repository");
            }

            String originalCwd = Cwd.getCwd();
            String originalBranch = Git.getCurrentBranch(repoRoot).join();
            String targetPath = resolveTargetPath(slug);
            long startNanos = System.nanoTime();
            WorktreeCreateResult result = backend.create(slug, repoRoot, targetPath).join();
            double durationMs = (System.nanoTime() - startNanos) / 1_000_000.0D;

            if (!result.isExisted()) {
                postCreationSetup(repoRoot, result.getWorktreePath()).join();
            }

            WorktreeSession session = new WorktreeSession();
            session.setOriginalCwd(originalCwd);
            session.setWorktreePath(result.getWorktreePath());
            session.setWorktreeName(slug);
            session.setWorktreeBranch(result.getWorktreeBranch());
            session.setOriginalBranch(originalBranch);
            session.setOriginalHeadCommit(result.getHeadCommit());
            session.setMemberName(memberName);
            session.setTeamName(teamName);
            session.setHookBased(result.isHookBased());
            session.setCreationDurationMs(durationMs);
            session.setUsedSparsePaths(config.getSparsePaths() != null && !config.getSparsePaths().isEmpty());
            session.setExisted(result.isExisted());

            WorktreeSessionContext.setCurrentSession(session);

            AGENT_LOGGER.info(
                    "Entered worktree '%s' at %s (%s, %.0fms)",
                    slug,
                    result.getWorktreePath(),
                    result.isExisted() ? "recovered" : "created",
                    durationMs
            );

            if (eventHandler != null) {
                eventHandler.handle(
                        new WorktreeCreatedEvent(
                                slug,
                                result.getWorktreePath(),
                                memberName,
                                teamName,
                                result.isExisted()
                        )
                ).join();
            }
            return CompletableFuture.completedFuture(session);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<Map<String, String>> exit(String action) {
        return exit(action, false);
    }

    public CompletableFuture<Map<String, String>> exit(
            String action,
            boolean discardChanges
    ) {
        try {
            WorktreeSession session = WorktreeSessionContext.requireCurrentSession();

            if ("remove".equals(action) && !discardChanges) {
                WorktreeChangeSummary summary = countChanges(session).join();
                if (summary == null) {
                    ErrorHelper.raiseError(
                            StatusCode.TOOL_WORKTREE_EXIT_INVALID,
                            null,
                            null,
                            null,
                            Map.of(
                                    "reason",
                                    "Could not verify worktree state at " + session.getWorktreePath()
                                            + ". Refusing to remove without explicit confirmation. "
                                            + "Set discard_changes=True to proceed, or use action='keep' "
                                            + "to preserve the worktree."
                            )
                    );
                }
                if (summary.getChangedFiles() > 0 || summary.getCommits() > 0) {
                    List<String> parts = new ArrayList<>();
                    if (summary.getChangedFiles() > 0) {
                        parts.add(summary.getChangedFiles() + " uncommitted files");
                    }
                    if (summary.getCommits() > 0) {
                        parts.add(summary.getCommits() + " commits on " + session.getWorktreeBranch());
                    }
                    ErrorHelper.raiseError(
                            StatusCode.TOOL_WORKTREE_EXIT_INVALID,
                            null,
                            null,
                            null,
                            Map.of(
                                    "reason",
                                    "Worktree has " + String.join(" and ", parts)
                                            + ". Removing will discard this work permanently. "
                                            + "Confirm with the user, then set discard_changes=True to proceed, "
                                            + "or use action='keep' to preserve the worktree."
                            )
                    );
                }
            }

            String repoRoot = Git.findCanonicalGitRoot(session.getOriginalCwd()).join();
            if ("keep".equals(action)) {
                WorktreeSessionContext.setCurrentSession(null);
                AGENT_LOGGER.info(
                        "Kept worktree '%s' at %s",
                        session.getWorktreeName(),
                        session.getWorktreePath()
                );
                return CompletableFuture.completedFuture(resultMap("keep", session));
            }

            if (repoRoot != null && !repoRoot.isBlank()) {
                removeWorktree(session.getWorktreePath(), repoRoot).join();
            }

            WorktreeSessionContext.setCurrentSession(null);

            if (eventHandler != null) {
                eventHandler.handle(
                        new WorktreeRemovedEvent(
                                session.getWorktreeName(),
                                session.getWorktreePath(),
                                session.getMemberName(),
                                session.getTeamName()
                        )
                ).join();
            }

            AGENT_LOGGER.info(
                    "Removed worktree '%s' at %s",
                    session.getWorktreeName(),
                    session.getWorktreePath()
            );
            return CompletableFuture.completedFuture(resultMap("remove", session));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<WorktreeCreateResult> createOwnerWorktree(String slug) {
        try {
            SlugUtils.validateSlug(slug);

            String repoRoot = Git.findCanonicalGitRoot(Cwd.getCwd()).join();
            if (repoRoot == null || repoRoot.isBlank()) {
                throw new RuntimeException("Cannot create owner worktree: not in a git repository");
            }

            String targetPath = resolveTargetPath(slug);
            WorktreeCreateResult result = backend.create(slug, repoRoot, targetPath).join();
            if (!result.isExisted()) {
                postCreationSetup(repoRoot, result.getWorktreePath()).join();
            } else {
                try {
                    Files.setLastModifiedTime(
                            Path.of(result.getWorktreePath()),
                            FileTime.fromMillis(System.currentTimeMillis())
                    );
                } catch (IOException ignored) {
                    // Keep parity with Python: failure to touch is non-fatal.
                }
            }
            return CompletableFuture.completedFuture(result);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<WorktreeCreateResult> createAgentWorktree(String slug) {
        return createOwnerWorktree(slug);
    }

    public CompletableFuture<WorktreeChangeSummary> countChanges(WorktreeSession session) {
        List<String> changes = Git.statusPorcelain(session.getWorktreePath()).join();
        int changedFiles = changes == null ? 0 : changes.size();
        if (session.getOriginalHeadCommit() == null || session.getOriginalHeadCommit().isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        Integer commits = Git.countCommitsSince(session.getOriginalHeadCommit(), session.getWorktreePath()).join();
        if (commits == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.completedFuture(new WorktreeChangeSummary(changedFiles, commits));
    }

    public CompletableFuture<WorktreeSession> recoverWorktreeForOwner(String ownerId, String tag) {
        try {
            String slug = ownerSlug(ownerId);
            String repoRoot = Git.findCanonicalGitRoot(Cwd.getCwd()).join();
            if (repoRoot == null || repoRoot.isBlank()) {
                return CompletableFuture.completedFuture(null);
            }

            String worktreePath = resolveTargetPath(slug);
            String headSha = Git.readWorktreeHeadSha(worktreePath).join();
            if (headSha == null || headSha.isBlank()) {
                return CompletableFuture.completedFuture(null);
            }

            WorktreeSession session = new WorktreeSession();
            session.setOriginalCwd(repoRoot);
            session.setWorktreePath(worktreePath);
            session.setWorktreeName(slug);
            session.setWorktreeBranch(Git.getCurrentBranch(worktreePath).join());
            session.setOriginalHeadCommit(headSha);
            session.setMemberName(ownerId);
            session.setTeamName(tag);
            session.setLifecyclePolicy(resolvePolicy());
            return CompletableFuture.completedFuture(session);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<WorktreeSession> recoverWorktreeForMember(String memberName, String teamName) {
        return recoverWorktreeForOwner(memberName, teamName);
    }

    public CompletableFuture<List<String>> cleanupWorktreesByPrefix(String slugPrefix, boolean force) {
        try {
            if (resolvePolicy() == WorktreeLifecyclePolicy.DURABLE && !force) {
                AGENT_LOGGER.info(
                        "Skipping worktree cleanup for prefix %s: durable policy active",
                        slugPrefix
                );
                return CompletableFuture.completedFuture(List.of());
            }

            String repoRoot = Git.findCanonicalGitRoot(Cwd.getCwd()).join();
            if (repoRoot == null || repoRoot.isBlank()) {
                return CompletableFuture.completedFuture(List.of());
            }

            String workspace = Cwd.getWorkspace();
            if (workspace == null || workspace.isBlank()) {
                AGENT_LOGGER.info(
                        "Skipping worktree cleanup for prefix %s: agent workspace not set",
                        slugPrefix
                );
                return CompletableFuture.completedFuture(List.of());
            }

            Path worktreesDir = Path.of(SlugUtils.worktreesDir(workspace));
            if (!Files.isDirectory(worktreesDir)) {
                return CompletableFuture.completedFuture(List.of());
            }

            List<String> removed = new ArrayList<>();
            try (var stream = Files.list(worktreesDir)) {
                for (Path path : stream.toList()) {
                    String slug = path.getFileName().toString();
                    if (!slug.startsWith(slugPrefix)) {
                        continue;
                    }
                    if (!force) {
                        WorktreeChangeSummary summary = checkChanges(path.toString()).join();
                        if (summary != null && (summary.getChangedFiles() > 0 || summary.getCommits() > 0)) {
                            AGENT_LOGGER.warning("Skipping worktree '%s': has uncommitted changes", slug);
                            continue;
                        }
                    }
                    if (removeWorktree(path.toString(), repoRoot).join()) {
                        removed.add(path.toString());
                        if (eventHandler != null) {
                            eventHandler.handle(
                                    new WorktreeRemovedEvent(
                                            slug,
                                            path.toString(),
                                            null,
                                            null
                                    )
                            ).join();
                        }
                    }
                }
            } catch (IOException exception) {
                return CompletableFuture.completedFuture(removed);
            }

            if (!removed.isEmpty()) {
                Git.worktreePrune(repoRoot).join();
            }
            return CompletableFuture.completedFuture(removed);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<List<String>> cleanupWorktreesByPrefix() {
        return cleanupWorktreesByPrefix("teammate-", false);
    }

    public CompletableFuture<List<String>> cleanupTeamWorktrees(String teamName, boolean force) {
        return cleanupWorktreesByPrefix("teammate-", force);
    }

    public CompletableFuture<Boolean> removeWorktree(String worktreePath, String repoRoot) {
        return backend.remove(worktreePath, repoRoot);
    }

    public CompletableFuture<Object> fireRail(String method, Object... args) {
        try {
            Object result = null;
            for (Object rail : rails) {
                Method handler = findRailMethod(rail, method, args.length);
                if (handler == null) {
                    continue;
                }
                try {
                    Object value = handler.invoke(rail, args);
                    if (value instanceof CompletableFuture<?> future) {
                        value = future.join();
                    }
                    if (value != null) {
                        result = value;
                    }
                } catch (IllegalAccessException | InvocationTargetException exception) {
                    throw new CompletionException(exception);
                }
            }
            return CompletableFuture.completedFuture(result);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    static String resolveTargetPath(String slug) {
        String workspace = Cwd.getWorkspace();
        if (workspace == null || workspace.isBlank()) {
            throw new RuntimeException(
                    "Cannot resolve worktree path: DeepAgent workspace is not set. "
                            + "Worktrees must be created from an agent that has a workspace "
                            + "configured (init_cwd was called with workspace=...)."
            );
        }
        return SlugUtils.worktreePathFor(workspace, slug);
    }

    static String ownerSlug(String ownerId) {
        return "teammate-" + ownerId.substring(0, Math.min(8, ownerId.length()));
    }

    WorktreeLifecyclePolicy resolvePolicy() {
        if (config.getLifecyclePolicy() != WorktreeLifecyclePolicy.AUTO) {
            return config.getLifecyclePolicy();
        }
        return WorktreeLifecyclePolicy.EPHEMERAL;
    }

    private CompletableFuture<Void> postCreationSetup(String repoRoot, String worktreePath) {
        List<String> symlinkDirectories = config.getSymlinkDirectories();
        if (symlinkDirectories != null) {
            for (String directory : symlinkDirectories) {
                if (directory == null || directory.contains("..")) {
                    AGENT_LOGGER.warning("Skipping symlink for '%s': path traversal detected", directory);
                    continue;
                }
                Path relative;
                try {
                    relative = Path.of(directory);
                } catch (RuntimeException exception) {
                    AGENT_LOGGER.warning("Skipping symlink for '%s': invalid path", directory);
                    continue;
                }
                if (relative.isAbsolute()) {
                    AGENT_LOGGER.warning("Skipping symlink for '%s': path traversal detected", directory);
                    continue;
                }
                Path source = Path.of(repoRoot).resolve(relative);
                Path target = Path.of(worktreePath).resolve(relative);
                try {
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    Files.createSymbolicLink(target, source);
                    AGENT_LOGGER.debug("Symlinked %s to worktree", directory);
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                    // Existing links/files are left untouched to match Python behavior.
                } catch (java.nio.file.NoSuchFileException ignored) {
                    // Missing source is non-fatal.
                } catch (IOException exception) {
                    AGENT_LOGGER.warning("Failed to symlink %s: %s", directory, exception.getMessage());
                }
            }
        }

        List<String> includePatterns = config.getIncludePatterns();
        if (includePatterns != null && !includePatterns.isEmpty()) {
            copyIncludeFiles(repoRoot, worktreePath, includePatterns).join();
        }
        configureHooksPath(repoRoot, worktreePath).join();
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<List<String>> copyIncludeFiles(
            String repoRoot,
            String worktreePath,
            List<String> patterns
    ) {
        Git.GitResult result = Git.runGit(
                List.of("ls-files", "--others", "--ignored", "--exclude-standard", "--directory"),
                repoRoot,
                false
        ).join();
        if (!result.ok() || result.stdout().isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<String> copied = new ArrayList<>();
        for (String entry : result.stdout().split("\\R")) {
            if (entry == null || entry.isBlank() || entry.endsWith("/")) {
                continue;
            }
            if (!matchesAnyPattern(entry, patterns)) {
                continue;
            }

            Path source = Path.of(repoRoot).resolve(entry);
            Path target = Path.of(worktreePath).resolve(entry);
            try {
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.copy(
                        source,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES
                );
                copied.add(entry);
            } catch (IOException exception) {
                AGENT_LOGGER.warning("Failed to copy %s: %s", entry, exception.getMessage());
            }
        }
        return CompletableFuture.completedFuture(copied);
    }

    private CompletableFuture<Void> configureHooksPath(String repoRoot, String worktreePath) {
        for (Path candidate : List.of(
                Path.of(repoRoot).resolve(".husky"),
                Path.of(repoRoot).resolve(".git").resolve("hooks")
        )) {
            if (!Files.isDirectory(candidate)) {
                continue;
            }
            Git.runGit(List.of("config", "core.hooksPath", candidate.toString()), worktreePath, false).join();
            AGENT_LOGGER.debug("Configured worktree hooks path: %s", candidate);
            break;
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<WorktreeChangeSummary> checkChanges(String worktreePath) {
        List<String> changes = Git.statusPorcelain(worktreePath).join();
        return CompletableFuture.completedFuture(new WorktreeChangeSummary(changes == null ? 0 : changes.size(), 0));
    }

    private static boolean matchesAnyPattern(String entry, List<String> patterns) {
        Path path = Path.of(entry);
        for (String pattern : patterns) {
            try {
                if (FileSystems.getDefault().getPathMatcher("glob:" + pattern).matches(path)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // Invalid user patterns are ignored like Python's best-effort matching.
            }
        }
        return false;
    }

    private static Method findRailMethod(Object rail, String method, int arity) {
        if (rail == null) {
            return null;
        }
        for (Method candidate : rail.getClass().getMethods()) {
            if (candidate.getName().equals(method) && candidate.getParameterCount() == arity) {
                return candidate;
            }
        }
        return null;
    }

    private static Map<String, String> resultMap(String action, WorktreeSession session) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("action", action);
        result.put("original_cwd", session.getOriginalCwd());
        result.put("worktree_path", session.getWorktreePath());
        result.put("worktree_branch", session.getWorktreeBranch());
        return result;
    }
}
