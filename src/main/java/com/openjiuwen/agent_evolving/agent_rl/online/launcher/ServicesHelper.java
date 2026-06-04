// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import com.openjiuwen.agent_evolving.agent_rl.online.scheduler.OnlineTrainingScheduler;

import java.io.File;
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
 * <p>
 * Mirrors Python's {@code services.py} module in
 * {@code openjiuwen.agent_evolving.agent_rl.online.launcher.services}.
 */
public final class ServicesHelper {

    private static final Logger LOG = Logger.getLogger("online_rl");
    public static final String DEFAULT_GATEWAY_APP_FACTORY =
            "openjiuwen.agent_evolving.agent_rl.online.gateway.app.proxy:create_app";
    public static final double EXISTING_SERVICE_HEALTH_TIMEOUT = 30.0;

    private ServicesHelper() {
    }

    /**
     * Resolve service URLs, skip flags, and port checks from config.
     */
    public static LaunchRuntime resolveLaunchRuntime(Object cfg, Path scriptDir) {
        if (cfg instanceof LauncherOnlineRlConfig launcherCfg) {
            return resolveLaunchRuntime(launcherCfg, scriptDir);
        }
        if (cfg instanceof com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig onlineCfg) {
            return resolveLaunchRuntime(onlineCfg, scriptDir);
        }
        throw new IllegalArgumentException("Unsupported online RL config type: " + typeName(cfg));
    }

    public static LaunchRuntime resolveLaunchRuntime(LauncherOnlineRlConfig cfg, Path scriptDir) {
        String loraRepo = truthy(cfg.training().loraRepo())
                ? cfg.training().loraRepo()
                : scriptDir.resolve("lora_repo").toString();
        boolean skipVllm = cfg.inference().existingUrl() != null;
        String inferenceUrl = truthy(cfg.inference().existingUrl())
                ? cfg.inference().existingUrl()
                : "http://%s:%d".formatted(urlHost(cfg.inference().host()), cfg.inference().port());
        String gatewayBaseUrl = "http://%s:%d".formatted(urlHost(cfg.gateway().host()), cfg.gateway().port());
        String gatewayApiUrl = gatewayBaseUrl + "/v1";

        boolean reuseInferenceForJudge = false;
        String judgeUrl;
        boolean skipJudge;
        if (truthy(cfg.judge().existingUrl())) {
            judgeUrl = cfg.judge().existingUrl();
            skipJudge = true;
        } else if (cfg.judge().reuseInferenceIfSameModel()
                && Objects.equals(cfg.judge().modelName(), cfg.inference().modelName())) {
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

        return new LaunchRuntime(inferenceUrl, judgeUrl, gatewayBaseUrl, gatewayApiUrl, loraRepo,
                skipVllm, skipJudge, reuseInferenceForJudge, judgeLabel, List.copyOf(portsToCheck));
    }

    public static LaunchRuntime resolveLaunchRuntime(com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig cfg,
                                                     Path scriptDir) {
        cfg.validate();
        String loraRepo = truthy(cfg.getTraining().getLoraRepo())
                ? cfg.getTraining().getLoraRepo()
                : scriptDir.resolve("lora_repo").toString();
        boolean skipVllm = cfg.getInference().getExistingUrl() != null;
        String inferenceUrl = truthy(cfg.getInference().getExistingUrl())
                ? cfg.getInference().getExistingUrl()
                : "http://%s:%d".formatted(urlHost(cfg.getInference().getHost()), requirePort(cfg.getInference().getPort(), "inference.port"));
        String gatewayBaseUrl = "http://%s:%d".formatted(urlHost(cfg.getGateway().getHost()), requirePort(cfg.getGateway().getPort(), "gateway.port"));
        String gatewayApiUrl = gatewayBaseUrl + "/v1";

        boolean reuseInferenceForJudge = false;
        String judgeUrl;
        boolean skipJudge;
        if (truthy(cfg.getJudge().getExistingUrl())) {
            judgeUrl = cfg.getJudge().getExistingUrl();
            skipJudge = true;
        } else if (cfg.getJudge().isReuseInferenceIfSameModel()
                && Objects.equals(cfg.getJudge().getModelName(), cfg.getInference().getModelName())) {
            judgeUrl = inferenceUrl;
            skipJudge = true;
            reuseInferenceForJudge = true;
        } else {
            judgeUrl = "http://%s:%d".formatted(urlHost(cfg.getJudge().getHost()), requirePort(cfg.getJudge().getPort(), "judge.port"));
            skipJudge = false;
        }

        String judgeLabel = judgeUrl.equals(inferenceUrl) ? "reuse inference" : cfg.getJudge().getModelName();
        List<PortCheck> portsToCheck = new ArrayList<>();
        portsToCheck.add(new PortCheck("Gateway", cfg.getGateway().getHost(), requirePort(cfg.getGateway().getPort(), "gateway.port")));
        if (!skipVllm) {
            portsToCheck.add(new PortCheck("vLLM-Inference", cfg.getInference().getHost(), requirePort(cfg.getInference().getPort(), "inference.port")));
        }
        if (!skipJudge) {
            portsToCheck.add(new PortCheck("vLLM-Judge", cfg.getJudge().getHost(), requirePort(cfg.getJudge().getPort(), "judge.port")));
        }
        if (cfg.getJiuwen().isEnabled()) {
            portsToCheck.add(new PortCheck("JiuwenClaw-AgentServer", cfg.getJiuwen().getAppHost(),
                    requirePort(cfg.getJiuwen().getAgentServerPort(), "jiuwen.agentServerPort")));
            portsToCheck.add(new PortCheck("JiuwenClaw-WS", cfg.getJiuwen().getAppHost(),
                    requirePort(cfg.getJiuwen().getWsPort(), "jiuwen.wsPort")));
            portsToCheck.add(new PortCheck("JiuwenClaw-Web", cfg.getJiuwen().getWebHost(),
                    requirePort(cfg.getJiuwen().getWebPort(), "jiuwen.webPort")));
        }

        return new LaunchRuntime(inferenceUrl, judgeUrl, gatewayBaseUrl, gatewayApiUrl, loraRepo,
                skipVllm, skipJudge, reuseInferenceForJudge, judgeLabel, List.copyOf(portsToCheck));
    }

    /**
     * Normalize host address: convert 0.0.0.0 or :: to 127.0.0.1.
     */
    public static String urlHost(String host) {
        return "0.0.0.0".equals(host) || "::".equals(host) ? "127.0.0.1" : host;
    }

    /**
     * Spawn child process and stream stdout/stderr to a file when requested.
     */
    public static Process spawnProcess(List<String> cmd, Map<String, String> env, String cwd, Path logPath)
            throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (cwd != null) {
            pb.directory(new File(cwd));
        }
        if (env != null) {
            pb.environment().putAll(env);
        }
        if (logPath != null) {
            if (logPath.getParent() != null) {
                Files.createDirectories(logPath.getParent());
            }
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
            pb.redirectErrorStream(true);
        } else {
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        }
        return pb.start();
    }

