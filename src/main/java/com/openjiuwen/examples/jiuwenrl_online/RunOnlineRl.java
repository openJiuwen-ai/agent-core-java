/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.jiuwenrl_online;

import com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.GatewayServiceConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.JiuwenConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.JudgeServiceConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LauncherCli;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LauncherCoordinator;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LauncherLoader;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LauncherOnlineRlConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LauncherPaths;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.RealLauncherOrchestrator;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.TrainingConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.TrajectoryConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.VllmServiceConfig;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * JiuwenClaw online RL loop launcher.
 *
 * <p>Mirrors Python's {@code examples.jiuwenrl_online.run_online_rl}.</p>
 */
public final class RunOnlineRl {

    public static final String OPENJIUWEN_PACKAGE = "openjiuwen";
    public static final String JIUWENCLAW_PACKAGE = "jiuwenclaw";

    private static final Logger ROOT_LOGGER = Logger.getLogger("");
    private static final String WORKSPACE_DIR = ".jiuwenclaw";
    private static final String CONFIG_ENV = "config/.env";

    private RunOnlineRl() {
    }

    public static void main(String[] args) throws Exception {
        run(args, new DefaultRuntime());
    }

    public static LaunchPlan run(String[] args, RuntimeAdapter runtime) throws IOException {
        Objects.requireNonNull(runtime, "runtime");
        configureLogging();
        LauncherCli.LauncherArgs parsedArgs = LauncherCli.parseArgs(args);
        Map<String, Object> cliOverrides = LauncherCli.buildCliOverrides(parsedArgs);
        LauncherLoader.RuntimeConfigResult loaded = runtime.loadRuntimeConfig(parsedArgs.getConfig(), cliOverrides);
        LauncherPaths paths = buildLauncherPaths(runtime);
        LauncherOnlineRlConfig launcherConfig = toLauncherConfig(loaded.validatedConfig());
        runtime.runOnlineRlLoop(launcherConfig, loaded.resolvedPath(), paths);
        return new LaunchPlan(parsedArgs, cliOverrides, launcherConfig, loaded.resolvedPath(), paths);
    }

    public static LauncherPaths buildLauncherPaths(RuntimeAdapter runtime) {
        Path workspace = runtime.workspaceRoot();
        return new LauncherPaths(
                runtime.packageParent(OPENJIUWEN_PACKAGE),
                runtime.packageParent(JIUWENCLAW_PACKAGE),
                workspace,
                workspace.resolve(CONFIG_ENV),
                runtime.scriptDir()
        );
    }

    public static Path defaultScriptDir() {
        String override = firstNonBlank(
                System.getProperty("openjiuwen.examples.jiuwenrl_online.script_dir"),
                System.getenv("OPENJIUWEN_JIUWENRL_ONLINE_SCRIPT_DIR")
        );
        if (override != null) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        return Path.of("examples", "jiuwenrl_online").toAbsolutePath().normalize();
    }

