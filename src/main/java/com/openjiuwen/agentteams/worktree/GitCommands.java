/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.worktree;

import com.openjiuwen.core.common.logging.Loggers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Pure subprocess wrapper for git operations. Zero business logic.
 * All methods are stateless and fail fast.
 *
 * <p>Mirrors Python worktree/git.py.</p>
 */
public final class GitCommands {

    private GitCommands() {
    }

    // ---- GitResult ----

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class GitResult {
        private final int returncode;
        private final String stdout;
        private final String stderr;

        GitResult(int returncode, String stdout, String stderr) {
            this.returncode = returncode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public int getReturncode() {
            return returncode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }

        public boolean isOk() {
            return returncode == 0;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class GitError extends RuntimeException {
        private final String command;
        private final int returncode;

        GitError(String command, int returncode, String stderr) {
            super("git " + command + " failed (rc=" + returncode + "): " + stderr);
            this.command = command;
            this.returncode = returncode;
        }

        public String getCommand() {
            return command;
        }

        public int getReturncode() {
            return returncode;
        }
    }

    // ---- Core execution ----

    private static GitResult runGit(List<String> args, Path cwd) {
        return runGit(args, cwd, false);
    }

    private static GitResult runGit(List<String> args, Path cwd, boolean check) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (cwd != null && Files.isDirectory(cwd)) {
                pb.directory(cwd.toFile());
            }
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            pb.environment().put("GIT_ASKPASS", "");
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StreamConsumer outputConsumer = new StreamConsumer(process.getInputStream());
            outputConsumer.start();

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new GitError(String.join(" ", args), -1, "Timed out after 30s");
            }

            outputConsumer.join();
            String stdout = outputConsumer.getOutput();
            String stderr = "";

            int rc = process.exitValue();
            GitResult result = new GitResult(rc, stdout, stderr);

            if (check && rc != 0) {
                // stderr has been merged into stdout above.
                throw new GitError(String.join(" ", args), rc, stdout);
            }
            return result;
        } catch (IOException | InterruptedException e) {
            throw new GitError(String.join(" ", args), -1, e.getMessage());
        }
    }

    /**
     * Background thread that consumes a process stream into a string,
     * preventing the external process from blocking on its output pipe.
     */
    private static final class StreamConsumer extends Thread {
        private final InputStream inputStream;
        private final StringBuilder output = new StringBuilder();

        StreamConsumer(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!output.isEmpty()) {
                        output.append('\n');
                    }
                    output.append(line);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String getOutput() {
            return output.toString();
        }
    }

