
package com.openjiuwen.autoharness.systemtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.openjiuwen.autoharness.infra.GitOperations;
import com.openjiuwen.autoharness.infra.WorktreeManager;
import com.openjiuwen.autoharness.schema.AutoHarnessConfig;
import com.openjiuwen.core.testsupport.OsTestSupport;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Tag("system-test")
class AutoHarnessGitCodeSystemTest {
    private static final String ENABLE_ENV = "AUTO_HARNESS_GITCODE_SYSTEM_TEST";
    private static final String ENABLE_PR_ENV = "AUTO_HARNESS_GITCODE_PR_SYSTEM_TEST";

    @TempDir
    Path tempDir;

    @Test
    void worktreeManagerShouldCloneAndFetchPublicGitCodeRepository() throws Exception {
        assumeTrue("1".equals(System.getenv(ENABLE_ENV)), ENABLE_ENV + "=1 is required for real GitCode network test");
        OsTestSupport.assumeGitAvailable();

        AutoHarnessConfig config = AutoHarnessConfig.builder().dataDir(tempDir.resolve("data").toString())
                .repoUrl("https://gitcode.com/openJiuwen/agent-core.git").upstreamRepo("agent-core")
                .gitBaseBranch("develop").build();
        WorktreeManager manager = new WorktreeManager(config);

        Path base = manager.ensureBaseRepo();

        assertThat(base).isEqualTo(config.cacheRepoPath());
        assertThat(base.resolve(".git")).isDirectory();
        assertThat(Files.exists(base.resolve("openjiuwen")) || Files.exists(base.resolve("pyproject.toml"))
                || Files.exists(base.resolve("README.md"))).isTrue();

        Path fetchedAgain = manager.ensureBaseRepo();

        assertThat(fetchedAgain).isEqualTo(base);
        assertThat(runCapture(base, "git", "rev-parse", "--verify", "origin/develop")).isNotBlank();
    }

    @Test
    void worktreeManagerShouldFailInitialCloneForMissingGitCodeRepository() {
        assumeTrue("1".equals(System.getenv(ENABLE_ENV)), ENABLE_ENV + "=1 is required for real GitCode network test");
        OsTestSupport.assumeGitAvailable();

        AutoHarnessConfig config = AutoHarnessConfig.builder().dataDir(tempDir.resolve("data-missing").toString())
                .repoUrl("https://gitcode.com/openJiuwen/auto-harness-missing-repo-for-system-test.git")
                .upstreamRepo("auto-harness-missing-repo-for-system-test").gitBaseBranch("develop").build();
        WorktreeManager manager = new WorktreeManager(config);

        assertThatThrownBy(manager::ensureBaseRepo).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("git clone").hasMessageContaining("failed");
    }

    @Test
    void worktreeManagerShouldContinueWhenAuthenticatedGitCodeFetchFails() throws Exception {
        assumeTrue("1".equals(System.getenv(ENABLE_ENV)), ENABLE_ENV + "=1 is required for real GitCode network test");
        OsTestSupport.assumeGitAvailable();

        Path dataDir = tempDir.resolve("data-auth-fetch-failure");
        AutoHarnessConfig seedConfig = AutoHarnessConfig.builder().dataDir(dataDir.toString())
                .repoUrl("https://gitcode.com/openJiuwen/agent-core.git").upstreamRepo("agent-core")
                .gitBaseBranch("develop").build();
        WorktreeManager seedManager = new WorktreeManager(seedConfig);
        Path base = seedManager.ensureBaseRepo();
        assertThat(base.resolve(".git")).isDirectory();

        AutoHarnessConfig badAuthConfig = AutoHarnessConfig.builder().dataDir(dataDir.toString())
                .repoUrl("https://gitcode.com/openJiuwen/agent-core.git").upstreamRepo("agent-core")
                .gitBaseBranch("develop").gitcodeUsername("auto-harness-invalid-user")
                .gitcodeToken("auto-harness-invalid-token").build();
        WorktreeManager badAuthManager = new WorktreeManager(badAuthConfig);

        Path fetched = badAuthManager.ensureBaseRepo();

        assertThat(fetched).isEqualTo(base);
        assertThat(runGitCodeLsRemoteWithInvalidAuth()).isNotZero();
    }

