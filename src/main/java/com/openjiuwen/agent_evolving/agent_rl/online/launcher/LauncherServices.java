/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.agent_rl.config.GatewayServiceConfig;
import com.openjiuwen.agent_evolving.agent_rl.config.JudgeConfig;
import com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig;
import com.openjiuwen.agent_evolving.agent_rl.config.VLLMServiceConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.inference.InferenceNotifier;
import com.openjiuwen.agent_evolving.agent_rl.online.scheduler.OnlineTrainingScheduler;
import com.openjiuwen.agent_evolving.agent_rl.storage.LoRARepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Process launch helpers and runtime resolution for the online RL loop.
 *
 * <p>Mirrors Python's module
 * {@code openjiuwen/agent_evolving/agent_rl/online/launcher/services.py}.</p>
 */
public final class LauncherServices {

    public static final String DEFAULT_GATEWAY_APP_FACTORY =
            "openjiuwen.agent_evolving.agent_rl.online.gateway.app.proxy:create_app";
    public static final double EXISTING_SERVICE_HEALTH_TIMEOUT = 30.0d;

    private static final Logger LOGGER = Logger.getLogger("online_rl");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private LauncherServices() {
    }

    public record PortCheck(String label, String host, int port) {
    }

    public record LaunchRuntime(
            String inferenceUrl,
            String judgeUrl,
            String gatewayBaseUrl,
            String gatewayApiUrl,
            String loraRepo,
            boolean skipVllm,
            boolean skipJudge,
            boolean reuseInferenceForJudge,
            String judgeLabel,
            List<PortCheck> portsToCheck
    ) {
        public LaunchRuntime {
            portsToCheck = List.copyOf(portsToCheck == null ? List.of() : portsToCheck);
        }
    }

    public record ProcessLaunchSpec(List<String> command,
                                    Map<String, String> environment,
                                    Path cwd,
                                    Path logPath) {
        public ProcessLaunchSpec {
            command = List.copyOf(command == null ? List.of() : command);
            environment = Map.copyOf(environment == null ? Map.of() : environment);
        }
    }

    public record GatewayStartOptions(String inferenceUrl,
                                      String judgeUrl,
                                      String judgeModel,
                                      String modelId,
                                      String modelPath,
                                      String loraRepoRoot,
                                      GatewayServiceConfig gatewayConfig,
                                      Path agentCoreRoot,
                                      Path logPath) {
    }

    public record JiuwenClawStartOptions(Path jiuwenclawRepo,
                                         Path workspaceRoot,
                                         String trajectoryGatewayUrl,
                                         String modelPath,
                                         String trajectoryMode,
                                         int trajectoryBatchSize,
                                         String appHost,
                                         int wsPort,
                                         String webHost,
                                         int webPort) {
    }

    public record JiuwenClawProcesses(Process appProcess, Process webProcess) {
    }

    public static LaunchRuntime resolveLaunchRuntime(OnlineRLConfig cfg, Path scriptDir) {
        Objects.requireNonNull(cfg, "cfg must not be null");
        Path safeScriptDir = scriptDir != null ? scriptDir : Path.of(".");
        String loraRepo = hasText(cfg.getTraining().getLoraRepo())
                ? cfg.getTraining().getLoraRepo()
                : safeScriptDir.resolve("lora_repo").toString();

        boolean skipVllm = cfg.getInference().getExistingUrl() != null;
        String inferenceUrl = skipVllm
                ? cfg.getInference().getExistingUrl()
                : "http://" + urlHost(cfg.getInference().getHost()) + ":" + cfg.getInference().getPort();
        String gatewayBaseUrl = "http://" + urlHost(cfg.getGateway().getHost()) + ":" + cfg.getGateway().getPort();
        String gatewayApiUrl = gatewayBaseUrl + "/v1";

        JudgeConfig judge = cfg.getJudge();
        boolean skipJudge;
        boolean reuseInferenceForJudge = false;
        String judgeUrl;
        if (judge.getExistingUrl() != null) {
            judgeUrl = judge.getExistingUrl();
            skipJudge = true;
        } else if (judge.isReuseInferenceIfSameModel()
                && Objects.equals(judge.getModelName(), cfg.getInference().getModelName())) {
            judgeUrl = inferenceUrl;
            skipJudge = true;
            reuseInferenceForJudge = true;
        } else {
            judgeUrl = "http://" + urlHost(judge.getHost()) + ":" + judge.getPort();
            skipJudge = false;
        }
        String judgeLabel = Objects.equals(judgeUrl, inferenceUrl) ? "reuse inference" : judge.getModelName();

        List<PortCheck> portsToCheck = new ArrayList<>();
        portsToCheck.add(new PortCheck("Gateway", cfg.getGateway().getHost(), cfg.getGateway().getPort()));
        if (!skipVllm) {
            portsToCheck.add(new PortCheck("vLLM-Inference", cfg.getInference().getHost(), cfg.getInference().getPort()));
        }
        if (!skipJudge) {
            portsToCheck.add(new PortCheck("vLLM-Judge", judge.getHost(), judge.getPort()));
        }
        if (cfg.getJiuwen().isEnabled()) {
            portsToCheck.add(new PortCheck(
                    "JiuwenClaw-AgentServer",
                    cfg.getJiuwen().getAppHost(),
                    cfg.getJiuwen().getAgentServerPort()
            ));
            portsToCheck.add(new PortCheck("JiuwenClaw-WS", cfg.getJiuwen().getAppHost(), cfg.getJiuwen().getWsPort()));
            portsToCheck.add(new PortCheck("JiuwenClaw-Web", cfg.getJiuwen().getWebHost(), cfg.getJiuwen().getWebPort()));
        }

        return new LaunchRuntime(
                inferenceUrl,
                judgeUrl,
                gatewayBaseUrl,
                gatewayApiUrl,
                loraRepo,
                skipVllm,
                skipJudge,
                reuseInferenceForJudge,
                judgeLabel,
                portsToCheck
        );
    }

