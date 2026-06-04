package com.openjiuwen.unit_tests.cli;

import com.openjiuwen.harness.cli.AutoHarnessCliSupport;
import com.openjiuwen.harness.cli.ui.CliRepl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mirrors Python's {@code tests.unit_tests.cli.test_auto_harness_repl}.
 */
class TestAutoHarnessRepl {

    @Test
    void testSubcmdRunGoalKeepsFullFlow(@TempDir Path tempDir) throws IOException {
        Path repo = makeFakeRepo(tempDir, "agent-core");
        Path neutralCwd = Files.createDirectories(tempDir.resolve("cwd"));

        AutoHarnessCliSupport.PreparedRun prepared = new CliRepl()
                .subcmdRun(List.of("--goal", "分析差距 claude-code"), tempDir.toString(), neutralCwd);

        assertEquals("分析差距 claude-code", prepared.getConfig().getOptimizationGoal());
        assertEquals(repo.toAbsolutePath().normalize().toString(), prepared.getConfig().getLocalRepo());
        assertEquals(repo.toAbsolutePath().normalize().toString(), prepared.getConfig().getWorkspace());
        assertNull(prepared.getTasks());
    }

    @Test
    void testNaturalLanguageDispatchRunsFullFlow(@TempDir Path tempDir) throws IOException {
        Path neutralCwd = Files.createDirectories(tempDir.resolve("cwd"));

        AutoHarnessCliSupport.PreparedRun prepared = new CliRepl()
                .cmdAutoHarness("/auto-harness 分析差距 claude-code", tempDir.toString(), neutralCwd);

        assertEquals("分析差距 claude-code", prepared.getConfig().getOptimizationGoal());
        assertNull(prepared.getTasks());
    }

    private static Path makeFakeRepo(Path parent, String name) throws IOException {
        Path repo = Files.createDirectories(parent.resolve(name));
        Files.createDirectories(repo.resolve(".git"));
        Files.writeString(repo.resolve("pyproject.toml"), "[project]\nname='x'\n", StandardCharsets.UTF_8);
        Files.createDirectories(repo.resolve("openjiuwen"));
        return repo;
    }
}
