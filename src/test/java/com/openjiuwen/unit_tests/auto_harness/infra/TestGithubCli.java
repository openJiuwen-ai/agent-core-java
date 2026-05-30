/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.infra;

import com.openjiuwen.auto_harness.infra.GitHubCli;
import com.openjiuwen.auto_harness.infra.GitHubCliStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for GitHub CLI preflight helpers.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.auto_harness.infra.test_github_cli}.</p>
 */
@DisplayName("GitHub CLI Tests")
class TestGithubCli {

    @Nested
    @DisplayName("GitHub CLI Ready Tests")
    class TestGhCliReady {

        @Test
        @DisplayName("gh present and authenticated")
        void testGithubCliPresentAndAuthenticated() {
            List<String> messages = new ArrayList<>();
            GitHubCli.Which which = name -> "/usr/bin/gh";
            GitHubCli.CommandRunner runner = command -> new GitHubCli.ProcessResult(0, "", "");

            GitHubCliStatus status = GitHubCli.ensureGithubCliReady(
                    messages::add,
                    which,
                    runner,
                    List::of);

            assertTrue(status.isAvailable());
            assertTrue(status.isAuthenticated());
            assertFalse(status.isInstalledNow());
            assertEquals("/usr/bin/gh", status.getPath());
            assertTrue(messages.stream().anyMatch(message -> message.contains("\u5df2\u767b\u5f55")));
        }

        @Test
        @DisplayName("gh missing installs and prompts authentication")
        void testGithubCliMissingInstallsAndPromptsAuthentication() {
            List<String> messages = new ArrayList<>();
            List<List<String>> runCalls = new ArrayList<>();
            Iterator<String> whichValues = List.of("", "/usr/local/bin/gh").iterator();
            GitHubCli.Which which = name -> whichValues.hasNext() ? whichValues.next() : "/usr/local/bin/gh";
            GitHubCli.CommandRunner runner = command -> {
                runCalls.add(List.copyOf(command));
                if (command.size() >= 3 && command.subList(1, 3).equals(List.of("auth", "status"))) {
                    return new GitHubCli.ProcessResult(1, "not logged in", "");
                }
                return new GitHubCli.ProcessResult(0, "", "");
            };

            GitHubCliStatus status = GitHubCli.ensureGithubCliReady(
                    messages::add,
                    which,
                    runner,
                    () -> List.of(new GitHubCli.InstallCommand(List.of("brew", "install", "gh"), "brew install gh")));

            assertTrue(status.isAvailable());
            assertTrue(status.isInstalledNow());
            assertFalse(status.isAuthenticated());
            assertEquals(List.of("brew", "install", "gh"), runCalls.get(0));
            assertEquals(List.of("/usr/local/bin/gh", "auth", "status"), runCalls.get(1));
            assertTrue(String.join("\n", messages).contains("gh auth login --web"));
        }

        @Test
        @DisplayName("gh missing and install fails")
        void testGithubCliMissingAndInstallFails() {
            List<String> messages = new ArrayList<>();
            GitHubCli.Which which = name -> "";
            GitHubCli.CommandRunner runner = command -> new GitHubCli.ProcessResult(1, "permission denied", "");

            GitHubCliStatus status = GitHubCli.ensureGithubCliReady(
                    messages::add,
                    which,
                    runner,
                    () -> List.of(new GitHubCli.InstallCommand(
                            List.of("apt-get", "install", "-y", "gh"),
                            "apt-get install -y gh")));

            assertFalse(status.isAvailable());
            assertFalse(status.isAuthenticated());
            assertFalse(status.isInstalledNow());
            String joined = String.join("\n", messages);
            assertTrue(joined.contains("\u81ea\u52a8\u5b89\u88c5 `gh` \u5931\u8d25"));
            assertTrue(joined.contains("https://cli.github.com/"));
        }
    }
}
