/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.optimizer;

import com.openjiuwen.agentevolving.agent_rl.config.RLConfig;
import com.openjiuwen.agentevolving.agent_rl.config.TrainingConfig;
import com.openjiuwen.agentevolving.agent_rl.online.scheduler.OnlineTrainingScheduler;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code BaseRLOptimizer}, {@code OfflineRLOptimizer}, and
 * {@code OnlineRLOptimizer} in
 * {@code openjiuwen/agent_evolving/agent_rl/optimizer/rl_optimizer.py}.</p>
 *
 * <p>Mirrors Python's online optimizer setup tests in
 * {@code tests/unit_tests/agent_evolving/agent_rl/optimizer/test_online_rl_optimizer.py}.</p>
 */
class RLOptimizerTest {

    @TempDir
    Path tempDir;

    @Test
    void baseOptimizerBuildsRunNameAndEnvironmentLikePython() {
        RLConfig config = config(tempDir.resolve("model"));
        Map<String, String> env = new HashMap<>();
        env.put("http_proxy", "http://proxy");
        env.put("HTTPS_PROXY", "https://proxy");

        TestBaseRLOptimizer optimizer = new TestBaseRLOptimizer(
                config,
                env,
                OffsetDateTime.of(2026, 6, 18, 3, 4, 5, 0, ZoneOffset.UTC)
        );
        Map<String, String> effective = optimizer.setupEnvironment();

        assertEquals("exp_20260618_030405", optimizer.getRunName());
        assertEquals("1", effective.get("HYDRA_FULL_ERROR"));
        assertEquals("0,7", effective.get("ASCEND_RT_VISIBLE_DEVICES"));
        assertEquals("1", effective.get("VLLM_USE_V1"));
        assertFalse(effective.containsKey("http_proxy"));
        assertFalse(effective.containsKey("HTTPS_PROXY"));
        assertEquals("127.0.0.1,localhost", effective.get("NO_PROXY"));
    }

    @Test
    void offlineOptimizerComposesHydraConfigAndInitializesLocalRunner() throws Exception {
        Path modelDir = Files.createDirectory(tempDir.resolve("offline-model"));
        RLConfig config = config(modelDir);
        OfflineRLOptimizer optimizer = new OfflineRLOptimizer(config);
        optimizer.setTools(List.of(new NamedTool("search")));
        optimizer.setTaskDataFn(sample -> Map.of("copied", sample));

        optimizer.initTrainer();

        assertNotNull(optimizer.getRunner());
        assertTrue(optimizer.getRunner().isReady());
        assertEquals(List.of("search"), optimizer.getToolNames());
        assertTrue(optimizer.isRayInitialized());

        Map<String, Object> hydra = optimizer.composeHydraConfig();
        assertEquals("grpo", TaskRunner.mapAt(hydra, "algorithm").get("adv_estimator"));
        assertEquals(modelDir.toString(), TaskRunner.mapAt(hydra, "actor_rollout_ref", "model").get("path"));
        assertEquals("exp_" + optimizer.getRunName().substring("exp_".length()),
                TaskRunner.mapAt(hydra, "trainer").get("experiment_name"));
    }

    @Test
    void onlineOptimizerRejectsGatewayHttpAndStartsSchedulerFromRedis() {
        RLConfig config = config(tempDir.resolve("online-model"));
        OnlineRLOptimizer optimizer = new OnlineRLOptimizer(config);
        AtomicBoolean started = new AtomicBoolean();
        optimizer.setSchedulerFactory(options -> new RecordingScheduler(options, started));

        assertThrows(IllegalArgumentException.class, () -> optimizer.setupGateway("http://localhost:8000"));

        optimizer.setupRedis("redis://localhost:6379/0/", 5.0, 2)
                .setupInference("http://vllm:8000")
                .setupTrainingGpu("2,3", 2)
                .initTrainer();
        optimizer.startTraining();

        assertEquals("redis://localhost:6379/0", optimizer.getRedisUrl());
        assertEquals("2,3", optimizer.getTrainingGpuIds());
        assertEquals(2, optimizer.getNprocPerNode());
        assertTrue(started.get());
        assertTrue(optimizer.isRayInitialized());
    }

    @Test
    void setupGatewayRejectsLegacyHttpGatewayUrl() {
        OnlineRLOptimizer optimizer = new OnlineRLOptimizer(config(tempDir.resolve("gateway-http-model")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> optimizer.setupGateway("http://127.0.0.1:18080/v1")
        );

        assertTrue(exception.getMessage().contains("setup_gateway() no longer accepts HTTP Gateway URLs"));
        assertTrue(exception.getMessage().contains("setup_redis()"));
    }

    @Test
    void setupGatewayAcceptsRedisUrlForTransition() {
        OnlineRLOptimizer optimizer = new OnlineRLOptimizer(config(tempDir.resolve("gateway-redis-model")));

        OnlineRLOptimizer returned = optimizer.setupGateway("redis://127.0.0.1:6379/0", 15.0, 8);

        assertSame(optimizer, returned);
        assertEquals("redis://127.0.0.1:6379/0", optimizer.getRedisUrl());
        assertEquals(15.0d, optimizer.getPollInterval());
        assertEquals(8, optimizer.getMinSamples());
    }

    @Disabled("Skipped in Python baseline: tests/unit_tests/agent_evolving/agent_rl/optimizer/test_rl_optimizer.py "
            + "could not import ray")
    @Test
    void rayDependentPythonOptimizerSuiteSkippedInBaseline() {
    }

    private RLConfig config(Path modelDir) {
        TrainingConfig training = new TrainingConfig();
        training.setProjectName("project");
        training.setExperimentName("exp");
        training.setModelPath(modelDir.toString());
        training.setSavePath(tempDir.resolve("save").toString());
        training.setVisibleDevice("0,7");
        training.setNGpusPerNode(1);
        training.setNnodes(1);
        RLConfig config = new RLConfig(training);
        config.validate();
        return config;
    }

    private static final class TestBaseRLOptimizer extends BaseRLOptimizer {
        private TestBaseRLOptimizer(RLConfig config, Map<String, String> env, OffsetDateTime now) {
            super(config, env, now);
        }

        @Override
        public void initTrainer() {
        }

        @Override
        public void startTraining() {
        }

        @Override
        public void stop() {
        }
    }

    private record NamedTool(String name) {
    }

    private static final class RecordingScheduler extends OnlineTrainingScheduler {
        private final AtomicBoolean started;

        private RecordingScheduler(Options options, AtomicBoolean started) {
            super(options);
            this.started = started;
        }

        @Override
        public void start() {
            started.set(true);
        }

        @Override
        public void stop() {
        }
    }
}
