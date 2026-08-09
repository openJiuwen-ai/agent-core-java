/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.offline;

import com.openjiuwen.agentevolving.agent_rl.offline.MainTrainer.RlSampler;
import com.openjiuwen.agentevolving.agent_rl.offline.MainTrainer.SamplerType;
import com.openjiuwen.agentevolving.agent_rl.offline.store.RLMetricsTracker;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code MainTrainer} tests in
 * {@code tests/unit_tests/agent_evolving/agent_rl/offline/test_main_trainer.py}.
 */
class MainTrainerTest {

    @Test
    void constructorCreatesCoordinatorProxyAndDataloaders() {
        FakeRlTrainer trainer = new FakeRlTrainer(rows(4), null);

        MainTrainer mainTrainer = new MainTrainer(trainer, config());

        assertNotNull(mainTrainer.getTrainingCoordinator());
        assertNotNull(mainTrainer.getProxy());
        assertSame(trainer.trainDataset, mainTrainer.getTrainDataset());
        assertEquals(2, mainTrainer.getTrainDataloader().size());
        assertEquals(SamplerType.RANDOM, mainTrainer.getTrainDataloader().getSampler().samplerType());
    }

    @Test
    void createRlSamplerHonorsSequentialConfig() {
        RlSampler sampler = MainTrainer.createRlSampler(Map.of("sampler", "sequential"), rows(2));

        assertEquals(SamplerType.SEQUENTIAL, sampler.samplerType());
    }

    @Test
    void proxyUrlReturnsStartedProxyUrlAndUpdateBackendsStoresServers() {
        FakeRlTrainer trainer = new FakeRlTrainer(rows(2), null);
        MainTrainer mainTrainer = new MainTrainer(trainer, config());
        try {
            mainTrainer.updateBackends(List.of("127.0.0.1:9000"));

            assertTrue(mainTrainer.isProxyStarted());
            assertTrue(mainTrainer.proxyUrl().startsWith("http://127.0.0.1:"));
            assertFalse(mainTrainer.proxyUrl().endsWith(":0"));
            assertEquals(List.of("127.0.0.1:9000"), mainTrainer.getProxy().getBackendServers());
        } finally {
            mainTrainer.stop();
        }
    }

    @Test
    void validateReturnsNullWhenNoValidationDataset() {
        FakeRlTrainer trainer = new FakeRlTrainer(rows(2), null);
        MainTrainer mainTrainer = new MainTrainer(trainer, config());

        assertNull(mainTrainer.validate());
    }

    @Test
    void stopFinishesMetricsTrackerAndStopsProxy() {
        FakeRlTrainer trainer = new FakeRlTrainer(rows(2), null);
        RLMetricsTracker tracker = new RLMetricsTracker("p", "e", List.of("tensorboard"));
        MainTrainer mainTrainer = new MainTrainer(trainer, config(), null, null, null, null, null, tracker, null);
        mainTrainer.updateBackends(List.of("127.0.0.1:9001"));

        mainTrainer.stop();

        assertTrue(tracker.isFinished());
        assertFalse(mainTrainer.isProxyStarted());
    }

    @Test
    @Disabled("Skipped in Python source: tests/unit_tests/agent_evolving/agent_rl/offline/test_main_trainer.py "
            + "uses pytest.importorskip(\"torch\") and the focused Python baseline skipped the module when torch "
            + "was unavailable.")
    void pythonTorchDependentMainTrainerTestsAreSkippedInSource() {
    }

    private static Map<String, Object> config() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("max_prompt_length", 32);
        data.put("max_response_length", 16);
        data.put("dataloader_num_workers", 0);
        data.put("train_batch_size", 2);
        data.put("validation_shuffle", false);

        Map<String, Object> trainer = new LinkedHashMap<>();
        trainer.put("total_epochs", 1);
        trainer.put("test_freq", 0);
        trainer.put("save_freq", 0);

        Map<String, Object> jiuwen = new LinkedHashMap<>();
        jiuwen.put("whole_trajectory", false);
        jiuwen.put("llm_timeout_seconds", 30_000);
        jiuwen.put("custom_fn", Map.of(
                "classifier", "default_classify_rollouts",
                "validator", "default_validate_stop",
                "sampler", "default_sampling"
        ));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("data", data);
        config.put("trainer", trainer);
        config.put("JiuwenRL", jiuwen);
        return config;
    }

    private static List<Map<String, Object>> rows(int count) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            rows.add(Map.of(
                    "fake_ids", List.of(index),
                    "prompt", "p" + index
            ));
        }
        return rows;
    }

    private static final class FakeRlTrainer {
        private final Object trainDataset;
        private final Object valDataset;
        private final Object tokenizer = Map.of("pad_token_id", 0);
        private int globalSteps = 7;
        private boolean rolloutSleeping;

        private FakeRlTrainer(Object trainDataset, Object valDataset) {
            this.trainDataset = trainDataset;
            this.valDataset = valDataset;
        }

        public Object getTrainDataset() {
            return trainDataset;
        }

        public Object getValDataset() {
            return valDataset;
        }

        public Object getTokenizer() {
            return tokenizer;
        }

        public int getGlobalSteps() {
            return globalSteps;
        }

        public void setGlobalSteps(int globalSteps) {
            this.globalSteps = globalSteps;
        }

        public List<String> wakeUpRollout() {
            return List.of("127.0.0.1:9000");
        }

        public void sleepRollout() {
            rolloutSleeping = true;
        }

        public boolean isRolloutSleeping() {
            return rolloutSleeping;
        }
    }
}
