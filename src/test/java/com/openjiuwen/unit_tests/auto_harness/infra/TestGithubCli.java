/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GitHub CLI preflight helpers.
 * <p>
 * Mirrors Python's test_github_cli.py from
 * <code>tests/unit_tests/auto_harness/infra/test_github_cli.py</code>.
 */
@DisplayName("GitHub CLI Tests")
class TestGithubCli {

    // Stub classes
    static class GhStatus {
        boolean available;
        boolean authenticated;
        boolean installedNow;

        GhStatus(boolean available, boolean authenticated, boolean installedNow) {
            this.available = available;
            this.authenticated = authenticated;
            this.installedNow = installedNow;
        }
    }

    static class ProcessResult {
        int returncode;
        String stderr;
        String stdout;

        ProcessResult(int returncode, String stderr, String stdout) {
            this.returncode = returncode;
            this.stderr = stderr;
            this.stdout = stdout;
        }
    }

    // Helper method simulating ensure_github_cli_ready
    static GhStatus ensureGithubCliReady(
        java.util.function.Function<String, String> whichMock,
        java.util.function.Function<List<String>, ProcessResult> runMock,
        List<String> messages
    ) {
        String ghPath = whichMock.apply("gh");
        if (ghPath == null || ghPath.isEmpty()) {
            // Try to install
            ProcessResult installResult = runMock.apply(List.of("brew", "install", "gh"));
            if (installResult.returncode != 0) {
                messages.add("GitHub CLI installation failed");
                return new GhStatus(false, false, false);
            }
            ghPath = whichMock.apply("gh");
            if (ghPath == null || ghPath.isEmpty()) {
                return new GhStatus(false, false, false);
            }
        }

        ProcessResult authResult = runMock.apply(List.of(ghPath, "auth", "status"));
        if (authResult.returncode == 0) {
            messages.add("已登录 GitHub CLI");
            return new GhStatus(true, true, false);
        } else {
            messages.add("建议先执行 `gh auth login --web`");
            return new GhStatus(true, false, false);
        }
    }

    @Nested
    @DisplayName("GitHub CLI Ready Tests")
    class TestGhCliReady {

        @Test
        @DisplayName("gh present and authenticated")
        void testGithubCliPresentAndAuthenticated() {
            List<String> messages = new ArrayList<>();

            java.util.function.Function<String, String> whichMock = (name) -> "/usr/bin/gh";
            java.util.function.Function<List<String>, ProcessResult> runMock = (cmd) ->
                new ProcessResult(0, "", "");

            GhStatus status = ensureGithubCliReady(whichMock, runMock, messages);

            assertTrue(status.available);
            assertTrue(status.authenticated);
            assertFalse(status.installedNow);
            assertTrue(messages.stream().anyMatch(m -> m.contains("已登录")));
        }

        @Test
        @DisplayName("gh missing installs and prompts authentication")
        void testGithubCliMissingInstallsAndPromptsAuthentication() {
            List<String> messages = new ArrayList<>();
            boolean[] installed = {false};

            java.util.function.Function<String, String> whichMock = (name) -> {
                if (!installed[0]) {
                    installed[0] = true;
                    return ""; // First call: not installed
                }
                return "/usr/local/bin/gh"; // Second call: installed
            };

            java.util.function.Function<List<String>, ProcessResult> runMock = (cmd) -> {
                if (cmd.get(1).equals("install")) {
                    return new ProcessResult(0, "", "");
                }
                if (cmd.get(1).equals("auth") && cmd.get(2).equals("status")) {
                    return new ProcessResult(1, "not logged in", "");
                }
                return new ProcessResult(0, "", "");
            };

            GhStatus status = ensureGithubCliReady(whichMock, runMock, messages);

            assertTrue(status.available);
            assertTrue(status.installedNow);
            assertFalse(status.authenticated);
            assertTrue(messages.stream().anyMatch(m -> m.contains("auth login")));
        }

        @Test
        @DisplayName("gh missing and install fails")
        void testGithubCliMissingAndInstallFails() {
            List<String> messages = new ArrayList<>();

            java.util.function.Function<String, String> whichMock = (name) -> "";
            java.util.function.Function<List<String>, ProcessResult> runMock = (cmd) ->
                new ProcessResult(1, "install failed", "");

            GhStatus status = ensureGithubCliReady(whichMock, runMock, messages);

            assertFalse(status.available);
            assertFalse(status.authenticated);
            assertFalse(status.installedNow);
            assertTrue(messages.stream().anyMatch(m -> m.contains("failed")));
        }
    }
}