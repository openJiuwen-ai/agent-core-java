/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Worktree manager for auto-harness.
 *
 * <p>Mirrors Python's {@code WorktreeManager} in
 * {@code openjiuwen.auto_harness.infra.worktree_manager}.</p>
 */
public class WorktreeManager {

    public interface GitCommandRunner {
        GitResult run(List<String> args, String cwd, Map<String, String> env)
                throws IOException, InterruptedException;
    }

    public record GitResult(int returnCode, String output) {}

    private static final Logger logger = Logger.getLogger(WorktreeManager.class.getName());

    private final AutoHarnessConfig config;
    private final Map<String, String> gitEnv;
    private final GitCommandRunner gitRunner;
    private final LongSupplier epochSeconds;

    public WorktreeManager(AutoHarnessConfig config) {
        this(config, null, null);
    }

    public WorktreeManager(AutoHarnessConfig config, GitCommandRunner gitRunner) {
        this(config, gitRunner, null);
    }

    public WorktreeManager(AutoHarnessConfig config, GitCommandRunner gitRunner, LongSupplier epochSeconds) {
        this.config = config;
        this.gitEnv = GitOperations.buildGitAuthEnv(
                config.resolveGitcodeUsername(),
                config.resolveGitcodeToken());
        this.gitRunner = gitRunner != null ? gitRunner : new ProcessGitCommandRunner();
        this.epochSeconds = epochSeconds != null ? epochSeconds : () -> System.currentTimeMillis() / 1000L;
    }

