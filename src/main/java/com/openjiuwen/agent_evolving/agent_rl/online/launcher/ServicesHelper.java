// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**
 * Process launch helpers and runtime resolution for the online RL loop.
 * Mirrors Python's services.py from agent-core-0.1.12/openjiuwen/agent_evolving/agent_rl/online/launcher/services.py.
 * 
 * This class provides static helper methods for:
 * - Resolving service URLs and runtime configuration
 * - Spawning subprocess processes with logging
 * - Starting vLLM inference and judge services
 * - Starting Gateway proxy services
 * - Starting JiuwenClaw app and web frontend
 * - Printing launch summaries
 */
public final class ServicesHelper {
    
    private static final Logger log = Logger.getLogger("online_rl");
    private static final String DEFAULT_GATEWAY_APP_FACTORY = "openjiuwen.agent_evolving.agent_rl.online.gateway.app.proxy:create_app";
    private static final double EXISTING_SERVICE_HEALTH_TIMEOUT = 30.0;
    
    private ServicesHelper() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Resolve service URLs, skip flags, and port checks from config.
     * Mirrors Python's resolve_launch_runtime function.
     * 
     * @param cfg Online RL configuration (placeholder until OnlineRLConfig Java class exists)
     * @param scriptDir Script directory path for LoRA repo default
     * @return Resolved LaunchRuntime instance
     */
    public static LaunchRuntime resolveLaunchRuntime(Object cfg, Path scriptDir) {
        // PLACEHOLDER: OnlineRLConfig Java class not yet translated
        // This method will be fully implemented once OnlineRLConfig exists
        
        throw new UnsupportedOperationException(
            "resolveLaunchRuntime requires OnlineRLConfig Java class. " +
            "Placeholder until agent_rl/config/online_config.py is translated."
        );
        
        /* Full implementation will mirror Python:
        String loraRepo = cfg.training.loraRepo != null ? 
            cfg.training.loraRepo : scriptDir.resolve("lora_repo").toString();
        boolean skipVllm = cfg.inference.existingUrl != null;
        String inferenceUrl = cfg.inference.existingUrl != null ? 
            cfg.inference.existingUrl : 
            "http://" + urlHost(cfg.inference.host) + ":" + cfg.inference.port;
        String gatewayBaseUrl = "http://" + urlHost(cfg.gateway.host) + ":" + cfg.gateway.port;
        String gatewayApiUrl = gatewayBaseUrl + "/v1";
        
        boolean reuseInferenceForJudge = false;
        String judgeUrl;
        boolean skipJudge;
        
        if (cfg.judge.existingUrl != null) {
            judgeUrl = cfg.judge.existingUrl;
            skipJudge = true;
        } else if (cfg.judge.reuseInferenceIfSameModel && 
                   cfg.judge.modelName.equals(cfg.inference.modelName)) {
            judgeUrl = inferenceUrl;
            skipJudge = true;
            reuseInferenceForJudge = true;
        } else {
            judgeUrl = "http://" + urlHost(cfg.judge.host) + ":" + cfg.judge.port;
            skipJudge = false;
        }
        
        String judgeLabel = judgeUrl.equals(inferenceUrl) ? 
            "reuse inference" : cfg.judge.modelName;
        
        List<LaunchRuntime.PortCheck> portsToCheck = new ArrayList<>();
        portsToCheck.add(new LaunchRuntime.PortCheck("Gateway", cfg.gateway.host, cfg.gateway.port));
        
        if (!skipVllm) {
            portsToCheck.add(new LaunchRuntime.PortCheck("vLLM-Inference", cfg.inference.host, cfg.inference.port));
        }
        if (!skipJudge) {
            portsToCheck.add(new LaunchRuntime.PortCheck("vLLM-Judge", cfg.judge.host, cfg.judge.port));
        }
        if (cfg.jiuwen.enabled) {
            portsToCheck.add(new LaunchRuntime.PortCheck("JiuwenClaw-AgentServer", cfg.jiuwen.appHost, cfg.jiuwen.agentServerPort));
            portsToCheck.add(new LaunchRuntime.PortCheck("JiuwenClaw-WS", cfg.jiuwen.appHost, cfg.jiuwen.wsPort));
            portsToCheck.add(new LaunchRuntime.PortCheck("JiuwenClaw-Web", cfg.jiuwen.webHost, cfg.jiuwen.webPort));
        }
        
        return LaunchRuntime.builder()
            .inferenceUrl(inferenceUrl)
            .judgeUrl(judgeUrl)
            .gatewayBaseUrl(gatewayBaseUrl)
            .gatewayApiUrl(gatewayApiUrl)
            .loraRepo(loraRepo)
            .skipVllm(skipVllm)
            .skipJudge(skipJudge)
            .reuseInferenceForJudge(reuseInferenceForJudge)
            .judgeLabel(judgeLabel)
            .portsToCheck(portsToCheck)
            .build();
        */
    }
    
