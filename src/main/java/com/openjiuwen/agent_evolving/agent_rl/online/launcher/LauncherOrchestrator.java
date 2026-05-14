/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import java.nio.file.Path;
import java.util.List;

/**
 * Injectable launcher orchestration surface for deterministic tests.
 * <p>
 * Mirrors the side-effectful launcher helpers consumed by Python's run loop.
 */
public interface LauncherOrchestrator {

    LaunchRuntime resolveLaunchRuntime(LauncherOnlineRlConfig cfg, Path scriptDir);

    void checkRequiredPorts(List<PortCheck> portsToCheck);

    LauncherProcess startInference(LauncherOnlineRlConfig cfg, Path logPath);

    LauncherProcess startJudge(LauncherOnlineRlConfig cfg, Path logPath);

    void waitForServiceHealths(LauncherOnlineRlConfig cfg, LaunchRuntime runtime);

    LauncherProcess startGateway(LauncherOnlineRlConfig cfg, LaunchRuntime runtime, LauncherPaths paths);

    void waitForGatewayHealth(LauncherOnlineRlConfig cfg, LaunchRuntime runtime);

    OnlineTrainingSchedulerHandle startOnlineTrainingScheduler(LauncherOnlineRlConfig cfg, LaunchRuntime runtime);

    void ensureWorkspace(LauncherOnlineRlConfig cfg, LaunchRuntime runtime, LauncherPaths paths);

    JiuwenLaunchResult startJiuwenclaw(LauncherOnlineRlConfig cfg, LaunchRuntime runtime, LauncherPaths paths);

    void printLaunchSummary(LauncherOnlineRlConfig cfg, Path cfgPath, LaunchRuntime runtime, boolean webStarted);
}