    private static String slugify(String topic) {
        String slug = topic == null ? "" : topic.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff]+", "-");
        slug = slug.replaceAll("^-+|-+$", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40);
        }
        return slug.isBlank() ? "task" : slug;
    }

    public String baseRepo() {
        if (config.getLocalRepo() != null && !config.getLocalRepo().isBlank()) {
            return Path.of(config.getLocalRepo()).toAbsolutePath().normalize().toString();
        }
        return config.getCacheRepoDir();
    }

    public String worktreeNameFor(String topic) {
        return slugify(topic);
    }

    public GitResult runGit(String cwd, String... args) throws IOException, InterruptedException {
        return runGit(List.of(args), cwd);
    }

    public String prepare(String topic) {
        String base = ensureBaseRepo();
        String slug = slugify(topic);
        String worktreeName = epochSeconds.getAsLong() + "-" + slug;
        String branchName = "auto-harness/" + slug;

        Path worktreeRoot = Path.of(config.getWorktreesDir());
        try {
            Files.createDirectories(worktreeRoot);
        } catch (IOException exception) {
            throw new RuntimeException("failed to create worktree root: " + exception.getMessage(), exception);
        }

        String worktreePath = worktreeRoot.resolve(worktreeName).toString();
        String baseBranch = blankDefault(config.getGitBaseBranch(), "develop");

        dropExistingBranch(base, branchName);

        GitResult add = runGitUnchecked(
                List.of("worktree", "add", "-b", branchName, worktreePath, "origin/" + baseBranch),
                base);
        if (add.returnCode() != 0) {
            throw new RuntimeException("worktree add failed: " + add.output());
        }

        logger.info(String.format("Created worktree: %s (branch: %s)", worktreePath, branchName));

        if (config.getGitUserName() != null && !config.getGitUserName().isBlank()) {
            runGitUnchecked(List.of("config", "user.name", config.getGitUserName()), worktreePath);
        }
        if (config.getGitUserEmail() != null && !config.getGitUserEmail().isBlank()) {
            runGitUnchecked(List.of("config", "user.email", config.getGitUserEmail()), worktreePath);
        }

        ensureForkRemote(base, worktreePath);
        return worktreePath;
    }

    public String prepareReadonlySnapshot(String label) {
        String base = ensureBaseRepo();
        String snapshotLabel = label == null || label.isBlank() ? "assess" : label;
        Path worktreeRoot = Path.of(config.getWorktreesDir());
        try {
            Files.createDirectories(worktreeRoot);
        } catch (IOException exception) {
            throw new RuntimeException("failed to create worktree root: " + exception.getMessage(), exception);
        }

        String worktreePath = worktreeRoot.resolve(epochSeconds.getAsLong() + "-" + snapshotLabel).toString();
        String baseBranch = blankDefault(config.getGitBaseBranch(), "develop");
        GitResult add = runGitUnchecked(
                List.of("worktree", "add", "--detach", worktreePath, "origin/" + baseBranch),
                base);
        if (add.returnCode() != 0) {
            throw new RuntimeException("readonly worktree add failed: " + add.output());
        }
        logger.info("Created readonly worktree: " + worktreePath);
        return worktreePath;
    }

    /**
     * Prepare a worktree synchronously for callers that tolerate deferred git setup.
     *
     * @param topic the task topic
     * @return the worktree path
     */
    public String prepareSync(String topic) {
        try {
            return prepare(topic);
        } catch (RuntimeException exception) {
            String worktreeName = worktreeNameFor(topic);
            return Path.of(config.getWorktreesDir(), worktreeName).toString();
        }
    }

    public void cleanup(String worktreePath) {
        Path worktree = Path.of(worktreePath);
        if (!Files.exists(worktree)) {
            return;
        }

        GitResult result = runGitUnchecked(
                List.of("worktree", "remove", "--force", worktree.toString()),
                baseRepo());
        if (result.returnCode() != 0) {
            logger.warning("worktree remove failed (manual cleanup needed): " + result.output());
        } else {
            logger.info("Cleaned up worktree: " + worktreePath);
        }
    }

    private boolean isManagedWorktreePath(String worktreePath) {
        Path root = Path.of(config.getWorktreesDir()).toAbsolutePath().normalize();
        Path candidate = Path.of(worktreePath).toAbsolutePath().normalize();
        return candidate.startsWith(root);
    }

    private List<Map<String, String>> listWorktrees(String base) {
        GitResult result = runGitUnchecked(List.of("worktree", "list", "--porcelain"), base);
        if (result.returnCode() != 0) {
            throw new RuntimeException("worktree list failed: " + result.output());
        }

        List<Map<String, String>> entries = new ArrayList<>();
        Map<String, String> current = new LinkedHashMap<>();
        for (String line : result.output().split("\\R")) {
            if (line.isEmpty()) {
                if (!current.isEmpty()) {
                    entries.add(current);
                    current = new LinkedHashMap<>();
                }
                continue;
            }
            if (line.startsWith("worktree ")) {
                if (!current.isEmpty()) {
                    entries.add(current);
                }
                current = new LinkedHashMap<>();
                current.put("path", line.substring("worktree ".length()));
                continue;
            }
            if (line.startsWith("branch ")) {
                current.put("branch", line.substring("branch ".length()));
            }
        }
        if (!current.isEmpty()) {
            entries.add(current);
        }
        return entries;
    }

    private void dropExistingBranch(String base, String branchName) {
        GitResult prune = runGitUnchecked(List.of("worktree", "prune"), base);
        if (prune.returnCode() != 0) {
            throw new RuntimeException("worktree prune failed: " + prune.output());
        }

        GitResult showRef = runGitUnchecked(
                List.of("show-ref", "--verify", "--quiet", "refs/heads/" + branchName),
                base);
        if (showRef.returnCode() != 0) {
            return;
        }

        String branchRef = "refs/heads/" + branchName;
        for (Map<String, String> entry : listWorktrees(base)) {
            if (!branchRef.equals(entry.get("branch"))) {
                continue;
            }
            String worktreePath = entry.getOrDefault("path", "");
            if (worktreePath.isBlank()) {
                continue;
            }
            if (!isManagedWorktreePath(worktreePath)) {
                throw new RuntimeException(
                        "existing auto-harness branch is checked out in unmanaged worktree: " + worktreePath);
            }
            GitResult remove = runGitUnchecked(
                    List.of("worktree", "remove", "--force", worktreePath),
                    base);
            if (remove.returnCode() != 0) {
                throw new RuntimeException(
                        "failed to remove existing managed worktree for branch "
                                + branchName + ": " + remove.output());
            }
            logger.info(String.format("Removed stale worktree for branch %s: %s", branchName, worktreePath));
        }

        GitResult delete = runGitUnchecked(List.of("branch", "-D", branchName), base);
        if (delete.returnCode() != 0) {
            throw new RuntimeException("failed to delete existing branch " + branchName + ": " + delete.output());
        }
        logger.info("Deleted stale auto-harness branch: " + branchName);
    }

    private String ensureBaseRepo() {
        String base = baseRepo();
        Path basePath = Path.of(base);

        if (config.getLocalRepo() != null && !config.getLocalRepo().isBlank()) {
            if (!Files.exists(basePath)) {
                throw new RuntimeException("local_repo not found: " + base);
            }
            GitResult fetch = runGitUnchecked(List.of("fetch", "origin"), base);
            if (fetch.returnCode() != 0) {
                logger.warning("fetch failed in local_repo (continuing): " + fetch.output());
            }
            return base;
        }

        Path gitDir = basePath.resolve(".git");
        if (Files.isDirectory(gitDir) || Files.isRegularFile(basePath.resolve("HEAD"))) {
            GitResult fetch = runGitUnchecked(List.of("fetch", "origin"), base);
            if (fetch.returnCode() != 0) {
                logger.warning("fetch failed in cache repo (continuing): " + fetch.output());
            }
            return base;
        }

        try {
            Path parent = basePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw new RuntimeException("failed to create cache repo parent: " + exception.getMessage(), exception);
        }

        String baseBranch = blankDefault(config.getGitBaseBranch(), "develop");
        GitResult clone = runGitUnchecked(
                List.of("clone", "-b", baseBranch, config.getRepoUrl(), basePath.toString()),
                basePath.getParent() != null ? basePath.getParent().toString() : ".");
        if (clone.returnCode() != 0) {
            throw new RuntimeException("git clone failed: " + clone.output());
        }
        logger.info("Cloned repo to " + base);
        return base;
    }

    private void ensureForkRemote(String base, String worktreePath) {
        if (config.getGitRemote() == null || config.getGitRemote().isBlank()) {
            return;
        }
        GitResult existing = runGitUnchecked(
                List.of("remote", "get-url", config.getGitRemote()),
                worktreePath);
        if (existing.returnCode() == 0) {
            return;
        }
        String forkUrl = "https://gitcode.com/" + config.getForkOwner() + "/" + config.getUpstreamRepo() + ".git";
        runGitUnchecked(
                List.of("remote", "add", config.getGitRemote(), forkUrl),
                base);
    }

    private GitResult runGit(List<String> args, String cwd) throws IOException, InterruptedException {
        return gitRunner.run(new ArrayList<>(args), cwd, gitEnv);
    }

    private GitResult runGitUnchecked(List<String> args, String cwd) {
        try {
            return runGit(args, cwd);
        } catch (IOException exception) {
            throw new RuntimeException("git command failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("git command interrupted", exception);
        }
    }

    private static String blankDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static final class ProcessGitCommandRunner implements GitCommandRunner {
        @Override
        public GitResult run(List<String> args, String cwd, Map<String, String> env)
                throws IOException, InterruptedException {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(args);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(cwd));
            pb.environment().putAll(env);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder out = new StringBuilder();
                String line;
                boolean first = true;
                while ((line = reader.readLine()) != null) {
                    if (!first) {
                        out.append('\n');
                    }
                    out.append(line);
                    first = false;
                }
                int code = proc.waitFor();
                return new GitResult(code, out.toString().strip());
            }
        }
    }
}
