/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.offline.coordinator.TaskQueue;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parallel rollout engine pulling tasks from a task queue.
 *
 * <p>Mirrors Python's {@code ParallelRuntimeExecutor} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/runtime/parallel_executor.py}.</p>
 */
public class ParallelRuntimeExecutor {

    private static final Logger LOGGER = Logger.getLogger(ParallelRuntimeExecutor.class.getName());
    private static final long IDLE_SLEEP_MILLIS = 100L;
    private static final long ERROR_SLEEP_MILLIS = 1_000L;

    private final TaskQueue dataStore;
    private final int numWorkers;
    private final List<CompletableFuture<Void>> runtimeTasks = Collections.synchronizedList(new ArrayList<>());

    private Function<RLTask, ?> agentFactory;
    private Function<Map<String, Object>, Map<String, Object>> taskDataFn;
    private Function<RolloutMessage, Map<String, Object>> rewardFn;
    private volatile boolean running;
    private ExecutorService workerPool;

    public ParallelRuntimeExecutor(TaskQueue dataStore, int numWorkers) {
        this(dataStore, numWorkers, null, null, null);
    }

    public ParallelRuntimeExecutor(TaskQueue dataStore,
                                   int numWorkers,
                                   Function<RLTask, ?> agentFactory,
                                   Function<Map<String, Object>, Map<String, Object>> taskDataFn,
                                   Function<RolloutMessage, Map<String, Object>> rewardFn) {
        this.dataStore = Objects.requireNonNull(dataStore, "dataStore");
        this.numWorkers = numWorkers != 0 ? numWorkers : Runtime.getRuntime().availableProcessors();
        this.agentFactory = agentFactory;
        this.taskDataFn = taskDataFn;
        this.rewardFn = rewardFn;
    }

    /**
     * Launch all worker loops.
     */
    public void start() {
        if (running) {
            LOGGER.warning("ParallelRuntimeExecutor is already running");
            return;
        }
        running = true;
        LOGGER.info(() -> "Starting ParallelRuntimeExecutor with " + numWorkers + " workers");

        int workerCount = Math.max(0, numWorkers);
        workerPool = Executors.newFixedThreadPool(Math.max(1, workerCount));
        for (int i = 0; i < workerCount; i++) {
            final int workerId = i;
            runtimeTasks.add(CompletableFuture.runAsync(() -> workerLoop(workerId), workerPool));
        }
    }

    /**
     * Java coroutine-style wrapper for callers that want a completion stage.
     *
     * @return already completed stage after workers have been launched
     */
    public CompletionStage<Void> startAsync() {
        start();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Stop all workers and clean up.
     */
    public void stop() {
        running = false;
        CompletableFuture<?>[] tasksSnapshot;
        synchronized (runtimeTasks) {
            tasksSnapshot = runtimeTasks.toArray(new CompletableFuture<?>[0]);
        }
        if (tasksSnapshot.length > 0) {
            CompletableFuture.allOf(tasksSnapshot).join();
            runtimeTasks.clear();
        }
        if (workerPool != null) {
            workerPool.shutdownNow();
            workerPool = null;
        }
        LOGGER.info("ParallelRuntimeExecutor stopped");
    }

    /**
     * Java coroutine-style wrapper for callers that want a completion stage.
     *
     * @return already completed stage after workers have stopped
     */
    public CompletionStage<Void> stopAsync() {
        stop();
        return CompletableFuture.completedFuture(null);
    }

    public boolean isRunning() {
        return running;
    }

    public void setAgentFactory(Function<RLTask, ?> factory) {
        this.agentFactory = factory;
    }

    public void setTaskDataFn(Function<Map<String, Object>, Map<String, Object>> fn) {
        this.taskDataFn = fn;
    }

    public void setRewardFn(Function<RolloutMessage, Map<String, Object>> fn) {
        this.rewardFn = fn;
    }

    private void workerLoop(int workerId) {
        LOGGER.fine(() -> "Worker " + workerId + " started");
        RuntimeExecutor executor = new RuntimeExecutor(agentFactory, taskDataFn, rewardFn);

        while (running) {
            RLTask task = null;
            try {
                task = dataStore.getTask();
                if (task == null) {
                    sleep(IDLE_SLEEP_MILLIS);
                    continue;
                }

                RLTask currentTask = task;
                LOGGER.fine(() -> "Worker " + workerId + " START task " + currentTask.getTaskId());
                RolloutMessage rolloutMessage = executor.executeAsync(task).toCompletableFuture().join();
                rolloutMessage.setRolloutId(task.getTaskId());
                dataStore.addRollout(rolloutMessage);
                LOGGER.fine(() -> "Worker " + workerId + " DONE task " + currentTask.getTaskId()
                        + ", reward=" + rolloutMessage.getGlobalReward());
            } catch (Exception exception) {
                LOGGER.log(Level.SEVERE,
                        "Worker " + workerId + " error: " + exception.getMessage() + ", deleting task directly.",
                        exception);
                if (task != null) {
                    dataStore.deleteTask(task);
                }
                sleep(ERROR_SLEEP_MILLIS);
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    public TaskQueue getDataStore() {
        return dataStore;
    }

    public int getNumWorkers() {
        return numWorkers;
    }

    public Function<RLTask, ?> getAgentFactory() {
        return agentFactory;
    }

    public Function<Map<String, Object>, Map<String, Object>> getTaskDataFn() {
        return taskDataFn;
    }

    public Function<RolloutMessage, Map<String, Object>> getRewardFn() {
        return rewardFn;
    }

    public int getRuntimeTaskCount() {
        return runtimeTasks.size();
    }

    public Map<String, Object> describeState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("is_running", running);
        state.put("num_workers", numWorkers);
        state.put("runtime_task_count", getRuntimeTaskCount());
        return state;
    }
}
