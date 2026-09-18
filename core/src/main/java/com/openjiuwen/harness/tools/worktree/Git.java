/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Git CLI wrapper for worktree operations.
 *
 * <p>Mirrors Python's {@code GitError}, {@code GitResult}, and git helpers in
 * {@code openjiuwen/harness/tools/worktree/git.py}.
 */
public final class Git {

    private Git() {
    }

    /**
     * Git command execution failed.
     *
     * <p>Mirrors Python's {@code GitError} in
     * {@code openjiuwen/harness/tools/worktree/git.py}.
     */
    public static final class GitError extends RuntimeException {
        private final List<String> command;
        private final int returncode;
        private final String stderr;

        public GitError(List<String> command, int returncode, String stderr) {
            super("git " + (command.isEmpty() ? "" : command.get(0)) + " failed (rc=" + returncode + "): " + stderr);
            this.command = List.copyOf(command);
            this.returncode = returncode;
            this.stderr = stderr;
        }

        public List<String> getCommand() {
            return command;
        }

        public int getReturncode() {
            return returncode;
        }

        public String getStderr() {
            return stderr;
        }
    }

    /**
     * Result of a git command execution.
     *
     * <p>Mirrors Python's {@code GitResult} in
     * {@code openjiuwen/harness/tools/worktree/git.py}.
     */
    public record GitResult(int returncode, String stdout, String stderr) {
        public boolean ok() {
            return returncode == 0;
        }

        public boolean isOk() {
            return ok();
        }
    }

    public static Map<String, String> gitEnv() {
        Map<String, String> env = new HashMap<>(System.getenv());
        if (!env.containsKey("PATH") && env.containsKey("Path")) {
            env.put("PATH", env.get("Path"));
        }
        env.put("GIT_TERMINAL_PROMPT", "0");
        env.put("GIT_ASKPASS", "");
        return env;
    }

    public static CompletableFuture<GitResult> runGit(List<String> args, String cwd) {
        return runGit(args, cwd, false);
    }

    public static CompletableFuture<GitResult> runGit(List<String> args, String cwd, boolean check) {
        return CompletableFuture.supplyAsync(() -> runGitSync(args, cwd, check));
    }

    public static CompletableFuture<String> findGitRoot(String cwd) {
        return CompletableFuture.supplyAsync(() -> {
            GitResult result = runGitSync(List.of("rev-parse", "--show-toplevel"), cwd, false);
            return result.ok() ? result.stdout() : null;
        });
    }

    public static CompletableFuture<String> getCurrentBranch(String cwd) {
        return CompletableFuture.supplyAsync(() -> {
            GitResult result = runGitSync(List.of("rev-parse", "--abbrev-ref", "HEAD"), cwd, false);
            if (!result.ok() || "HEAD".equals(result.stdout())) {
                return null;
            }
            return result.stdout();
        });
    }

    public static CompletableFuture<String> getDefaultBranch(String cwd) {
        return CompletableFuture.supplyAsync(() -> {
            GitResult result = runGitSync(List.of("symbolic-ref", "refs/remotes/origin/HEAD", "--short"), cwd, false);
            if (result.ok()) {
                String stdout = result.stdout();
                int slash = stdout.indexOf('/');
                return slash >= 0 ? stdout.substring(slash + 1) : stdout;
            }
            for (String name : List.of("main", "master")) {
                GitResult check = runGitSync(List.of("rev-parse", "--verify", "origin/" + name), cwd, false);
                if (check.ok()) {
                    return name;
                }
            }
            return "main";
        });
    }

    public static CompletableFuture<String> revParse(String ref, String cwd) {
        return CompletableFuture.supplyAsync(() -> {
            GitResult result = runGitSync(List.of("rev-parse", ref), cwd, false);
            return result.ok() ? result.stdout() : null;
        });
    }

    public static CompletableFuture<String> resolveGitDir(String cwd) {
        return CompletableFuture.supplyAsync(() -> {
            GitResult result = runGitSync(List.of("rev-parse", "--git-dir"), cwd, false);
            if (!result.ok()) {
                return null;
            }
            Path gitDir = Path.of(result.stdout());
            if (!gitDir.isAbsolute()) {
                gitDir = Path.of(cwd).resolve(gitDir);
            }
            return gitDir.normalize().toString();
        });
    }

    public static CompletableFuture<String> findCanonicalGitRoot(String cwd) {
        return CompletableFuture.supplyAsync(() -> {
            String gitDir = resolveGitDir(cwd).join();
            if (gitDir == null) {
                return null;
            }
            Path commonDirPath = Path.of(gitDir).resolve("commondir");
            if (Files.isRegularFile(commonDirPath)) {
                try {
                    String common = Files.readString(commonDirPath).strip();
                    Path commonAbs = Path.of(gitDir).resolve(common).normalize();
                    if (".git".equals(commonAbs.getFileName().toString())) {
                        Path parent = commonAbs.getParent();
                        return parent == null ? null : parent.toString();
                    }
                    return commonAbs.toString();
                } catch (IOException e) {
                    return null;
                }
            }
            return findGitRoot(cwd).join();
        });
    }

