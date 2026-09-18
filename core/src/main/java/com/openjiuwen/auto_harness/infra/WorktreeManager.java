/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Create and clean task-scoped git worktrees.
 * <p>
 * Mirrors Python's {@code WorktreeManager} in
 * {@code openjiuwen/auto_harness/infra/worktree_manager.py}.
 */
public class WorktreeManager {

    private static final Logger LOGGER = Logger.getLogger(WorktreeManager.class.getName());
    private static final Pattern UNSAFE_SLUG_CHARS = Pattern.compile("[^a-zA-Z0-9\\p{IsHan}]+");

    private final AutoHarnessConfig config;
    private final Map<String, String> gitEnv;
    private final GitCommandExecutor executor;
    private final ReentrantLock gitLock = new ReentrantLock();

    /**
     * Mirrors Python's subprocess executor in
     * {@code openjiuwen/auto_harness/infra/worktree_manager.py}.
     */
    public interface GitCommandExecutor {
        GitCommandResult execute(List<String> args, String cwd, Map<String, String> env)
                throws IOException, InterruptedException;
    }

    /**
     * Mirrors Python's {@code (returncode, stdout+stderr)} git command tuple in
     * {@code openjiuwen/auto_harness/infra/worktree_manager.py}.
     */
    public record GitCommandResult(int returnCode, String output) {
    }

    /**
     * Mirrors Python's parsed {@code git worktree list --porcelain} entries in
     * {@code openjiuwen/auto_harness/infra/worktree_manager.py}.
     */
    public record WorktreeEntry(String path, String branch) {
    }

    public WorktreeManager(AutoHarnessConfig config) {
        this(config, new ProcessGitCommandExecutor());
    }

