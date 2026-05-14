/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

/**
 * Deterministic launcher/service orchestration helper.
 * <p>
 * Mirrors the fake-driven shutdown path in Python's launcher runner without real runtime wiring.
 */
public class LauncherCoordinator {

    private final LauncherOrchestrator orchestrator;
    private final Sleeper sleeper;

    public LauncherCoordinator(LauncherOrchestrator orchestrator, Sleeper sleeper) {
        this.orchestrator = orchestrator;
        this.sleeper = sleeper;
    }

    public void runOnlineRlLoop(LauncherOnlineRlConfig cfg, Path cfgPath, LauncherPaths paths) {
        LaunchRuntime runtime = orchestrator.resolveLaunchRuntime(cfg, paths.scriptDir());
        orchestrator.checkRequiredPorts(runtime.portsToCheck());
        LauncherState state = new LauncherState();
        try {
            if (!runtime.skipVllm()) {
                state.vllmProc = orchestrator.startInference(cfg, paths.scriptDir().resolve("logs").resolve("inference_vllm.log"));
            }
            if (!runtime.skipJudge()) {
                state.judgeProc = orchestrator.startJudge(cfg, paths.scriptDir().resolve("logs").resolve("judge_vllm.log"));
            }
            orchestrator.waitForServiceHealths(cfg, runtime);
            state.gatewayProc = orchestrator.startGateway(cfg, runtime, paths);
            orchestrator.waitForGatewayHealth(cfg, runtime);
            state.trainingScheduler = orchestrator.startOnlineTrainingScheduler(cfg, runtime);
            if (cfg.jiuwen().enabled()) {
                orchestrator.ensureWorkspace(cfg, runtime, paths);
                JiuwenLaunchResult result = orchestrator.startJiuwenclaw(cfg, runtime, paths);
                state.clawProc = result.appProc();
                state.webProc = result.webProc();
            }
            orchestrator.printLaunchSummary(cfg, cfgPath, runtime, state.webProc != null);
            sleeper.sleep(5000L);
        } catch (ShutdownRequested ignored) {
            // expected controlled shutdown in fake-driven tests
        } finally {
            shutdown(state);
        }
    }

    public void terminate(LauncherProcess process) {
        if (process == null || process.poll() != null) {
            return;
        }
        process.terminate();
        try {
            process.waitFor(10_000L);
        } catch (TimeoutException exception) {
            process.kill();
            try {
                process.waitFor(5_000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (TimeoutException ignored) {
                // fake path only
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void shutdown(LauncherState state) {
        if (state.trainingScheduler != null) {
            state.trainingScheduler.stop();
        }
        terminate(state.webProc);
        terminate(state.clawProc);
        terminate(state.gatewayProc);
        terminate(state.judgeProc);
        terminate(state.vllmProc);
    }

    public interface Sleeper {
        void sleep(long millis) throws ShutdownRequested;
    }

    public static final class ShutdownRequested extends RuntimeException {
    }

    private static final class LauncherState {
        private LauncherProcess vllmProc;
        private LauncherProcess judgeProc;
        private LauncherProcess gatewayProc;
        private LauncherProcess clawProc;
        private LauncherProcess webProc;
        private OnlineTrainingSchedulerHandle trainingScheduler;
    }
}
