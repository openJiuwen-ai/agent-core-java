/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.offline;

import com.openjiuwen.agent_evolving.agent_rl.offline.MainTrainer;
import com.openjiuwen.agent_evolving.agent_rl.offline.coordinator.TrainingCoordinator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MainTrainer.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/agent_evolving/agent_rl/offline/test_main_trainer.py}.
 */
@DisplayName("MainTrainer Tests")
class TestMainTrainer {

    @Test
    @DisplayName("init creates coordinator and proxy")
    void testMainTrainerInitCreatesCoordinatorAndProxy() {
        FakeRlTrainer rlTrainer = new FakeRlTrainer();
        FakeAgentFactory agentFactory = new FakeAgentFactory();

        MainTrainer trainer = new MainTrainer(rlTrainer, config(), null, null, agentFactory);

        assertThat(trainer.getTrainingCoordinator()).isInstanceOf(TrainingCoordinator.class);
        assertThat(trainer.getProxyUrl()).isEqualTo("http://127.0.0.1:0");
        assertThat(trainer.getTrainDataset()).containsExactly("batch-1");
    }

    @Test
    @DisplayName("proxy url returns proxy url after start")
    void testProxyUrlReturnsProxyUrlAfterStart() {
        MainTrainer trainer = new MainTrainer(new FakeRlTrainer(), config(), null, null, new FakeAgentFactory());

        trainer.updateBackends(List.of("http://a:8000"));

        assertThat(trainer.getProxyUrl()).isEqualTo("http://127.0.0.1:0");
        assertThat(trainer.isProxyStarted()).isTrue();
    }

    @Test
    @DisplayName("update backends starts proxy and updates agent factory url")
    void testUpdateBackendsCallsProxyUpdateBackendServers() {
        FakeRlTrainer rlTrainer = new FakeRlTrainer();
        FakeAgentFactory agentFactory = new FakeAgentFactory();
        MainTrainer trainer = new MainTrainer(rlTrainer, config(), null, null, agentFactory);

        trainer.updateBackends(List.of("http://a:8000"));

        assertThat(trainer.isProxyStarted()).isTrue();
        assertThat(trainer.getBackendServers()).containsExactly("http://a:8000");
        assertThat(agentFactory.proxyUrl).isEqualTo("http://127.0.0.1:0");
    }

