package com.openjiuwen.unit_tests.cli;

import com.openjiuwen.harness.cli.AutoHarnessCliSupport;
import com.openjiuwen.harness.cli.AutoHarnessRunRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.cli.test_auto_harness_cli}.
 */
class TestAutoHarnessCli {

    @Test
    void testRunWithoutManualTasksUsesFullSession(@TempDir Path tempDir) throws IOException {
        Path fakeCwdRepo = makeFakeRepo(tempDir, "cwd_repo");
        AutoHarnessCliSupport.CliOptions opts = options(tempDir);
        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        request.setGoal("分析差距 claude-code");

        AutoHarnessCliSupport.PreparedRun prepared = AutoHarnessCliSupport.prepareRun(opts, request, fakeCwdRepo);

        assertEquals("分析差距 claude-code", prepared.getConfig().getOptimizationGoal());
        assertEquals(fakeCwdRepo.toAbsolutePath().normalize().toString(), prepared.getConfig().getLocalRepo());
        assertEquals(fakeCwdRepo.toAbsolutePath().normalize().toString(), prepared.getConfig().getWorkspace());
        assertEquals(tempDir.resolve("auto_harness").toString(), prepared.getConfig().getDataDir());
        assertNull(prepared.getTasks());
    }

    @Test
    void testRunWithDetectedLocalRepoSetsWorkspace(@TempDir Path tempDir) throws IOException {
        Path repo = makeFakeRepo(tempDir, "agent-core");
        Path neutralCwd = Files.createDirectories(tempDir.resolve("cwd"));
        AutoHarnessCliSupport.CliOptions opts = options(tempDir);

        AutoHarnessCliSupport.PreparedRun prepared =
                AutoHarnessCliSupport.prepareRun(opts, new AutoHarnessRunRequest(), neutralCwd);

        assertEquals(repo.toAbsolutePath().normalize().toString(), prepared.getConfig().getLocalRepo());
        assertEquals(repo.toAbsolutePath().normalize().toString(), prepared.getConfig().getWorkspace());
    }

    @Test
    void testRunAssessInvokesGithubCliPreflight(@TempDir Path tempDir) throws IOException {
        Path neutralCwd = Files.createDirectories(tempDir.resolve("cwd"));
        AutoHarnessCliSupport.CliOptions opts = options(tempDir);
        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        request.setStage("assess");
        request.setCompetitor("claude-code");

        AutoHarnessCliSupport.PreparedRun prepared = AutoHarnessCliSupport.prepareRun(opts, request, neutralCwd);

        assertEquals("claude-code", prepared.getConfig().getCompetitor());
        assertTrue(prepared.isGithubCliPreflightRequired());
    }

    private static AutoHarnessCliSupport.CliOptions options(Path workspace) {
        AutoHarnessCliSupport.CliOptions opts = new AutoHarnessCliSupport.CliOptions();
        opts.setWorkspace(workspace.toString());
        opts.setProvider("OpenAI");
        opts.setModel("gpt-4o");
        opts.setApiKey("mock-api-key");
        opts.setApiBase("https://api.openai.com/v1");
        return opts;
    }

    private static Path makeFakeRepo(Path parent, String name) throws IOException {
        Path repo = Files.createDirectories(parent.resolve(name));
        Files.createDirectories(repo.resolve(".git"));
        Files.writeString(repo.resolve("pyproject.toml"), "[project]\nname='fake'\n", StandardCharsets.UTF_8);
        Files.createDirectories(repo.resolve("openjiuwen"));
        return repo;
    }
}
