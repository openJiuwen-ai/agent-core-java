/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Process launch helpers and runtime resolution for the online RL loop.
 * <p>
 * Mirrors Python's deterministic helpers in
 * {@code openjiuwen.agent_evolving.agent_rl.online.launcher.services}.
 */
public final class LauncherServices {

    private static final Logger LOG = LoggerFactory.getLogger("online_rl");
    public static final String DEFAULT_GATEWAY_APP_FACTORY = "openjiuwen.agent_evolving.agent_rl.online.gateway.app.proxy:create_app";
    public static final double EXISTING_SERVICE_HEALTH_TIMEOUT = 30.0;

    private LauncherServices() {
    }

    public static LaunchRuntime resolveLaunchRuntime(LauncherOnlineRlConfig cfg, Path scriptDir) {
        String loraRepo = !blank(cfg.training().loraRepo()) ? cfg.training().loraRepo() : scriptDir.resolve("lora_repo").toString();
        boolean skipVllm = !blank(cfg.inference().existingUrl());
        String inferenceUrl = !blank(cfg.inference().existingUrl())
                ? cfg.inference().existingUrl()
                : "http://%s:%d".formatted(urlHost(cfg.inference().host()), cfg.inference().port());
        String gatewayBaseUrl = "http://%s:%d".formatted(urlHost(cfg.gateway().host()), cfg.gateway().port());
        String gatewayApiUrl = gatewayBaseUrl + "/v1";

        boolean reuseInferenceForJudge = false;
        String judgeUrl;
        boolean skipJudge;
        if (!blank(cfg.judge().existingUrl())) {
            judgeUrl = cfg.judge().existingUrl();
            skipJudge = true;
        } else if (cfg.judge().reuseInferenceIfSameModel() && cfg.judge().modelName().equals(cfg.inference().modelName())) {
            judgeUrl = inferenceUrl;
            skipJudge = true;
            reuseInferenceForJudge = true;
        } else {
            judgeUrl = "http://%s:%d".formatted(urlHost(cfg.judge().host()), cfg.judge().port());
            skipJudge = false;
        }

        String judgeLabel = judgeUrl.equals(inferenceUrl) ? "reuse inference" : cfg.judge().modelName();

        List<PortCheck> portsToCheck = new ArrayList<>();
        portsToCheck.add(new PortCheck("Gateway", cfg.gateway().host(), cfg.gateway().port()));
        if (!skipVllm) {
            portsToCheck.add(new PortCheck("vLLM-Inference", cfg.inference().host(), cfg.inference().port()));
        }
        if (!skipJudge) {
            portsToCheck.add(new PortCheck("vLLM-Judge", cfg.judge().host(), cfg.judge().port()));
        }
        if (cfg.jiuwen().enabled()) {
            portsToCheck.add(new PortCheck("JiuwenClaw-AgentServer", cfg.jiuwen().appHost(), cfg.jiuwen().agentServerPort()));
            portsToCheck.add(new PortCheck("JiuwenClaw-WS", cfg.jiuwen().appHost(), cfg.jiuwen().wsPort()));
            portsToCheck.add(new PortCheck("JiuwenClaw-Web", cfg.jiuwen().webHost(), cfg.jiuwen().webPort()));
        }

        return new LaunchRuntime(inferenceUrl, judgeUrl, gatewayBaseUrl, gatewayApiUrl, loraRepo, skipVllm, skipJudge,
                reuseInferenceForJudge, judgeLabel, List.copyOf(portsToCheck));
    }

    public static String urlHost(String host) {
        return "0.0.0.0".equals(host) || "::".equals(host) ? "127.0.0.1" : host;
    }

    public static String printLaunchSummary(LauncherOnlineRlConfig cfg, Path cfgPath, LaunchRuntime runtime, boolean webStarted) {
        List<String> lines = new ArrayList<>();
        lines.add("=".repeat(60));
        lines.add("  JiuwenClaw online RL loop started (v2: per-turn + Judge)");
        lines.add("");
        lines.add("  Config file:      " + cfgPath);
        if (cfg.jiuwen().enabled()) {
            String wsDisplayHost = urlHost(cfg.jiuwen().appHost());
            if (webStarted) {
                String webDisplayHost = urlHost(cfg.jiuwen().webHost());
                lines.add("  Web frontend:    http://" + webDisplayHost + ":" + cfg.jiuwen().webPort());
            }
            lines.add("  JiuwenClaw WS:   ws://" + wsDisplayHost + ":" + cfg.jiuwen().wsPort() + "/ws");
        } else {
            lines.add("  JiuwenClaw:      skipped (jiuwen.enabled=false)");
        }
        lines.add("  vLLM Inference:  " + runtime.inferenceUrl());
        lines.add("  vLLM Judge:      " + runtime.judgeUrl() + " (" + runtime.judgeLabel() + ")");
        lines.add("  Gateway proxy:   " + runtime.gatewayBaseUrl());
        lines.add("  Redis store:     " + cfg.gateway().redisUrl());
        lines.add("  Trajectory mode: " + cfg.trajectory().mode());
        lines.add("  Trajectory log:  " + cfg.gateway().recordDir() + "/ (JSONL, per-turn)");
        lines.add("  LoRA repo:       " + runtime.loraRepo());
        lines.add("  Train threshold: " + cfg.training().threshold() + " samples");
        lines.add("  Collect batch:   " + cfg.trajectory().batchSize());
        lines.add("  Scan interval:   " + cfg.training().scanInterval() + "s");
        lines.add("  Training mode:   PPO (Ray)");
        lines.add("  Train GPUs:      [" + cfg.training().gpuIds() + "]");
        lines.add("");
        if (cfg.jiuwen().enabled()) {
            String wsDisplayHost = urlHost(cfg.jiuwen().appHost());
            if (webStarted) {
                String webDisplayHost = urlHost(cfg.jiuwen().webHost());
                lines.add("  Open http://" + webDisplayHost + ":" + cfg.jiuwen().webPort() + " to start chatting,");
            } else {
                lines.add("  Chat via WebSocket (ws://" + wsDisplayHost + ":" + cfg.jiuwen().wsPort() + "/ws),");
            }
        }
        lines.add("  Each turn auto-records token_ids + logprobs,");
        lines.add("  next turn triggers delayed Judge scoring,");
        lines.add("  when pending trajectories reach threshold, PPO LoRA training auto-triggers.");
        lines.add("  Press Ctrl+C to stop all services.");
        lines.add("=".repeat(60));
        String summary = String.join("\n", lines);
        LOG.info("\n{}", summary);
        return summary;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
