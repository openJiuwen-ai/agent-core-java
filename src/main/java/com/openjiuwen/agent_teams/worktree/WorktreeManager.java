/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.events.TeamTopic;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Worktree lifecycle manager.
 *
 * <p>Coordinates worktree creation, removal, session state, event publishing,
 * change detection before removal, and rail dispatch.</p>
 *
 * <p>Mirrors Python's {@code WorktreeManager} in
 * {@code openjiuwen.agent_teams.worktree.manager}.</p>
 */
public class WorktreeManager {

    private static final Logger LOGGER = Logger.getLogger(WorktreeManager.class.getName());

    private final WorktreeConfig config;
    private final Messager messager;
    private final String workspaceRoot;
    private final WorktreeBackend backend;
    private final List<Object> rails;
    private final GitProbe gitProbe;
    private final boolean pythonWorkspaceLayout;
    private Consumer<WorktreeEvent> eventHandler;
    private String currentCwd = System.getProperty("user.dir");

    public WorktreeManager(WorktreeConfig config) {
        this(config, null, null);
    }

    public WorktreeManager(WorktreeConfig config, Messager messager, String workspaceRoot) {
        this(config, messager, workspaceRoot, new GitBackend(), List.of(), null, false);
    }

    public WorktreeManager(
            WorktreeConfig config,
            Messager messager,
            String workspaceRoot,
            WorktreeBackend backend,
            List<?> rails
    ) {
        this(config, messager, workspaceRoot, backend, rails, null, false);
    }

    /**
     * Constructor used by the Python-parity tests and translated call sites.
     */
    public WorktreeManager(
            WorktreeConfig config,
            WorktreeBackend backend,
            Consumer<WorktreeEvent> eventHandler,
            List<?> rails,
            String workspaceRoot,
            GitProbe gitProbe
    ) {
        this(config, null, workspaceRoot, backend, rails, gitProbe, true);
        this.eventHandler = eventHandler;
    }

    private WorktreeManager(
            WorktreeConfig config,
            Messager messager,
            String workspaceRoot,
            WorktreeBackend backend,
            List<?> rails,
            GitProbe gitProbe,
            boolean pythonWorkspaceLayout
    ) {
        this.config = config != null ? config : new WorktreeConfig();
        this.messager = messager;
        this.workspaceRoot = workspaceRoot;
        this.backend = backend != null ? backend : new GitBackend();
        this.rails = rails != null ? new ArrayList<>(rails) : new ArrayList<>();
        this.gitProbe = gitProbe != null
                ? gitProbe
                : (pythonWorkspaceLayout ? GitProbe.defaultProbe() : GitProbe.legacyProbe(workspaceRoot));
        this.pythonWorkspaceLayout = pythonWorkspaceLayout;
    }

    public void setEventHandler(Consumer<WorktreeEvent> handler) {
        this.eventHandler = handler;
    }

    public void setCurrentCwd(String currentCwd) {
        this.currentCwd = currentCwd != null ? currentCwd : System.getProperty("user.dir");
    }

    /**
     * Create or recover a worktree and enter it.
     */
    public WorktreeSession enter(String slug, String memberName, String teamName) {
        SlugUtils.validateSlug(slug);

        String repoRoot = gitProbe.findCanonicalGitRoot(currentCwd);
        if (repoRoot == null || repoRoot.isBlank()) {
            throw new IllegalStateException("Cannot create worktree: not in a git repository");
        }

        String originalCwd = currentCwd;
        String originalBranch = gitProbe.getCurrentBranch(repoRoot);
        String targetPath = resolveTargetPath(slug, workspaceRoot != null ? workspaceRoot : repoRoot);

        long startNanos = System.nanoTime();
        WorktreeModels.WorktreeCreateResult result = backend.create(slug, repoRoot, targetPath);
        if (result == null || !result.isSuccess()) {
            String message = result != null ? result.getError() : "backend returned null";
            throw new IllegalStateException("Cannot create worktree: " + message);
        }
        double durationMs = (System.nanoTime() - startNanos) / 1_000_000.0;

        WorktreeSession session = new WorktreeSession(
                originalCwd,
                result.getWorktreePath(),
                slug,
                result.getBranchName(),
                originalBranch,
                result.getHeadCommit(),
                memberName,
                teamName,
                false,
                resolvePolicy(),
                null,
                durationMs,
                config.getSparsePaths() != null && !config.getSparsePaths().isEmpty()
        );
        session.setWorkspaceRoot(workspaceRoot);

        WorktreeSessionHolder.setCurrentSession(session);
        linkWorktreeToWorkspace(slug, result.getWorktreePath());

        publishEvent("worktree_created", teamName, Map.of(
                "worktree_name", slug,
                "worktree_path", result.getWorktreePath(),
                "duration_ms", durationMs
        ));

        LOGGER.info(() -> "Entered worktree '" + slug + "' at " + result.getWorktreePath());
        return session;
    }

