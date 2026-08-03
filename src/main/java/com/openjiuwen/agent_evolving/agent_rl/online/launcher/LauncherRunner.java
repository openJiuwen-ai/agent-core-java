/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import com.openjiuwen.agent_evolving.agent_rl.config.JudgeConfig;
import com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig;
import com.openjiuwen.agent_evolving.agent_rl.config.VLLMServiceConfig;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestration runtime for the online RL launcher.
 *
 * <p>Mirrors Python's {@code LauncherPaths} and {@code run_online_rl_loop} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/launcher/runner.py}.</p>
 */
public final class LauncherRunner {

    private static final Logger LOGGER = Logger.getLogger("online_rl");
    private static final Duration PROCESS_TERMINATE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration PROCESS_KILL_TIMEOUT = Duration.ofSeconds(5);

    private final LauncherBackend backend;
    private final WaitStrategy waitStrategy;

    public LauncherRunner() {
        this(new DefaultLauncherBackend(), duration -> {
            if (!duration.isZero() && !duration.isNegative()) {
                Thread.sleep(duration.toMillis());
            }
        });
    }

    LauncherRunner(LauncherBackend backend, WaitStrategy waitStrategy) {
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
        this.waitStrategy = Objects.requireNonNull(waitStrategy, "waitStrategy must not be null");
    }

    public static void runOnlineRlLoop(OnlineRLConfig cfg, Path cfgPath, LauncherPaths paths) {
        new LauncherRunner().runOnlineRlLoop(cfg, cfgPath, paths, RunOptions.defaults());
    }

    public void runOnlineRlLoop(OnlineRLConfig cfg, Path cfgPath, LauncherPaths paths, RunOptions options) {
        Objects.requireNonNull(cfg, "cfg must not be null");
        Objects.requireNonNull(cfgPath, "cfgPath must not be null");
        Objects.requireNonNull(paths, "paths must not be null");
        RunOptions safeOptions = options != null ? options : RunOptions.defaults();

        if (cfg.isDemo()) {
            LOGGER.info("Demo mode enabled (compatibility flag): using configured runtime options.");
        }

        Path logDir = paths.scriptDir().resolve("logs");
        createDirectories(logDir);
        LauncherServices.LaunchRuntime runtime = backend.resolveLaunchRuntime(cfg, paths.scriptDir());
        checkRequiredPorts(runtime.portsToCheck());
        if (runtime.reuseInferenceForJudge()) {
            LOGGER.info(() -> "Judge will reuse inference vLLM (" + cfg.getInference().getModelName() + ")");
        }

        RuntimeHandles handles = new RuntimeHandles();
        Thread shutdownHook = registerShutdownHook(handles, safeOptions.installShutdownHook());
        try {
            startInference(cfg, runtime, logDir, handles);
            startJudge(cfg, runtime, logDir, handles);
            waitForServiceHealths(cfg, runtime);

            handles.gatewayProcess = backend.startGateway(
                    runtime.inferenceUrl(),
                    runtime.judgeUrl(),
                    cfg.getJudge().getModelName(),
                    cfg.getInference().getModelName(),
                    cfg.getInference().getModelPath(),
                    runtime.loraRepo(),
                    cfg,
                    paths.agentCoreRoot(),
                    logDir.resolve("gateway.log")
            );
            backend.waitForHealth(runtime.gatewayBaseUrl() + "/health", cfg.getGateway().getHealthTimeout());
            LOGGER.info(() -> "  Gateway ready at " + runtime.gatewayBaseUrl());

            LOGGER.info(() -> "[3/5] Starting OnlineTrainingScheduler (PPO, threshold="
                    + cfg.getTraining().getThreshold() + ", interval="
                    + cfg.getTraining().getScanInterval() + "s) ...");
            handles.trainingScheduler = backend.startOnlineTrainingScheduler(cfg, runtime);
            LOGGER.info(() -> "  OnlineTrainingScheduler running (PPO, train GPU: ["
                    + cfg.getTraining().getGpuIds() + "])");

            startJiuwenIfEnabled(cfg, runtime, paths, handles);
            backend.printLaunchSummary(cfg, cfgPath, runtime, handles.webProcess != null);
            monitorProcesses(handles, safeOptions);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.info("Launcher interrupted by keyboard.");
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Fatal error", exception);
        } finally {
            shutdown(handles);
            removeShutdownHook(shutdownHook);
        }
    }