    // ---- Query operations ----

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String findGitRoot(Path cwd) {
        return runGit(List.of("rev-parse", "--show-toplevel"), cwd, true).getStdout().trim();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String getCurrentBranch(Path cwd) {
        return runGit(List.of("rev-parse", "--abbrev-ref", "HEAD"), cwd, true).getStdout().trim();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String getDefaultBranch(Path cwd) {
        try {
            GitResult result = runGit(
                    List.of("symbolic-ref", "refs/remotes/origin/HEAD"), cwd);
            if (result.isOk()) {
                String ref = result.getStdout().trim();
                return ref.substring(ref.lastIndexOf('/') + 1);
            }
        } catch (Exception ignored) {
            // Fall through to probing
        }
        // Probe main then master
        for (String branch : List.of("main", "master")) {
            try {
                runGit(List.of("rev-parse", "--verify", branch), cwd, true);
                return branch;
            } catch (Exception ignored) {
                // continue
            }
        }
        return "main";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String revParse(String ref, Path cwd) {
        return runGit(List.of("rev-parse", ref), cwd, true).getStdout().trim();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String resolveGitDir(Path cwd) {
        return runGit(List.of("rev-parse", "--git-dir"), cwd, true).getStdout().trim();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String findCanonicalGitRoot(Path cwd) {
        String gitDir = resolveGitDir(cwd);
        Path gitDirPath = cwd.resolve(gitDir).normalize();
        if (Files.isRegularFile(gitDirPath)) {
            // This is a worktree — .git is a file pointing to the real gitdir
            try {
                String content = Files.readString(gitDirPath).trim();
                // Content: "gitdir: /path/to/main/.git/worktrees/name"
                if (content.startsWith("gitdir: ")) {
                    String realGitDir = content.substring(8).trim();
                    // realGitDir is .../main/.git/worktrees/name
                    // Navigate up to find the common dir
                    Path wtPath = Path.of(realGitDir);
                    Path commondirPath = wtPath.resolve("commondir");
                    if (Files.exists(commondirPath)) {
                        String commondir = Files.readString(commondirPath).trim();
                        return Path.of(realGitDir).resolve(commondir).normalize().toString();
                    }
                    // Fallback: gitdir is .../main/.git, common dir is .../main/.git
                    Path dotGit = wtPath.getParent(); // up from worktrees/
                    if (dotGit != null && dotGit.getFileName().toString().equals("worktrees")) {
                        return dotGit.getParent().toString();
                    }
                }
            } catch (IOException e) {
                Loggers.AGENT.debug("Failed to read worktree gitdir: {}", e.getMessage());
            }
        }
        // Regular repo or cannot resolve — use direct git root
        return findGitRoot(cwd);
    }

    // ---- Worktree operations ----

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void worktreeAdd(
            Path repoRoot, Path worktreePath, String branchName, String baseRef) {
        List<String> args = new ArrayList<>();
        args.add("worktree");
        args.add("add");
        if (baseRef != null && !baseRef.isBlank()) {
            args.add("-b");
            args.add(branchName);
            args.add(worktreePath.toString());
            args.add(baseRef);
        } else {
            args.add(worktreePath.toString());
        }
        runGit(args, repoRoot, true);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void worktreeRemove(Path worktreePath, Path repoRoot, boolean force) {
        List<String> args = new ArrayList<>();
        args.add("worktree");
        args.add("remove");
        if (force) {
            args.add("--force");
        }
        args.add(worktreePath.toString());
        runGit(args, repoRoot, true);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void worktreePrune(Path repoRoot) {
        runGit(List.of("worktree", "prune"), repoRoot, true);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void branchDelete(String branch, Path repoRoot) {
        runGit(List.of("branch", "-D", branch), repoRoot, true);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void fetchRef(Path repoRoot, String ref, String remote) {
        String r = remote != null && !remote.isBlank() ? remote : "origin";
        runGit(List.of("fetch", r, ref), repoRoot, true);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void sparseCheckoutSet(Path worktreePath, List<String> paths) {
        runGit(List.of("sparse-checkout", "set", "--cone"), worktreePath, true);
        if (paths != null && !paths.isEmpty()) {
            List<String> checkoutArgs = new ArrayList<>();
            checkoutArgs.add("checkout");
            checkoutArgs.add("HEAD");
            checkoutArgs.add("--");
            checkoutArgs.addAll(paths);
            runGit(checkoutArgs, worktreePath, true);
        }
    }

    // ---- Status queries ----

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String statusPorcelain(Path cwd) {
        return runGit(List.of("status", "--porcelain"), cwd).getStdout();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static int countCommitsSince(String baseCommit, Path cwd) {
        String result = runGit(
                List.of("rev-list", "--count", baseCommit + "..HEAD"), cwd, true)
                .getStdout().trim();
        return result.isEmpty() ? 0 : Integer.parseInt(result);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static boolean hasUnpushedCommits(Path cwd) {
        try {
            GitResult result = runGit(
                    List.of("rev-list", "--max-count=1", "HEAD", "--not", "--remotes"),
                    cwd);
            return result.isOk() && !result.getStdout().trim().isEmpty();
        } catch (Exception e) {
            Loggers.AGENT.debug("Failed to check unpushed commits: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String readWorktreeHeadSha(Path worktreePath) {
        Path gitFile = worktreePath.resolve(".git");
        if (Files.isRegularFile(gitFile)) {
            try {
                String content = Files.readString(gitFile).trim();
                if (content.startsWith("gitdir: ")) {
                    String realGitDir = content.substring(8).trim();
                    Path headFile = Path.of(realGitDir).resolve("HEAD");
                    if (Files.exists(headFile)) {
                        String headContent = Files.readString(headFile).trim();
                        if (headContent.startsWith("ref: ")) {
                            String ref = headContent.substring(5).trim();
                            Path refFile = Path.of(realGitDir).resolve(ref);
                            if (Files.exists(refFile)) {
                                return Files.readString(refFile).trim();
                            }
                        }
                        return headContent; // Detached HEAD (raw SHA)
                    }
                }
            } catch (IOException e) {
                Loggers.AGENT.debug("Failed to read worktree HEAD: {}", e.getMessage());
            }
        }
        return "";
    }
}
