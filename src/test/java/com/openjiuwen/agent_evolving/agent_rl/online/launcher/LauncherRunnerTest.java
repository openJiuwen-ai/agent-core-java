/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import com.openjiuwen.agent_evolving.agent_rl.config.JudgeConfig;
import com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig;
import com.openjiuwen.agent_evolving.agent_rl.config.VLLMServiceConfig;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's orchestration loop in
 * {@code openjiuwen/agent_evolving/agent_rl/online/launcher/runner.py}.</p>
 *
 * <p>Mirrors Python's launcher runner tests in
 * {@code tests/unit_tests/agent_evolving/agent_rl/online/test_launcher_runner.py}.</p>
 */
class LauncherRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void runOnlineRlLoopStartsServicesAndShutsDownInReverseOrder() {
        List<String> events = new ArrayList<>();
        OnlineRLConfig cfg = validConfig(true);
        FakeBackend backend = new FakeBackend(events, runtime(false, false, true));
        LauncherRunner runner = new LauncherRunner(backend, duration -> events.add("sleep:" + duration));

        runner.runOnlineRlLoop(
                cfg,
                tempDir.resolve("online.yaml"),
                paths(),
                new LauncherRunner.RunOptions(Duration.ZERO, 0, false)
        );

        assertEquals(List.of(
                "checkPort:Gateway:127.0.0.1:19000",
                "checkPort:vLLM-Inference:127.0.0.1:18000",
                "checkPort:vLLM-Judge:127.0.0.1:18001",
                "checkPort:JiuwenClaw-AgentServer:127.0.0.1:20001",
                "checkPort:JiuwenClaw-WS:127.0.0.1:20002",
                "checkPort:JiuwenClaw-Web:127.0.0.1:20003",
                "startVllm:Inference:1a/5:true",
                "startJudge:Judge:1b/5:false",
                "health:http://inference.local/health:12.0",
                "health:http://judge.local/health:21.0",
                "startGateway:http://inference.local:http://judge.local",
                "health:http://gateway.local/health:7.0",
                "startScheduler:http://gateway.local/v1",
                "ensureWorkspace:http://gateway.local/v1:http://gateway.local",
                "startJiuwenClaw:http://gateway.local",
                "sleep:PT5S",
                "summary:true",
                "stopScheduler",
                "terminate:web",
                "terminate:claw",
                "terminate:gateway",
                "terminate:judge",
                "terminate:Inference"
        ), events);
    }

    @Test
    void existingServicesSkipVllmLaunchButStillWaitForHealth() {
        List<String> events = new ArrayList<>();
        OnlineRLConfig cfg = validConfig(false);
        FakeBackend backend = new FakeBackend(events, runtime(true, true, false));
        LauncherRunner runner = new LauncherRunner(backend, duration -> events.add("sleep:" + duration));

        runner.runOnlineRlLoop(
                cfg,
                tempDir.resolve("online.yaml"),
                paths(),
                new LauncherRunner.RunOptions(Duration.ZERO, 0, false)
        );

        assertFalse(events.stream().anyMatch(event -> event.startsWith("startVllm")));
        assertFalse(events.stream().anyMatch(event -> event.startsWith("startJudge")));
        assertFalse(events.stream().anyMatch(event -> event.startsWith("ensureWorkspace")));
        assertTrue(events.contains("health:http://inference.local/health:30.0"));
        assertTrue(events.contains("health:http://judge.local/health:30.0"));
        assertTrue(events.contains("summary:false"));
        assertTrue(events.contains("stopScheduler"));
    }

    @Test
    void printLaunchSummaryWorksWithoutGatewayMode() {
        OnlineRLConfig cfg = validConfig(true);
        LauncherServices.LaunchRuntime runtime = new LauncherServices.LaunchRuntime(
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

        String summary = LauncherServices.buildLaunchSummary(
                cfg,
                tempDir.resolve("cfg.yaml"),
                runtime,
                true
        );

        assertTrue(summary.contains("Gateway proxy"));
        assertTrue(summary.contains("Trajectory mode: feedback_level"));
        assertFalse(summary.contains("Gateway mode"));
    }

    @Test
    void ensureWorkspaceWritesWebUserHeaders() throws Exception {
        Path configEnv = tempDir.resolve("config/.env");
        Files.createDirectories(configEnv.getParent());
        Files.writeString(configEnv, "", StandardCharsets.UTF_8);

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

        Map<String, String> values = readEnvFile(configEnv);
        assertEquals("\"alice\"", values.get("WEB_USER_ID"));
        assertEquals("\"alice\"", values.get("RL_ONLINE_TENANT_ID"));
        assertEquals("'{\"x-user-id\":\"alice\"}'", values.get("CUSTOM_HEADERS"));
    }

    @Test
    void startJiuwenclawPassesWebUserHeaders() {
        List<LauncherServices.ProcessLaunchSpec> specs = new ArrayList<>();

        LauncherServices.JiuwenClawProcesses processes = LauncherServices.startJiuwenClaw(
                new LauncherServices.JiuwenClawStartOptions(
                        tempDir.resolve("jiuwenclaw"),
                        tempDir.resolve("workspace"),
                        "http://127.0.0.1:18080",
                        "/tmp/model",
                        "feedback_level",
                        4,
                        "127.0.0.1",
                        19000,
                        "127.0.0.1",
                        5173
                ),
                Map.of("WEB_USER_ID", "bob"),
                spec -> {
                    specs.add(spec);
                    return new FakeProcess("app", new ArrayList<>());
                }
        );

        assertTrue(processes.appProcess().isAlive());
        assertNull(processes.webProcess());
        assertEquals(1, specs.size());
        Map<String, String> env = specs.getFirst().environment();
        assertEquals("bob", env.get("WEB_USER_ID"));
        assertEquals("bob", env.get("RL_ONLINE_TENANT_ID"));
        assertEquals("{\"x-user-id\":\"bob\"}", env.get("CUSTOM_HEADERS"));
    }

    private LauncherRunner.LauncherPaths paths() {
        return new LauncherRunner.LauncherPaths(
                tempDir.resolve("agent-core"),
                tempDir.resolve("jiuwenclaw"),
                tempDir.resolve("workspace"),
                tempDir.resolve("workspace/.env"),
                tempDir.resolve("script")
        );
    }

    private static LauncherServices.LaunchRuntime runtime(boolean skipVllm, boolean skipJudge, boolean jiuwenEnabled) {
        List<LauncherServices.PortCheck> ports = new ArrayList<>();
        ports.add(new LauncherServices.PortCheck("Gateway", "127.0.0.1", 19000));
        if (!skipVllm) {
            ports.add(new LauncherServices.PortCheck("vLLM-Inference", "127.0.0.1", 18000));
        }
        if (!skipJudge) {
            ports.add(new LauncherServices.PortCheck("vLLM-Judge", "127.0.0.1", 18001));
        }
        if (jiuwenEnabled) {
            ports.add(new LauncherServices.PortCheck("JiuwenClaw-AgentServer", "127.0.0.1", 20001));
            ports.add(new LauncherServices.PortCheck("JiuwenClaw-WS", "127.0.0.1", 20002));
            ports.add(new LauncherServices.PortCheck("JiuwenClaw-Web", "127.0.0.1", 20003));
        }
        return new LauncherServices.LaunchRuntime(
                "http://inference.local",
                "http://judge.local",
                "http://gateway.local",
                "http://gateway.local/v1",
                "/tmp/lora",
                skipVllm,
                skipJudge,
                false,
                "judge",
                ports
        );
    }

    private static OnlineRLConfig validConfig(boolean jiuwenEnabled) {
        OnlineRLConfig cfg = new OnlineRLConfig();
        cfg.getInference().setModelPath("/models/base");
        cfg.getInference().setModelName("base");
        cfg.getInference().setPort(18000);
        cfg.getInference().setHealthTimeout(12.0);
        cfg.getJudge().setModelPath("/models/judge");
        cfg.getJudge().setModelName("judge");
        cfg.getJudge().setPort(18001);
        cfg.getJudge().setReuseInferenceIfSameModel(false);
        cfg.getJudge().setHealthTimeout(21.0);
        cfg.getGateway().setPort(19000);
        cfg.getGateway().setRedisUrl("redis://localhost:6379/0");
        cfg.getGateway().setRecordDir("records");
        cfg.getGateway().setHealthTimeout(7.0);
        cfg.getTraining().setGpuIds("4,5");
        cfg.getTraining().setThreshold(4);
        cfg.getTraining().setScanInterval(30);
        cfg.getTrajectory().setMode("feedback_level");
        cfg.getTrajectory().setBatchSize(4);
        cfg.getJiuwen().setEnabled(jiuwenEnabled);
        cfg.getJiuwen().setAgentServerPort(20001);
        cfg.getJiuwen().setWsPort(20002);
        cfg.getJiuwen().setWebPort(20003);
        cfg.validate();
        return cfg;
    }

    private static Map<String, String> readEnvFile(Path path) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                values.put(parts[0], parts[1]);
            }
        }
        return values;
    }

    private static final class FakeBackend implements LauncherRunner.LauncherBackend {
        private final List<String> events;
        private final LauncherServices.LaunchRuntime runtime;

        private FakeBackend(List<String> events, LauncherServices.LaunchRuntime runtime) {
            this.events = events;
            this.runtime = runtime;
        }

        @Override
        public LauncherServices.LaunchRuntime resolveLaunchRuntime(OnlineRLConfig cfg, Path scriptDir) {
            return runtime;
        }

        @Override
        public void checkPortFree(String host, int port) {
            LauncherServices.PortCheck portCheck = runtime.portsToCheck().stream()
                    .filter(item -> item.host().equals(host) && item.port() == port)
                    .findFirst()
                    .orElseThrow();
            events.add("checkPort:" + portCheck.label() + ":" + host + ":" + port);
        }

        @Override
        public void waitForHealth(String url, double timeoutSeconds) {
            events.add("health:" + url + ":" + timeoutSeconds);
        }

        @Override
        public Process startInferenceVllmService(
                VLLMServiceConfig serviceConfig,
                String stepLabel,
                String serviceName,
                boolean enableRuntimeLora,
                Path logPath
        ) {
            events.add("startVllm:" + serviceName + ":" + stepLabel + ":" + enableRuntimeLora);
            return new FakeProcess("Inference", events);
        }

        @Override
        public Process startJudgeVllmService(
                JudgeConfig serviceConfig,
                String stepLabel,
                String serviceName,
                boolean enableRuntimeLora,
                Path logPath
        ) {
            events.add("startJudge:" + serviceName + ":" + stepLabel + ":" + enableRuntimeLora);
            return new FakeProcess("judge", events);
        }

        @Override
        public Process startGateway(
                String inferenceUrl,
                String judgeUrl,
                String judgeModel,
                String modelId,
                String modelPath,
                String loraRepoRoot,
                OnlineRLConfig cfg,
                Path agentCoreRoot,
                Path logPath
        ) {
            events.add("startGateway:" + inferenceUrl + ":" + judgeUrl);
            return new FakeProcess("gateway", events);
        }

        @Override
        public LauncherRunner.SchedulerHandle startOnlineTrainingScheduler(
                OnlineRLConfig cfg,
                LauncherServices.LaunchRuntime runtime
        ) {
            events.add("startScheduler:" + runtime.gatewayApiUrl());
            return () -> events.add("stopScheduler");
        }

        @Override
        public void ensureWorkspace(
                Path configEnv,
                String gatewayUrl,
                String modelName,
                String modelPath,
                String trajectoryMode,
                String trajectoryGatewayUrl,
                int trajectoryBatchSize
        ) {
            events.add("ensureWorkspace:" + gatewayUrl + ":" + trajectoryGatewayUrl);
        }

        @Override
        public LauncherServices.JiuwenClawProcesses startJiuwenClaw(
                Path jiuwenclawRepo,
                Path workspaceRoot,
                String trajectoryGatewayUrl,
                String modelPath,
                String trajectoryMode,
                int trajectoryBatchSize,
                String appHost,
                int wsPort,
                String webHost,
                int webPort
        ) {
            events.add("startJiuwenClaw:" + trajectoryGatewayUrl);
            return new LauncherServices.JiuwenClawProcesses(
                    new FakeProcess("claw", events),
                    new FakeProcess("web", events)
            );
        }

        @Override
        public String printLaunchSummary(
                OnlineRLConfig cfg,
                Path cfgPath,
                LauncherServices.LaunchRuntime runtime,
                boolean webStarted
        ) {
            events.add("summary:" + webStarted);
            return "summary";
        }
    }

    private static final class FakeProcess extends Process {
        private final String name;
        private final List<String> events;
        private boolean alive = true;

        private FakeProcess(String name, List<String> events) {
            this.name = name;
            this.events = events;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            alive = false;
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            alive = false;
            return true;
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException("process still alive");
            }
            return 0;
        }

        @Override
        public void destroy() {
            events.add("terminate:" + name);
            alive = false;
        }

        @Override
        public Process destroyForcibly() {
            events.add("kill:" + name);
            alive = false;
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public long pid() {
            return Math.abs(name.hashCode());
        }
    }
}