    /**
     * Normalize host address: convert 0.0.0.0 or :: to 127.0.0.1.
     * Mirrors Python's url_host function.
     * 
     * @param host Original host address
     * @return Normalized host address
     */
    public static String urlHost(String host) {
        if ("0.0.0.0".equals(host) || "::".equals(host)) {
            return "127.0.0.1";
        }
        return host;
    }
    
    /**
     * Spawn child process and stream stdout/stderr to a file (if provided).
     * Mirrors Python's spawn_process function.
     * 
     * @param cmd Command and arguments list
     * @param env Environment variables (optional)
     * @param cwd Working directory (optional)
     * @param logPath Log file path (optional)
     * @return Process instance
     * @throws IOException if process spawn fails
     */
    public static Process spawnProcess(List<String> cmd, Map<String, String> env, 
                                       String cwd, Path logPath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        
        if (cwd != null) {
            pb.directory(new File(cwd));
        }
        
        if (env != null) {
            Map<String, String> currentEnv = pb.environment();
            currentEnv.putAll(env);
        }
        
        if (logPath != null) {
            // Create parent directories if needed
            Files.createDirectories(logPath.getParent());
            
            // Redirect stdout and stderr to log file (append mode)
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
            pb.redirectErrorStream(true); // Merge stderr into stdout
        } else {
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        }
        
        return pb.start();
    }
    
    /**
     * Start a vLLM OpenAI API server from config.
     * Mirrors Python's start_vllm_service function.
     * 
     * @param serviceCfg VLLM service configuration (placeholder)
     * @param stepLabel Step label for logging
     * @param serviceName Service name for logging
     * @param enableRuntimeLora Enable runtime LoRA updating
     * @param logPath Log file path (optional)
     * @return Process instance
     * @throws IOException if process spawn fails
     */
    public static Process startVllmService(Object serviceCfg, String stepLabel, 
                                           String serviceName, boolean enableRuntimeLora,
                                           Path logPath) throws IOException {
        // PLACEHOLDER: VLLMServiceConfig Java class not yet translated
        
        throw new UnsupportedOperationException(
            "startVllmService requires VLLMServiceConfig Java class. " +
            "Placeholder until agent_rl/config/online_config.py is translated."
        );
        
        /* Full implementation will mirror Python:
        Map<String, String> env = new HashMap<>(System.getenv());
        env.putAll(serviceCfg.env);
        env.put("CUDA_VISIBLE_DEVICES", serviceCfg.gpuIds);
        
        if (enableRuntimeLora) {
            env.putIfAbsent("VLLM_ALLOW_RUNTIME_LORA_UPDATING", "1");
        }
        
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + "/bin/java"); // In Python: sys.executable
        cmd.add("-m");
        cmd.add("vllm.entrypoints.openai.api_server");
        cmd.add("--model");
        cmd.add(serviceCfg.modelPath);
        cmd.add("--served-model-name");
        cmd.add(serviceCfg.modelName != null ? serviceCfg.modelName : serviceCfg.modelPath);
        cmd.add("--port");
        cmd.add(String.valueOf(serviceCfg.port));
        cmd.add("--host");
        cmd.add(serviceCfg.host);
        cmd.add("--tensor-parallel-size");
        cmd.add(String.valueOf(serviceCfg.tp));
        cmd.addAll(serviceCfg.extraArgs);
        
        log.info(String.format("[%s] Starting %s vLLM (TP=%d) on GPU [%s], host=%s, port=%d ...",
            stepLabel, serviceName, serviceCfg.tp, serviceCfg.gpuIds, 
            serviceCfg.host, serviceCfg.port));
        
        return spawnProcess(cmd, env, null, logPath);
        */
    }
    