    /**
     * Start a vLLM OpenAI API server from config.
     */
    public static Process startVllmService(Object serviceCfg,
                                           String stepLabel,
                                           String serviceName,
                                           boolean enableRuntimeLora,
                                           Path logPath) throws IOException {
        VllmDetails details = vllmDetails(serviceCfg);
        Map<String, String> env = buildVllmEnvironment(serviceCfg, enableRuntimeLora);
        List<String> cmd = buildVllmCommand(serviceCfg);

        LOG.info(String.format("[%s] Starting %s vLLM (TP=%d) on GPU [%s], host=%s, port=%d ...",
                stepLabel, serviceName, details.tp(), details.gpuIds(), details.host(), details.port()));
        return spawnProcess(cmd, env, null, logPath);
    }

    public static Map<String, String> buildVllmEnvironment(Object serviceCfg, boolean enableRuntimeLora) {
        VllmDetails details = vllmDetails(serviceCfg);
        Map<String, String> env = new LinkedHashMap<>(System.getenv());
        env.putAll(details.env());
        env.put("CUDA_VISIBLE_DEVICES", details.gpuIds());
        if (enableRuntimeLora) {
            env.putIfAbsent("VLLM_ALLOW_RUNTIME_LORA_UPDATING", "1");
        }
        return env;
    }

