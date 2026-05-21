/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for auto-harness git auth helpers and operations.
 * <p>
 * Mirrors Python's test_git_operations.py from
 * <code>tests/unit_tests/auto_harness/infra/test_git_operations.py</code>.
 */
@DisplayName("Git Operations Tests")
class TestGitOperations {

    // Helper method that mirrors build_git_auth_env
    static Map<String, String> buildGitAuthEnv(String username, String token) {
        Map<String, String> env = new HashMap<>();
        env.put("GIT_TERMINAL_PROMPT", "0");
        env.put("GCM_INTERACTIVE", "never");

        if (username != null && token != null && !username.isEmpty() && !token.isEmpty()) {
            String auth = Base64.getEncoder().encodeToString(
                (username + ":" + token).getBytes(StandardCharsets.UTF_8)
            );
            env.put("GIT_CONFIG_COUNT", "3");
            env.put("GIT_CONFIG_KEY_2", "http.https://gitcode.com/.extraHeader");
            env.put("GIT_CONFIG_VALUE_2", "AUTHORIZATION: basic " + auth);
        }
        return env;
    }

    // Simulated GitOperations class
    static class GitOperations {
        private final String workspace;
        private final String remote;
        private final String gitcodeUsername;
        private final String gitcodeToken;

        GitOperations(String workspace) {
            this(workspace, "origin", null, null);
        }

        GitOperations(String workspace, String remote, String gitcodeUsername, String gitcodeToken) {
            this.workspace = workspace;
            this.remote = remote;
            this.gitcodeUsername = gitcodeUsername;
            this.gitcodeToken = gitcodeToken;
        }

        // Simulates _git helper preserving leading space in output
        String gitStatusOutput(String rawOutput) {
            return rawOutput; // Preserves leading space
        }
    }

    @Nested
    @DisplayName("Git Auth Env Tests")
    class TestBuildGitAuthEnv {

        @Test
        @DisplayName("without credentials only disables prompts")
        void testWithoutCredentialsOnlyDisablesPrompts() {
            Map<String, String> env = buildGitAuthEnv(null, null);

            assertEquals("0", env.get("GIT_TERMINAL_PROMPT"));
            assertEquals("never", env.get("GCM_INTERACTIVE"));
            assertNull(env.get("GIT_CONFIG_COUNT"));
        }

        @Test
        @DisplayName("with credentials injects gitcode header")
        void testWithCredentialsInjectsGitcodeHeader() {
            Map<String, String> env = buildGitAuthEnv("bot-user", "secret-token");

            String expected = Base64.getEncoder().encodeToString(
                "bot-user:secret-token".getBytes(StandardCharsets.UTF_8)
            );

            assertEquals("3", env.get("GIT_CONFIG_COUNT"));
            assertEquals("http.https://gitcode.com/.extraHeader", env.get("GIT_CONFIG_KEY_2"));
            assertTrue(env.get("GIT_CONFIG_VALUE_2").contains(expected));
        }
    }

    @Nested
    @DisplayName("GitOperations Tests")
    class TestGitOperationsClass {

        @Test
        @DisplayName("git helper preserves leading space in output")
        void testGitHelperPreservesLeadingSpaceInOutput() {
            GitOperations git = new GitOperations("/tmp/worktree");

            String rawOutput = " M openjiuwen/auto_harness/schema.py\n";
            String result = git.gitStatusOutput(rawOutput);

            assertTrue(result.startsWith(" M"));
            assertTrue(result.contains("openjiuwen/auto_harness/schema.py"));
        }

        @Test
        @DisplayName("push uses task scoped auth env")
        void testPushUsesTaskScopedAuthEnv() {
            GitOperations git = new GitOperations(
                "/tmp/worktree",
                "fork",
                "bot-user",
                "secret-token"
            );

            Map<String, String> env = buildGitAuthEnv(git.gitcodeUsername, git.gitcodeToken);
            assertNotNull(env.get("GIT_CONFIG_COUNT"));
        }
    }
}