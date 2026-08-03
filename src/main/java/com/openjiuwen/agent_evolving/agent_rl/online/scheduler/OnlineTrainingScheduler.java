/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.scheduler;

import com.openjiuwen.agent_evolving.agent_rl.online.inference.InferenceNotifier;
import com.openjiuwen.agent_evolving.agent_rl.storage.LoRARepository;
import com.openjiuwen.agent_evolving.agent_rl.storage.TrajectorySampleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polls the trajectory store and triggers PPO LoRA training.
 *
 * <p>Mirrors Python's {@code OnlineTrainingScheduler} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/scheduler/online_training_scheduler.py}.</p>
 */
public class OnlineTrainingScheduler implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("online_rl.scheduler");
    private static final long STOP_JOIN_TIMEOUT_MILLIS = 15_000L;
    private static final String DEFAULT_REDIS_URL = "redis://127.0.0.1:6379/0";
    private static final String DEFAULT_TMP_ROOT = "/tmp/agent_rl_online";

    private final String redisUrl;
    private final double pollInterval;
    private final int minSamplesForTraining;
    private final String tmpRoot;
    private final PpoTrainingExecutor trainer;
    private final RedisStoreFactory redisStoreFactory;
    private final Executor trainingExecutor;
    private final AtomicBoolean stopEvent = new AtomicBoolean();

    private volatile Thread thread;
    private volatile TrajectorySampleStore trajectoryStore;
    private volatile CompletableFuture<Void> activeTrainingTask;
    private volatile String activeTrainingUser;
    private volatile int trainingCount;

    public OnlineTrainingScheduler() {
        this(new Options());
    }

    public OnlineTrainingScheduler(String redisUrl) {
        this(new Options().setRedisUrl(redisUrl));
    }

    public OnlineTrainingScheduler(Options options) {
        Options safeOptions = options != null ? options : new Options();
        this.redisUrl = safeOptions.redisUrl;
        this.pollInterval = safeOptions.pollInterval;
        this.minSamplesForTraining = safeOptions.minSamplesForTraining;
        this.tmpRoot = safeOptions.tmpRoot;
        this.trainer = safeOptions.trainer != null
                ? safeOptions.trainer
                : new PpoExecutor(
                        safeOptions.baseModelPath,
                        safeOptions.loraRepo,
                        safeOptions.notifier,
                        safeOptions.nprocPerNode,
                        safeOptions.trainingGpuIds,
                        safeOptions.ppoConfigPath
                );
        this.redisStoreFactory = safeOptions.redisStoreFactory;
        this.trainingExecutor = safeOptions.trainingExecutor;
        this.trajectoryStore = safeOptions.trajectoryStore;
    }

    public void start() {
        Thread current = thread;
        if (current != null && current.isAlive()) {
            LOGGER.warn("OnlineTrainingScheduler already running");
            return;
        }
        stopEvent.set(false);
        Thread worker = new Thread(this::pollLoop, "OnlineTrainScheduler");
        worker.setDaemon(true);
        thread = worker;
        worker.start();
        LOGGER.info(
                "OnlineTrainingScheduler started: redis={} min_samples={} poll={}s",
                redisUrl,
                minSamplesForTraining,
                Math.round(pollInterval)
        );
    }

    public void stop() {
        stopEvent.set(true);
        Thread current = thread;
        if (current != null) {
            try {
                current.join(STOP_JOIN_TIMEOUT_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            if (current.isAlive()) {
                LOGGER.warn("OnlineTrainingScheduler stop timed out while training is still in progress");
            }
        }
        closeTrainer(false);
        LOGGER.info("OnlineTrainingScheduler stopped");
    }

    @Override
    public void close() {
        stop();
    }

    void pollOnce() {
        TrajectorySampleStore store = trajectoryStore;
        if (store == null) {
            return;
        }
        CompletableFuture<Void> currentTask = activeTrainingTask;
        if (currentTask != null) {
            return;
        }

        List<String> userIds = store.getUsersAboveThreshold(minSamplesForTraining);
        if (userIds == null || userIds.isEmpty()) {
            LOGGER.debug("No users above threshold={}", minSamplesForTraining);
            return;
        }

        for (String userId : userIds) {
            List<Map<String, Object>> samples = store.fetchAndMarkTraining(userId, minSamplesForTraining);
            if (samples == null || samples.isEmpty()) {
                continue;
            }
            List<String> sampleIds = sampleIds(samples);
            trainingCount += 1;
            int currentTrainingCount = trainingCount;
            LOGGER.info(
                    "Triggering PPO training #{} for user={} samples={}",
                    currentTrainingCount,
                    userId,
                    samples.size()
            );
            activeTrainingUser = userId;
            activeTrainingTask = CompletableFuture.runAsync(
                    () -> trainBatch(userId, samples, sampleIds),
                    trainingExecutor
            );
            return;
        }
    }

    void reapTrainingTask(boolean wait) {
        CompletableFuture<Void> task = activeTrainingTask;
        if (task == null) {
            return;
        }
        if (!wait && !task.isDone()) {
            return;
        }
        String userId = activeTrainingUser;
        try {
            if (wait) {
                task.join();
            } else {
                task.getNow(null);
            }
        } catch (CompletionException exception) {
            LOGGER.error("Background PPO training task failed for user={}", userId, exception);
        } finally {
            activeTrainingTask = null;
            activeTrainingUser = null;
        }
    }

    void trainBatch(String userId, List<Map<String, Object>> samples, List<String> sampleIds) {
        TrajectorySampleStore store = trajectoryStore;
        if (store == null) {
            return;
        }
        List<String> safeSampleIds = sampleIds != null ? sampleIds : List.of();
        try {
            trainer.trainBatch(
                    userId,
                    samples != null ? samples : List.of(),
                    trainingCount,
                    tmpRoot
            );
            store.markTrained(safeSampleIds);
        } catch (RuntimeException exception) {
            LOGGER.error("PPO training #{} failed for user={}", trainingCount, userId, exception);
            store.markFailed(safeSampleIds);
        }
    }

    int getTrainingCount() {
        return trainingCount;
    }

    CompletableFuture<Void> getActiveTrainingTask() {
        return activeTrainingTask;
    }

    String getActiveTrainingUser() {
        return activeTrainingUser;
    }

    void setTrajectoryStoreForTesting(TrajectorySampleStore store) {
        trajectoryStore = store;
    }

    void setTrainingCountForTesting(int trainingCount) {
        this.trainingCount = trainingCount;
    }

    private void pollLoop() {
        if (isBlank(redisUrl)) {
            LOGGER.warn("OnlineTrainingScheduler disabled: redis_url is empty");
            return;
        }
        try {
            if (trajectoryStore == null) {
                trajectoryStore = redisStoreFactory.create(redisUrl);
            }
            if (trajectoryStore == null) {
                LOGGER.warn("OnlineTrainingScheduler disabled: no trajectory store for redis_url={}", redisUrl);
                return;
            }
            pollMain();
        } finally {
            closeTrainer(true);
            trajectoryStore = null;
        }
    }

    private void pollMain() {
        while (!stopEvent.get()) {
            try {
                reapTrainingTask(false);
                pollOnce();
            } catch (RuntimeException exception) {
                LOGGER.error("Error in online training scheduler poll", exception);
            }
            sleepPollInterval();
        }
        reapTrainingTask(true);
    }

    private void sleepPollInterval() {
        long sleepMillis = Math.max(0L, Math.round(pollInterval * 1_000.0d));
        if (sleepMillis == 0L || stopEvent.get()) {
            return;
        }
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            stopEvent.set(true);
        }
    }

    private void closeTrainer(boolean asyncClose) {
        if (trainer instanceof PpoExecutor ppoExecutor && asyncClose) {
            ppoExecutor.aclose();
            return;
        }
        if (trainer instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                LOGGER.debug("Failed to close PPO trainer: {}", exception.getMessage());
            }
        }
    }

    private static List<String> sampleIds(List<Map<String, Object>> samples) {
        List<String> result = new ArrayList<>();
        for (Map<String, Object> sample : samples) {
            Object sampleId = sample != null ? sample.get("sample_id") : null;
            if (pythonTruthy(sampleId)) {
                result.add(String.valueOf(sampleId));
            }
        }
        return result;
    }

    private static boolean pythonTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence chars) {
            return !chars.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @FunctionalInterface
    public interface RedisStoreFactory {
        TrajectorySampleStore create(String redisUrl);
    }

    public static final class Options {
        private String redisUrl = DEFAULT_REDIS_URL;
        private double pollInterval = 30.0d;
        private int minSamplesForTraining = 32;
        private String baseModelPath = "";
        private LoRARepository loraRepo;
        private InferenceNotifier notifier;
        private int nprocPerNode = 1;
        private String trainingGpuIds = "";
        private String tmpRoot = DEFAULT_TMP_ROOT;
        private String ppoConfigPath;
        private PpoTrainingExecutor trainer;
        private TrajectorySampleStore trajectoryStore;
        private RedisStoreFactory redisStoreFactory = ignored -> null;
        private Executor trainingExecutor = ForkJoinPool.commonPool();

        public Options setRedisUrl(String redisUrl) {
            this.redisUrl = redisUrl != null ? redisUrl : "";
            return this;
        }

        public Options setPollInterval(double pollInterval) {
            this.pollInterval = pollInterval;
            return this;
        }

        public Options setMinSamplesForTraining(int minSamplesForTraining) {
            this.minSamplesForTraining = minSamplesForTraining;
            return this;
        }

        public Options setBaseModelPath(String baseModelPath) {
            this.baseModelPath = baseModelPath != null ? baseModelPath : "";
            return this;
        }

        public Options setLoraRepo(LoRARepository loraRepo) {
            this.loraRepo = loraRepo;
            return this;
        }

        public Options setNotifier(InferenceNotifier notifier) {
            this.notifier = notifier;
            return this;
        }

        public Options setNprocPerNode(int nprocPerNode) {
            this.nprocPerNode = nprocPerNode;
            return this;
        }

        public Options setTrainingGpuIds(String trainingGpuIds) {
            this.trainingGpuIds = trainingGpuIds != null ? trainingGpuIds : "";
            return this;
        }

        public Options setTmpRoot(String tmpRoot) {
            this.tmpRoot = tmpRoot != null ? tmpRoot : DEFAULT_TMP_ROOT;
            return this;
        }

        public Options setPpoConfigPath(String ppoConfigPath) {
            this.ppoConfigPath = ppoConfigPath;
            return this;
        }

        public Options setTrainer(PpoTrainingExecutor trainer) {
            this.trainer = trainer;
            return this;
        }

        public Options setTrajectoryStore(TrajectorySampleStore trajectoryStore) {
            this.trajectoryStore = trajectoryStore;
            return this;
        }

        public Options setRedisStoreFactory(RedisStoreFactory redisStoreFactory) {
            this.redisStoreFactory = redisStoreFactory != null ? redisStoreFactory : ignored -> null;
            return this;
        }

        public Options setTrainingExecutor(Executor trainingExecutor) {
            this.trainingExecutor = trainingExecutor != null ? trainingExecutor : ForkJoinPool.commonPool();
            return this;
        }
    }
}
