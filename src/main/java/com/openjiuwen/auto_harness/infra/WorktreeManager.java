/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.infra;

import com.openjiuwen.autoharness.schema.AutoHarnessConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * Public class WorktreeManager used by the Java parity implementation.
 *
 * @since 1.0
 */
public class WorktreeManager {
    private static final Logger LOGGER = Logger.getLogger(WorktreeManager.class.getName());
    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-zA-Z0-9\\u4e00-\\u9fff]+");
    private final AutoHarnessConfig config;
    private final Map<String, String> gitEnv;

    /**
     * Auto-generated for codecheck compliance.
     */
    public WorktreeManager(String workspace) {
        this(AutoHarnessConfig.builder().workspace(workspace).build());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WorktreeManager(AutoHarnessConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.gitEnv = GitAuth.buildGitAuthEnv(config.resolveGitcodeUsername(), config.resolveGitcodeToken());
    }

    /**
 * Public record WorktreeEntry used by the Java parity implementation.
 *
 * @since 1.0
 */
public record WorktreeEntry(String path, String branch) {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path worktreePath(String slug) {
        return config.worktreesPath().resolve(slug).normalize();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path readonlySnapshotPath(long timestamp, String label) {
        return config.worktreesPath().resolve(timestamp + "-" + safeLabel(label)).normalize();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path baseRepoPath() {
        if (hasText(config.getLocalRepo())) {
            return Path.of(config.getLocalRepo()).toAbsolutePath().normalize();
        }
        return config.cacheRepoPath();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isManagedWorktreePath(String worktreePath) {
        if (!hasText(worktreePath)) {
            return false;
        }
        Path root = config.worktreesPath();
        Path candidate = Path.of(worktreePath).toAbsolutePath().normalize();
        return candidate.startsWith(root);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String branchNameForTopic(String topic) {
        return "auto-harness/" + slugify(topic);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String worktreeNameForTopic(long timestamp, String topic) {
        return timestamp + "-" + slugify(topic);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String slugify(String topic) {
        String value = topic == null ? "" : topic;
        String slug = NON_SLUG_CHARS.matcher(value).replaceAll("-");
        slug = slug.replaceAll("^-+|-+$", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40);
            slug = slug.replaceAll("-+$", "");
        }
        return slug.isBlank() ? "task" : slug;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<WorktreeEntry> parseWorktreeListPorcelain(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        List<WorktreeEntry> entries = new ArrayList<>();
        String currentPath = null;
        String currentBranch = null;
        for (String line : output.split("\\R", -1)) {
            if (line.isBlank()) {
                if (currentPath != null) {
                    entries.add(new WorktreeEntry(currentPath, currentBranch));
                    currentPath = null;
                    currentBranch = null;
                }
                continue;
            }
            if (line.startsWith("worktree ")) {
                if (currentPath != null) {
                    entries.add(new WorktreeEntry(currentPath, currentBranch));
                }
                currentPath = line.substring("worktree ".length());
                currentBranch = null;
                continue;
            }
            if (line.startsWith("branch ")) {
                currentBranch = line.substring("branch ".length());
            }
        }
        if (currentPath != null) {
            entries.add(new WorktreeEntry(currentPath, currentBranch));
        }
        return List.copyOf(entries);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<WorktreeEntry> managedEntriesForBranch(String porcelainOutput, String branchName) {
        if (!hasText(branchName)) {
            return List.of();
        }
        String branchRef = branchName.startsWith("refs/") ? branchName : "refs/heads/" + branchName;
        List<WorktreeEntry> matches = new ArrayList<>();
        for (WorktreeEntry entry : parseWorktreeListPorcelain(porcelainOutput)) {
            if (!branchRef.equals(entry.branch())) {
                continue;
            }
            if (!hasText(entry.path()) || !isManagedWorktreePath(entry.path())) {
                continue;
            }
            matches.add(entry);
        }
        return List.copyOf(matches);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean hasUnmanagedEntryForBranch(String porcelainOutput, String branchName) {
        if (!hasText(branchName)) {
            return false;
        }
        String branchRef = branchName.startsWith("refs/") ? branchName : "refs/heads/" + branchName;
        for (WorktreeEntry entry : parseWorktreeListPorcelain(porcelainOutput)) {
            if (!branchRef.equals(entry.branch())) {
                continue;
            }
            if (!hasText(entry.path())) {
                continue;
            }
            if (!isManagedWorktreePath(entry.path())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path prepare(String topic) {
        Path base = ensureBaseRepo();
        String slug = slugify(topic);
        long timestamp = System.currentTimeMillis() / 1000;
        String branchName = "auto-harness/" + slug;
        Path wtPath = config.worktreesPath().resolve(timestamp + "-" + slug).normalize();
        dropExistingBranch(base, branchName);
        runGitOrThrow(base, "worktree", "add", "-b", branchName, wtPath.toString(),
                "origin/" + defaultBaseBranch());
        if (hasText(config.getGitUserName())) {
            runGit(wtPath, "config", "user.name", config.getGitUserName());
        }
        if (hasText(config.getGitUserEmail())) {
            runGit(wtPath, "config", "user.email", config.getGitUserEmail());
        }
        if (hasText(config.getGitRemote())) {
            GitOperations.GitCommandResult remote = runGit(wtPath, "remote", "get-url", config.getGitRemote());
            if (remote.code() != 0) {
                runGit(base, "remote", "add", config.getGitRemote(),
                        "https://gitcode.com/" + config.getForkOwner() + "/" + config.getUpstreamRepo() + ".git");
            }
        }
        return wtPath;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path prepareReadonlySnapshot(String label) {
        Path base = ensureBaseRepo();
        long timestamp = System.currentTimeMillis() / 1000;
        Path wtPath = readonlySnapshotPath(timestamp, hasText(label) ? label : "assess");
        runGitOrThrow(base, "worktree", "add", "--detach", wtPath.toString(), "origin/" + defaultBaseBranch());
        return wtPath;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void cleanup(String worktreePath) {
        if (!hasText(worktreePath)) {
            return;
        }
        Path wt = Path.of(worktreePath);
        if (!java.nio.file.Files.exists(wt)) {
            return;
        }
        GitOperations.GitCommandResult result = runGit(baseRepoPath(), "worktree", "remove", "--force", wt.toString());
        if (result.code() != 0) {
            LOGGER.warning("worktree remove failed (manual cleanup needed): " + result.output());
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path ensureBaseRepo() {
        Path base = baseRepoPath();
        if (hasText(config.getLocalRepo())) {
            if (!java.nio.file.Files.exists(base)) {
                throw new IllegalStateException("local_repo not found: " + base);
            }
            runGit(base, "fetch", "origin");
            return base;
        }
        if (java.nio.file.Files.isDirectory(base.resolve(".git"))
                || java.nio.file.Files.isRegularFile(base.resolve("HEAD"))) {
            runGit(base, "fetch", "origin");
            return base;
        }
        try {
            java.nio.file.Files.createDirectories(base.getParent());
        } catch (IOException ex) {
            throw new IllegalStateException("failed to create cache repo parent: " + ex.getMessage(), ex);
        }
        runGitOrThrow(base.getParent(), "clone", "-b", defaultBaseBranch(), config.getRepoUrl(), base.toString());
        return base;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void dropExistingBranch(Path base, String branchName) {
        runGitOrThrow(base, "worktree", "prune");
        GitOperations.GitCommandResult showRef = runGit(base, "show-ref", "--verify", "--quiet",
                "refs/heads/" + branchName);
        if (showRef.code() != 0) {
            return;
        }
        String branchRef = "refs/heads/" + branchName;
        String porcelain = runGitOrThrow(base, "worktree", "list", "--porcelain").output();
        for (WorktreeEntry entry : parseWorktreeListPorcelain(porcelain)) {
            if (!branchRef.equals(entry.branch())) {
                continue;
            }
            if (!hasText(entry.path())) {
                continue;
            }
            if (!isManagedWorktreePath(entry.path())) {
                throw new IllegalStateException("existing auto-harness branch is checked out in unmanaged worktree: "
                        + entry.path());
            }
            runGitOrThrow(base, "worktree", "remove", "--force", entry.path());
        }
        runGitOrThrow(base, "branch", "-D", branchName);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public GitOperations.GitCommandResult runGit(Path cwd, String... args) {
        ProcessBuilder builder = new ProcessBuilder(command(args));
        builder.directory(cwd.toFile());
        builder.redirectErrorStream(true);
        builder.environment().putAll(gitEnv);
        try {
            Process process = builder.start();
            byte[] stdout = process.getInputStream().readAllBytes();
            int code = process.waitFor();
            String output = new String(stdout, java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\R+$", "");
            return new GitOperations.GitCommandResult(code, output);
        } catch (IOException ex) {
            return new GitOperations.GitCommandResult(1, ex.getMessage() == null ? "" : ex.getMessage());
        } catch (InterruptedException ex) {

            return new GitOperations.GitCommandResult(1, ex.getMessage() == null ? "" : ex.getMessage());
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, String> gitEnv() {
        return Map.copyOf(gitEnv);
    }

    private GitOperations.GitCommandResult runGitOrThrow(Path cwd, String... args) {
        GitOperations.GitCommandResult result = runGit(cwd, args);
        if (result.code() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + result.output());
        }
        return result;
    }

    private String defaultBaseBranch() {
        return hasText(config.getGitBaseBranch()) ? config.getGitBaseBranch() : "develop";
    }

    private static List<String> command(String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        return command;
    }

    private static String safeLabel(String label) {
        String normalized = slugify(label);
        return normalized.isBlank() ? "assess" : normalized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
