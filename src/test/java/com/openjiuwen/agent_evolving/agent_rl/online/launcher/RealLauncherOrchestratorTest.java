/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RealLauncherOrchestratorTest {

    @Test
    void processLauncherWrapsRealProcess() throws Exception {
        ProcessLauncherProcess launcherProcess = new ProcessLauncherProcess(new ProcessBuilder("cmd", "/c", "exit", "0").start());
        launcherProcess.waitFor(5000L);
        assertTrue(launcherProcess.poll() != null);
    }

    @Test
    void healthCheckTimesOutWhenEndpointUnavailable() {
        assertThrows(Exception.class, () -> LauncherHealthChecks.waitForHealth("http://127.0.0.1:9/health", Duration.ofMillis(100)));
    }

    @Test
    void ensureWorkspaceCreatesLogsDirectory() throws Exception {
        RealLauncherOrchestrator orchestrator = new RealLauncherOrchestrator();
        Path temp = Files.createTempDirectory("launcher-orchestrator");
        LauncherPaths paths = new LauncherPaths(temp, temp, temp, temp.resolve(".env"), temp.resolve("script"));

        orchestrator.ensureWorkspace(sampleConfig(), new LaunchRuntime("i", "j", "g", "ga", "l", false, false, false, "label", List.of()), paths);

        assertTrue(Files.exists(paths.scriptDir().resolve("logs")));
    }

    private static LauncherOnlineRlConfig sampleConfig() {
        return new LauncherOnlineRlConfig(
                false,
                new VllmServiceConfig("/tmp/model", "model-a", "127.0.0.1", 18000, "0,1", 2, null, 1.0),
                new JudgeServiceConfig("/tmp/judge", "model-b", "127.0.0.1", 18001, "2,3", 2, null, 1.0, false),
                new GatewayServiceConfig("127.0.0.1", 18080, "redis://127.0.0.1:6379/0", "records", "info", 1.0, true),
                new TrajectoryConfig(4, "feedback_level"),
                new TrainingConfig("4,5", 4, 30, null, null),
                new JiuwenConfig(true, 18092, "127.0.0.1", 19000, "127.0.0.1", 5173)
        );
    }
}
