/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.online;

import com.openjiuwen.agent_evolving.agent_rl.online.launcher.GatewayServiceConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.JiuwenConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.JiuwenLaunchResult;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LaunchRuntime;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LauncherCoordinator;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LauncherOnlineRlConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LauncherOrchestrator;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LauncherPaths;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LauncherProcess;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LauncherServices;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LauncherWorkspace;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.OnlineTrainingSchedulerHandle;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.PortCheck;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.JudgeServiceConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.TrajectoryConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.TrainingConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.VllmServiceConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.ServicesHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for LauncherRunner.
 * <p>
 * Mirrors Python's {@code test_launcher_runner.py} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/online/}.
 */
@DisplayName("LauncherRunner Tests")
class TestLauncherRunner {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("run online rl loop signal shutdown returns and stops children")
    void testRunOnlineRlLoopSignalShutdownReturnsAndStopsChildren() {
        FakeLauncherOrchestrator orchestrator = new FakeLauncherOrchestrator(tempDir);
        LauncherCoordinator coordinator = new LauncherCoordinator(orchestrator, millis -> {
            throw new LauncherCoordinator.ShutdownRequested();
        });
        LauncherOnlineRlConfig cfg = minimalConfig(true, tempDir);
        LauncherPaths paths = new LauncherPaths(tempDir, tempDir.resolve("jiuwenclaw"), tempDir.resolve("workspace"),
                tempDir.resolve("workspace").resolve("config").resolve(".env"), tempDir);

        coordinator.runOnlineRlLoop(cfg, tempDir.resolve("cfg.yaml"), paths);

        assertThat(orchestrator.scheduler.stopCalls).isEqualTo(1);
        assertThat(orchestrator.inferenceProc.terminateCalls).isEqualTo(1);
        assertThat(orchestrator.judgeProc.terminateCalls).isEqualTo(1);
        assertThat(orchestrator.gatewayProc.terminateCalls).isEqualTo(1);
        assertThat(orchestrator.clawProc.terminateCalls).isEqualTo(1);
        assertThat(orchestrator.webProc.terminateCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("print launch summary works without gateway mode")
    void testPrintLaunchSummaryWorksWithoutGatewayMode() {
        LauncherOnlineRlConfig cfg = minimalConfig(true, tempDir);
        LaunchRuntime runtime = new LaunchRuntime(
                "http://127.0.0.1:18002",
                "http://127.0.0.1:18003",
                "http://127.0.0.1:18080",
                "http://127.0.0.1:18080/v1",
                tempDir.resolve("lora_repo").toString(),
                false,
                false,
                false,
                "model-b",
                List.of()
        );

        String summary = LauncherServices.printLaunchSummary(cfg, tempDir.resolve("cfg.yaml"), runtime, true);

        assertThat(summary).contains("Gateway proxy");
        assertThat(summary).contains("Trajectory mode: feedback_level");
        assertThat(summary).doesNotContain("Gateway mode");
    }

    @Test
    @DisplayName("ensure workspace writes web user headers")
    void testEnsureWorkspaceWritesWebUserHeaders() throws Exception {
        Path configEnv = tempDir.resolve("config").resolve(".env");
        configEnv.getParent().toFile().mkdirs();
        configEnv.toFile().createNewFile();

        Map<String, String> env = new LinkedHashMap<>();
        env.put("WEB_USER_ID", "alice");
        LauncherWorkspace.ensureWorkspace(
                configEnv,
                "http://127.0.0.1:18080/v1",
                "model-a",
                "/tmp/model",
                "feedback_level",
                "http://127.0.0.1:18080",
                4,
                env
        );

        Map<String, String> values = new LinkedHashMap<>();
        for (String line : java.nio.file.Files.readAllLines(configEnv)) {
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                values.put(parts[0], parts[1]);
            }
        }
        assertThat(values.get("WEB_USER_ID")).isEqualTo("\"alice\"");
        assertThat(values.get("RL_ONLINE_TENANT_ID")).isEqualTo("\"alice\"");
        assertThat(values.get("CUSTOM_HEADERS")).contains("{\"x-user-id\":\"alice\"}");
    }

    @Test
    @DisplayName("start jiuwenclaw passes web user headers")
    void testStartJiuwenclawPassesWebUserHeaders() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("WEB_USER_ID", "bob");
        Map<String, String> built = ServicesHelper.buildJiuwenclawEnvironment(
                tempDir.resolve("jiuwenclaw"),
                tempDir.resolve("workspace"),
                "http://127.0.0.1:18080",
                "/tmp/model",
                "feedback_level",
                4,
                "127.0.0.1",
                19000,
                env
        );

        assertThat(built.get("WEB_USER_ID")).isEqualTo("bob");
        assertThat(built.get("RL_ONLINE_TENANT_ID")).isEqualTo("bob");
        assertThat(built.get("CUSTOM_HEADERS")).isEqualTo("{\"x-user-id\":\"bob\"}");
    }