    @Test
    void gitOperationsShouldPushBranchAndCreateGitCodePrWhenExplicitlyEnabled() throws Exception {
        assumeTrue("1".equals(System.getenv(ENABLE_PR_ENV)),
                ENABLE_PR_ENV + "=1 is required for real GitCode PR creation test");
        OsTestSupport.assumeGitAvailable();
        String token = System.getenv("GITCODE_ACCESS_TOKEN");
        String forkOwner = System.getenv("AUTO_HARNESS_GITCODE_FORK_OWNER");
        assumeTrue(hasText(token), "GITCODE_ACCESS_TOKEN is required");
        assumeTrue(hasText(forkOwner), "AUTO_HARNESS_GITCODE_FORK_OWNER is required");
        String username =
            resolveGitCodeUsernameForSystemTest(System.getenv("AUTO_HARNESS_GITCODE_USERNAME"), forkOwner);

        AutoHarnessConfig config = AutoHarnessConfig.builder().dataDir(tempDir.resolve("data-pr").toString())
                .repoUrl("https://gitcode.com/openJiuwen/agent-core.git").upstreamOwner("openJiuwen")
                .upstreamRepo("agent-core").gitBaseBranch("develop").gitRemote("fork").forkOwner(forkOwner)
                .gitcodeUsername(username).gitcodeToken(token).gitUserName("Auto Harness")
                .gitUserEmail("auto-harness@example.com").build();
        WorktreeManager manager = new WorktreeManager(config);
        Path base = manager.ensureBaseRepo();
        String branch = "auto-harness/system-test-" + System.currentTimeMillis();
        Path worktree = tempDir.resolve("pr-worktree");
        runCapture(base, "git", "remote", "remove", "fork");
        runCapture(base, "git", "remote", "add", "fork", "https://gitcode.com/" + forkOwner + "/agent-core.git");
        runCapture(base, "git", "worktree", "add", "-b", branch, worktree.toString(), "origin/develop");
        runCapture(worktree, "git", "config", "user.name", "Auto Harness");
        runCapture(worktree, "git", "config", "user.email", "auto-harness@example.com");
        Files.writeString(worktree.resolve("auto-harness-system-test.txt"), "auto harness system test\n");
        runCapture(worktree, "git", "add", "auto-harness-system-test.txt");
        runCapture(worktree, "git", "commit", "-m", "test(auto-harness): system pr smoke");

        GitOperations git = new GitOperations(worktree.toString(), "fork", "develop", forkOwner, "openJiuwen",
                "agent-core", username, token, "Auto Harness", "auto-harness@example.com");

        Map<String, Object> push = git.push(branch);
        assertThat(push).containsEntry("success", true);

        Map<String, Object> pr = git.createPr("test(auto-harness): system pr smoke",
                "/kind task\n\n## Summary\n- Auto Harness GitCode PR system test.\n\n## Verification\n- system-test created this PR.",
                branch);

        assertThat(pr).containsEntry("success", true);
        assertThat(String.valueOf(pr.getOrDefault("pr_url", ""))).contains("gitcode.com");
    }

    private static String runCapture(Path cwd, String... command) throws Exception {
        if (command.length > 0 && "git".equals(command[0])) {
            OsTestSupport.assumeGitAvailable();
        }
        Process process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int code = process.waitFor();
        assertThat(code).as(String.join(" ", command) + "\n" + output).isZero();
        return output.strip();
    }

    private static int runGitCodeLsRemoteWithInvalidAuth() throws Exception {
        OsTestSupport.assumeGitAvailable();
        ProcessBuilder builder =
            new ProcessBuilder("git", "ls-remote", "https://gitcode.com/openJiuwen/agent-core.git", "HEAD");
        builder.redirectErrorStream(true);
        Map<String, String> env = builder.environment();
        env.put("GIT_TERMINAL_PROMPT", "0");
        env.put("GCM_INTERACTIVE", "never");
        env.put("GIT_CONFIG_COUNT", "3");
        env.put("GIT_CONFIG_KEY_0", "credential.helper");
        env.put("GIT_CONFIG_VALUE_0", "");
        env.put("GIT_CONFIG_KEY_1", "credential.interactive");
        env.put("GIT_CONFIG_VALUE_1", "never");
        env.put("GIT_CONFIG_KEY_2", "http.https://gitcode.com/.extraheader");
        String basic =
            java.util.Base64.getEncoder().encodeToString("auto-harness-invalid-user:auto-harness-invalid-token"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        env.put("GIT_CONFIG_VALUE_2", "AUTHORIZATION: basic " + basic);
        Process process = builder.start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static String resolveGitCodeUsernameForSystemTest(String username, String forkOwner) {
        return hasText(username) ? username : forkOwner;
    }
}
