/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicesHelperTest {

    @AfterEach
    void clearPythonExecutableOverride() {
        System.clearProperty("python.executable");
    }

    @Test
    void resolveLaunchRuntimeMirrorsPythonReuseAndPortRulesForFullConfig() {
        com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig cfg =
                new com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig();
        cfg.getInference().setModelPath("/models/base");
        cfg.getInference().setModelName("model-a");
        cfg.getInference().setHost("0.0.0.0");
        cfg.getInference().setPort(18000);
        cfg.getJudge().setReuseInferenceIfSameModel(true);
        cfg.getJudge().setPort(18001);
        cfg.getGateway().setHost("::");
        cfg.getGateway().setPort(18080);
        cfg.getGateway().setRedisUrl("redis://127.0.0.1:6379/0");
        cfg.getTraining().setLoraRepo("");
        cfg.getJiuwen().setEnabled(false);

        LaunchRuntime runtime = ServicesHelper.resolveLaunchRuntime(cfg, Path.of("/opt/scripts"));

        assertEquals("http://127.0.0.1:18000", runtime.inferenceUrl());
        assertEquals(runtime.inferenceUrl(), runtime.judgeUrl());
        assertEquals("http://127.0.0.1:18080", runtime.gatewayBaseUrl());
        assertEquals(Path.of("/opt/scripts").resolve("lora_repo").toString(), runtime.loraRepo());
        assertTrue(runtime.skipJudge());
        assertTrue(runtime.reuseInferenceForJudge());
        assertEquals(List.of(
                new PortCheck("Gateway", "::", 18080),
                new PortCheck("vLLM-Inference", "0.0.0.0", 18000)
        ), runtime.portsToCheck());
    }

    @Test
    void buildsVllmCommandAndEnvironmentFromFullConfig() {
        System.setProperty("python.executable", "python-test");
        com.openjiuwen.agent_evolving.agent_rl.config.VLLMServiceConfig cfg =
                new com.openjiuwen.agent_evolving.agent_rl.config.VLLMServiceConfig();
        cfg.setModelPath("/models/base");
        cfg.setModelName("");
        cfg.setHost("0.0.0.0");
        cfg.setPort(19000);
        cfg.setGpuIds("0,2");
        cfg.setTp(2);
        cfg.setEnv(Map.of("CUSTOM_ENV", "1", "VLLM_ALLOW_RUNTIME_LORA_UPDATING", "0"));
        cfg.setExtraArgs(List.of("--max-model-len", "4096"));

        List<String> command = ServicesHelper.buildVllmCommand(cfg);
        Map<String, String> env = ServicesHelper.buildVllmEnvironment(cfg, true);

        assertEquals(List.of(
                "python-test",
                "-m",
                "vllm.entrypoints.openai.api_server",
                "--model",
                "/models/base",
                "--served-model-name",
                "/models/base",
                "--port",
                "19000",
                "--host",
                "0.0.0.0",
                "--tensor-parallel-size",
                "2",
                "--max-model-len",
                "4096"
        ), command);
        assertEquals("0,2", env.get("CUDA_VISIBLE_DEVICES"));
        assertEquals("1", env.get("CUSTOM_ENV"));
        assertEquals("0", env.get("VLLM_ALLOW_RUNTIME_LORA_UPDATING"));
    }

    @Test
    void buildsGatewayCommandAndEnvironmentWithConfigOverridesLast() {
        System.setProperty("python.executable", "python-test");
        com.openjiuwen.agent_evolving.agent_rl.config.GatewayServiceConfig cfg =
                new com.openjiuwen.agent_evolving.agent_rl.config.GatewayServiceConfig();
        cfg.setHost("0.0.0.0");
        cfg.setPort(18080);
        cfg.setRedisUrl("redis://127.0.0.1:6379/0");
        cfg.setRecordDir("records");
        cfg.setLogLevel("debug");
        cfg.setDisableTrajectoryCollection(true);
        cfg.setEnv(Map.of("JUDGE_MODEL", "env-judge", "EXTRA", "yes"));

        Map<String, String> env = ServicesHelper.buildGatewayEnvironment(
                "http://llm",
                "http://judge",
                "judge-a",
                "model-a",
                "/models/base",
                "/tmp/lora",
                cfg
        );
        List<String> command = ServicesHelper.buildGatewayCommand(cfg);

        assertEquals("http://llm", env.get("LLM_URL"));
        assertEquals("http://judge", env.get("JUDGE_URL"));
        assertEquals("env-judge", env.get("JUDGE_MODEL"));
        assertEquals("model-a", env.get("MODEL_ID"));
        assertEquals("/models/base", env.get("MODEL_PATH"));
        assertEquals("0.0.0.0", env.get("GATEWAY_HOST"));
        assertEquals("18080", env.get("GATEWAY_PORT"));
        assertEquals("records", env.get("RECORD_DIR"));
        assertEquals("redis://127.0.0.1:6379/0", env.get("REDIS_URL"));
        assertEquals("/tmp/lora", env.get("LORA_REPO_ROOT"));
        assertEquals("true", env.get("DISABLE_GATEWAY_TRAJECTORY_COLLECTION"));
        assertEquals("yes", env.get("EXTRA"));
        assertEquals(List.of(
                "python-test",
                "-m",
                "uvicorn",
                ServicesHelper.DEFAULT_GATEWAY_APP_FACTORY,
                "--factory",
                "--host",
                "0.0.0.0",
                "--port",
                "18080",
                "--log-level",
                "debug"
        ), command);
    }

    @Test
    void buildsJiuwenClawEnvironmentAndWebCommand() {
        System.setProperty("python.executable", "python-test");

        Map<String, String> env = ServicesHelper.buildJiuwenclawEnvironment(
                Path.of("/jiuwenclaw"),
                Path.of("/workspace"),
                "http://gateway",
                "/models/base",
                "feedback_level",
                4,
                "0.0.0.0",
                19000,
                Map.of("WEB_USER_ID", " alice ")
        );
        List<String> webCommand = ServicesHelper.buildJiuwenclawWebCommand(
                "127.0.0.1",
                5173,
                Path.of("/workspace/web/dist"),
                "0.0.0.0",
                19000
        );

        assertEquals("alice", env.get("WEB_USER_ID"));
        assertEquals("{\"x-user-id\":\"alice\"}", env.get("CUSTOM_HEADERS"));
        assertEquals("1", env.get("USE_RL_ONLINE_RAIL"));
        assertEquals("false", env.get("ENABLE_TRAJECTORY_COLLECTION"));
        assertEquals("http://gateway", env.get("TRAJECTORY_GATEWAY_URL"));
        assertEquals("/models/base", env.get("TRAJECTORY_TOKENIZER_PATH"));
        assertEquals("4", env.get("TRAJECTORY_BATCH_SIZE"));
        assertEquals("feedback_level", env.get("TRAJECTORY_MODE"));
        assertEquals("alice", env.get("RL_ONLINE_TENANT_ID"));
        assertEquals("0.0.0.0", env.get("WEB_HOST"));
        assertEquals("19000", env.get("WEB_PORT"));
        assertEquals(List.of(
                "python-test",
                "-m",
                "jiuwenclaw.app_web",
                "--host",
                "127.0.0.1",
                "--port",
                "5173",
                "--dist",
                Path.of("/workspace/web/dist").toString(),
                "--proxy-target",
                "http://127.0.0.1:19000"
        ), webCommand);
    }

    @Test
    void summaryAndSchedulerHandleExposePythonLauncherValues() {
        LauncherOnlineRlConfig cfg = sampleConfig();
        LaunchRuntime runtime = ServicesHelper.resolveLaunchRuntime(cfg, Path.of("/opt/scripts"));

        String summary = ServicesHelper.buildLaunchSummary(cfg, Path.of("cfg.yaml"), runtime, true);
        ServicesHelper.SchedulerLaunchHandle handle = ServicesHelper.startOnlineTrainingScheduler(cfg, runtime);

        assertTrue(summary.contains("Web frontend:    http://127.0.0.1:5173"));
        assertTrue(summary.contains("Redis store:     redis://127.0.0.1:6379/0"));
        assertTrue(summary.contains("Trajectory mode: feedback_level"));
        assertTrue(summary.contains("Train threshold: 4 samples"));
        assertFalse(summary.contains("PLACE" + "HOLDER"));
        assertEquals("redis://127.0.0.1:6379/0", handle.getScheduler().getRedisUrl());
        assertEquals(30.0, handle.getScheduler().getPollInterval());
        assertEquals(4, handle.getScheduler().getMinSamplesForTraining());
        assertEquals("/tmp/model", handle.getScheduler().getBaseModelPath());
        assertEquals(2, handle.getScheduler().getNprocPerNode());
        assertEquals("4,5", handle.getScheduler().getTrainingGpuIds());
        assertEquals(Path.of("/opt/scripts").resolve("lora_repo").toString(), handle.getLoraRepo());
        assertEquals(runtime.inferenceUrl(), handle.getInferenceUrl());
        handle.stop();
        assertTrue(handle.isStopped());
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