    public static CompletableFuture<Void> worktreeAdd(
            String repoRoot,
            String worktreePath,
            String branchName,
            String baseRef,
            boolean noCheckout
    ) {
        return CompletableFuture.runAsync(() -> {
            List<String> args = new ArrayList<>();
            args.add("worktree");
            args.add("add");
            if (noCheckout) {
                args.add("--no-checkout");
            }
            args.addAll(List.of("-B", branchName, worktreePath, baseRef));
            runGitSync(args, repoRoot, true);
        });
    }

    public static CompletableFuture<Boolean> worktreeRemove(String worktreePath, String repoRoot, boolean force) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> args = new ArrayList<>(List.of("worktree", "remove"));
            if (force) {
                args.add("--force");
            }
            args.add(worktreePath);
            return runGitSync(args, repoRoot, false).ok();
        });
    }

    public static CompletableFuture<Void> worktreePrune(String repoRoot) {
        return CompletableFuture.runAsync(() -> runGitSync(List.of("worktree", "prune"), repoRoot, false));
    }

    public static CompletableFuture<Boolean> branchDelete(String branch, String repoRoot) {
        return CompletableFuture.supplyAsync(() -> runGitSync(List.of("branch", "-D", branch), repoRoot, false).ok());
    }

    public static CompletableFuture<Boolean> fetchRef(String repoRoot, String ref) {
        return fetchRef(repoRoot, ref, "origin");
    }

    public static CompletableFuture<Boolean> fetchRef(String repoRoot, String ref, String remote) {
        return CompletableFuture.supplyAsync(() -> runGitSync(List.of("fetch", remote, ref), repoRoot, false).ok());
    }

    public static CompletableFuture<Void> sparseCheckoutSet(String worktreePath, List<String> paths) {
        return CompletableFuture.runAsync(() -> {
            List<String> args = new ArrayList<>(List.of("sparse-checkout", "set", "--cone", "--"));
            args.addAll(paths);
            runGitSync(args, worktreePath, true);
            runGitSync(List.of("checkout", "HEAD"), worktreePath, true);
        });
    }

    public static CompletableFuture<List<String>> statusPorcelain(String cwd) {
        return CompletableFuture.supplyAsync(() -> {
            GitResult result = runGitSync(List.of("status", "--porcelain"), cwd, false);
            if (!result.ok()) {
                return List.of();
            }
            return result.stdout().lines().filter(line -> !line.strip().isEmpty()).toList();
        });
    }

    public static CompletableFuture<Integer> countCommitsSince(String baseCommit, String cwd) {
        return CompletableFuture.supplyAsync(() -> {
            GitResult result = runGitSync(List.of("rev-list", "--count", baseCommit + "..HEAD"), cwd, false);
            if (!result.ok()) {
                return null;
            }
            try {
                return Integer.parseInt(result.stdout());
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }

    public static CompletableFuture<Boolean> hasUnpushedCommits(String cwd) {
        return CompletableFuture.supplyAsync(() -> {
            GitResult result = runGitSync(List.of("rev-list", "--max-count=1", "HEAD", "--not", "--remotes"), cwd, false);
            if (!result.ok()) {
                return null;
            }
            return !result.stdout().isEmpty();
        });
    }

    public static CompletableFuture<String> readWorktreeHeadSha(String worktreePath) {
        return CompletableFuture.supplyAsync(() -> {
            Path gitFile = Path.of(worktreePath).resolve(".git");
            String content;
            try {
                content = Files.readString(gitFile).strip();
            } catch (IOException e) {
                return null;
            }
            if (!content.startsWith("gitdir:")) {
                return null;
            }

            Path gitDir = Path.of(worktreePath).resolve(content.substring("gitdir:".length()).strip()).normalize();
            Path headFile = gitDir.resolve("HEAD");
            String head;
            try {
                head = Files.readString(headFile).strip();
            } catch (IOException e) {
                return null;
            }

            if (!head.startsWith("ref:")) {
                return head.length() == 40 ? head : null;
            }

            String refPath = head.substring("ref:".length()).strip();
            Path localRef = gitDir.resolve(refPath);
            try {
                return Files.readString(localRef).strip();
            } catch (IOException ignored) {
            }

            Path commonDirFile = gitDir.resolve("commondir");
            try {
                String common = Files.readString(commonDirFile).strip();
                Path commonAbs = gitDir.resolve(common).normalize();
                return Files.readString(commonAbs.resolve(refPath)).strip();
            } catch (IOException e) {
                return null;
            }
        });
    }

    private static GitResult runGitSync(List<String> args, String cwd, boolean check) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        if (cwd != null) {
            builder.directory(Path.of(cwd).toFile());
        }
        Map<String, String> env = builder.environment();
        env.put("GIT_TERMINAL_PROMPT", "0");
        env.put("GIT_ASKPASS", "");

        try {
            Process process = builder.start();
            process.getOutputStream().close();
            CompletableFuture<String> stdout = readAll(process.getInputStream());
            CompletableFuture<String> stderr = readAll(process.getErrorStream());
            int exitCode = process.waitFor();
            GitResult result = new GitResult(exitCode, stdout.join().strip(), stderr.join().strip());
            if (check && !result.ok()) {
                throw new GitError(args, result.returncode(), result.stderr());
            }
            return result;
        } catch (IOException e) {
            if (check) {
                throw new GitError(args, 127, e.getMessage());
            }
            return new GitResult(127, "", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (check) {
                throw new GitError(args, 130, "Interrupted");
            }
            return new GitResult(130, "", "Interrupted");
        }
    }

    private static CompletableFuture<String> readAll(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
    }
}