    public static List<String> buildVllmCommand(Object serviceCfg) {
        VllmDetails details = vllmDetails(serviceCfg);
        String servedModelName = truthy(details.modelName()) ? details.modelName() : details.modelPath();
        List<String> cmd = new ArrayList<>();
        cmd.add(pythonExecutable());
        cmd.add("-m");
        cmd.add("vllm.entrypoints.openai.api_server");
        cmd.add("--model");
        cmd.add(details.modelPath());
        cmd.add("--served-model-name");
        cmd.add(servedModelName);
        cmd.add("--port");
        cmd.add(String.valueOf(details.port()));
        cmd.add("--host");
        cmd.add(details.host());
        cmd.add("--tensor-parallel-size");
        cmd.add(String.valueOf(details.tp()));
        cmd.addAll(details.extraArgs());
        return List.copyOf(cmd);
    }

    /**
     * Start agent-core gateway.
     */
    public static Process startGateway(String inferenceUrl,
                                       String judgeUrl,
                                       String judgeModel,
                                       String modelId,
                                       String modelPath,
                                       String loraRepoRoot,
                                       Object gatewayCfg,
                                       Path agentCoreRoot,
                                       Path logPath) throws IOException {
        GatewayDetails details = gatewayDetails(gatewayCfg);
        Map<String, String> env = buildGatewayEnvironment(
                inferenceUrl, judgeUrl, judgeModel, modelId, modelPath, loraRepoRoot, gatewayCfg);
        List<String> cmd = buildGatewayCommand(gatewayCfg);

        LOG.info(String.format("[2/5] Starting Gateway on %s:%d ...", details.host(), details.port()));
        return spawnProcess(cmd, env, agentCoreRoot != null ? agentCoreRoot.toString() : null, logPath);
    }

    public static Map<String, String> buildGatewayEnvironment(String inferenceUrl,
                                                              String judgeUrl,
                                                              String judgeModel,
                                                              String modelId,
                                                              String modelPath,
                                                              String loraRepoRoot,
                                                              Object gatewayCfg) {
        GatewayDetails details = gatewayDetails(gatewayCfg);
        Map<String, String> env = new LinkedHashMap<>(System.getenv());
        env.put("LLM_URL", inferenceUrl);
        env.put("JUDGE_URL", judgeUrl);
        env.put("JUDGE_MODEL", judgeModel);
        env.put("MODEL_ID", modelId);
        env.put("MODEL_PATH", modelPath);
        env.put("GATEWAY_HOST", details.host());
        env.put("GATEWAY_PORT", String.valueOf(details.port()));
        env.put("RECORD_DIR", details.recordDir());
        env.put("REDIS_URL", details.redisUrl());
        if (truthy(loraRepoRoot)) {
            env.put("LORA_REPO_ROOT", loraRepoRoot);
        }
        if (details.disableTrajectoryCollection()) {
            env.put("DISABLE_GATEWAY_TRAJECTORY_COLLECTION", "true");
        }
        env.putAll(details.env());
        return env;
    }

    public static List<String> buildGatewayCommand(Object gatewayCfg) {
        GatewayDetails details = gatewayDetails(gatewayCfg);
        return List.of(
                pythonExecutable(),
                "-m",
                "uvicorn",
                DEFAULT_GATEWAY_APP_FACTORY,
                "--factory",
                "--host",
                details.host(),
                "--port",
                String.valueOf(details.port()),
                "--log-level",
                details.logLevel()
        );
    }

    /**
     * Configure the online training scheduler used by the launcher.
     */
    public static SchedulerLaunchHandle startOnlineTrainingScheduler(Object cfg, LaunchRuntime runtime) {
        if (cfg instanceof LauncherOnlineRlConfig launcherCfg) {
            return startOnlineTrainingScheduler(launcherCfg, runtime);
        }
        if (cfg instanceof com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig onlineCfg) {
            return startOnlineTrainingScheduler(onlineCfg, runtime);
        }
        throw new IllegalArgumentException("Unsupported online RL config type: " + typeName(cfg));
    }

    public static SchedulerLaunchHandle startOnlineTrainingScheduler(LauncherOnlineRlConfig cfg, LaunchRuntime runtime) {
        OnlineTrainingScheduler scheduler = new OnlineTrainingScheduler(
                cfg.gateway().redisUrl(),
                (double) cfg.training().scanInterval(),
                cfg.training().threshold(),
                cfg.inference().modelPath(),
                trainGpuCount(cfg.training().gpuIds()),
                cfg.training().gpuIds(),
                "/tmp/agent_rl_online",
                cfg.training().ppoConfig()
        );
        return new SchedulerLaunchHandle(scheduler, runtime.loraRepo(), runtime.inferenceUrl());
    }

