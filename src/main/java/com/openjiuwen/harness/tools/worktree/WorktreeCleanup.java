/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import com.openjiuwen.core.sys_operation.Cwd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Stale worktree cleanup helpers.
 *
 * <p>Mirrors Python's {@code cleanup} in
 * {@code openjiuwen/harness/tools/worktree/cleanup.py}.</p>
 */
public final class WorktreeCleanup {

    private static final List<Pattern> EPHEMERAL_PATTERNS = List.of(
            Pattern.compile("^teammate-[0-9a-f]{8}$"),
            Pattern.compile("^agent-[0-9a-f]{7}$")
    );

    private WorktreeCleanup() {
    }

    public static boolean isEphemeralSlug(String slug) {
        if (slug == null) {
            return false;
        }
        for (Pattern pattern : EPHEMERAL_PATTERNS) {
            if (pattern.matcher(slug).matches()) {
                return true;
            }
        }
        return false;
    }

    public static CompletableFuture<Integer> cleanupStaleWorktrees(
            WorktreeConfig config,
            WorktreeBackend backend
    ) {
        return cleanupStaleWorktrees(config, backend, null);
    }

    public static CompletableFuture<Integer> cleanupStaleWorktrees(
            WorktreeConfig config,
            WorktreeBackend backend,
            String currentWorktreePath
    ) {
        return cleanupStaleWorktrees(config, backend, currentWorktreePath, new DefaultDependencies());
    }

    static CompletableFuture<Integer> cleanupStaleWorktrees(
            WorktreeConfig config,
            WorktreeBackend backend,
            String currentWorktreePath,
            Dependencies dependencies
    ) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(dependencies, "dependencies");

        return CompletableFuture.supplyAsync(() -> {
            String repoRoot = dependencies.findCanonicalGitRoot(dependencies.getCwd()).join();
            if (repoRoot == null || repoRoot.isBlank()) {
                return 0;
            }

            String workspace = dependencies.getWorkspace();
            if (workspace == null || workspace.isBlank()) {
                return 0;
            }

            Path worktreesDir = Path.of(SlugUtils.worktreesDir(workspace));
            List<String> entries;
            try {
                entries = dependencies.listEntries(worktreesDir);
            } catch (IOException ignored) {
                return 0;
            }

            Instant cutoff = dependencies.now().minus(Duration.ofDays(config.getCleanupAfterDays()));
            Path currentPath = currentWorktreePath == null ? null : Path.of(currentWorktreePath).normalize();
            int removed = 0;

            for (String slug : entries) {
                if (!isEphemeralSlug(slug)) {
                    continue;
                }

                Path worktreePath = worktreesDir.resolve(slug).normalize();
                if (currentPath != null && worktreePath.equals(currentPath)) {
                    continue;
                }

                Instant lastModified;
                try {
                    lastModified = dependencies.lastModified(worktreePath);
                } catch (IOException ignored) {
                    continue;
                }

                if (!lastModified.isBefore(cutoff)) {
                    continue;
                }

                List<String> changes = dependencies.statusPorcelain(worktreePath.toString()).join();
                Boolean hasUnpushedCommits = dependencies.hasUnpushedCommits(worktreePath.toString()).join();
                if (changes != null && !changes.isEmpty()) {
                    continue;
                }
                if (hasUnpushedCommits == null || hasUnpushedCommits) {
                    continue;
                }

                if (backend.remove(worktreePath.toString(), repoRoot).join()) {
                    removed += 1;
                }
            }

            if (removed > 0) {
                dependencies.worktreePrune(repoRoot).join();
            }
            return removed;
        });
    }

    interface Dependencies {
        String getCwd();

        String getWorkspace();

        CompletableFuture<String> findCanonicalGitRoot(String cwd);

        CompletableFuture<List<String>> statusPorcelain(String cwd);

        CompletableFuture<Boolean> hasUnpushedCommits(String cwd);

        CompletableFuture<Void> worktreePrune(String repoRoot);

        List<String> listEntries(Path dir) throws IOException;

        Instant lastModified(Path path) throws IOException;

        Instant now();
    }

    private static final class DefaultDependencies implements Dependencies {

        @Override
        public String getCwd() {
            return Cwd.getCwd();
        }

        @Override
        public String getWorkspace() {
            return Cwd.getWorkspace();
        }

        @Override
        public CompletableFuture<String> findCanonicalGitRoot(String cwd) {
            return Git.findCanonicalGitRoot(cwd);
        }

        @Override
        public CompletableFuture<List<String>> statusPorcelain(String cwd) {
            return Git.statusPorcelain(cwd);
        }

        @Override
        public CompletableFuture<Boolean> hasUnpushedCommits(String cwd) {
            return Git.hasUnpushedCommits(cwd);
        }

        @Override
        public CompletableFuture<Void> worktreePrune(String repoRoot) {
            return Git.worktreePrune(repoRoot);
        }

        @Override
        public List<String> listEntries(Path dir) throws IOException {
            try (var stream = Files.list(dir)) {
                List<String> entries = new ArrayList<>();
                stream.forEach(path -> entries.add(path.getFileName().toString()));
                return entries;
            }
        }

        @Override
        public Instant lastModified(Path path) throws IOException {
            return Files.getLastModifiedTime(path).toInstant();
        }

        @Override
        public Instant now() {
            return Instant.now();
        }
    }
}
