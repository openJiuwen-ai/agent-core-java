/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.worktree;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Public class WorktreeManager used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class WorktreeManager {
    private static final List<Pattern> EPHEMERAL_PATTERNS =
        List.of(Pattern.compile("^teammate-[0-9a-f]{8}$"), Pattern.compile("^agent-[0-9a-f]{7}$"));
    private final WorktreeConfig config;
    private final List<WorktreeRail> rails;
    private WorktreeSession currentSession;

    /**
     * WorktreeManager.
     * 
     * @param config config
     * @since 0.1.7
     */
    public WorktreeManager(WorktreeConfig config) {
        this(config, List.of());
    }

    /**
     * WorktreeManager.
     * 
     * @param config config
     * @param rails rails
     * @since 0.1.7
     */
    public WorktreeManager(WorktreeConfig config, List<WorktreeRail> rails) {
        this.config = config != null ? config : WorktreeConfig.builder().build();
        this.rails = rails != null ? List.copyOf(rails) : List.of();
    }

    /**
     * enter.
     * 
     * @param slug slug
     * @param repoRoot repoRoot
     * @param memberName memberName
     * @param teamName teamName
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public WorktreeSession enter(String slug, String repoRoot, String memberName, String teamName) throws IOException {
        Object railSlug = fireRail("beforeWorktreeCreate", new Object[]{slug, repoRoot});
        String effectiveSlug = slug;
        if (railSlug instanceof String replacement && !replacement.isBlank()) {
            effectiveSlug = replacement;
        }
        validateSlug(effectiveSlug);
        Path root = findCanonicalGitRoot(Path.of(repoRoot));
        if (root == null) {
            throw new IllegalStateException("Cannot create worktree: not in a git repository");
        }
        Path base = config.getBaseDir() != null && !config.getBaseDir().isBlank()
                ? Path.of(config.getBaseDir())
                : root.resolve(".agent_teams").resolve("worktrees");
        Files.createDirectories(base);
        Path target = base.resolve(effectiveSlug).toAbsolutePath().normalize();
        String branchName = "worktree-" + effectiveSlug;

        Instant start = Instant.now();
        String existingHead = readWorktreeHeadSha(target);
        boolean isExisted = existingHead != null;
        String originalBranch = getCurrentBranch(root);
        String baseRef = resolveBaseRef(root);
        String headCommit = isExisted ? existingHead : revParse(baseRef, root);
        if (!isExisted) {
            runGitOrThrow(root, List.of("worktree", "add", "-B", branchName, target.toString(), baseRef));
            if (headCommit == null || headCommit.isBlank()) {
                headCommit = revParse("HEAD", target);
            }
        }

        currentSession = WorktreeSession.builder().originalCwd(root.toString()).worktreePath(target.toString())
                .worktreeName(effectiveSlug).worktreeBranch(branchName).originalBranch(originalBranch)
                .originalHeadCommit(headCommit).memberName(memberName).teamName(teamName).isHookBased(isExisted)
                .lifecyclePolicy(config.getLifecyclePolicy())
                .creationDurationMs((double) Duration.between(start, Instant.now()).toMillis())
                .isUsedSparsePaths(config.getSparsePaths() != null && !config.getSparsePaths().isEmpty()).build();
        fireRail("afterWorktreeCreate", new Object[]{currentSession});
        return currentSession;
    }

    /**
     * fireRail.
     * 
     * @param method method
     * @param args args
     * @return the result
     * @since 0.1.7
     */
    public Object fireRail(String method, Object[] args) {
        return WorktreeRail.fire(rails, method, args);
    }

    /**
     * createResult.
     * 
     * @return the result
     * @since 0.1.7
     */
    public WorktreeCreateResult createResult() {
        if (currentSession == null) {
            return nullValue();
        }
        return WorktreeCreateResult.builder().worktreePath(currentSession.getWorktreePath())
                .worktreeBranch(currentSession.getWorktreeBranch()).headCommit(currentSession.getOriginalHeadCommit())
                .baseBranch(currentSession.getOriginalBranch()).build();
    }

    /**
     * createAgentWorktree.
     * 
     * @param slug slug
     * @param repoRoot repoRoot
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public WorktreeCreateResult createAgentWorktree(String slug, String repoRoot) throws IOException {
        validateSlug(slug);
        Path root = findCanonicalGitRoot(Path.of(repoRoot));
        if (root == null) {
            throw new IllegalStateException("Cannot create agent worktree: not in a git repository");
        }
        Path base = config.getBaseDir() != null && !config.getBaseDir().isBlank()
                ? Path.of(config.getBaseDir())
                : root.resolve(".agent_teams").resolve("worktrees");
        Files.createDirectories(base);
        Path target = base.resolve(slug).toAbsolutePath().normalize();
        String branchName = "worktree-" + slug;
        String existingHead = readWorktreeHeadSha(target);
        boolean isExisted = existingHead != null;
        String baseRef = resolveBaseRef(root);
        String headCommit;
        if (isExisted) {
            headCommit = existingHead;
        } else {
            headCommit = revParse(baseRef, root);
        }
        if (!isExisted) {
            runGitOrThrow(root, List.of("worktree", "add", "-B", branchName, target.toString(), baseRef));
            if (headCommit == null || headCommit.isBlank()) {
                headCommit = revParse("HEAD", target);
            }
        } else {
            Files.setLastModifiedTime(target, java.nio.file.attribute.FileTime.from(Instant.now()));
        }
        return WorktreeCreateResult.builder().worktreePath(target.toString()).worktreeBranch(branchName)
                .headCommit(headCommit).baseBranch(getCurrentBranch(root)).isExisted(isExisted).build();
    }

    /**
     * countChanges.
     * 
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public WorktreeChangeSummary countChanges() throws IOException {
        if (currentSession == null) {
            return WorktreeChangeSummary.builder().build();
        }
        Path worktreePath = Path.of(currentSession.getWorktreePath());
        int changedFiles = statusPorcelain(worktreePath).size();
        int commits = 0;
        if (currentSession.getOriginalHeadCommit() != null && !currentSession.getOriginalHeadCommit().isBlank()) {
            String output =
                runGit(worktreePath, List.of("rev-list", "--count", currentSession.getOriginalHeadCommit() + "..HEAD"))
                        .output();
            try {
                commits = Integer.parseInt(output.trim());
            } catch (NumberFormatException ignored) {
                commits = 0;
            }
        }
        return WorktreeChangeSummary.builder().changedFiles(changedFiles).commits(commits).build();
    }

    /**
     * exit.
     * 
     * @param action action
     * @param discardChanges discardChanges
     * @throws IOException IOException
     * @since 0.1.7
     */
    public void exit(String action, boolean discardChanges) throws IOException {
        if (currentSession == null) {
            return;
        }
        WorktreeSession session = currentSession;
        Object railAction = fireRail("beforeWorktreeExit", new Object[]{session, action});
        String effectiveAction = action;
        if (railAction instanceof String replacement && !replacement.isBlank()) {
            effectiveAction = replacement;
        }
        if ("remove".equals(effectiveAction)) {
            Path root = Path.of(session.getWorktreePath());
            if (!discardChanges) {
                WorktreeChangeSummary summary = countChanges();
                if (summary.getChangedFiles() > 0 || summary.getCommits() > 0) {
                    throw new IllegalStateException("Worktree has pending changes");
                }
            }
            Path repoRoot = findCanonicalGitRoot(Path.of(session.getOriginalCwd()));
            if (repoRoot != null) {
                runGit(repoRoot, List.of("worktree", "remove", "--force", session.getWorktreePath()));
                if (session.getWorktreeBranch() != null && session.getWorktreeBranch().startsWith("worktree-")) {
                    runGit(repoRoot, List.of("branch", "-D", session.getWorktreeBranch()));
                }
            } else if (Files.exists(root)) {
                deleteRecursively(root);
            } else {
                // no-op
            }
        }
        currentSession = null;
        fireRail("afterWorktreeExit", new Object[]{session, effectiveAction});
    }

    /**
     * getCurrentSession.
     * 
     * @return the result
     * @since 0.1.7
     */
    public WorktreeSession getCurrentSession() {
        return currentSession;
    }

    /**
     * removeWorktree.
     * 
     * @param worktreePath worktreePath
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public boolean removeWorktree(String worktreePath) throws IOException {
        if (worktreePath == null || worktreePath.isBlank()) {
            return false;
        }
        Path root = findCanonicalGitRoot(Path.of(worktreePath));
        if (root == null) {
            return false;
        }
        GitCommandResult result = runGit(root, List.of("worktree", "remove", "--force", worktreePath));
        return result.code() == 0 || !Files.exists(Path.of(worktreePath));
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public WorktreeConfig getConfig() {
        return config;
    }

    /**
     * cleanupStaleWorktrees.
     * 
     * @param repoRoot repoRoot
     * @param worktreesDir worktreesDir
     * @param currentWorktreePath currentWorktreePath
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public int cleanupStaleWorktrees(String repoRoot, String worktreesDir, String currentWorktreePath)
            throws IOException {
        Path root = findCanonicalGitRoot(Path.of(repoRoot));
        if (root == null || worktreesDir == null || worktreesDir.isBlank()) {
            return 0;
        }
        Path base = Path.of(worktreesDir);
        if (!Files.isDirectory(base)) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - Duration.ofDays(config.getCleanupAfterDays()).toMillis();
        int removed = 0;
        try (var entries = Files.list(base)) {
            for (Path entry : entries.toList()) {
                String slug = entry.getFileName().toString();
                if (!isEphemeralSlug(slug)) {
                    continue;
                }
                Path normalized = entry.toAbsolutePath().normalize();
                if (currentWorktreePath != null
                        && normalized.equals(Path.of(currentWorktreePath).toAbsolutePath().normalize())) {
                    continue;
                }
                if (Files.getLastModifiedTime(entry).toMillis() >= cutoff) {
                    continue;
                }
                if (!statusPorcelain(entry).isEmpty()) {
                    continue;
                }
                Boolean hasUnpushedCommits = hasUnpushedCommits(entry);
                if (hasUnpushedCommits == null || hasUnpushedCommits) {
                    continue;
                }
                GitCommandResult removedResult =
                    runGit(root, List.of("worktree", "remove", "--force", entry.toString()));
                if (removedResult.code() == 0) {
                    removed++;
                    String branch = "worktree-" + slug;
                    runGit(root, List.of("branch", "-D", branch));
                }
            }
        }
        if (removed > 0) {
            runGit(root, List.of("worktree", "prune"));
        }
        return removed;
    }

    /**
     * recoverWorktreeForMember.
     * 
     * @param memberName memberName
     * @param teamName teamName
     * @param repoRoot repoRoot
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public WorktreeSession recoverWorktreeForMember(String memberName, String teamName, String repoRoot)
            throws IOException {
        if (memberName == null || memberName.isBlank()) {
            return nullValue();
        }
        Path root = findCanonicalGitRoot(Path.of(repoRoot));
        if (root == null) {
            return nullValue();
        }
        String slug = memberSlug(memberName);
        Path base = config.getBaseDir() != null && !config.getBaseDir().isBlank()
                ? Path.of(config.getBaseDir())
                : root.resolve(".agent_teams").resolve("worktrees");
        Path worktreePath = base.resolve(slug).toAbsolutePath().normalize();
        String headSha = readWorktreeHeadSha(worktreePath);
        if (headSha == null || headSha.isBlank()) {
            return nullValue();
        }
        return WorktreeSession.builder().originalCwd(root.toString()).worktreePath(worktreePath.toString())
                .worktreeName(slug).worktreeBranch(getCurrentBranch(worktreePath)).originalHeadCommit(headSha)
                .memberName(memberName).teamName(teamName).lifecyclePolicy(resolvePolicy()).build();
    }

    /**
     * isEphemeralSlug.
     * 
     * @param slug slug
     * @return the result
     * @since 0.1.7
     */
    public static boolean isEphemeralSlug(String slug) {
        if (slug == null) {
            return false;
        }
        return EPHEMERAL_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(slug).matches());
    }

    /**
     * memberSlug.
     * 
     * @param memberName memberName
     * @return the result
     * @since 0.1.7
     */
    public static String memberSlug(String memberName) {
        if (memberName == null) {
            return "teammate-";
        }
        return "teammate-" + memberName.substring(0, Math.min(8, memberName.length()));
    }

    /**
     * resolvePolicy.
     * 
     * @return the result
     * @since 0.1.7
     */
    private WorktreeLifecyclePolicy resolvePolicy() {
        if (config.getLifecyclePolicy() != null && config.getLifecyclePolicy() != WorktreeLifecyclePolicy.AUTO) {
            return config.getLifecyclePolicy();
        }
        return WorktreeLifecyclePolicy.EPHEMERAL;
    }

    /**
     * validateSlug.
     * 
     * @param slug slug
     * @since 0.1.7
     */
    private static void validateSlug(String slug) {
        if (slug == null || slug.isBlank() || slug.contains("..") || slug.contains("/") || slug.contains("\\")) {
            throw new IllegalArgumentException("Invalid worktree slug: " + slug);
        }
    }

    /**
     * findCanonicalGitRoot.
     * 
     * @param cwd cwd
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    private static Path findCanonicalGitRoot(Path cwd) throws IOException {
        GitCommandResult gitDir = runGit(cwd, List.of("rev-parse", "--git-dir"));
        if (gitDir.code() != 0 || gitDir.output().isBlank()) {
            return cwd.toAbsolutePath().normalize();
        }
        Path gitDirPath = Path.of(gitDir.output());
        if (!gitDirPath.isAbsolute()) {
            gitDirPath = cwd.resolve(gitDirPath).normalize();
        }
        Path commondir = gitDirPath.resolve("commondir");
        if (Files.isRegularFile(commondir)) {
            String common = Files.readString(commondir, StandardCharsets.UTF_8).trim();
            Path commonPath = gitDirPath.resolve(common).normalize();
            return ".git".equals(commonPath.getFileName().toString()) ? commonPath.getParent() : commonPath;
        }
        GitCommandResult root = runGit(cwd, List.of("rev-parse", "--show-toplevel"));
        return root.code() == 0 && !root.output().isBlank()
                ? Path.of(root.output()).toAbsolutePath().normalize()
                : null;
    }

    /**
     * getCurrentBranch.
     * 
     * @param cwd cwd
     * @return the result
     * @since 0.1.7
     */
    private static String getCurrentBranch(Path cwd) {
        GitCommandResult result = runGit(cwd, List.of("rev-parse", "--abbrev-ref", "HEAD"));
        return result.code() == 0 && !"HEAD".equals(result.output()) ? result.output() : null;
    }

    /**
     * resolveBaseRef.
     * 
     * @param repoRoot repoRoot
     * @return the result
     * @since 0.1.7
     */
    private static String resolveBaseRef(Path repoRoot) {
        String branch = getCurrentBranch(repoRoot);
        return branch != null && !branch.isBlank() ? branch : "HEAD";
    }

    /**
     * revParse.
     * 
     * @param ref ref
     * @param cwd cwd
     * @return the result
     * @since 0.1.7
     */
    private static String revParse(String ref, Path cwd) {
        GitCommandResult result = runGit(cwd, List.of("rev-parse", ref));
        return result.code() == 0 ? result.output() : null;
    }

    /**
     * readWorktreeHeadSha.
     * 
     * @param worktreePath worktreePath
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    private static String readWorktreeHeadSha(Path worktreePath) throws IOException {
        Path gitFile = worktreePath.resolve(".git");
        if (!Files.isRegularFile(gitFile)) {
            return nullValue();
        }
        String content = Files.readString(gitFile, StandardCharsets.UTF_8).trim();
        if (!content.startsWith("gitdir:")) {
            return nullValue();
        }
        Path gitDir = worktreePath.resolve(content.substring("gitdir:".length()).trim()).normalize();
        Path headFile = gitDir.resolve("HEAD");
        if (!Files.isRegularFile(headFile)) {
            return nullValue();
        }
        String head = Files.readString(headFile, StandardCharsets.UTF_8).trim();
        if (!head.startsWith("ref:")) {
            return head.length() == 40 ? head : null;
        }
        String ref = head.substring("ref:".length()).trim();
        Path localRef = gitDir.resolve(ref);
        if (Files.isRegularFile(localRef)) {
            return Files.readString(localRef, StandardCharsets.UTF_8).trim();
        }
        Path commondir = gitDir.resolve("commondir");
        if (!Files.isRegularFile(commondir)) {
            return nullValue();
        }
        Path commonPath = gitDir.resolve(Files.readString(commondir, StandardCharsets.UTF_8).trim()).normalize();
        Path commonRef = commonPath.resolve(ref);
        return Files.isRegularFile(commonRef) ? Files.readString(commonRef, StandardCharsets.UTF_8).trim() : null;
    }

    /**
     * statusPorcelain.
     * 
     * @param cwd cwd
     * @return the result
     * @since 0.1.7
     */
    private static List<String> statusPorcelain(Path cwd) {
        GitCommandResult result = runGit(cwd, List.of("status", "--porcelain"));
        if (result.code() != 0 || result.output().isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : result.output().split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * hasUnpushedCommits.
     * 
     * @param cwd cwd
     * @return the result
     * @since 0.1.7
     */
    private static Boolean hasUnpushedCommits(Path cwd) {
        GitCommandResult result = runGit(cwd, List.of("rev-list", "--max-count=1", "HEAD", "--not", "--remotes"));
        if (result.code() != 0) {
            return nullValue();
        }
        return !result.output().isBlank();
    }

    /**
     * runGitOrThrow.
     * 
     * @param cwd cwd
     * @param args args
     * @since 0.1.7
     */
    private static void runGitOrThrow(Path cwd, List<String> args) {
        GitCommandResult result = runGit(cwd, args);
        if (result.code() != 0) {
            if (result.output() != null && result.output().toLowerCase(Locale.ROOT).contains("not a git repository")) {
                throw new IllegalStateException(
                        "git " + String.join(" ", args) + " failed: not in a git repository. " + result.output());
            }
            throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + result.output());
        }
    }

    /**
     * runGit.
     * 
     * @param cwd cwd
     * @param args args
     * @return the result
     * @since 0.1.7
     */
    private static GitCommandResult runGit(Path cwd, List<String> args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(cwd.toFile());
        builder.redirectErrorStream(true);
        Map<String, String> env = builder.environment();
        env.put("GIT_TERMINAL_PROMPT", "0");
        env.put("GIT_ASKPASS", "");
        try {
            Process process = builder.start();
            byte[] output = process.getInputStream().readAllBytes();
            int code = process.waitFor();
            return new GitCommandResult(code, new String(output, StandardCharsets.UTF_8).replaceAll("\\R+$", ""));
        } catch (IOException e) {
            return new GitCommandResult(1, e.getMessage() == null ? "" : e.getMessage());
        } catch (InterruptedException e) {
            // do not self-interrupt (G.CON.10)
            return new GitCommandResult(1, e.getMessage() == null ? "" : e.getMessage());
        }
    }

    /**
     * deleteRecursively.
     * 
     * @param root root
     * @throws IOException IOException
     * @since 0.1.7
     */
    private static void deleteRecursively(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    /**
     * GitCommandResult.
     * 
     * @param code code
     * @param output output
     * @since 0.1.7
     */
    private record GitCommandResult(int code, String output) {
    }

    /**
     * nullValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static <T> T nullValue() {
        return null;
    }
}