    public static SchedulerLaunchHandle startOnlineTrainingScheduler(
            com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig cfg,
            LaunchRuntime runtime) {
        cfg.validate();
        OnlineTrainingScheduler scheduler = new OnlineTrainingScheduler(
                cfg.getGateway().getRedisUrl(),
                (double) cfg.getTraining().getScanInterval(),
                cfg.getTraining().getThreshold(),
                cfg.getInference().getModelPath(),
                trainGpuCount(cfg.getTraining().getGpuIds()),
                cfg.getTraining().getGpuIds(),
                "/tmp/agent_rl_online",
                cfg.getTraining().getPpoConfig()
        );
        return new SchedulerLaunchHandle(scheduler, runtime.loraRepo(), runtime.inferenceUrl());
    }

    /**
     * Start JiuwenClaw app and web frontend if a built web dist exists.
     */
    public static JiuwenClawProcesses startJiuwenclaw(Path jiuwenclawRepo,
                                                      Path workspaceRoot,
                                                      String trajectoryGatewayUrl,
                                                      String modelPath,
                                                      String trajectoryMode,
                                                      int trajectoryBatchSize,
                                                      String appHost,
                                                      int wsPort,
                                                      String webHost,
                                                      int webPort) throws IOException {
        Map<String, String> env = buildJiuwenclawEnvironment(
                jiuwenclawRepo,
                workspaceRoot,
                trajectoryGatewayUrl,
                modelPath,
                trajectoryMode,
                trajectoryBatchSize,
                appHost,
                wsPort,
                System.getenv()
        );

        List<String> cmd = buildJiuwenclawCommand();
        LOG.info("[4/5] Starting JiuwenClaw app ...");
        LOG.info(String.format("  JiuwenClaw env: gateway=%s, model=%s, batch_size=%d, mode=%s; ws=%s:%d",
                trajectoryGatewayUrl, modelPath, trajectoryBatchSize, trajectoryMode, appHost, wsPort));
        Process appProc = spawnProcess(cmd, env, jiuwenclawRepo.toString(), null);

        Process webProc = null;
        Path distDir = resolveWebDist(jiuwenclawRepo, workspaceRoot);
        if (Files.exists(distDir)) {
            List<String> webCmd = buildJiuwenclawWebCommand(webHost, webPort, distDir, appHost, wsPort);
            LOG.info(String.format("[5/5] Starting JiuwenClaw web frontend at http://%s:%d (dist=%s, ws_proxy=%s:%d) ...",
                    webHost, webPort, distDir, urlHost(appHost), wsPort));
            webProc = spawnProcess(webCmd, env, jiuwenclawRepo.toString(), null);
        } else {
            LOG.warning("[5/5] Web dist not found, skipping frontend. "
                    + "Build it: cd jiuwenclaw/jiuwenclaw/web && npm install && npm run build");
        }
        return new JiuwenClawProcesses(appProc, webProc);
    }

    public static Map<String, String> buildJiuwenclawEnvironment(Path jiuwenclawRepo,
                                                                 Path workspaceRoot,
                                                                 String trajectoryGatewayUrl,
                                                                 String modelPath,
                                                                 String trajectoryMode,
                                                                 int trajectoryBatchSize,
                                                                 String appHost,
                                                                 int wsPort,
                                                                 Map<String, String> baseEnv) {
        Map<String, String> env = new LinkedHashMap<>(baseEnv != null ? baseEnv : Map.of());
        String trajectoryTenantId = stripToEmpty(env.get("RL_ONLINE_TENANT_ID"));
        if (trajectoryTenantId.isEmpty()) {
            trajectoryTenantId = stripToEmpty(env.getOrDefault("WEB_USER_ID", "local-web-user"));
        }
        if (trajectoryTenantId.isEmpty()) {
            trajectoryTenantId = "local-web-user";
        }

        env.put("WEB_USER_ID", trajectoryTenantId);
        env.put("CUSTOM_HEADERS", "{\"x-user-id\":\"" + jsonEscape(trajectoryTenantId) + "\"}");
        env.putAll(LauncherWorkspace.buildTrajectoryEnvUpdates(
                trajectoryGatewayUrl,
                modelPath,
                trajectoryBatchSize,
                trajectoryMode,
                trajectoryTenantId
        ));
        env.put("WEB_HOST", appHost);
        env.put("WEB_PORT", String.valueOf(wsPort));
        return env;
    }