    public static void checkPortFree(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(LauncherServices.urlHost(host), port), 1_000);
            throw new IllegalStateException(
                    "Port " + host + ":" + port + " is already in use. "
                            + "Kill the occupying process first: lsof -i :" + port
            );
        } catch (IOException ignored) {
            // Python's connect_ex treats connection errors as a free port.
        }
    }

    public static void waitForHealth(String url, double timeoutSeconds) {
        long deadline = System.nanoTime() + Math.round(timeoutSeconds * 1_000_000_000.0d);
        while (System.nanoTime() < deadline) {
            try {
                HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3_000);
                connection.setReadTimeout(3_000);
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    return;
                }
            } catch (IOException exception) {
                LOGGER.fine(() -> "Health check retry url=" + url + " err=" + exception.getMessage());
            }
            try {
                Thread.sleep(2_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Health check interrupted: " + url, exception);
            }
        }
        throw new IllegalStateException("Health check " + url + " did not pass within " + timeoutSeconds + "s");
    }

    public static void terminate(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(PROCESS_TERMINATE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(PROCESS_KILL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private void startInference(
            OnlineRLConfig cfg,
            LauncherServices.LaunchRuntime runtime,
            Path logDir,
            RuntimeHandles handles
    ) {
        if (!runtime.skipVllm()) {
            handles.vllmProcess = backend.startInferenceVllmService(
                    cfg.getInference(),
                    "1a/5",
                    "Inference",
                    true,
                    logDir.resolve("inference_vllm.log")
            );
        } else {
            LOGGER.info(() -> "[1a/5] Using existing inference at " + runtime.inferenceUrl());
        }
    }

    private void startJudge(
            OnlineRLConfig cfg,
            LauncherServices.LaunchRuntime runtime,
            Path logDir,
            RuntimeHandles handles
    ) {
        if (!runtime.skipJudge()) {
            handles.judgeProcess = backend.startJudgeVllmService(
                    cfg.getJudge(),
                    "1b/5",
                    "Judge",
                    false,
                    logDir.resolve("judge_vllm.log")
            );
        } else {
            LOGGER.info(() -> "[1b/5] Using existing Judge at " + runtime.judgeUrl());
        }
    }

    private void waitForServiceHealths(OnlineRLConfig cfg, LauncherServices.LaunchRuntime runtime) {
        if (!runtime.skipVllm()) {
            LOGGER.info("  Waiting for Inference vLLM health check (may take 1-3 min) ...");
            backend.waitForHealth(runtime.inferenceUrl() + "/health", cfg.getInference().getHealthTimeout());
            LOGGER.info(() -> "  Inference vLLM ready at " + runtime.inferenceUrl());
        } else {
            backend.waitForHealth(
                    runtime.inferenceUrl() + "/health",
                    LauncherServices.EXISTING_SERVICE_HEALTH_TIMEOUT
            );
        }

        if (!runtime.skipJudge()) {
            LOGGER.info("  Waiting for Judge vLLM health check (may take 2-5 min) ...");
            backend.waitForHealth(runtime.judgeUrl() + "/health", cfg.getJudge().getHealthTimeout());
            LOGGER.info(() -> "  Judge vLLM ready at " + runtime.judgeUrl());
        } else {
            backend.waitForHealth(runtime.judgeUrl() + "/health", LauncherServices.EXISTING_SERVICE_HEALTH_TIMEOUT);
        }
    }

    private void startJiuwenIfEnabled(
            OnlineRLConfig cfg,
            LauncherServices.LaunchRuntime runtime,
            LauncherPaths paths,
            RuntimeHandles handles
    ) throws InterruptedException {
        if (!cfg.getJiuwen().isEnabled()) {
            LOGGER.info("[4/5] Skip JiuwenClaw startup (jiuwen.enabled=false)");
            return;
        }

        backend.ensureWorkspace(
                paths.workspaceEnv(),
                runtime.gatewayApiUrl(),
                cfg.getInference().getModelName(),
                cfg.getInference().getModelPath(),
                cfg.getTrajectory().getMode(),
                runtime.gatewayBaseUrl(),
                cfg.getTrajectory().getBatchSize()
        );
        LauncherServices.JiuwenClawProcesses processes = backend.startJiuwenClaw(
                paths.jiuwenclawRepo(),
                paths.workspaceRoot(),
                runtime.gatewayBaseUrl(),
                cfg.getInference().getModelPath(),
                cfg.getTrajectory().getMode(),
                cfg.getTrajectory().getBatchSize(),
                cfg.getJiuwen().getAppHost(),
                cfg.getJiuwen().getWsPort(),
                cfg.getJiuwen().getWebHost(),
                cfg.getJiuwen().getWebPort()
        );
        handles.clawProcess = processes.appProcess();
        handles.webProcess = processes.webProcess();
        waitStrategy.sleep(Duration.ofSeconds(5));
        if (handles.clawProcess != null) {
            LOGGER.info(() -> "  JiuwenClaw app started (pid=" + handles.clawProcess.pid() + ")");
        }
        if (handles.webProcess != null) {
            LOGGER.info(() -> "  JiuwenClaw web started (pid=" + handles.webProcess.pid() + ")");
        }
    }

    private void monitorProcesses(RuntimeHandles handles, RunOptions options) throws InterruptedException {
        int iterations = 0;
        while (options.maxMonitorIterations() < 0 || iterations < options.maxMonitorIterations()) {
            if (hasExited("vllm", handles.vllmProcess)
                    || hasExited("judge_vllm", handles.judgeProcess)
                    || hasExited("gateway", handles.gatewayProcess)
                    || hasExited("jiuwenclaw", handles.clawProcess)) {
                return;
            }
            iterations += 1;
            waitStrategy.sleep(options.monitorInterval());
        }
    }

    private boolean hasExited(String name, Process process) {
        if (process == null || process.isAlive()) {
            return false;
        }
        int exitCode = process.exitValue();
        LOGGER.severe(() -> name + " exited unexpectedly with code " + exitCode + " - stopping");
        return true;
    }

    private void checkRequiredPorts(List<LauncherServices.PortCheck> portsToCheck) {
        for (LauncherServices.PortCheck portCheck : portsToCheck) {
            backend.checkPortFree(portCheck.host(), portCheck.port());
            LOGGER.info(() -> "  Port " + portCheck.port() + " (" + portCheck.label()
                    + ", host=" + portCheck.host() + ") is free");
        }
    }

    private void shutdown(RuntimeHandles handles) {
        if (handles.shutdownStarted) {
            return;
        }
        handles.shutdownStarted = true;
        LOGGER.info("Shutting down all services ...");
        if (handles.trainingScheduler != null) {
            handles.trainingScheduler.stop();
        }
        terminate(handles.webProcess);
        terminate(handles.clawProcess);
        terminate(handles.gatewayProcess);
        terminate(handles.judgeProcess);
        terminate(handles.vllmProcess);
        LOGGER.info("All services stopped.");
    }

    private Thread registerShutdownHook(RuntimeHandles handles, boolean enabled) {
        if (!enabled) {
            return null;
        }
        Thread hook = new Thread(() -> shutdown(handles), "online-rl-launcher-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
        return hook;
    }

    private void removeShutdownHook(Thread shutdownHook) {
        if (shutdownHook == null) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down.
        }
    }

    private static void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create log directory: " + path, exception);
        }
    }

    public record LauncherPaths(
            Path agentCoreRoot,
            Path jiuwenclawRepo,
            Path workspaceRoot,
            Path workspaceEnv,
            Path scriptDir
    ) {
        public LauncherPaths {
            agentCoreRoot = Objects.requireNonNull(agentCoreRoot, "agentCoreRoot");
            jiuwenclawRepo = Objects.requireNonNull(jiuwenclawRepo, "jiuwenclawRepo");
            workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot");
            workspaceEnv = Objects.requireNonNull(workspaceEnv, "workspaceEnv");
            scriptDir = Objects.requireNonNull(scriptDir, "scriptDir");
        }
    }

    public record RunOptions(Duration monitorInterval, int maxMonitorIterations, boolean installShutdownHook) {
        public RunOptions {
            monitorInterval = monitorInterval != null ? monitorInterval : Duration.ofSeconds(30);
        }

        public static RunOptions defaults() {
            return new RunOptions(Duration.ofSeconds(30), -1, true);
        }
    }

    @FunctionalInterface
    public interface WaitStrategy {
        void sleep(Duration duration) throws InterruptedException;
    }

    @FunctionalInterface
    public interface SchedulerHandle {
        void stop();
    }

    public interface LauncherBackend {

        LauncherServices.LaunchRuntime resolveLaunchRuntime(OnlineRLConfig cfg, Path scriptDir);

        void checkPortFree(String host, int port);

        void waitForHealth(String url, double timeoutSeconds);

        Process startInferenceVllmService(
                VLLMServiceConfig serviceConfig,
                String stepLabel,
                String serviceName,
                boolean enableRuntimeLora,
                Path logPath
        );

        Process startJudgeVllmService(
                JudgeConfig serviceConfig,
                String stepLabel,
                String serviceName,
                boolean enableRuntimeLora,
                Path logPath
        );

        Process startGateway(
                String inferenceUrl,
                String judgeUrl,
                String judgeModel,
                String modelId,
                String modelPath,
                String loraRepoRoot,
                OnlineRLConfig cfg,
                Path agentCoreRoot,
                Path logPath
        );

        SchedulerHandle startOnlineTrainingScheduler(OnlineRLConfig cfg, LauncherServices.LaunchRuntime runtime);

        void ensureWorkspace(
                Path configEnv,
                String gatewayUrl,
                String modelName,
                String modelPath,
                String trajectoryMode,
                String trajectoryGatewayUrl,
                int trajectoryBatchSize
        );

        LauncherServices.JiuwenClawProcesses startJiuwenClaw(
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
        );

        String printLaunchSummary(
                OnlineRLConfig cfg,
                Path cfgPath,
                LauncherServices.LaunchRuntime runtime,
                boolean webStarted
        );
    }

    private static final class DefaultLauncherBackend implements LauncherBackend {

        @Override
        public LauncherServices.LaunchRuntime resolveLaunchRuntime(OnlineRLConfig cfg, Path scriptDir) {
            return LauncherServices.resolveLaunchRuntime(cfg, scriptDir);
        }

        @Override
        public void checkPortFree(String host, int port) {
            LauncherRunner.checkPortFree(host, port);
        }

        @Override
        public void waitForHealth(String url, double timeoutSeconds) {
            LauncherRunner.waitForHealth(url, timeoutSeconds);
        }

        @Override
        public Process startInferenceVllmService(
                VLLMServiceConfig serviceConfig,
                String stepLabel,
                String serviceName,
                boolean enableRuntimeLora,
                Path logPath
        ) {
            return LauncherServices.startVllmService(
                    serviceConfig,
                    stepLabel,
                    serviceName,
                    enableRuntimeLora,
                    logPath
            );
        }

        @Override
        public Process startJudgeVllmService(
                JudgeConfig serviceConfig,
                String stepLabel,
                String serviceName,
                boolean enableRuntimeLora,
                Path logPath
        ) {
            return LauncherServices.startVllmService(
                    toVllmServiceConfig(serviceConfig),
                    stepLabel,
                    serviceName,
                    enableRuntimeLora,
                    logPath
            );
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
            return LauncherServices.startGateway(new LauncherServices.GatewayStartOptions(
                    inferenceUrl,
                    judgeUrl,
                    judgeModel,
                    modelId,
                    modelPath,
                    loraRepoRoot,
                    cfg.getGateway(),
                    agentCoreRoot,
                    logPath
            ));
        }

        @Override
        public SchedulerHandle startOnlineTrainingScheduler(
                OnlineRLConfig cfg,
                LauncherServices.LaunchRuntime runtime
        ) {
            return LauncherServices.startOnlineTrainingScheduler(cfg, runtime)::stop;
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
            LauncherWorkspace.ensureWorkspace(
                    configEnv,
                    gatewayUrl,
                    modelName,
                    modelPath,
                    trajectoryMode,
                    trajectoryGatewayUrl,
                    trajectoryBatchSize
            );
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
            return LauncherServices.startJiuwenClaw(new LauncherServices.JiuwenClawStartOptions(
                    jiuwenclawRepo,
                    workspaceRoot,
                    trajectoryGatewayUrl,
                    modelPath,
                    trajectoryMode,
                    trajectoryBatchSize,
                    appHost,
                    wsPort,
                    webHost,
                    webPort
            ));
        }

        @Override
        public String printLaunchSummary(
                OnlineRLConfig cfg,
                Path cfgPath,
                LauncherServices.LaunchRuntime runtime,
                boolean webStarted
        ) {
            return LauncherServices.printLaunchSummary(cfg, cfgPath, runtime, webStarted);
        }

        private static VLLMServiceConfig toVllmServiceConfig(JudgeConfig judgeConfig) {
            VLLMServiceConfig serviceConfig = new VLLMServiceConfig();
            serviceConfig.setModelPath(judgeConfig.getModelPath());
            serviceConfig.setModelName(judgeConfig.getModelName());
            serviceConfig.setHost(judgeConfig.getHost());
            serviceConfig.setPort(judgeConfig.getPort());
            serviceConfig.setGpuIds(judgeConfig.getGpuIds());
            serviceConfig.setTp(judgeConfig.getTp());
            serviceConfig.setExistingUrl(judgeConfig.getExistingUrl());
            serviceConfig.setHealthTimeout(judgeConfig.getHealthTimeout());
            serviceConfig.setEnv(judgeConfig.getEnv());
            serviceConfig.setExtraArgs(judgeConfig.getExtraArgs());
            return serviceConfig;
        }
    }

    private static final class RuntimeHandles {
        private Process vllmProcess;
        private Process judgeProcess;
        private Process gatewayProcess;
        private Process clawProcess;
        private Process webProcess;
        private SchedulerHandle trainingScheduler;
        private boolean shutdownStarted;
    }
}