    public WorktreeManager(AutoHarnessConfig config, GitCommandExecutor executor) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
        this.gitEnv = GitAuth.buildGitAuthEnv(
                config.resolveGitcodeUsername(),
                config.resolveGitcodeToken()
        );
        this.executor = executor == null ? new ProcessGitCommandExecutor() : executor;
    }

    /**
     * Convert a task topic into a filesystem-safe slug.
     *
     * @param topic task topic
     * @return slug containing only letters, digits, CJK ideographs, and hyphens
     */
    public static String slugify(String topic) {
        String raw = topic == null ? "" : topic;
        String slug = UNSAFE_SLUG_CHARS.matcher(raw).replaceAll("-");
        slug = stripHyphen(slug);
        if (slug.length() > 40) {
            slug = slug.substring(0, 40);
        }
        return slug.isBlank() ? "task" : slug;
    }

    /**
     * Create an isolated worktree for one task.
     *
     * @param topic task topic used for directory and branch names
     * @return absolute or configured worktree path
     * @throws IOException when filesystem or git IO fails
     * @throws InterruptedException when git execution is interrupted
     */
    public String prepare(String topic) throws IOException, InterruptedException {
        gitLock.lock();
        try {
            String base = ensureBaseRepo();
            String slug = slugify(topic);
            long timestamp = Instant.now().getEpochSecond();
            String worktreeName = timestamp + "-" + slug;
            String branchName = "auto-harness/" + slug;

            Path worktreeRoot = Path.of(config.getWorktreesDir());
            Files.createDirectories(worktreeRoot);
            String worktreePath = worktreeRoot.resolve(worktreeName).toString();
            String baseBranch = isBlank(config.getGitBaseBranch()) ? "develop" : config.getGitBaseBranch();

            dropExistingBranch(base, branchName);

            GitCommandResult result = runGit(
                    base,
                    "worktree",
                    "add",
                    "-b",
                    branchName,
                    worktreePath,
                    "origin/" + baseBranch
            );
            if (result.returnCode() != 0) {
                throw new IllegalStateException("worktree add failed: " + result.output());
            }

            LOGGER.info("Created worktree: " + worktreePath + " (branch: " + branchName + ")");
            configureWorktree(worktreePath, base);
            return worktreePath;
        } finally {
            gitLock.unlock();
        }
    }

    /**
     * Create a detached read-only snapshot from origin/base.
     *
     * @param label snapshot directory label
     * @return worktree path
     * @throws IOException when filesystem or git IO fails
     * @throws InterruptedException when git execution is interrupted
     */
    public String prepareReadonlySnapshot(String label) throws IOException, InterruptedException {
        gitLock.lock();
        try {
            String base = ensureBaseRepo();
            long timestamp = Instant.now().getEpochSecond();
            Path worktreeRoot = Path.of(config.getWorktreesDir());
            Files.createDirectories(worktreeRoot);
            String worktreePath = worktreeRoot.resolve(timestamp + "-" + (isBlank(label) ? "assess" : label)).toString();
            String baseBranch = isBlank(config.getGitBaseBranch()) ? "develop" : config.getGitBaseBranch();
            GitCommandResult result = runGit(
                    base,
                    "worktree",
                    "add",
                    "--detach",
                    worktreePath,
                    "origin/" + baseBranch
            );
            if (result.returnCode() != 0) {
                throw new IllegalStateException("readonly worktree add failed: " + result.output());
            }
            LOGGER.info("Created readonly worktree: " + worktreePath);
            return worktreePath;
        } finally {
            gitLock.unlock();
        }
    }

    /**
     * Remove a managed worktree if it exists.
     *
     * @param worktreePath worktree path to remove
     * @throws IOException when filesystem or git IO fails
     * @throws InterruptedException when git execution is interrupted
     */
    public void cleanup(String worktreePath) throws IOException, InterruptedException {
        gitLock.lock();
        try {
            Path worktree = Path.of(worktreePath);
            if (!Files.exists(worktree)) {
                return;
            }
            GitCommandResult result = runGit(baseRepo(), "worktree", "remove", "--force", worktree.toString());
            if (result.returnCode() != 0) {
                LOGGER.warning("worktree remove failed (manual cleanup needed): " + result.output());
            } else {
                LOGGER.info("Cleaned up worktree: " + worktreePath);
            }
        } finally {
            gitLock.unlock();
        }
    }

    private String baseRepo() {
        if (!isBlank(config.getLocalRepo())) {
            return Path.of(config.getLocalRepo()).toAbsolutePath().normalize().toString();
        }
        return config.getCacheRepoDir();
    }

    private boolean isManagedWorktreePath(String worktreePath) {
        Path root = Path.of(config.getWorktreesDir()).toAbsolutePath().normalize();
        Path candidate = Path.of(worktreePath).toAbsolutePath().normalize();
        return candidate.startsWith(root);
    }

    private List<WorktreeEntry> listWorktrees(String base) throws IOException, InterruptedException {
        GitCommandResult result = runGit(base, "worktree", "list", "--porcelain");
        if (result.returnCode() != 0) {
            throw new IllegalStateException("worktree list failed: " + result.output());
        }

        List<WorktreeEntry> entries = new ArrayList<>();
        Map<String, String> current = new HashMap<>();
        for (String line : result.output().split("\\R", -1)) {
            if (line.isEmpty()) {
                appendWorktreeEntry(entries, current);
                current = new HashMap<>();
                continue;
            }
            if (line.startsWith("worktree ")) {
                appendWorktreeEntry(entries, current);
                current = new HashMap<>();
                current.put("path", line.substring("worktree ".length()));
                continue;
            }
            if (line.startsWith("branch ")) {
                current.put("branch", line.substring("branch ".length()));
            }
        }
        appendWorktreeEntry(entries, current);
        return entries;
    }

    private void dropExistingBranch(String base, String branchName) throws IOException, InterruptedException {
        GitCommandResult prune = runGit(base, "worktree", "prune");
        if (prune.returnCode() != 0) {
            throw new IllegalStateException("worktree prune failed: " + prune.output());
        }

        GitCommandResult showRef = runGit(
                base,
                "show-ref",
                "--verify",
                "--quiet",
                "refs/heads/" + branchName
        );
        if (showRef.returnCode() != 0) {
            return;
        }

        String branchRef = "refs/heads/" + branchName;
        for (WorktreeEntry entry : listWorktrees(base)) {
            if (!branchRef.equals(entry.branch()) || isBlank(entry.path())) {
                continue;
            }
            if (!isManagedWorktreePath(entry.path())) {
                throw new IllegalStateException(
                        "existing auto-harness branch is checked out in unmanaged worktree: " + entry.path()
                );
            }
            GitCommandResult remove = runGit(base, "worktree", "remove", "--force", entry.path());
            if (remove.returnCode() != 0) {
                throw new IllegalStateException(
                        "failed to remove existing managed worktree for branch " + branchName + ": " + remove.output()
                );
            }
            LOGGER.info("Removed stale worktree for branch " + branchName + ": " + entry.path());
        }

        GitCommandResult delete = runGit(base, "branch", "-D", branchName);
        if (delete.returnCode() != 0) {
            throw new IllegalStateException("failed to delete existing branch " + branchName + ": " + delete.output());
        }
        LOGGER.info("Deleted stale auto-harness branch: " + branchName);
    }

    private String ensureBaseRepo() throws IOException, InterruptedException {
        String base = baseRepo();
        Path basePath = Path.of(base);

        if (!isBlank(config.getLocalRepo())) {
            if (!Files.exists(basePath)) {
                throw new IllegalStateException("local_repo not found: " + base);
            }
            GitCommandResult fetch = runGit(base, "fetch", "origin");
            if (fetch.returnCode() != 0) {
                LOGGER.warning("fetch failed in local_repo (continuing): " + fetch.output());
            }
            return base;
        }

        Path gitDir = basePath.resolve(".git");
        if (Files.isDirectory(gitDir) || Files.isDirectory(basePath) && Files.isRegularFile(basePath.resolve("HEAD"))) {
            GitCommandResult fetch = runGit(base, "fetch", "origin");
            if (fetch.returnCode() != 0) {
                LOGGER.warning("fetch failed in cache repo (continuing): " + fetch.output());
            }
            return base;
        }

        Path parent = basePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String baseBranch = isBlank(config.getGitBaseBranch()) ? "develop" : config.getGitBaseBranch();
        GitCommandResult clone = runGit(
                parent == null ? "." : parent.toString(),
                "clone",
                "-b",
                baseBranch,
                config.getRepoUrl(),
                basePath.toString()
        );
        if (clone.returnCode() != 0) {
            throw new IllegalStateException("git clone failed: " + clone.output());
        }
        LOGGER.info("Cloned repo to " + base);
        return base;
    }

    private void configureWorktree(String worktreePath, String base) throws IOException, InterruptedException {
        if (!isBlank(config.getGitUserName())) {
            runGit(worktreePath, "config", "user.name", config.getGitUserName());
        }
        if (!isBlank(config.getGitUserEmail())) {
            runGit(worktreePath, "config", "user.email", config.getGitUserEmail());
        }
        if (isBlank(config.getGitRemote())) {
            return;
        }
        GitCommandResult remote = runGit(worktreePath, "remote", "get-url", config.getGitRemote());
        if (remote.returnCode() == 0) {
            return;
        }
        String forkUrl = "https://gitcode.com/" + config.getForkOwner() + "/" + config.getUpstreamRepo() + ".git";
        runGit(base, "remote", "add", config.getGitRemote(), forkUrl);
    }

    private GitCommandResult runGit(String cwd, String... args) throws IOException, InterruptedException {
        return executor.execute(List.of(args), cwd, gitEnv);
    }

    private static void appendWorktreeEntry(List<WorktreeEntry> entries, Map<String, String> current) {
        if (current.isEmpty()) {
            return;
        }
        entries.add(new WorktreeEntry(current.getOrDefault("path", ""), current.getOrDefault("branch", "")));
    }

    private static String stripHyphen(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Mirrors Python's actual git subprocess invocation in
     * {@code openjiuwen/auto_harness/infra/worktree_manager.py}.
     */
    private static final class ProcessGitCommandExecutor implements GitCommandExecutor {
        @Override
        public GitCommandResult execute(List<String> args, String cwd, Map<String, String> env)
                throws IOException, InterruptedException {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(args);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(new File(cwd));
            builder.environment().putAll(env);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder output = new StringBuilder();
                String line;
                boolean first = true;
                while ((line = reader.readLine()) != null) {
                    if (!first) {
                        output.append('\n');
                    }
                    output.append(line);
                    first = false;
                }
                int code = process.waitFor();
                return new GitCommandResult(code, output.toString().strip());
            }
        }
    }
}
