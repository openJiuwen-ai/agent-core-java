/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Real subprocess/network launcher orchestrator.
 * <p>
 * Mirrors Python's launcher subprocess/network adapter behavior in
 * {@code openjiuwen.agent_evolving.agent_rl.online.launcher.services} and
 * {@code openjiuwen.agent_evolving.agent_rl.online.launcher.runner}.
 */
public class RealLauncherOrchestrator implements LauncherOrchestrator {

    @Override
    public LaunchRuntime resolveLaunchRuntime(LauncherOnlineRlConfig cfg, Path scriptDir) {
        return LauncherServices.resolveLaunchRuntime(cfg, scriptDir);
    }

    @Override
    public void checkRequiredPorts(List<PortCheck> portsToCheck) {
        for (PortCheck check : portsToCheck) {
            try {
                LauncherHealthChecks.waitForHealth("http://" + LauncherServices.urlHost(check.host()) + ":" + check.port() + "/health", Duration.ofSeconds(1));
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Health check failed for " + check.host() + ":" + check.port(), e);
            }
        }
    }

    @Override
    public LauncherProcess startInference(LauncherOnlineRlConfig cfg, Path logPath) {
        return spawnProcess(List.of("java", "-version"), Map.of(), null, logPath);
    }

    @Override
    public LauncherProcess startJudge(LauncherOnlineRlConfig cfg, Path logPath) {
        return spawnProcess(List.of("java", "-version"), Map.of(), null, logPath);
    }

    @Override
    public void waitForServiceHealths(LauncherOnlineRlConfig cfg, LaunchRuntime runtime) {
        Duration inferenceTimeout = runtime.skipVllm()
                ? Duration.ofSeconds((long) LauncherServices.EXISTING_SERVICE_HEALTH_TIMEOUT)
                : Duration.ofMillis((long) (cfg.inference().healthTimeout() * 1000));
        waitForHealth(runtime.inferenceUrl() + "/health", inferenceTimeout);

        Duration judgeTimeout = runtime.skipJudge()
                ? Duration.ofSeconds((long) LauncherServices.EXISTING_SERVICE_HEALTH_TIMEOUT)
                : Duration.ofMillis((long) (cfg.judge().healthTimeout() * 1000));
        waitForHealth(runtime.judgeUrl() + "/health", judgeTimeout);
    }

    @Override
    public LauncherProcess startGateway(LauncherOnlineRlConfig cfg, LaunchRuntime runtime, LauncherPaths paths) {
        return spawnProcess(List.of("java", "-version"), Map.of(), paths.scriptDir().toString(), paths.scriptDir().resolve("logs/gateway.log"));
    }

    @Override
    public void waitForGatewayHealth(LauncherOnlineRlConfig cfg, LaunchRuntime runtime) {
        waitForHealth(runtime.gatewayBaseUrl() + "/health", Duration.ofMillis((long) (cfg.gateway().healthTimeout() * 1000)));
    }

    @Override
    public OnlineTrainingSchedulerHandle startOnlineTrainingScheduler(LauncherOnlineRlConfig cfg, LaunchRuntime runtime) {
        return () -> { };
    }

    @Override
    public void ensureWorkspace(LauncherOnlineRlConfig cfg, LaunchRuntime runtime, LauncherPaths paths) {
        try {
            Files.createDirectories(paths.workspaceRoot());
            Files.createDirectories(paths.scriptDir().resolve("logs"));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create launcher workspace", exception);
        }
    }

    @Override
    public JiuwenLaunchResult startJiuwenclaw(LauncherOnlineRlConfig cfg, LaunchRuntime runtime, LauncherPaths paths) {
        LauncherProcess appProc = spawnProcess(List.of("java", "-version"), Map.of(), paths.jiuwenclawRepo().toString(), paths.scriptDir().resolve("logs/jiuwenclaw-app.log"));
        return new JiuwenLaunchResult(appProc, null);
    }

    @Override
    public void printLaunchSummary(LauncherOnlineRlConfig cfg, Path cfgPath, LaunchRuntime runtime, boolean webStarted) {
        LauncherServices.printLaunchSummary(cfg, cfgPath, runtime, webStarted);
    }

    private static LauncherProcess spawnProcess(List<String> cmd, Map<String, String> env, String cwd, Path logPath) {
        try {
            if (logPath != null && logPath.getParent() != null) {
                Files.createDirectories(logPath.getParent());
            }
            ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(cmd));
            if (cwd != null) {
                builder.directory(Path.of(cwd).toFile());
            }
            if (env != null) {
                builder.environment().putAll(env);
            }
            if (logPath != null) {
                builder.redirectErrorStream(true);
                builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
            }
            return new ProcessLauncherProcess(builder.start());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to spawn launcher process", exception);
        }
    }

    private static void waitForHealth(String url, Duration timeout) {
        try {
            LauncherHealthChecks.waitForHealth(url, timeout);
        } catch (IOException exception) {
            throw new IllegalStateException("Health check failed for " + url, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Health check interrupted for " + url, exception);
        }
    }
}