    /**
     * Start agent-core gateway.
     * Mirrors Python's start_gateway function.
     * 
     * @param inferenceUrl Inference service URL
     * @param judgeUrl Judge service URL
     * @param judgeModel Judge model name
     * @param modelId Model ID
     * @param modelPath Model path
     * @param loraRepoRoot LoRA repository root path
     * @param gatewayCfg Gateway configuration (placeholder)
     * @param agentCoreRoot Agent core root path
     * @param logPath Log file path (optional)
     * @return Process instance
     * @throws IOException if process spawn fails
     */
    public static Process startGateway(String inferenceUrl, String judgeUrl, String judgeModel,
                                       String modelId, String modelPath, String loraRepoRoot,
                                       Object gatewayCfg, Path agentCoreRoot, Path logPath) throws IOException {
        // PLACEHOLDER: GatewayServiceConfig Java class not yet translated
        
        throw new UnsupportedOperationException(
            "startGateway requires GatewayServiceConfig Java class. " +
            "Placeholder until agent_rl/config/online_config.py is translated."
        );
        
        /* Full implementation will mirror Python:
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("LLM_URL", inferenceUrl);
        env.put("JUDGE_URL", judgeUrl);
        env.put("JUDGE_MODEL", judgeModel);
        env.put("MODEL_ID", modelId);
        env.put("MODEL_PATH", modelPath);
        env.put("GATEWAY_HOST", gatewayCfg.host);
        env.put("GATEWAY_PORT", String.valueOf(gatewayCfg.port));
        env.put("RECORD_DIR", gatewayCfg.recordDir);
        env.put("REDIS_URL", gatewayCfg.redisUrl);
        
        if (loraRepoRoot != null && !loraRepoRoot.isEmpty()) {
            env.put("LORA_REPO_ROOT", loraRepoRoot);
        }
        
        if (gatewayCfg.disableTrajectoryCollection) {
            env.put("DISABLE_GATEWAY_TRAJECTORY_COLLECTION", "true");
        }
        
        env.putAll(gatewayCfg.env);
        
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + "/bin/java");
        cmd.add("-m");
        cmd.add("uvicorn");
        cmd.add(DEFAULT_GATEWAY_APP_FACTORY);
        cmd.add("--factory");
        cmd.add("--host");
        cmd.add(gatewayCfg.host);
        cmd.add("--port");
        cmd.add(String.valueOf(gatewayCfg.port));
        cmd.add("--log-level");
        cmd.add(gatewayCfg.logLevel);
        
        log.info(String.format("[2/5] Starting Gateway on %s:%d ...", 
            gatewayCfg.host, gatewayCfg.port));
        
        return spawnProcess(cmd, env, agentCoreRoot.toString(), logPath);
        */
    }
    
    /**
     * Start the OnlineTrainingScheduler that polls RedisTrajectoryStore.
     * Mirrors Python's start_online_training_scheduler function.
     * 
     * PLACEHOLDER: Requires OnlineTrainingScheduler, InferenceNotifier, LoRARepository classes.
     */
    public static Object startOnlineTrainingScheduler(Object cfg, LaunchRuntime runtime) {
        throw new UnsupportedOperationException(
            "startOnlineTrainingScheduler requires multiple Java classes: " +
            "OnlineTrainingScheduler, InferenceNotifier, LoRARepository. " +
            "Placeholder until agent_rl online components are translated."
        );
    }
    
    /**
     * Start JiuwenClaw app + web frontend (if dist exists).
     * Mirrors Python's start_jiuwenclaw function.
     * 
     * @param jiuwenclawRepo JiuwenClaw repository path
     * @param workspaceRoot Workspace root path
     * @param trajectoryGatewayUrl Trajectory gateway URL
     * @param modelPath Model path
     * @param trajectoryMode Trajectory mode
     * @param trajectoryBatchSize Trajectory batch size
     * @param appHost App host address
     * @param wsPort WebSocket port
     * @param webHost Web frontend host
     * @param webPort Web frontend port
     * @return Tuple of (appProcess, webProcess) - webProcess may be null
     * @throws IOException if process spawn fails
     */
    public static JiuwenClawProcesses startJiuwenclaw(
            Path jiuwenclawRepo, Path workspaceRoot, String trajectoryGatewayUrl,
            String modelPath, String trajectoryMode, int trajectoryBatchSize,
            String appHost, int wsPort, String webHost, int webPort) throws IOException {
        
        Map<String, String> env = new HashMap<>(System.getenv());
        
        String trajectoryTenantId = System.getenv("RL_ONLINE_TENANT_ID");
        if (trajectoryTenantId == null || trajectoryTenantId.trim().isEmpty()) {
            trajectoryTenantId = System.getenv("WEB_USER_ID");
        }
        if (trajectoryTenantId == null || trajectoryTenantId.trim().isEmpty()) {
            trajectoryTenantId = "local-web-user";
        }
        trajectoryTenantId = trajectoryTenantId.trim();
        
        env.put("WEB_USER_ID", trajectoryTenantId);
        
        // Build custom headers JSON
        String customHeaders = String.format("{\"x-user-id\":\"%s\"}", trajectoryTenantId);
        env.put("CUSTOM_HEADERS", customHeaders);
        
        // PLACEHOLDER: build_trajectory_env_updates requires WorkspaceBuilder Java class
        // env.putAll(WorkspaceBuilder.buildTrajectoryEnvUpdates(...));
        
        env.put("WEB_HOST", appHost);
        env.put("WEB_PORT", String.valueOf(wsPort));
        
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + "/bin/java");
        cmd.add("-m");
        cmd.add("jiuwenclaw.app");
        