    public static List<String> buildJiuwenclawCommand() {
        return List.of(pythonExecutable(), "-m", "jiuwenclaw.app");
    }

    public static List<String> buildJiuwenclawWebCommand(String webHost,
                                                         int webPort,
                                                         Path distDir,
                                                         String appHost,
                                                         int wsPort) {
        return List.of(
                pythonExecutable(),
                "-m",
                "jiuwenclaw.app_web",
                "--host",
                webHost,
                "--port",
                String.valueOf(webPort),
                "--dist",
                distDir.toString(),
                "--proxy-target",
                "http://%s:%d".formatted(urlHost(appHost), wsPort)
        );
    }

    /**
     * Log runtime summary after successful startup.
     */
    public static void printLaunchSummary(Object cfg, Path cfgPath, LaunchRuntime runtime, boolean webStarted) {
        if (cfg instanceof LauncherOnlineRlConfig launcherCfg) {
            printLaunchSummary(launcherCfg, cfgPath, runtime, webStarted);
            return;
        }
        if (cfg instanceof com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig onlineCfg) {
            printLaunchSummary(onlineCfg, cfgPath, runtime, webStarted);
            return;
        }
        throw new IllegalArgumentException("Unsupported online RL config type: " + typeName(cfg));
    }

    public static String printLaunchSummary(LauncherOnlineRlConfig cfg, Path cfgPath, LaunchRuntime runtime, boolean webStarted) {
        String summary = buildLaunchSummary(cfg, cfgPath, runtime, webStarted);
        LOG.info("\n" + summary);
        return summary;
    }

    public static String printLaunchSummary(com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig cfg,
                                            Path cfgPath,
                                            LaunchRuntime runtime,
                                            boolean webStarted) {
        String summary = buildLaunchSummary(cfg, cfgPath, runtime, webStarted);
        LOG.info("\n" + summary);
        return summary;
    }

    public static String buildLaunchSummary(LauncherOnlineRlConfig cfg, Path cfgPath, LaunchRuntime runtime, boolean webStarted) {
        List<String> lines = new ArrayList<>();
        appendSummaryHeader(lines, cfgPath);
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
        appendSummaryRuntime(lines, runtime, cfg.gateway().redisUrl(), cfg.trajectory().mode(),
                cfg.gateway().recordDir(), cfg.training().threshold(), cfg.trajectory().batchSize(),
                cfg.training().scanInterval(), cfg.training().gpuIds());
        appendSummaryFooter(lines, cfg.jiuwen().enabled(), cfg.jiuwen().appHost(), cfg.jiuwen().wsPort(),
                cfg.jiuwen().webHost(), cfg.jiuwen().webPort(), webStarted);
        return String.join("\n", lines);
    }

    public static String buildLaunchSummary(com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig cfg,
                                            Path cfgPath,
                                            LaunchRuntime runtime,
                                            boolean webStarted) {
        cfg.validate();
        List<String> lines = new ArrayList<>();
        appendSummaryHeader(lines, cfgPath);
        if (cfg.getJiuwen().isEnabled()) {
            String wsDisplayHost = urlHost(cfg.getJiuwen().getAppHost());
            if (webStarted) {
                String webDisplayHost = urlHost(cfg.getJiuwen().getWebHost());
                lines.add("  Web frontend:    http://" + webDisplayHost + ":" + cfg.getJiuwen().getWebPort());
            }
            lines.add("  JiuwenClaw WS:   ws://" + wsDisplayHost + ":" + cfg.getJiuwen().getWsPort() + "/ws");
        } else {
            lines.add("  JiuwenClaw:      skipped (jiuwen.enabled=false)");
        }
        appendSummaryRuntime(lines, runtime, cfg.getGateway().getRedisUrl(), cfg.getTrajectory().getMode(),
                cfg.getGateway().getRecordDir(), cfg.getTraining().getThreshold(), cfg.getTrajectory().getBatchSize(),
                cfg.getTraining().getScanInterval(), cfg.getTraining().getGpuIds());
        appendSummaryFooter(lines, cfg.getJiuwen().isEnabled(), cfg.getJiuwen().getAppHost(), cfg.getJiuwen().getWsPort(),
                cfg.getJiuwen().getWebHost(), cfg.getJiuwen().getWebPort(), webStarted);
        return String.join("\n", lines);
    }

