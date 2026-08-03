/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import com.openjiuwen.agent_evolving.agent_rl.config.GatewayServiceConfig;
import com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig;
import com.openjiuwen.agent_evolving.agent_rl.config.VLLMServiceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's service helpers in
 * {@code openjiuwen/agent_evolving/agent_rl/online/launcher/services.py}.</p>
 */
class LauncherServicesTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveLaunchRuntimeReusesInferenceForJudgeAndBuildsPortChecks() {
        OnlineRLConfig cfg = validConfig();
        cfg.getInference().setHost("0.0.0.0");
        cfg.getInference().setPort(18000);
        cfg.getJudge().setReuseInferenceIfSameModel(true);
        cfg.getGateway().setHost("::");
        cfg.getGateway().setPort(19000);
        cfg.getJiuwen().setAgentServerPort(20001);
        cfg.getJiuwen().setWsPort(20002);
        cfg.getJiuwen().setWebPort(20003);

        LauncherServices.LaunchRuntime runtime = LauncherServices.resolveLaunchRuntime(cfg, tempDir);

        assertEquals("http://127.0.0.1:18000", runtime.inferenceUrl());
        assertEquals(runtime.inferenceUrl(), runtime.judgeUrl());
        assertEquals("http://127.0.0.1:19000", runtime.gatewayBaseUrl());
        assertEquals("http://127.0.0.1:19000/v1", runtime.gatewayApiUrl());
        assertEquals(tempDir.resolve("lora_repo").toString(), runtime.loraRepo());
        assertFalse(runtime.skipVllm());
        assertTrue(runtime.skipJudge());
        assertTrue(runtime.reuseInferenceForJudge());
        assertEquals("reuse inference", runtime.judgeLabel());
        assertEquals("Gateway", runtime.portsToCheck().get(0).label());
        assertTrue(runtime.portsToCheck().stream().anyMatch(item -> item.label().equals("vLLM-Inference")));
        assertTrue(runtime.portsToCheck().stream().noneMatch(item -> item.label().equals("vLLM-Judge")));
        assertTrue(runtime.portsToCheck().stream().anyMatch(item -> item.label().equals("JiuwenClaw-Web")));
    }

    @Test
    void buildVllmServiceSpecPreservesCommandAndEnvironment() {
        VLLMServiceConfig serviceCfg = new VLLMServiceConfig();
        serviceCfg.setModelPath("/models/base");
        serviceCfg.setModelName("served");
        serviceCfg.setHost("127.0.0.1");
        serviceCfg.setPort(18080);
        serviceCfg.setGpuIds("0,1");
        serviceCfg.setTp(2);
        serviceCfg.setEnv(Map.of("EXTRA", "1"));
        serviceCfg.setExtraArgs(java.util.List.of("--max-model-len", "8192"));

        LauncherServices.ProcessLaunchSpec spec = LauncherServices.buildVllmServiceSpec(
                serviceCfg,
                "Inference",
                true,
                tempDir.resolve("vllm.log"),
                Map.of("BASE", "x")
        );

        assertEquals("python", spec.command().get(0));
        assertTrue(spec.command().contains("vllm.entrypoints.openai.api_server"));
        assertTrue(spec.command().contains("--served-model-name"));
        assertTrue(spec.command().contains("served"));
        assertEquals("0,1", spec.environment().get("CUDA_VISIBLE_DEVICES"));
        assertEquals("1", spec.environment().get("VLLM_ALLOW_RUNTIME_LORA_UPDATING"));
        assertEquals("1", spec.environment().get("EXTRA"));
        assertEquals("x", spec.environment().get("BASE"));
    }

    @Test
    void buildGatewaySpecPreservesEnvironmentAndUvicornCommand() {
        OnlineRLConfig cfg = validConfig();
        GatewayServiceConfig gatewayCfg = cfg.getGateway();
        LauncherServices.GatewayStartOptions options = new LauncherServices.GatewayStartOptions(
                "http://infer",
                "http://judge",
                "judge-model",
                "model-id",
                "/models/base",
                tempDir.resolve("lora").toString(),
                gatewayCfg,
                tempDir,
                tempDir.resolve("gateway.log")
        );

        LauncherServices.ProcessLaunchSpec spec = LauncherServices.buildGatewaySpec(options, Map.of());

        assertTrue(spec.command().contains("uvicorn"));
        assertTrue(spec.command().contains(LauncherServices.DEFAULT_GATEWAY_APP_FACTORY));
        assertEquals("http://infer", spec.environment().get("LLM_URL"));
        assertEquals("http://judge", spec.environment().get("JUDGE_URL"));
        assertEquals("judge-model", spec.environment().get("JUDGE_MODEL"));
        assertEquals("model-id", spec.environment().get("MODEL_ID"));
        assertEquals("/models/base", spec.environment().get("MODEL_PATH"));
        assertEquals("true", spec.environment().get("DISABLE_GATEWAY_TRAJECTORY_COLLECTION"));
        assertEquals(tempDir, spec.cwd());
    }

    @Test
    void buildLaunchSummaryIncludesRuntimeValues() {
        OnlineRLConfig cfg = validConfig();
        cfg.getJiuwen().setEnabled(false);
        LauncherServices.LaunchRuntime runtime = LauncherServices.resolveLaunchRuntime(cfg, tempDir);

        String summary = LauncherServices.buildLaunchSummary(cfg, tempDir.resolve("online.yaml"), runtime, false);

        assertTrue(summary.contains("JiuwenClaw:      skipped"));
        assertTrue(summary.contains("Gateway proxy:   http://127.0.0.1:19000"));
        assertTrue(summary.contains("Redis store:     redis://localhost:6379/0"));
        assertTrue(summary.contains("Train GPUs:      [4,5]"));
    }

    private OnlineRLConfig validConfig() {
        OnlineRLConfig cfg = new OnlineRLConfig();
        cfg.getInference().setModelPath("/models/base");
        cfg.getInference().setModelName("base");
        cfg.getInference().setPort(18000);
        cfg.getJudge().setModelPath("/models/base");
        cfg.getJudge().setModelName("base");
        cfg.getJudge().setPort(18001);
        cfg.getGateway().setPort(19000);
        cfg.getGateway().setRedisUrl("redis://localhost:6379/0");
        cfg.getGateway().setRecordDir("records");
        cfg.getTraining().setGpuIds("4,5");
        cfg.getTraining().setThreshold(4);
        cfg.getTraining().setScanInterval(30);
        cfg.getJiuwen().setEnabled(true);
        cfg.getJiuwen().setAgentServerPort(20001);
        cfg.getJiuwen().setWsPort(20002);
        cfg.getJiuwen().setWebPort(20003);
        cfg.validate();
        return cfg;
    }
}
