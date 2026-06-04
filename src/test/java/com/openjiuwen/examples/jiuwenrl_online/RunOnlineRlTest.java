/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.jiuwenrl_online;

import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LauncherCli;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LauncherLoader;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LauncherOnlineRlConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.launcher.LauncherPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunOnlineRlTest {

    @TempDir
    Path tempDir;

    @Test
    void parseArgsSupportsPythonLauncherOptionsAndBuildsOverrides() {
        LauncherCli.LauncherArgs args = LauncherCli.parseArgs(new String[] {
                "--config", "online.yaml",
                "--model-path=/models/base",
                "--model-name", "base-model",
                "--vllm-gpu", "0,1",
                "--vllm-tp", "2",
                "--vllm-port", "18001",
                "--inference-url", "http://inference.local",
                "--judge-model-path", "/models/judge",
                "--judge-model-name", "judge-model",
                "--judge-gpu", "2,3",
                "--judge-tp", "1",
                "--judge-port", "18002",
                "--judge-url", "http://judge.local",
                "--gateway-port", "18080",
                "--redis-url", "redis://localhost:6379/0",
                "--threshold", "9",
                "--scan-interval", "11",
                "--train-gpu", "4,5",
                "--ppo-config", "ppo.yaml",
                "--trajectory-batch-size", "6",
                "--lora-repo", "lora_repo",
                "--jiuwen-agent-server-port", "18091",
                "--demo",
                "--skip_jiuwen",
                "--jiuwen-ws-port", "19000",
                "--jiuwen-web-host", "0.0.0.0",
                "--jiuwen-web-port", "5173"
        });

        Map<String, Object> overrides = LauncherCli.buildCliOverrides(args);

        assertThat(args.getConfig()).isEqualTo("online.yaml");
        assertThat(overrides).containsEntry("demo", true);
        assertNested(overrides, "inference", "model_path", "/models/base");
        assertNested(overrides, "inference", "tp", 2);
        assertNested(overrides, "inference", "existing_url", "http://inference.local");
        assertNested(overrides, "judge", "model_name", "judge-model");
        assertNested(overrides, "gateway", "redis_url", "redis://localhost:6379/0");
        assertNested(overrides, "training", "threshold", 9);
        assertNested(overrides, "training", "scan_interval", 11);
        assertNested(overrides, "trajectory", "batch_size", 6);
        assertNested(overrides, "jiuwen", "enabled", false);
        assertNested(overrides, "jiuwen", "web_host", "0.0.0.0");
    }

    @Test
    void parseArgsRejectsUnknownAndMissingValues() {
        assertThatThrownBy(() -> LauncherCli.parseArgs(new String[] {"--bad"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown argument");
        assertThatThrownBy(() -> LauncherCli.parseArgs(new String[] {"--config"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing value");
        assertThatThrownBy(() -> LauncherCli.parseArgs(new String[] {"--vllm-port", "abc"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid integer");
    }

    @Test
    void runLoadsConfigBuildsPathsAndDelegatesToLauncherLoop() throws Exception {
        Path config = tempDir.resolve("online.yaml");
        Files.writeString(config, """
                inference:
                  model_path: /models/base
                  model_name: BaseModel
                  existing_url: http://inference.local
                judge:
                  existing_url: http://judge.local
                gateway:
                  port: 19003
                  redis_url: redis://localhost:6379/0
                jiuwen:
                  enabled: false
                training:
                  threshold: 4
                """);
        FakeRuntime runtime = new FakeRuntime(tempDir);

        RunOnlineRl.LaunchPlan plan = RunOnlineRl.run(new String[] {
                "--config", config.toString(),
                "--threshold", "9",
                "--lora-repo", tempDir.resolve("lora").toString(),
                "--skip-jiuwen"
        }, runtime);

        assertThat(runtime.loadedConfigPath).isEqualTo(config.toString());
        assertNested(runtime.loadedOverrides, "training", "threshold", 9);
        assertNested(runtime.loadedOverrides, "training", "lora_repo", tempDir.resolve("lora").toString());
        assertNested(runtime.loadedOverrides, "jiuwen", "enabled", false);
        assertThat(runtime.loopCalled).isTrue();
        assertThat(runtime.loopConfig).isSameAs(plan.config());
        assertThat(runtime.loopConfig.training().threshold()).isEqualTo(9);
        assertThat(runtime.loopConfig.training().loraRepo()).isEqualTo(tempDir.resolve("lora").toString());
        assertThat(runtime.loopConfig.jiuwen().enabled()).isFalse();
        assertThat(runtime.loopConfig.inference().existingUrl()).isEqualTo("http://inference.local");
        assertThat(plan.configPath()).isEqualTo(config.toAbsolutePath().normalize());
        assertThat(plan.paths().agentCoreRoot()).isEqualTo(tempDir.resolve("openjiuwen-root"));
        assertThat(plan.paths().jiuwenclawRepo()).isEqualTo(tempDir.resolve("jiuwenclaw-root"));
        assertThat(plan.paths().workspaceRoot()).isEqualTo(tempDir.resolve("workspace"));
        assertThat(plan.paths().workspaceEnv()).isEqualTo(tempDir.resolve("workspace/config/.env"));
        assertThat(plan.paths().scriptDir()).isEqualTo(tempDir.resolve("script"));
    }

    @Test
    void toLauncherConfigPreservesValidatedFields() {
        com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig config =
                new com.openjiuwen.agent_evolving.agent_rl.config.OnlineRLConfig();
        config.getInference().setExistingUrl("http://inference.local");
        config.getInference().setModelPath("/models/base");
        config.getInference().setModelName("base-model");
        config.getJudge().setExistingUrl("http://judge.local");
        config.getGateway().setPort(18080);
        config.getGateway().setRedisUrl("redis://localhost:6379/0");
        config.getTrajectory().setBatchSize(8);
        config.getTraining().setThreshold(12);
        config.getTraining().setScanInterval(13);
        config.getJiuwen().setEnabled(false);

        LauncherOnlineRlConfig launcherConfig = RunOnlineRl.toLauncherConfig(config);

        assertThat(launcherConfig.inference().modelPath()).isEqualTo("/models/base");
        assertThat(launcherConfig.inference().modelName()).isEqualTo("base-model");
        assertThat(launcherConfig.judge().existingUrl()).isEqualTo("http://judge.local");
        assertThat(launcherConfig.gateway().port()).isEqualTo(18080);
        assertThat(launcherConfig.gateway().redisUrl()).isEqualTo("redis://localhost:6379/0");
        assertThat(launcherConfig.trajectory().batchSize()).isEqualTo(8);
        assertThat(launcherConfig.training().threshold()).isEqualTo(12);
        assertThat(launcherConfig.training().scanInterval()).isEqualTo(13);
        assertThat(launcherConfig.jiuwen().enabled()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static void assertNested(Map<String, Object> values, String section, String key, Object expected) {
        assertThat((Map<String, Object>) values.get(section)).containsEntry(key, expected);
    }

    private static final class FakeRuntime implements RunOnlineRl.RuntimeAdapter {
        private final Path root;
        private String loadedConfigPath;
        private Map<String, Object> loadedOverrides;
        private boolean loopCalled;
        private LauncherOnlineRlConfig loopConfig;

        private FakeRuntime(Path root) {
            this.root = root;
        }

        @Override
        public LauncherLoader.RuntimeConfigResult loadRuntimeConfig(String configPath, Map<String, Object> cliOverrides)
                throws IOException {
            this.loadedConfigPath = configPath;
            this.loadedOverrides = cliOverrides;
            return LauncherLoader.loadRuntimeConfig(configPath, cliOverrides);
        }

        @Override
        public Path packageParent(String packageName) {
            return root.resolve(packageName + "-root");
        }

        @Override
        public Path workspaceRoot() {
            return root.resolve("workspace");
        }

        @Override
        public Path scriptDir() {
            return root.resolve("script");
        }

        @Override
        public void runOnlineRlLoop(LauncherOnlineRlConfig config, Path configPath, LauncherPaths paths) {
            this.loopCalled = true;
            this.loopConfig = config;
        }
    }
}
