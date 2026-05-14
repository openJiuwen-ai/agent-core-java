/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherServicesAndRunnerTest {

    @Test
    void printLaunchSummaryWorksWithoutGatewayMode() {
        LauncherOnlineRlConfig cfg = sampleConfig();
        LaunchRuntime runtime = new LaunchRuntime(
                "http://127.0.0.1:18002",
                "http://127.0.0.1:18003",
                "http://127.0.0.1:18080",
                "http://127.0.0.1:18080/v1",
                "/tmp/lora_repo",
                false,
                false,
                false,
                "model-b",
                List.of()
        );

        String summary = LauncherServices.printLaunchSummary(cfg, Path.of("cfg.yaml"), runtime, true);

        assertTrue(summary.contains("Gateway proxy"));
        assertTrue(summary.contains("Trajectory mode: feedback_level"));
        assertFalse(summary.contains("Gateway mode"));
    }

    @Test
    void ensureWorkspaceWritesWebUserHeaders() {
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "launcher-workspace-test");
        Path configEnv = root.resolve("config/.env");
        LauncherWorkspace.ensureWorkspace(
                configEnv,
                "http://127.0.0.1:18080/v1",
                "model-a",
                "/tmp/model",
                "feedback_level",
                "http://127.0.0.1:18080",
                4,
                Map.of("WEB_USER_ID", "alice")
        );

        try {
            Map<String, String> values = java.nio.file.Files.readAllLines(configEnv).stream()
                    .filter(line -> line.contains("="))
                    .map(line -> line.split("=", 2))
                    .collect(java.util.stream.Collectors.toMap(parts -> parts[0], parts -> parts[1], (a, b) -> b, java.util.LinkedHashMap::new));
            assertEquals("\"alice\"", values.get("WEB_USER_ID"));
            assertEquals("\"alice\"", values.get("RL_ONLINE_TENANT_ID"));
            assertEquals("'{\"x-user-id\":\"alice\"}'", values.get("CUSTOM_HEADERS"));
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    @Test
    void runOnlineRlLoopSignalShutdownReturnsAndStopsChildren() {
        FakeProc inferenceProc = new FakeProc();
        FakeProc judgeProc = new FakeProc();
        FakeProc gatewayProc = new FakeProc();
        FakeProc clawProc = new FakeProc();
        FakeProc webProc = new FakeProc();
        FakeScheduler scheduler = new FakeScheduler();

        LauncherOrchestrator orchestrator = new LauncherOrchestrator() {
            @Override
            public LaunchRuntime resolveLaunchRuntime(LauncherOnlineRlConfig cfg, Path scriptDir) {
                return new LaunchRuntime(
                        "http://127.0.0.1:18002",
                        "http://127.0.0.1:18003",
                        "http://127.0.0.1:18080",
                        "http://127.0.0.1:18080/v1",
                        scriptDir.resolve("lora_repo").toString(),
                        false,
                        false,
                        false,
                        "model-b",
                        List.of()
                );
            }

            @Override public void checkRequiredPorts(List<PortCheck> portsToCheck) { }
            @Override public LauncherProcess startInference(LauncherOnlineRlConfig cfg, Path logPath) { return inferenceProc; }
            @Override public LauncherProcess startJudge(LauncherOnlineRlConfig cfg, Path logPath) { return judgeProc; }
            @Override public void waitForServiceHealths(LauncherOnlineRlConfig cfg, LaunchRuntime runtime) { }
            @Override public LauncherProcess startGateway(LauncherOnlineRlConfig cfg, LaunchRuntime runtime, LauncherPaths paths) { return gatewayProc; }
            @Override public void waitForGatewayHealth(LauncherOnlineRlConfig cfg, LaunchRuntime runtime) { }
            @Override public OnlineTrainingSchedulerHandle startOnlineTrainingScheduler(LauncherOnlineRlConfig cfg, LaunchRuntime runtime) { return scheduler; }
            @Override public void ensureWorkspace(LauncherOnlineRlConfig cfg, LaunchRuntime runtime, LauncherPaths paths) { }
            @Override public JiuwenLaunchResult startJiuwenclaw(LauncherOnlineRlConfig cfg, LaunchRuntime runtime, LauncherPaths paths) { return new JiuwenLaunchResult(clawProc, webProc); }
            @Override public void printLaunchSummary(LauncherOnlineRlConfig cfg, Path cfgPath, LaunchRuntime runtime, boolean webStarted) { }
        };

        LauncherCoordinator coordinator = new LauncherCoordinator(orchestrator, millis -> {
            if (millis == 5000L) {
                throw new LauncherCoordinator.ShutdownRequested();
            }
        });

        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        LauncherPaths paths = new LauncherPaths(tmp, tmp.resolve("jiuwenclaw"), tmp.resolve("workspace"), tmp.resolve("workspace/.env"), tmp);
        coordinator.runOnlineRlLoop(sampleConfig(), tmp.resolve("cfg.yaml"), paths);

        assertEquals(1, scheduler.stopCalls);
        for (FakeProc proc : List.of(inferenceProc, judgeProc, gatewayProc, clawProc, webProc)) {
            assertEquals(1, proc.terminateCalls);
        }
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

    static final class FakeProc implements LauncherProcess {
        Integer returnCode;
        int terminateCalls;
        int killCalls;
        int waitCalls;

        @Override public Integer poll() { return returnCode; }
        @Override public void terminate() { terminateCalls += 1; returnCode = 0; }
        @Override public void waitFor(long timeoutMillis) { waitCalls += 1; }
        @Override public void kill() { killCalls += 1; returnCode = -9; }
    }

    static final class FakeScheduler implements OnlineTrainingSchedulerHandle {
        int stopCalls;
        @Override public void stop() { stopCalls += 1; }
    }
}
