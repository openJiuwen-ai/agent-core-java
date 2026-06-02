/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.offline.coordinator.TaskQueue;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Parallel rollout execution engine that manages multiple async worker loops,
 * pulling tasks from a TaskQueue and writing results back.
 * <p>
 * Each worker creates its own RuntimeExecutor and processes tasks
 * concurrently until stopped.
 * <p>
 * Mirrors Python's {@code ParallelRuntimeExecutor} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.runtime.parallel_executor}.
 */
public class ParallelRuntimeExecutor {

    private static final Logger logger = Logger.getLogger(ParallelRuntimeExecutor.class.getName());
    
    private final TaskQueue dataStore;
    private final int numWorkers;
    private final ExecutorService executorService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final List<CompletableFuture<Void>> runtimeTasks = new ArrayList<>();
    private final List<RolloutCollector> collectors = new ArrayList<>();
    
    private Function<?, ?> agentFactory;
    private Function<?, ?> taskDataFn;
    private Function<?, ?> rewardFn;

    /**
     * Initialize the parallel executor with a task queue and worker count.
     *
     * @param dataStore Task queue for pulling tasks
     * @param numWorkers Number of parallel workers (defaults to CPU count if 0)
     */
    public ParallelRuntimeExecutor(TaskQueue dataStore, int numWorkers) {
        this.dataStore = dataStore;
        this.numWorkers = numWorkers > 0 ? numWorkers : Runtime.getRuntime().availableProcessors();
        this.executorService = Executors.newFixedThreadPool(this.numWorkers);
    }

    /**
     * Execute rollouts in parallel for a batch of prompts.
     * <p>
     * Worker loop: pull task → execute → push results.
     *
     * @param prompts List of prompts to process
     * @return List of rollouts (as CompletableFuture objects)
     */
    public List<CompletableFuture<Object>> executeBatch(List<Object> prompts) {
        List<CompletableFuture<Object>> futures = new ArrayList<>();
        
        for (Object prompt : prompts) {
            CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
                RuntimeExecutor executor = new RuntimeExecutor(
                    agentFactory, taskDataFn, rewardFn
                );
                
                try {
                    // Get task from data store
                    RLTask task = dataStore.getTask();
                    if (task == null) {
                        Thread.sleep(100);
                        return null;
                    }
                    
                    logger.info("Worker START task " + task.getTaskId());
                    
                    // Execute the task
                    RolloutMessage rolloutMessage = executor.execute(task);
                    rolloutMessage.setRolloutId(task.getTaskId());
                    
                    // Add rollout result to data store
                    dataStore.addRollout(rolloutMessage);
                    
                    logger.info("Worker DONE task " + task.getTaskId() + 
                        ", reward=" + rolloutMessage.getGlobalReward());
                    
                    return rolloutMessage;
                    
                } catch (Exception e) {
                    logger.warning("Worker error: " + e.getMessage());
                    e.printStackTrace();
                    // Handle error by deleting the task
                    try {
                        RLTask currentTask = dataStore.getTask();
                        if (currentTask != null) {
                            dataStore.deleteTask(currentTask);
                        }
                    } catch (Exception ex) {
                        logger.warning("Failed to delete task: " + ex.getMessage());
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }
            }, executorService);
            futures.add(future);
        }
        
        return futures;
    }

    /**
     * Launch all worker loops.
     */
    public void start() {
        if (isRunning.get()) {
            logger.warning("ParallelRuntimeExecutor is already running");
            return;
        }
        isRunning.set(true);
        logger.info("Starting ParallelRuntimeExecutor with " + numWorkers + " workers");
        
        for (int i = 0; i < numWorkers; i++) {
            final int workerId = i;
            CompletableFuture<Void> task = CompletableFuture.runAsync(
                () -> workerLoop(workerId), executorService
            );
            runtimeTasks.add(task);
        }
    }

    /**
     * Worker loop: pull → execute → push results.
     */
    private void workerLoop(int workerId) {
        logger.fine("Worker " + workerId + " started");
        RuntimeExecutor executor = new RuntimeExecutor(
            agentFactory, taskDataFn, rewardFn
        );
        
        while (isRunning.get()) {
            RLTask task = null;
            try {
                task = dataStore.getTask();
                if (task == null) {
                    Thread.sleep(100);
                    continue;
                }
                
                logger.fine("Worker " + workerId + " START task " + task.getTaskId());
                
                RolloutMessage rolloutMessage = executor.execute(task);
                rolloutMessage.setRolloutId(task.getTaskId());
                
                dataStore.addRollout(rolloutMessage);
                
                logger.fine("Worker " + workerId + " DONE task " + task.getTaskId() +
                    ", reward=" + rolloutMessage.getGlobalReward());
                    
            } catch (Exception e) {
                e.printStackTrace();
                logger.warning("Worker " + workerId + " error: " + e.getMessage() +
                    ", deleting task directly.");
                if (task != null) {
                    try {
                        dataStore.deleteTask(task);
                    } catch (Exception ex) {
                        logger.warning("Failed to delete task: " + ex.getMessage());
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Stop all workers and clean up.
     */
    public void stop() {
        isRunning.set(false);
        
        // Wait for all tasks to complete
        if (!runtimeTasks.isEmpty()) {
            CompletableFuture.allOf(runtimeTasks.toArray(new CompletableFuture[0]))
                .join();
            runtimeTasks.clear();
        }
        
        logger.info("ParallelRuntimeExecutor stopped");
    }

    /**
     * Return whether the executor is currently running.
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Set the agent factory for creating agents per task.
     */
    public void setAgentFactory(Function<?, ?> factory) {
        this.agentFactory = factory;
    }

    /**
     * Set the function to convert task samples to agent inputs.
     */
    public void setTaskDataFn(Function<?, ?> fn) {
        this.taskDataFn = fn;
    }

    /**
     * Set the reward function to compute rewards from rollout messages.
     */
    public void setRewardFn(Function<?, ?> fn) {
        this.rewardFn = fn;
    }

    /**
     * Shutdown the executor.
     */
    public void shutdown() {
        stop();
        executorService.shutdown();
    }

    /**
     * Add a collector to the executor.
     *
     * @param collector Rollout collector
     */
    public void addCollector(RolloutCollector collector) {
        collectors.add(collector);
    }

    public int getNumWorkers() { return numWorkers; }
    public int getConcurrency() { return numWorkers; }
    public List<RolloutCollector> getCollectors() { return collectors; }
    public TaskQueue getDataStore() { return dataStore; }
    public Function<?, ?> getAgentFactory() { return agentFactory; }
    public Function<?, ?> getTaskDataFn() { return taskDataFn; }
    public Function<?, ?> getRewardFn() { return rewardFn; }
}