    public static String urlHost(String host) {
        return "0.0.0.0".equals(host) || "::".equals(host) ? "127.0.0.1" : host;
    }

    public static Process spawnProcess(ProcessLaunchSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        ProcessBuilder builder = new ProcessBuilder(spec.command());
        if (spec.cwd() != null) {
            builder.directory(spec.cwd().toFile());
        }
        builder.environment().clear();
        builder.environment().putAll(spec.environment());
        if (spec.logPath() != null) {
            try {
                Path parent = spec.logPath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to create log directory: " + spec.logPath(), exception);
            }
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(spec.logPath().toFile()));
        }
        try {
            return builder.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to spawn process: " + spec.command(), exception);
        }
    }

    public static ProcessLaunchSpec buildVllmServiceSpec(VLLMServiceConfig serviceCfg,
                                                         String serviceName,
                                                         boolean enableRuntimeLora,
                                                         Path logPath) {
        return buildVllmServiceSpec(serviceCfg, serviceName, enableRuntimeLora, logPath, System.getenv());
    }

    public static ProcessLaunchSpec buildVllmServiceSpec(VLLMServiceConfig serviceCfg,
                                                         String serviceName,
                                                         boolean enableRuntimeLora,
                                                         Path logPath,
                                                         Map<String, String> baseEnvironment) {
        Objects.requireNonNull(serviceCfg, "serviceCfg must not be null");
        Map<String, String> env = new LinkedHashMap<>(baseEnvironment == null ? Map.of() : baseEnvironment);
        env.putAll(serviceCfg.getEnv());
        env.put("CUDA_VISIBLE_DEVICES", serviceCfg.getGpuIds());
        if (enableRuntimeLora) {
            env.putIfAbsent("VLLM_ALLOW_RUNTIME_LORA_UPDATING", "1");
        }

        List<String> cmd = new ArrayList<>(List.of(
                pythonExecutable(),
                "-m",
                "vllm.entrypoints.openai.api_server",
                "--model",
                serviceCfg.getModelPath(),
                "--served-model-name",
                hasText(serviceCfg.getModelName()) ? serviceCfg.getModelName() : serviceCfg.getModelPath(),
                "--port",
                String.valueOf(serviceCfg.getPort()),
                "--host",
                serviceCfg.getHost(),
                "--tensor-parallel-size",
                String.valueOf(serviceCfg.getTp())
        ));
        cmd.addAll(serviceCfg.getExtraArgs());
        LOGGER.info(() -> "Starting " + serviceName + " vLLM (TP=" + serviceCfg.getTp()
                + ") on GPU [" + serviceCfg.getGpuIds() + "], host=" + serviceCfg.getHost()
                + ", port=" + serviceCfg.getPort());
        return new ProcessLaunchSpec(cmd, env, null, logPath);
    }

    public static Process startVllmService(VLLMServiceConfig serviceCfg,
                                           String stepLabel,
                                           String serviceName,
                                           boolean enableRuntimeLora,
                                           Path logPath) {
        LOGGER.info(() -> "[" + stepLabel + "] Starting " + serviceName + " vLLM ...");
        return spawnProcess(buildVllmServiceSpec(serviceCfg, serviceName, enableRuntimeLora, logPath));
    }

    public static ProcessLaunchSpec buildGatewaySpec(GatewayStartOptions options) {
        return buildGatewaySpec(options, System.getenv());
    }

    public static ProcessLaunchSpec buildGatewaySpec(GatewayStartOptions options, Map<String, String> baseEnvironment) {
        Objects.requireNonNull(options, "options must not be null");
        GatewayServiceConfig gatewayCfg = Objects.requireNonNull(options.gatewayConfig(), "gatewayConfig");
        Map<String, String> env = new LinkedHashMap<>(baseEnvironment == null ? Map.of() : baseEnvironment);
        env.put("LLM_URL", options.inferenceUrl());
        env.put("JUDGE_URL", options.judgeUrl());
        env.put("JUDGE_MODEL", options.judgeModel());
        env.put("MODEL_ID", options.modelId());
        env.put("MODEL_PATH", options.modelPath());
        env.put("GATEWAY_HOST", gatewayCfg.getHost());
        env.put("GATEWAY_PORT", String.valueOf(gatewayCfg.getPort()));
        env.put("RECORD_DIR", gatewayCfg.getRecordDir());
        env.put("REDIS_URL", gatewayCfg.getRedisUrl());
        if (hasText(options.loraRepoRoot())) {
            env.put("LORA_REPO_ROOT", options.loraRepoRoot());
        }
        if (gatewayCfg.isDisableTrajectoryCollection()) {
            env.put("DISABLE_GATEWAY_TRAJECTORY_COLLECTION", "true");
        }
        env.putAll(gatewayCfg.getEnv());

        List<String> cmd = List.of(
                pythonExecutable(),
                "-m",
                "uvicorn",
                DEFAULT_GATEWAY_APP_FACTORY,
                "--factory",
                "--host",
                gatewayCfg.getHost(),
                "--port",
                String.valueOf(gatewayCfg.getPort()),
                "--log-level",
                gatewayCfg.getLogLevel()
        );
        return new ProcessLaunchSpec(cmd, env, options.agentCoreRoot(), options.logPath());
    }

    public static Process startGateway(GatewayStartOptions options) {
        LOGGER.info(() -> "[2/5] Starting Gateway on "
                + options.gatewayConfig().getHost() + ":" + options.gatewayConfig().getPort() + " ...");
        return spawnProcess(buildGatewaySpec(options));
    }

    public static OnlineTrainingScheduler startOnlineTrainingScheduler(OnlineRLConfig cfg, LaunchRuntime runtime) {
        OnlineTrainingScheduler scheduler = new OnlineTrainingScheduler(
                buildOnlineTrainingSchedulerOptions(cfg, runtime)
        );
        scheduler.start();
        return scheduler;
    }

    public static OnlineTrainingScheduler.Options buildOnlineTrainingSchedulerOptions(OnlineRLConfig cfg,
                                                                                      LaunchRuntime runtime) {
        Objects.requireNonNull(cfg, "cfg must not be null");
        Objects.requireNonNull(runtime, "runtime must not be null");
        int trainGpuCount = (int) List.of(cfg.getTraining().getGpuIds().split(",")).stream()
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .count();
        if (trainGpuCount == 0) {
            trainGpuCount = 1;
        }
        return new OnlineTrainingScheduler.Options()
                .setRedisUrl(cfg.getGateway().getRedisUrl())
                .setPollInterval((double) cfg.getTraining().getScanInterval())
                .setMinSamplesForTraining(cfg.getTraining().getThreshold())
                .setBaseModelPath(cfg.getInference().getModelPath())
                .setLoraRepo(new LoRARepository(runtime.loraRepo()))
                .setNotifier(new InferenceNotifier(runtime.inferenceUrl()))
                .setNprocPerNode(trainGpuCount)
                .setTrainingGpuIds(cfg.getTraining().getGpuIds())
                .setPpoConfigPath(cfg.getTraining().getPpoConfig());
    }

    public static JiuwenClawProcesses startJiuwenClaw(JiuwenClawStartOptions options) {
        Objects.requireNonNull(options, "options must not be null");
        Map<String, String> env = new LinkedHashMap<>(System.getenv());
        String trajectoryTenantId = firstNonBlank(
                env.get("RL_ONLINE_TENANT_ID"),
                env.get("WEB_USER_ID"),
                "local-web-user"
        );
        env.put("WEB_USER_ID", trajectoryTenantId);
        env.put("CUSTOM_HEADERS", compactJson(Map.of("x-user-id", trajectoryTenantId)));
        env.putAll(LauncherWorkspace.buildTrajectoryEnvUpdates(
                options.trajectoryGatewayUrl(),
                options.modelPath(),
                options.trajectoryBatchSize(),
                options.trajectoryMode(),
                trajectoryTenantId
        ));
        env.put("WEB_HOST", options.appHost());
        env.put("WEB_PORT", String.valueOf(options.wsPort()));

        Process appProc = spawnProcess(new ProcessLaunchSpec(
                List.of(pythonExecutable(), "-m", "jiuwenclaw.app"),
                env,
                options.jiuwenclawRepo(),
                null
        ));

        Process webProc = null;
        Path distDir = options.jiuwenclawRepo().resolve("jiuwenclaw").resolve("web").resolve("dist");
        if (!Files.exists(distDir)) {
            distDir = options.workspaceRoot().resolve("web").resolve("dist");
        }
        if (Files.exists(distDir)) {
            List<String> webCmd = List.of(
                    pythonExecutable(),
                    "-m",
                    "jiuwenclaw.app_web",
                    "--host",
                    options.webHost(),
                    "--port",
                    String.valueOf(options.webPort()),
                    "--dist",
                    distDir.toString(),
                    "--proxy-target",
                    "http://" + urlHost(options.appHost()) + ":" + options.wsPort()
            );
            webProc = spawnProcess(new ProcessLaunchSpec(webCmd, env, options.jiuwenclawRepo(), null));
        } else {
            LOGGER.warning("Web dist not found, skipping frontend.");
        }
        return new JiuwenClawProcesses(appProc, webProc);
    }

    public static String printLaunchSummary(OnlineRLConfig cfg,
                                            Path cfgPath,
                                            LaunchRuntime runtime,
                                            boolean webStarted) {
        String summary = buildLaunchSummary(cfg, cfgPath, runtime, webStarted);
        LOGGER.info("\n" + summary);
        return summary;
    }

    public static String buildLaunchSummary(OnlineRLConfig cfg,
                                            Path cfgPath,
                                            LaunchRuntime runtime,
                                            boolean webStarted) {
        List<String> lines = new ArrayList<>();
        lines.add("============================================================");
        lines.add("  JiuwenClaw online RL loop started (v2: per-turn + Judge)");
        lines.add("");
        lines.add("  Config file:      " + cfgPath);
        if (cfg.getJiuwen().isEnabled()) {
            String wsDisplayHost = urlHost(cfg.getJiuwen().getAppHost());
            if (webStarted) {
                lines.add("  Web frontend:    http://" + urlHost(cfg.getJiuwen().getWebHost())
                        + ":" + cfg.getJiuwen().getWebPort());
            }
            lines.add("  JiuwenClaw WS:   ws://" + wsDisplayHost + ":" + cfg.getJiuwen().getWsPort() + "/ws");
        } else {
            lines.add("  JiuwenClaw:      skipped (jiuwen.enabled=false)");
        }
        lines.add("  vLLM Inference:  " + runtime.inferenceUrl());
        lines.add("  vLLM Judge:      " + runtime.judgeUrl() + " (" + runtime.judgeLabel() + ")");
        lines.add("  Gateway proxy:   " + runtime.gatewayBaseUrl());
        lines.add("  Redis store:     " + cfg.getGateway().getRedisUrl());
        lines.add("  Trajectory mode: " + cfg.getTrajectory().getMode());
        lines.add("  Trajectory log:  " + cfg.getGateway().getRecordDir() + "/ (JSONL, per-turn)");
        lines.add("  LoRA repo:       " + runtime.loraRepo());
        lines.add("  Train threshold: " + cfg.getTraining().getThreshold() + " samples");
        lines.add("  Collect batch:   " + cfg.getTrajectory().getBatchSize());
        lines.add("  Scan interval:   " + cfg.getTraining().getScanInterval() + "s");
        lines.add("  Training mode:   PPO (Ray)");
        lines.add("  Train GPUs:      [" + cfg.getTraining().getGpuIds() + "]");
        lines.add("");
        if (cfg.getJiuwen().isEnabled()) {
            if (webStarted) {
                lines.add("  Open http://" + urlHost(cfg.getJiuwen().getWebHost()) + ":"
                        + cfg.getJiuwen().getWebPort() + " to start chatting,");
            } else {
                lines.add("  Chat via WebSocket (ws://" + urlHost(cfg.getJiuwen().getAppHost()) + ":"
                        + cfg.getJiuwen().getWsPort() + "/ws),");
            }
        }
        lines.add("  Each turn auto-records token_ids + logprobs,");
        lines.add("  next turn triggers delayed Judge scoring,");
        lines.add("  when pending trajectories reach threshold, PPO LoRA training auto-triggers.");
        lines.add("  Press Ctrl+C to stop all services.");
        lines.add("============================================================");
        return String.join(System.lineSeparator(), lines);
    }

    private static String pythonExecutable() {
        return System.getProperty("python.executable", "python");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (hasText(first)) {
            return first.strip();
        }
        if (hasText(second)) {
            return second.strip();
        }
        return fallback;
    }

    private static String compactJson(Map<String, String> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize JSON", exception);
        }
    }
}