    public java.util.concurrent.CompletableFuture<WorktreeSession> enterAsync(
            String slug,
            String memberName,
            String teamName
    ) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> enter(slug, memberName, teamName));
    }

    /**
     * Exit the current worktree session.
     */
    public Map<String, String> exit(String action) {
        return exit(action, false);
    }

    public Map<String, String> exit(String action, boolean discardChanges) {
        WorktreeSession session = WorktreeSessionHolder.requireCurrentSession();
        String normalizedAction = (action == null || action.isBlank()) ? "keep" : action;

        if ("remove".equals(normalizedAction) && !discardChanges) {
            WorktreeChangeSummary summary = countChanges(session);
            if (summary != null && (summary.getChangedFiles() > 0 || summary.getCommits() > 0)) {
                List<String> parts = new ArrayList<>();
                if (summary.getChangedFiles() > 0) {
                    parts.add(summary.getChangedFiles() + " uncommitted files");
                }
                if (summary.getCommits() > 0) {
                    parts.add(summary.getCommits() + " commits on " + session.getWorktreeBranch());
                }
                throw new IllegalArgumentException(
                        "Worktree has " + String.join(" and ", parts)
                                + ". Set discard_changes=true to proceed."
                );
            }
        }

        String repoRoot = gitProbe.findCanonicalGitRoot(session.getOriginalCwd());
        if ("keep".equals(normalizedAction)) {
            WorktreeSessionHolder.setCurrentSession(null);
            return resultMap("keep", session);
        }

        if (!"remove".equals(normalizedAction)) {
            throw new IllegalArgumentException("'action' must be 'keep' or 'remove'.");
        }

        if (repoRoot != null && !repoRoot.isBlank()) {
            backend.remove(session.getWorktreePath(), repoRoot);
        }
        unlinkWorktreeFromWorkspace(session.getWorktreeName());
        WorktreeSessionHolder.setCurrentSession(null);

        publishEvent("worktree_removed", session.getTeamName(), Map.of(
                "worktree_name", session.getWorktreeName(),
                "worktree_path", session.getWorktreePath()
        ));

        return resultMap("remove", session);
    }

    public boolean removeCurrent(boolean force) {
        if (WorktreeSessionHolder.getCurrentSession() == null) {
            return false;
        }
        try {
            exit("remove", force);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public java.util.concurrent.CompletableFuture<Boolean> removeCurrentAsync(boolean force) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> removeCurrent(force));
    }

    /**
     * Create a worktree without entering it.
     */
    public WorktreeModels.WorktreeCreateResult create(String slug, String repoRoot) {
        SlugUtils.validateSlug(slug);
        String targetPath = resolveTargetPath(slug, repoRoot);
        return backend.create(slug, repoRoot, targetPath);
    }

    public WorktreeModels.WorktreeCreateResult createAgentWorktree(String slug) {
        SlugUtils.validateSlug(slug);
        String repoRoot = gitProbe.findCanonicalGitRoot(currentCwd);
        if (repoRoot == null || repoRoot.isBlank()) {
            throw new IllegalStateException("Cannot create agent worktree: not in a git repository");
        }

        String targetPath = resolveTargetPath(slug, workspaceRoot != null ? workspaceRoot : repoRoot);
        WorktreeModels.WorktreeCreateResult result = backend.create(slug, repoRoot, targetPath);
        if (result == null || !result.isSuccess()) {
            String message = result != null ? result.getError() : "backend returned null";
            throw new IllegalStateException("Cannot create agent worktree: " + message);
        }
        linkWorktreeToWorkspace(slug, result.getWorktreePath());
        return result;
    }

    public boolean remove(String worktreePath, boolean force) {
        boolean removed = backend.remove(worktreePath, force);
        if (removed) {
            unlinkWorktreeFromWorkspace(Path.of(worktreePath).getFileName().toString());
        }
        return removed;
    }

    public boolean removeWorktree(String worktreePath, String repoRoot) {
        boolean removed = backend.remove(worktreePath, repoRoot);
        if (removed) {
            unlinkWorktreeFromWorkspace(Path.of(worktreePath).getFileName().toString());
        }
        return removed;
    }

    public WorktreeChangeSummary summarizeChanges() {
        WorktreeSession session = WorktreeSessionHolder.getCurrentSession();
        if (session == null) {
            return new WorktreeChangeSummary(false, 0, null);
        }
        WorktreeChangeSummary summary = countChanges(session);
        return summary != null ? summary : new WorktreeChangeSummary(false, 0, null);
    }

    public WorktreeChangeSummary countChanges(WorktreeSession session) {
        if (session == null) {
            return null;
        }
        List<String> changes = gitProbe.statusPorcelain(session.getWorktreePath());
        if (session.getOriginalHeadCommit() == null || session.getOriginalHeadCommit().isBlank()) {
            return null;
        }
        Integer commits = gitProbe.countCommitsSince(session.getOriginalHeadCommit(), session.getWorktreePath());
        if (commits == null) {
            return null;
        }
        return new WorktreeChangeSummary(changes.size(), commits);
    }

    public WorktreeSession recoverWorktreeForMember(String memberName, String teamName) {
        String slug = memberSlug(memberName);
        String repoRoot = gitProbe.findCanonicalGitRoot(currentCwd);
        if (repoRoot == null || repoRoot.isBlank()) {
            return null;
        }
        String worktreePath = resolveTargetPath(slug, workspaceRoot != null ? workspaceRoot : repoRoot);
        String headSha = Git.readWorktreeHeadSha(worktreePath).join();
        if (headSha == null) {
            return null;
        }
        String branch = gitProbe.getCurrentBranch(worktreePath);
        return new WorktreeSession(
                repoRoot,
                worktreePath,
                slug,
                branch,
                null,
                headSha,
                memberName,
                teamName,
                false,
                resolvePolicy(),
                null,
                null,
                false
        );
    }

    public List<String> cleanupTeamWorktrees(String teamName, boolean force) {
        if (resolvePolicy() == WorktreeLifecyclePolicy.DURABLE && !force) {
            return List.of();
        }

        String repoRoot = gitProbe.findCanonicalGitRoot(currentCwd);
        if (repoRoot == null || repoRoot.isBlank() || workspaceRoot == null || workspaceRoot.isBlank()) {
            return List.of();
        }

        Path worktreesDir = worktreesDir(workspaceRoot);
        if (!Files.isDirectory(worktreesDir)) {
            return List.of();
        }

        List<String> removed = new ArrayList<>();
        try (Stream<Path> paths = Files.list(worktreesDir)) {
            for (Path wtPath : paths.toList()) {
                String slug = wtPath.getFileName().toString();
                if (!slug.startsWith("teammate-")) {
                    continue;
                }
                if (!force) {
                    WorktreeChangeSummary summary = checkChanges(wtPath.toString());
                    if (summary != null && (summary.getChangedFiles() > 0 || summary.getCommits() > 0)) {
                        continue;
                    }
                }
                if (backend.remove(wtPath.toString(), repoRoot)) {
                    unlinkWorktreeFromWorkspace(slug);
                    removed.add(wtPath.toString());
                }
            }
        } catch (Exception e) {
            return removed;
        }

        if (!removed.isEmpty()) {
            gitProbe.worktreePrune(repoRoot);
        }
        return removed;
    }

    public String memberSlug(String memberName) {
        String safe = memberName != null ? memberName : "";
        return "teammate-" + safe.substring(0, Math.min(8, safe.length()));
    }

    public WorktreeLifecyclePolicy resolvePolicy() {
        if (config.getLifecyclePolicy() != WorktreeLifecyclePolicy.AUTO) {
            return config.getLifecyclePolicy();
        }
        return WorktreeLifecyclePolicy.EPHEMERAL;
    }

    public Object fireRail(String method, Object... args) {
        Object result = null;
        for (Object rail : rails) {
            Method handler = findMethod(rail, method, args.length);
            if (handler == null) {
                continue;
            }
            try {
                Object value = handler.invoke(rail, args);
                if (value instanceof CompletionStage<?> stage) {
                    value = stage.toCompletableFuture().join();
                }
                if (value != null) {
                    result = value;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new IllegalStateException("Rail method failed: " + method, e);
            }
        }
        return result;
    }

    private WorktreeChangeSummary checkChanges(String worktreePath) {
        List<String> changes = gitProbe.statusPorcelain(worktreePath);
        return new WorktreeChangeSummary(changes.size(), 0);
    }

    private Method findMethod(Object rail, String method, int arity) {
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

    private String resolveTargetPath(String slug, String ownerRoot) {
        if (config.getBaseDir() != null && !config.getBaseDir().isBlank()) {
            return Path.of(config.getBaseDir(), slug).toString();
        }
        String root = ownerRoot != null && !ownerRoot.isBlank() ? ownerRoot : System.getProperty("user.dir");
        if (pythonWorkspaceLayout) {
            return SlugUtils.worktreePathFor(root, slug);
        }
        return Path.of(root, ".agent_teams", "worktrees", slug).toString();
    }

    private Path worktreesDir(String ownerRoot) {
        if (config.getBaseDir() != null && !config.getBaseDir().isBlank()) {
            return Path.of(config.getBaseDir());
        }
        if (pythonWorkspaceLayout) {
            return Path.of(SlugUtils.worktreesDir(ownerRoot));
        }
        return Path.of(ownerRoot, ".agent_teams", "worktrees");
    }

    private void linkWorktreeToWorkspace(String slug, String worktreePath) {
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            return;
        }
        Path link = Path.of(workspaceRoot, ".worktree", slug);
        try {
            Files.createDirectories(link.getParent());
            if (Files.isSymbolicLink(link)) {
                Files.deleteIfExists(link);
            } else if (Files.exists(link)) {
                return;
            }
            Files.createSymbolicLink(link, Path.of(worktreePath));
        } catch (Exception e) {
            LOGGER.fine(() -> "Skipping workspace worktree symlink: " + e.getMessage());
        }
    }

    private void unlinkWorktreeFromWorkspace(String slug) {
        if (workspaceRoot == null || workspaceRoot.isBlank() || slug == null || slug.isBlank()) {
            return;
        }
        Path link = Path.of(workspaceRoot, ".worktree", slug);
        try {
            if (Files.isSymbolicLink(link)) {
                Files.deleteIfExists(link);
            }
        } catch (Exception e) {
            LOGGER.fine(() -> "Skipping workspace worktree unlink: " + e.getMessage());
        }
    }

    private void publishEvent(String eventType, String teamName, Map<String, Object> payload) {
        if (messager != null && teamName != null) {
            try {
                messager.publish(TeamTopic.TEAM.build("shared", teamName), new EventMessage(eventType, payload));
            } catch (RuntimeException e) {
                LOGGER.warning("Failed to publish worktree event: " + e.getMessage());
            }
        }
        if (eventHandler != null) {
            eventHandler.accept(new WorktreeEvent(eventType, teamName, payload));
        }
    }

    private Map<String, String> resultMap(String action, WorktreeSession session) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("action", action);
        result.put("original_cwd", session.getOriginalCwd());
        result.put("worktree_path", session.getWorktreePath());
        result.put("worktree_branch", session.getWorktreeBranch());
        return result;
    }

    public WorktreeConfig getConfig() {
        return config;
    }

    public WorktreeBackend getBackend() {
        return backend;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public List<Object> getRails() {
        return List.copyOf(rails);
    }

    public interface GitProbe {
        String findCanonicalGitRoot(String cwd);

        String getCurrentBranch(String cwd);

        List<String> statusPorcelain(String cwd);

        Integer countCommitsSince(String baseCommit, String cwd);

        void worktreePrune(String repoRoot);

        static GitProbe defaultProbe() {
            return new GitProbe() {
                @Override
                public String findCanonicalGitRoot(String cwd) {
                    return Git.findCanonicalGitRoot(cwd).join();
                }

                @Override
                public String getCurrentBranch(String cwd) {
                    return Git.getCurrentBranch(cwd).join();
                }

                @Override
                public List<String> statusPorcelain(String cwd) {
                    return Git.statusPorcelain(cwd).join();
                }

                @Override
                public Integer countCommitsSince(String baseCommit, String cwd) {
                    return Git.countCommitsSince(baseCommit, cwd).join();
                }

                @Override
                public void worktreePrune(String repoRoot) {
                    Git.worktreePrune(repoRoot).join();
                }
            };
        }

        static GitProbe legacyProbe(String workspaceRoot) {
            return new GitProbe() {
                @Override
                public String findCanonicalGitRoot(String cwd) {
                    return workspaceRoot != null && !workspaceRoot.isBlank() ? workspaceRoot : cwd;
                }

                @Override
                public String getCurrentBranch(String cwd) {
                    return "main";
                }

                @Override
                public List<String> statusPorcelain(String cwd) {
                    return List.of();
                }

                @Override
                public Integer countCommitsSince(String baseCommit, String cwd) {
                    return 0;
                }

                @Override
                public void worktreePrune(String repoRoot) {
                    // Legacy synchronous manager used no-op cleanup.
                }
            };
        }
    }

    /**
     * Backend interface for worktree operations.
     */
    public interface WorktreeBackend {
        WorktreeModels.WorktreeCreateResult create(String slug, String repoRoot, String targetPath);

        boolean remove(String worktreePath, boolean force);

        default boolean remove(String worktreePath, String repoRoot) {
            return remove(worktreePath, true);
        }

        WorktreeChangeSummary detectChanges(String worktreePath);
    }

    /**
     * Git-based worktree backend implementation used by legacy synchronous APIs.
     */
    public static class GitBackend implements WorktreeBackend {
        @Override
        public WorktreeModels.WorktreeCreateResult create(String slug, String repoRoot, String targetPath) {
            Objects.requireNonNull(targetPath, "targetPath");
            return WorktreeModels.WorktreeCreateResult.success(
                    targetPath,
                    SlugUtils.worktreeBranchName(slug),
                    Git.revParse("HEAD", repoRoot).join()
            );
        }

        @Override
        public boolean remove(String worktreePath, boolean force) {
            if (worktreePath == null || worktreePath.isBlank()) {
                return false;
            }
            String repoRoot = Git.findCanonicalGitRoot(worktreePath).join();
            if (repoRoot == null || repoRoot.isBlank()) {
                return false;
            }
            return Git.worktreeRemove(worktreePath, repoRoot, force).join();
        }

        @Override
        public boolean remove(String worktreePath, String repoRoot) {
            return Git.worktreeRemove(worktreePath, repoRoot, true).join();
        }

        @Override
        public WorktreeChangeSummary detectChanges(String worktreePath) {
            return new WorktreeChangeSummary(Git.statusPorcelain(worktreePath).join().size(), 0);
        }
    }

    /**
     * Simple worktree event class for event publishing.
     */
    public static class WorktreeEvent {
        private final String type;
        private final String teamName;
        private final Map<String, Object> payload;

        public WorktreeEvent(String type, String teamName, Map<String, Object> payload) {
            this.type = type;
            this.teamName = teamName;
            this.payload = payload != null ? Map.copyOf(payload) : Map.of();
        }

        public String getType() {
            return type;
        }

        public String getTeamName() {
            return teamName;
        }

        public Map<String, Object> getPayload() {
            return payload;
        }
    }
}
