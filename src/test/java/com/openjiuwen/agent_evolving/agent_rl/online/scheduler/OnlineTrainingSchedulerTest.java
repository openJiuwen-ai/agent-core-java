/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.scheduler;

import com.openjiuwen.agent_evolving.agent_rl.storage.TrajectorySampleStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OnlineTrainingSchedulerTest {

    @Test
    void trainBatchMarksTrainedOnSuccess() {
        OnlineTrainingScheduler scheduler = new OnlineTrainingScheduler("");
        FakeStore store = new FakeStore();
        FakeTrainer trainer = new FakeTrainer(false);
        scheduler.setTrajectoryStore(store);
        scheduler.setTrainer(trainer);
        scheduler.setTrainingCount(3);

        scheduler.trainBatch("u1", List.of(Map.of("sample_id", "s1")), List.of("s1"));

        assertEquals(List.of(List.of("s1")), store.trained);
        assertEquals(List.of(), store.failed);
        assertEquals(List.of(Map.of(
                "user_id", "u1",
                "samples", List.of(Map.of("sample_id", "s1")),
                "training_count", 3,
                "tmp_root", "/tmp/agent_rl_online"
        )), trainer.calls);
    }

    @Test
    void trainBatchMarksFailedOnError() {
        OnlineTrainingScheduler scheduler = new OnlineTrainingScheduler("");
        FakeStore store = new FakeStore();
        FakeTrainer trainer = new FakeTrainer(true);
        scheduler.setTrajectoryStore(store);
        scheduler.setTrainer(trainer);
        scheduler.setTrainingCount(7);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> scheduler.trainBatch("u2", List.of(Map.of("sample_id", "s2")), List.of("s2")));

        assertEquals("boom", error.getMessage());
        assertEquals(List.of(), store.trained);
        assertEquals(List.of(List.of("s2")), store.failed);
    }

    @Test
    void trainBatchRequiresTrajectoryStore() {
        OnlineTrainingScheduler scheduler = new OnlineTrainingScheduler("");
        scheduler.setTrainer(new FakeTrainer(false));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> scheduler.trainBatch("u1", List.of(), List.of()));
        assertEquals("trajectory store is not initialized", error.getMessage());
    }

    @Test
    void trainBatchRequiresTrainer() {
        OnlineTrainingScheduler scheduler = new OnlineTrainingScheduler("");
        scheduler.setTrajectoryStore(new FakeStore());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> scheduler.trainBatch("u1", List.of(), List.of()));
        assertEquals("trainer is not initialized", error.getMessage());
    }

    static final class FakeStore implements TrajectorySampleStore {
        final List<List<String>> trained = new ArrayList<>();
        final List<List<String>> failed = new ArrayList<>();

        @Override
        public void saveSample(Map<String, Object> sample, String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getPendingCount(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> getUsersAboveThreshold(int threshold) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Map<String, Object>> fetchAndMarkTraining(String userId, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markTrained(List<String> sampleIds) {
            trained.add(List.copyOf(sampleIds));
        }

        @Override
        public void markFailed(List<String> sampleIds) {
            failed.add(List.copyOf(sampleIds));
        }

        @Override
        public void resetToPending(List<String> sampleIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Integer> stats() {
            return Map.of();
        }
    }

    static final class FakeTrainer implements PpoTrainingExecutor {
        private final boolean shouldFail;
        final List<Map<String, Object>> calls = new ArrayList<>();

        FakeTrainer(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }

        @Override
        public String trainBatch(String userId, List<Map<String, Object>> samples, int trainingCount, String tmpRoot) {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("user_id", userId);
            call.put("samples", samples);
            call.put("training_count", trainingCount);
            call.put("tmp_root", tmpRoot);
            calls.add(call);
            if (shouldFail) {
                throw new RuntimeException("boom");
            }
            return "/tmp/lora";
        }
    }
}