    public static String pythonExecutable() {
        String configured = System.getProperty("python.executable");
        if (truthy(configured)) {
            return configured;
        }
        String envPython = System.getenv("PYTHON");
        return truthy(envPython) ? envPython : "python";
    }

    private static void appendSummaryHeader(List<String> lines, Path cfgPath) {
        lines.add("=".repeat(60));
        lines.add("  JiuwenClaw online RL loop started (v2: per-turn + Judge)");
        lines.add("");
        lines.add("  Config file:      " + cfgPath);
    }

    private static void appendSummaryRuntime(List<String> lines,
                                             LaunchRuntime runtime,
                                             String redisUrl,
                                             String trajectoryMode,
                                             String recordDir,
                                             int threshold,
                                             int batchSize,
                                             int scanInterval,
                                             String gpuIds) {
        lines.add("  vLLM Inference:  " + runtime.inferenceUrl());
        lines.add("  vLLM Judge:      " + runtime.judgeUrl() + " (" + runtime.judgeLabel() + ")");
        lines.add("  Gateway proxy:   " + runtime.gatewayBaseUrl());
        lines.add("  Redis store:     " + redisUrl);
        lines.add("  Trajectory mode: " + trajectoryMode);
        lines.add("  Trajectory log:  " + recordDir + "/ (JSONL, per-turn)");
        lines.add("  LoRA repo:       " + runtime.loraRepo());
        lines.add("  Train threshold: " + threshold + " samples");
        lines.add("  Collect batch:   " + batchSize);
        lines.add("  Scan interval:   " + scanInterval + "s");
        lines.add("  Training mode:   PPO (Ray)");
        lines.add("  Train GPUs:      [" + gpuIds + "]");
        lines.add("");
    }

    private static void appendSummaryFooter(List<String> lines,
                                            boolean jiuwenEnabled,
                                            String appHost,
                                            Integer wsPort,
                                            String webHost,
                                            Integer webPort,
                                            boolean webStarted) {
        if (jiuwenEnabled) {
            String wsDisplayHost = urlHost(appHost);
            if (webStarted) {
                String webDisplayHost = urlHost(webHost);
                lines.add("  Open http://" + webDisplayHost + ":" + webPort + " to start chatting,");
            } else {
                lines.add("  Chat via WebSocket (ws://" + wsDisplayHost + ":" + wsPort + "/ws),");
            }
        }
        lines.add("  Each turn auto-records token_ids + logprobs,");
        lines.add("  next turn triggers delayed Judge scoring,");
        lines.add("  when pending trajectories reach threshold, PPO LoRA training auto-triggers.");
        lines.add("  Press Ctrl+C to stop all services.");
        lines.add("=".repeat(60));
    }

    private static Path resolveWebDist(Path jiuwenclawRepo, Path workspaceRoot) {
        Path distDir = jiuwenclawRepo.resolve("jiuwenclaw").resolve("web").resolve("dist");
        return Files.exists(distDir) ? distDir : workspaceRoot.resolve("web").resolve("dist");
    }

    private static VllmDetails vllmDetails(Object serviceCfg) {
        if (serviceCfg instanceof VllmServiceConfig cfg) {
            return new VllmDetails(cfg.modelPath(), cfg.modelName(), cfg.host(), cfg.port(),
                    cfg.gpuIds(), cfg.tp(), Map.of(), List.of(), cfg.healthTimeout());
        }
        if (serviceCfg instanceof JudgeServiceConfig cfg) {
            return new VllmDetails(cfg.modelPath(), cfg.modelName(), cfg.host(), cfg.port(),
                    cfg.gpuIds(), cfg.tp(), Map.of(), List.of(), cfg.healthTimeout());
        }
        if (serviceCfg instanceof com.openjiuwen.agent_evolving.agent_rl.config.VLLMServiceConfig cfg) {
            return new VllmDetails(cfg.getModelPath(), cfg.getModelName(), cfg.getHost(),
                    requirePort(cfg.getPort(), "vllm.port"), cfg.getGpuIds(), cfg.getTp(),
                    safeMap(cfg.getEnv()), safeList(cfg.getExtraArgs()), cfg.getHealthTimeout());
        }
        if (serviceCfg instanceof com.openjiuwen.agent_evolving.agent_rl.config.JudgeConfig cfg) {
            return new VllmDetails(cfg.getModelPath(), cfg.getModelName(), cfg.getHost(),
                    requirePort(cfg.getPort(), "judge.port"), cfg.getGpuIds(), cfg.getTp(),
                    safeMap(cfg.getEnv()), safeList(cfg.getExtraArgs()), cfg.getHealthTimeout());
        }
        throw new IllegalArgumentException("Unsupported vLLM config type: " + typeName(serviceCfg));
    }

