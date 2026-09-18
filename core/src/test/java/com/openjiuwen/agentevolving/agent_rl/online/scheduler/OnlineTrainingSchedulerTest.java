/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.scheduler;

import com.openjiuwen.agentevolving.agent_rl.storage.TrajectorySampleStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mirrors Python's {@code OnlineTrainingScheduler} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/scheduler/online_training_scheduler.py}.
 *
 * <p>Mirrors Python's online training scheduler unit tests in
 * {@code tests/unit_tests/agent_evolving/agent_rl/online/test_online_training_scheduler.py}.</p>
 */
class OnlineTrainingSchedulerTest {

    @Test
    void trainBatchMarksTrainedOnSuccess() {
        FakeStore store = new FakeStore();
        FakeTrainer trainer = new FakeTrainer(false);
        OnlineTrainingScheduler scheduler = scheduler(store, trainer);
        scheduler.setTrainingCountForTesting(3);

        scheduler.trainBatch("u1", List.of(sample("s1")), List.of("s1"));

        assertEquals(List.of(List.of("s1")), store.trained);
        assertEquals(List.of(), store.failed);
        assertEquals(List.of(new TrainCall("u1", List.of(sample("s1")), 3, "/tmp/agent_rl_online")), trainer.calls);
    }

    @Test
    void trainBatchMarksFailedOnError() {
        FakeStore store = new FakeStore();
        FakeTrainer trainer = new FakeTrainer(true);
        OnlineTrainingScheduler scheduler = scheduler(store, trainer);
        scheduler.setTrainingCountForTesting(7);

        scheduler.trainBatch("u2", List.of(sample("s2")), List.of("s2"));

        assertEquals(List.of(), store.trained);
        assertEquals(List.of(List.of("s2")), store.failed);
    }

    @Test
    void pollOnceStartsOnlyOneActiveTrainingTask() {
        FakeStore store = new FakeStore();
        store.usersAboveThreshold = List.of("u1", "u2");
        store.samplesByUser.put("u1", List.of());
        store.samplesByUser.put("u2", List.of(sample("s2")));
        FakeTrainer trainer = new FakeTrainer(false);
        OnlineTrainingScheduler scheduler = new OnlineTrainingScheduler(new OnlineTrainingScheduler.Options()
                .setRedisUrl("")
                .setMinSamplesForTraining(2)
                .setTrainer(trainer)
                .setTrajectoryStore(store)
                .setTrainingExecutor(Runnable::run));

        scheduler.pollOnce();
        scheduler.reapTrainingTask(false);

        assertEquals(1, scheduler.getTrainingCount());
        assertNull(scheduler.getActiveTrainingTask());
        assertNull(scheduler.getActiveTrainingUser());
        assertEquals(List.of(new TrainCall("u2", List.of(sample("s2")), 1, "/tmp/agent_rl_online")), trainer.calls);
        assertEquals(List.of(List.of("s2")), store.trained);
    }

    private static OnlineTrainingScheduler scheduler(FakeStore store, FakeTrainer trainer) {
        return new OnlineTrainingScheduler(new OnlineTrainingScheduler.Options()
                .setRedisUrl("")
                .setTrainer(trainer)
                .setTrajectoryStore(store)
                .setTrainingExecutor(Runnable::run));
    }

    private static Map<String, Object> sample(String sampleId) {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("sample_id", sampleId);
        return sample;
    }

    record TrainCall(String userId, List<Map<String, Object>> samples, int trainingCount, String tmpRoot) {
    }

    static final class FakeTrainer implements PpoTrainingExecutor {
        private final boolean shouldFail;
        final List<TrainCall> calls = new ArrayList<>();

        FakeTrainer(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }

        @Override
        public String trainBatch(String userId, List<Map<String, Object>> samples, int trainingCount, String tmpRoot) {
            calls.add(new TrainCall(userId, List.copyOf(samples), trainingCount, tmpRoot));
            if (shouldFail) {
                throw new RuntimeException("boom");
            }
            return "/tmp/lora";
        }
    }

    static final class FakeStore implements TrajectorySampleStore {
        final List<List<String>> trained = new ArrayList<>();
        final List<List<String>> failed = new ArrayList<>();
        final Map<String, List<Map<String, Object>>> samplesByUser = new LinkedHashMap<>();
        List<String> usersAboveThreshold = List.of();

        @Override
        public void saveSample(Map<String, Object> sample, String userId) {
        }

        @Override
        public int getPendingCount(String userId) {
            return 0;
        }

        @Override
        public List<String> getUsersAboveThreshold(int threshold) {
            return usersAboveThreshold;
        }

        @Override
        public List<Map<String, Object>> fetchAndMarkTraining(String userId, int limit) {
            return samplesByUser.getOrDefault(userId, List.of());
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
        }

        @Override
        public Map<String, Integer> stats() {
            return Map.of();
        }
    }
}