        log.info("[4/5] Starting JiuwenClaw app ...");
        log.info(String.format("  JiuwenClaw env: gateway=%s, model=%s, batch_size=%d, mode=%s; ws=%s:%d",
            trajectoryGatewayUrl, modelPath, trajectoryBatchSize, trajectoryMode, appHost, wsPort));
        
        Process appProc = spawnProcess(cmd, env, jiuwenclawRepo.toString(), null);
        
        Process webProc = null;
        Path distDir = jiuwenclawRepo.resolve("jiuwenclaw").resolve("web").resolve("dist");
        if (!Files.exists(distDir)) {
            distDir = workspaceRoot.resolve("web").resolve("dist");
        }
        
        if (Files.exists(distDir)) {
            List<String> webCmd = new ArrayList<>();
            webCmd.add(System.getProperty("java.home") + "/bin/java");
            webCmd.add("-m");
            webCmd.add("jiuwenclaw.app_web");
            webCmd.add("--host");
            webCmd.add(webHost);
            webCmd.add("--port");
            webCmd.add(String.valueOf(webPort));
            webCmd.add("--dist");
            webCmd.add(distDir.toString());
            webCmd.add("--proxy-target");
            webCmd.add(String.format("http://%s:%d", urlHost(appHost), wsPort));
            
            log.info(String.format("[5/5] Starting JiuwenClaw web frontend at http://%s:%d (dist=%s, ws_proxy=%s:%d) ...",
                webHost, webPort, distDir, urlHost(appHost), wsPort));
            
            webProc = spawnProcess(webCmd, env, jiuwenclawRepo.toString(), null);
        } else {
            log.warning("[5/5] Web dist not found, skipping frontend. " +
                "Build it: cd jiuwenclaw/jiuwenclaw/web && npm install && npm run build");
        }
        
        return new JiuwenClawProcesses(appProc, webProc);
    }
    
    /**
     * Log runtime summary after successful startup.
     * Mirrors Python's print_launch_summary function.
     * 
     * @param cfg Online RL configuration (placeholder)
     * @param cfgPath Config file path
     * @param runtime Resolved launch runtime
     * @param webStarted Whether web frontend started successfully
     */
    public static void printLaunchSummary(Object cfg, Path cfgPath, 
                                          LaunchRuntime runtime, boolean webStarted) {
        List<String> lines = new ArrayList<>();
        lines.add("=" .repeat(60));
        lines.add("  JiuwenClaw online RL loop started (v2: per-turn + Judge)");
        lines.add("");
        lines.add(String.format("  Config file:      %s", cfgPath));
        
        // PLACEHOLDER: Full implementation requires OnlineRLConfig Java class
        // Will add conditional output for jiuwen.enabled, web frontend URL, etc.
        
        lines.add(String.format("  vLLM Inference:  %s", runtime.inferenceUrl()));
        lines.add(String.format("  vLLM Judge:      %s (%s)", runtime.judgeUrl(), runtime.judgeLabel()));
        lines.add(String.format("  Gateway proxy:   %s", runtime.gatewayBaseUrl()));
        lines.add(String.format("  LoRA repo:       %s", runtime.loraRepo()));
        lines.add(String.format("  Train threshold: PLACEHOLDER samples"));
        lines.add(String.format("  Collect batch:   PLACEHOLDER"));
        lines.add(String.format("  Scan interval:   PLACEHOLDERs"));
        lines.add(String.format("  Training mode:   PPO (Ray)"));
        lines.add(String.format("  Train GPUs:      [PLACEHOLDER]"));
        lines.add("");
        lines.add("  Each turn auto-records token_ids + logprobs,");
        lines.add("  next turn triggers delayed Judge scoring,");
        lines.add("  when pending trajectories reach threshold, PPO LoRA training auto-triggers.");
        lines.add("  Press Ctrl+C to stop all services.");
        lines.add("=" .repeat(60));
        
        log.info("\n" + String.join("\n", lines));
    }
    
    /**
     * Container for JiuwenClaw app and web processes.
     * Mirrors Python's tuple[Process, Process | None] return type.
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
}