    private static GatewayDetails gatewayDetails(Object gatewayCfg) {
        if (gatewayCfg instanceof GatewayServiceConfig cfg) {
            return new GatewayDetails(cfg.host(), cfg.port(), cfg.redisUrl(), cfg.recordDir(),
                    cfg.logLevel(), cfg.healthTimeout(), cfg.disableTrajectoryCollection(), Map.of());
        }
        if (gatewayCfg instanceof com.openjiuwen.agent_evolving.agent_rl.config.GatewayServiceConfig cfg) {
            return new GatewayDetails(cfg.getHost(), requirePort(cfg.getPort(), "gateway.port"),
                    cfg.getRedisUrl(), cfg.getRecordDir(), cfg.getLogLevel(), cfg.getHealthTimeout(),
                    cfg.isDisableTrajectoryCollection(), safeMap(cfg.getEnv()));
        }
        throw new IllegalArgumentException("Unsupported gateway config type: " + typeName(gatewayCfg));
    }

    private static int requirePort(Integer port, String label) {
        if (port == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return port;
    }

    private static int trainGpuCount(String gpuIds) {
        if (gpuIds == null) {
            return 1;
        }
        int count = 0;
        for (String gpu : gpuIds.split(",")) {
            if (!gpu.trim().isEmpty()) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private static boolean truthy(String value) {
        return value != null && !value.isEmpty();
    }

    private static String stripToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, String> safeMap(Map<String, String> value) {
        return value == null ? Map.of() : new LinkedHashMap<>(value);
    }

    private static List<String> safeList(List<String> value) {
        return value == null ? List.of() : List.copyOf(value);
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20 || ch > 0x7e) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }

    /**
     * Container for JiuwenClaw app and web processes.
     */
    public static final class JiuwenClawProcesses {
        private final Process appProcess;
        private final Process webProcess;

        public JiuwenClawProcesses(Process appProcess, Process webProcess) {
            this.appProcess = appProcess;
            this.webProcess = webProcess;
        }

        public Process getAppProcess() {
            return appProcess;
        }

        public Process getWebProcess() {
            return webProcess;
        }

        public boolean hasWebProcess() {
            return webProcess != null;
        }
    }

    /**
     * Scheduler handle used by launcher shutdown.
     */
    public static final class SchedulerLaunchHandle implements OnlineTrainingSchedulerHandle {
        private final OnlineTrainingScheduler scheduler;
        private final String loraRepo;
        private final String inferenceUrl;
        private boolean stopped;

        public SchedulerLaunchHandle(OnlineTrainingScheduler scheduler, String loraRepo, String inferenceUrl) {
            this.scheduler = scheduler;
            this.loraRepo = loraRepo;
            this.inferenceUrl = inferenceUrl;
        }

        public OnlineTrainingScheduler getScheduler() {
            return scheduler;
        }

        public String getLoraRepo() {
            return loraRepo;
        }

        public String getInferenceUrl() {
            return inferenceUrl;
        }

        public boolean isStopped() {
            return stopped;
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }

    private record VllmDetails(String modelPath,
                               String modelName,
                               String host,
                               int port,
                               String gpuIds,
                               int tp,
                               Map<String, String> env,
                               List<String> extraArgs,
                               double healthTimeout) {
    }

    private record GatewayDetails(String host,
                                  int port,
                                  String redisUrl,
                                  String recordDir,
                                  String logLevel,
                                  double healthTimeout,
                                  boolean disableTrajectoryCollection,
                                  Map<String, String> env) {
    }
}