    @Test
    @DisplayName("validate delegates to coordinator and logs metrics")
    void validateDelegatesToCoordinatorAndLogsMetrics() {
        FakeRlTrainer rlTrainer = new FakeRlTrainer();
        rlTrainer.valDataset = List.of(Map.of("val", 1));
        rlTrainer.globalSteps = 7;
        FakeCoordinator coordinator = new FakeCoordinator();
        FakeMetricsTracker tracker = new FakeMetricsTracker();
        FakePersistence persistence = new FakePersistence();
        Map<String, Object> cfg = config();
        cfg.put("trainingCoordinator", coordinator);
        MainTrainer trainer = new MainTrainer(rlTrainer, cfg, tracker, persistence, null);

        Map<String, Object> metrics = trainer.validate();

        assertThat(metrics).containsEntry("val/accuracy", 0.5d);
        assertThat(coordinator.validatedData).isEqualTo(Map.of("val", 1));
        assertThat(trainer.getBackendServers()).containsExactly("http://rollout:8000");
        assertThat(tracker.validationStep).isEqualTo(7);
        assertThat(tracker.validationMetrics).containsOnlyKeys("val/accuracy", "val/sample_count");
        assertThat(persistence.savedStep).isEqualTo(7);
        assertThat(rlTrainer.sleepRolloutCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("run epoch drives trainer batch and checkpoints delegate")
    void runEpochDrivesTrainerAndCheckpointDelegates() {
        FakeRlTrainer rlTrainer = new FakeRlTrainer();
        FakeCoordinator coordinator = new FakeCoordinator();
        FakeMetricsTracker tracker = new FakeMetricsTracker();
        Map<String, Object> cfg = config();
        cfg.put("trainingCoordinator", coordinator);
        MainTrainer trainer = new MainTrainer(rlTrainer, cfg, tracker, null, null);

        trainer.runEpoch(3);
        trainer.saveCheckpoint("ckpt-dir");
        trainer.loadCheckpoint("ckpt-dir");

        assertThat(rlTrainer.globalSteps).isEqualTo(1);
        assertThat(rlTrainer.trainStepCalls).isEqualTo(1);
        assertThat(rlTrainer.lastOriginBatch).isEqualTo("formatted:batch-1");
        assertThat(rlTrainer.lastTrainBatch).isEqualTo("formatted:assembled:batch-1");
        assertThat(tracker.trainingMetrics).containsEntry("training/epoch", 3);
        assertThat(tracker.trainingMetrics).containsEntry("training/global_step", 1);
        assertThat(rlTrainer.savedPath).isEqualTo("ckpt-dir");
        assertThat(rlTrainer.loadedPath).isEqualTo("ckpt-dir");
    }

    @Test
    @DisplayName("get metrics returns latest training validation and proxy state")
    void getMetricsReturnsLatestTrainingValidationAndProxyState() {
        FakeRlTrainer rlTrainer = new FakeRlTrainer();
        rlTrainer.valDataset = List.of(Map.of("val", 1));
        FakeCoordinator coordinator = new FakeCoordinator();
        Map<String, Object> cfg = config();
        cfg.put("trainingCoordinator", coordinator);
        MainTrainer trainer = new MainTrainer(rlTrainer, cfg, null, null, null);

        trainer.runEpoch(0);
        trainer.validate();

        Map<String, Object> metrics = trainer.getMetrics();

        assertThat(metrics).containsEntry("loss", 0.25d);
        assertThat(metrics).containsEntry("training/global_step", 1);
        assertThat(metrics).containsEntry("proxy/url", "http://127.0.0.1:0");
        assertThat(metrics).containsKey("validation");
    }

    private static Map<String, Object> config() {
        Map<String, Object> config = new HashMap<>();
        config.put("data", Map.of(
                "max_prompt_length", 32,
                "max_response_length", 16,
                "dataloader_num_workers", 0,
                "train_batch_size", 1,
                "validation_shuffle", false
        ));
        config.put("trainer", Map.of(
                "total_epochs", 1,
                "test_freq", 0,
                "save_freq", 0
        ));
        config.put("actor_rollout_ref", Map.of("rollout", Map.of("n", 1)));
        config.put("JiuwenRL", Map.of(
                "whole_trajectory", false,
                "llm_timeout_seconds", 30_000,
                "custom_fn", Map.of(
                        "classifier", "default_classify_rollouts",
                        "validator", "default_validate_stop",
                        "sampler", "default_sampling"
                )
        ));
        return config;
    }

    public static final class FakeRlTrainer {
        private final List<Object> trainDataset = List.of("batch-1");
        private List<Object> valDataset = new ArrayList<>();
        private final Object tokenizer = new Object();
        private int globalSteps;
        private int trainStepCalls;
        private int sleepRolloutCalls;
        private String savedPath;
        private String loadedPath;
        private Object lastOriginBatch;
        private Object lastTrainBatch;

        public Object getRlFormatData(Object batch) {
            return "formatted:" + batch;
        }

        public List<String> wakeUpRollout() {
            return List.of("http://rollout:8000");
        }

        public void sleepRollout() {
            sleepRolloutCalls++;
        }

        public Map<String, Object> trainStep(Object originBatch, Object trainBatch) {
            trainStepCalls++;
            lastOriginBatch = originBatch;
            lastTrainBatch = trainBatch;
            return Map.of("loss", 0.25d, "rollout/reward_mean", 1.0d);
        }

        public Map<String, Object> getMetrics() {
            return Map.of("trainer/status", "ok");
        }

        public void saveCheckpoint(String path) {
            savedPath = path;
        }

        public void loadCheckpoint(String path) {
            loadedPath = path;
        }
    }

    public static final class FakeCoordinator {
        private Object validatedData;
        private double lastAvgTurnCount = 2.0d;

        public Object runDemonLoopSync(Object batch, Object device, int step) {
            return "assembled:" + batch;
        }

        public Map<String, Object> validateSync(Object validationData) {
            validatedData = validationData;
            return Map.of(
                    "val/accuracy", 0.5d,
                    "val/sample_count", 2,
                    "val/reward_list", List.of(0.0d, 1.0d)
            );
        }
    }

    public static final class FakeMetricsTracker {
        private int validationStep = -1;
        private Map<String, Object> validationMetrics = new HashMap<>();
        private Map<String, Object> trainingMetrics = new HashMap<>();

        public void logValidation(int step, Map<String, Object> metrics) {
            validationStep = step;
            validationMetrics = new HashMap<>(metrics);
        }

        public void logTrainingStep(Map<String, Object> metrics) {
            trainingMetrics = new HashMap<>(metrics);
        }
    }

    public static final class FakePersistence {
        private int savedStep = -1;
        private Map<String, Object> savedMetrics = new HashMap<>();

        public void saveStepSummary(int step, Map<String, Object> metrics) {
            savedStep = step;
            savedMetrics = new HashMap<>(metrics);
        }
    }

    public static final class FakeAgentFactory {
        private String proxyUrl;
    }
}
