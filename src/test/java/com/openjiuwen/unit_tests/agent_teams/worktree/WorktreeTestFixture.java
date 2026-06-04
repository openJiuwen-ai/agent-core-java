/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.worktree;

import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.worktree.WorktreeConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.mockito.Mockito;

/**
 * Test fixtures for worktree tests.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.agent_teams.worktree.conftest}.
 * Provides common test fixtures and setup methods.
 */
public class WorktreeTestFixture {

    /**
     * Default worktree config for testing.
     */
    public static WorktreeConfig worktreeConfig() {
        WorktreeConfig config = new WorktreeConfig();
        config.setEnabled(true);
        return config;
    }

    /**
     * Mock Messager for testing.
     */
    public static Messager mockMessager() {
        return Mockito.mock(Messager.class);
    }

    /**
     * Create a temporary git repository for testing.
     *
     * @param tempDir The temporary directory to use
     * @return The repo root path
     * @throws IOException if creation fails
     * @throws InterruptedException if git command fails
     */
    public static Path tmpGitRepo(Path tempDir) throws IOException, InterruptedException {
        Path repo = tempDir.resolve("test_repo");
        Files.createDirectories(repo);

        // Initialize git repo
        runGitCommand(repo, "init");
        runGitCommand(repo, "config", "user.email", "test@test.com");
        runGitCommand(repo, "config", "user.name", "Test");

        // Create initial commit
        Path readme = repo.resolve("README.md");
        Files.writeString(readme, "# Test Repo");
        runGitCommand(repo, "add", ".");
        runGitCommand(repo, "commit", "-m", "Initial commit");

        return repo;
    }

    private static void runGitCommand(Path cwd, String... args) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("git");
        for (String arg : args) {
            pb.command().add(arg);
        }
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Git command failed: " + String.join(" ", args));
        }
    }

}