    private LauncherOnlineRlConfig minimalConfig(boolean jiuwenEnabled, Path baseDir) {
        return new LauncherOnlineRlConfig(
                false,
                new VllmServiceConfig("/tmp/model", "model-a", "127.0.0.1", 18000, "0,1", 2, null, 1.0),
                new JudgeServiceConfig("/tmp/judge", "model-b", "127.0.0.1", 18001, "2,3", 2, null, 1.0, false),
                new GatewayServiceConfig("127.0.0.1", 18080, "redis://127.0.0.1:6379/0",
                        baseDir.resolve("records").toString(), "info", 1.0, true),
                new TrajectoryConfig(4, "feedback_level"),
                new TrainingConfig("4,5", 4, 30, null, null),
                new JiuwenConfig(jiuwenEnabled, 18092, "127.0.0.1", 19000, "127.0.0.1", 5173)
        );
    }

    private static final class FakeLauncherOrchestrator implements LauncherOrchestrator {
        final FakeProcess inferenceProc = new FakeProcess(101);
        final FakeProcess judgeProc = new FakeProcess(102);
        final FakeProcess gatewayProc = new FakeProcess(103);
        final FakeProcess clawProc = new FakeProcess(104);
        final FakeProcess webProc = new FakeProcess(105);
        final FakeScheduler scheduler = new FakeScheduler();
        private final LaunchRuntime runtime;

        FakeLauncherOrchestrator(Path baseDir) {
            runtime = new LaunchRuntime(
                    "http://127.0.0.1:18002",
                    "http://127.0.0.1:18003",
                    "http://127.0.0.1:18080",
                    "http://127.0.0.1:18080/v1",
                    baseDir.resolve("lora_repo").toString(),
                    false,
                    false,
                    false,
                    "model-b",
                    List.of(new PortCheck("Gateway", "127.0.0.1", 18080))
            );
        }

        @Override
        public LaunchRuntime resolveLaunchRuntime(LauncherOnlineRlConfig cfg, Path scriptDir) {
            return runtime;
        }

        @Override
        public void checkRequiredPorts(List<PortCheck> portsToCheck) {
        }

        @Override
        public LauncherProcess startInference(LauncherOnlineRlConfig cfg, Path logPath) {
            return inferenceProc;
        }

        @Override
        public LauncherProcess startJudge(LauncherOnlineRlConfig cfg, Path logPath) {
            return judgeProc;
        }

        @Override
        public void waitForServiceHealths(LauncherOnlineRlConfig cfg, LaunchRuntime runtime) {
        }

        @Override
        public LauncherProcess startGateway(LauncherOnlineRlConfig cfg, LaunchRuntime runtime, LauncherPaths paths) {
            return gatewayProc;
        }

        @Override
        public void waitForGatewayHealth(LauncherOnlineRlConfig cfg, LaunchRuntime runtime) {
        }

        @Override
        public OnlineTrainingSchedulerHandle startOnlineTrainingScheduler(LauncherOnlineRlConfig cfg, LaunchRuntime runtime) {
            return scheduler;
        }

        @Override
        public void ensureWorkspace(LauncherOnlineRlConfig cfg, LaunchRuntime runtime, LauncherPaths paths) {
        }

        @Override
        public JiuwenLaunchResult startJiuwenclaw(LauncherOnlineRlConfig cfg, LaunchRuntime runtime, LauncherPaths paths) {
            return new JiuwenLaunchResult(clawProc, webProc);
        }

        @Override
        public void printLaunchSummary(LauncherOnlineRlConfig cfg, Path cfgPath, LaunchRuntime runtime, boolean webStarted) {
        }
    }

    private static final class FakeScheduler implements OnlineTrainingSchedulerHandle {
        int stopCalls;

        @Override
        public void stop() {
            stopCalls++;
        }
    }

    private static final class FakeProcess implements LauncherProcess {
        final int pid;
        int terminateCalls;
        int killCalls;
        int waitCalls;
        private Integer returnCode;

        FakeProcess(int pid) {
            this.pid = pid;
        }

        @Override
        public Integer poll() {
            return returnCode;
        }

        @Override
        public void terminate() {
            terminateCalls++;
            returnCode = 0;
        }

        @Override
        public void waitFor(long timeoutMillis) {
            waitCalls++;
        }

        @Override
        public void kill() {
            killCalls++;
            returnCode = -9;
        }
    }
}