    public static Path defaultWorkspaceRoot() {
        String override = firstNonBlank(
                System.getProperty("openjiuwen.examples.jiuwenrl_online.workspace"),
                System.getenv("OPENJIUWEN_JIUWENCLAW_WORKSPACE")
        );
        if (override != null) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), WORKSPACE_DIR).toAbsolutePath().normalize();
    }

    public static Path packageParent(String packageName) {
        String override = firstNonBlank(
                System.getProperty("openjiuwen.examples.jiuwenrl_online." + packageName + ".parent"),
                System.getenv("OPENJIUWEN_PACKAGE_PARENT_" + packageName.toUpperCase().replace('.', '_'))
        );
        if (override != null) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        Path resourceParent = packageParentFromResource(packageName, Thread.currentThread().getContextClassLoader());
        if (resourceParent != null) {
            return resourceParent;
        }
        throw new RuntimeException(packageName
                + " is not importable. Install it with pip install -e . or pip install *.whl.");
    }

    public static LauncherOnlineRlConfig toLauncherConfig(OnlineRLConfig config) {
        Objects.requireNonNull(config, "config");
        config.validate();
        com.openjiuwen.agent_evolving.agent_rl.config.VLLMServiceConfig inference = config.getInference();
        com.openjiuwen.agent_evolving.agent_rl.config.JudgeConfig judge = config.getJudge();
        com.openjiuwen.agent_evolving.agent_rl.config.GatewayServiceConfig gateway = config.getGateway();
        com.openjiuwen.agent_evolving.agent_rl.config.OnlineTrajectoryConfig trajectory = config.getTrajectory();
        com.openjiuwen.agent_evolving.agent_rl.config.OnlineTrainingConfig training = config.getTraining();
        com.openjiuwen.agent_evolving.agent_rl.config.JiuwenConfig jiuwen = config.getJiuwen();

        return new LauncherOnlineRlConfig(
                config.isDemo(),
                new VllmServiceConfig(
                        inference.getModelPath(),
                        inference.getModelName(),
                        inference.getHost(),
                        portOrZero(inference.getPort()),
                        inference.getGpuIds(),
                        inference.getTp(),
                        inference.getExistingUrl(),
                        inference.getHealthTimeout()
                ),
                new JudgeServiceConfig(
                        judge.getModelPath(),
                        judge.getModelName(),
                        judge.getHost(),
                        portOrZero(judge.getPort()),
                        judge.getGpuIds(),
                        judge.getTp(),
                        judge.getExistingUrl(),
                        judge.getHealthTimeout(),
                        judge.isReuseInferenceIfSameModel()
                ),
                new GatewayServiceConfig(
                        gateway.getHost(),
                        portOrZero(gateway.getPort()),
                        gateway.getRedisUrl(),
                        gateway.getRecordDir(),
                        gateway.getLogLevel(),
                        gateway.getHealthTimeout(),
                        gateway.isDisableTrajectoryCollection()
                ),
                new TrajectoryConfig(trajectory.getBatchSize(), trajectory.getMode()),
                new TrainingConfig(
                        training.getGpuIds(),
                        training.getThreshold(),
                        training.getScanInterval(),
                        training.getPpoConfig(),
                        training.getLoraRepo()
                ),
                new JiuwenConfig(
                        jiuwen.isEnabled(),
                        portOrZero(jiuwen.getAgentServerPort()),
                        jiuwen.getAppHost(),
                        portOrZero(jiuwen.getWsPort()),
                        jiuwen.getWebHost(),
                        portOrZero(jiuwen.getWebPort())
                )
        );
    }

    private static Path packageParentFromResource(String packageName, ClassLoader loader) {
        ClassLoader safeLoader = loader == null ? RunOnlineRl.class.getClassLoader() : loader;
        String packagePath = packageName.replace('.', '/');
        Path direct = resourceRoot(safeLoader.getResource(packagePath + "/"), 1);
        if (direct != null) {
            return direct;
        }
        return resourceRoot(safeLoader.getResource("com/" + packagePath + "/"), 2);
    }

    private static Path resourceRoot(URL resource, int segmentsToTrim) {
        if (resource == null || !"file".equalsIgnoreCase(resource.getProtocol())) {
            return null;
        }
        try {
            Path current = Path.of(resource.toURI()).toAbsolutePath().normalize();
            for (int i = 0; i < segmentsToTrim; i++) {
                current = current.getParent();
                if (current == null) {
                    return null;
                }
            }
            return current;
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static int portOrZero(Integer port) {
        return port == null ? 0 : port;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static void configureLogging() {
        ROOT_LOGGER.setLevel(Level.INFO);
        for (java.util.logging.Handler handler : ROOT_LOGGER.getHandlers()) {
            ROOT_LOGGER.removeHandler(handler);
        }
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.INFO);
        handler.setFormatter(new PythonStyleFormatter());
        ROOT_LOGGER.addHandler(handler);
    }

    public interface RuntimeAdapter {
        LauncherLoader.RuntimeConfigResult loadRuntimeConfig(String configPath, Map<String, Object> cliOverrides)
                throws IOException;

        Path packageParent(String packageName);

        Path workspaceRoot();

        Path scriptDir();

        void runOnlineRlLoop(LauncherOnlineRlConfig config, Path configPath, LauncherPaths paths);
    }

    public record LaunchPlan(
            LauncherCli.LauncherArgs args,
            Map<String, Object> cliOverrides,
            LauncherOnlineRlConfig config,
            Path configPath,
            LauncherPaths paths
    ) {
    }

    public static final class DefaultRuntime implements RuntimeAdapter {
        @Override
        public LauncherLoader.RuntimeConfigResult loadRuntimeConfig(String configPath, Map<String, Object> cliOverrides)
                throws IOException {
            return LauncherLoader.loadRuntimeConfig(configPath, cliOverrides);
        }

        @Override
        public Path packageParent(String packageName) {
            return RunOnlineRl.packageParent(packageName);
        }

        @Override
        public Path workspaceRoot() {
            return defaultWorkspaceRoot();
        }

        @Override
        public Path scriptDir() {
            return defaultScriptDir();
        }

        @Override
        public void runOnlineRlLoop(LauncherOnlineRlConfig config, Path configPath, LauncherPaths paths) {
            LauncherCoordinator coordinator = new LauncherCoordinator(new RealLauncherOrchestrator(), millis -> {
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new LauncherCoordinator.ShutdownRequested();
                }
            });
            coordinator.runOnlineRlLoop(config, configPath, paths);
        }
    }

    private static final class PythonStyleFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            return "%1$tF %1$tT %2$s %3$s: %4$s%n".formatted(
                    record.getMillis(),
                    record.getLevel().getName(),
                    record.getLoggerName(),
                    formatMessage(record)
            );
        }
    }
}
