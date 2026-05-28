/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * Workflow task executor for parallel task execution.
 *
 * <p>Mirrors Python's {@code WorkflowTaskExecutor} in {@code openjiuwen.core.application.workflow_agent.workflow_task_executor}.</p>
 */
public class WorkflowTaskExecutor {

    private static final Logger logger = Logger.getLogger(WorkflowTaskExecutor.class.getName());

    private final ExecutorService executor;
    private final int maxConcurrentTasks;

    public WorkflowTaskExecutor() {
        this(4);
    }

    public WorkflowTaskExecutor(int maxConcurrentTasks) {
        this.maxConcurrentTasks = maxConcurrentTasks;
        this.executor = Executors.newFixedThreadPool(maxConcurrentTasks);
    }

    /**
     * Submit a task for execution.
     *
     * @param task the callable task
     * @return a future representing the pending result
     */
    public <T> Future<T> submit(Callable<T> task) {
        return executor.submit(task);
    }

    /**
     * Submit multiple tasks and collect results.
     *
     * @param tasks the list of callable tasks
     * @return list of futures
     */
    public <T> List<Future<T>> submitAll(List<Callable<T>> tasks) {
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(executor.submit(task));
        }
        return futures;
    }

    /**
     * Shutdown the executor.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Shutdown the executor and wait for completion.
     *
     * @param timeoutSeconds the timeout in seconds
     */
    public void shutdownAndWait(int timeoutSeconds) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    /**
     * Get the maximum concurrent tasks.
     *
     * @return the max concurrent tasks
     */
    public int getMaxConcurrentTasks() {
        return maxConcurrentTasks;
    }
}