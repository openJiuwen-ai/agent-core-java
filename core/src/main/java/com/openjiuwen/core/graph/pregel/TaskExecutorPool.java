/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.common.concurrent.InterruptSignals;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;

import com.openjiuwen.core.graph.store.PendingNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs Pregel node tasks and collects success/failure state.
 *
 * <p>Mirrors Python's {@code TaskExecutorPool} in
 * {@code openjiuwen/core/graph/pregel/task.py}.</p>
 */
public class TaskExecutorPool {

    private static final long STOP_TIMEOUT_SECONDS = 30;

    private final PregelConfig config;
    private final ExecutorService executorService;
    private final List<Message> succeedMessages = new ArrayList<>();
    private final Map<String, PendingNode> failed = new LinkedHashMap<>();
    private final Map<CompletableFuture<Object>, PregelNode> runningTasks = new ConcurrentHashMap<>();

    private final Map<CompletableFuture<Object>, PregelTaskExecution> executions = new ConcurrentHashMap<>();

    public TaskExecutorPool(PregelConfig config) {
        this.config = config;
        this.executorService = OpenJiuwenExecutors.newBoundedModulePool("pregel-task", false);
    }

    /**
     * Submit a node for asynchronous execution.
     *
     * @param node node to execute
     * @param version node version
     */
    public void submit(PregelNode node, int version) {
        PregelTaskExecution execution = new PregelTaskExecution(node, config, version, executorService);
        executions.put(execution.result(), execution);
        runningTasks.put(execution.result(), node);
    }

    /**
     * Wait for all tasks, cancelling remaining tasks after the first non-interrupt error.
     *
     * @throws Exception first normal error or graph interrupt
     */
    public void waitAll() throws Exception {
        if (runningTasks.isEmpty()) {
            return;
        }

        Exception firstError = null;
        GraphInterrupt interrupt = null;
        while (!runningTasks.isEmpty()) {
            CompletableFuture<?> any = CompletableFuture.anyOf(runningTasks.keySet().toArray(CompletableFuture[]::new));
            try {
                any.join();
            } catch (CompletionException | CancellationException ignored) {
                // Process the completed exceptional futures below.
            }

            List<CompletableFuture<Object>> completed = new ArrayList<>();
            for (CompletableFuture<Object> task : runningTasks.keySet()) {
                if (task.isDone() || task.isCancelled()) {
                    completed.add(task);
                }
            }
            for (CompletableFuture<Object> task : completed) {
                TaskOutcome outcome = collect(task);
                if (outcome.error() != null && firstError == null) {
                    firstError = outcome.error();
                } else if (outcome.result() instanceof GraphInterrupt graphInterrupt) {
                    interrupt = interrupt == null ? graphInterrupt : mergeInterrupts(interrupt, graphInterrupt);
                }
            }

            if (firstError != null) {
                cancelRemaining();
                throw firstError;
            }
        }
        if (interrupt != null) {
            throw interrupt;
        }
    }

    /**
     * Request cancellation, await worker exit, and collect every completed outcome.
     * Nodes that finish normally during cancellation retain their routing messages.
     *
     * @throws IllegalStateException if a worker does not stop or the waiting caller is interrupted
     */
    public void cancelAll() {
        cancelRemaining();
    }

    /**
     * Stop owned work before clearing success, failure, and running task state.
     *
     * @throws IllegalStateException if owned work cannot be stopped safely
     */
    public void clear() {
        cancelRemaining();
        succeedMessages.clear();
        failed.clear();
        runningTasks.clear();
    }

    /**
     * Stop owned work and shut down the underlying worker executor.
     *
     * @throws IllegalStateException if owned work cannot be stopped safely
     */
    public void shutdown() {
        try {
            cancelRemaining();
        } finally {
            executorService.shutdownNow();
        }
    }

    public List<Message> getSucceedMessages() {
        return new ArrayList<>(succeedMessages);
    }

    public Map<String, PendingNode> getFailed() {
        return new LinkedHashMap<>(failed);
    }

    public Map<CompletableFuture<Object>, PregelNode> getRunningTasks() {
        return new LinkedHashMap<>(runningTasks);
    }

    boolean isQuiescent() {
        return executions.values().stream().allMatch(PregelTaskExecution::isStopped);
    }

    private void cancelRemaining() {
        List<CompletableFuture<Object>> tasks = new ArrayList<>(runningTasks.keySet());
        tasks.forEach(task -> executions.get(task).cancel());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STOP_TIMEOUT_SECONDS);
        for (CompletableFuture<Object> task : tasks) {
            awaitStopped(task, deadline);
        }
        tasks.forEach(this::collect);
    }

    private void awaitStopped(CompletableFuture<Object> task, long deadline) {
        try {
            executions.get(task).awaitStopped(deadline);
        } catch (InterruptedException error) {
            InterruptSignals.restoreCurrentThread();
            throw new IllegalStateException("Interrupted while stopping Pregel workers", error);
        } catch (ExecutionException | TimeoutException error) {
            throw new IllegalStateException("Pregel worker did not stop before checkpoint", error);
        }
    }

    private TaskOutcome collect(CompletableFuture<Object> task) {
        awaitStopped(task, System.nanoTime() + TimeUnit.SECONDS.toNanos(STOP_TIMEOUT_SECONDS));
        TaskOutcome outcome = readOutcome(task);
        PregelNode node = runningTasks.remove(task);
        executions.remove(task);
        if (outcome.error() != null) {
            commitFailure(node, outcome.error());
        } else if (outcome.result() instanceof GraphInterrupt interrupt) {
            commitFailure(node, interrupt);
        } else if (outcome.result() instanceof List<?> messages) {
            for (Object message : messages) {
                succeedMessages.add((Message) message);
            }
        }
        return outcome;
    }

    private void commitFailure(PregelNode node, Exception error) {
        failed.computeIfAbsent(node.getName(), name -> {
            String status = error instanceof GraphInterrupt
                    ? PregelConstants.TASK_STATUS_INTERRUPT
                    : PregelConstants.TASK_STATUS_ERROR;
            return new PendingNode(name, status, List.of(error));
        });
    }

    private TaskOutcome readOutcome(CompletableFuture<Object> task) {
        if (task.isCancelled()) {
            return new TaskOutcome(null, new CancellationException());
        }
        try {
            return new TaskOutcome(task.join(), null);
        } catch (CompletionException error) {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            if (cause instanceof Exception exception) {
                return new TaskOutcome(null, exception);
            }
            return new TaskOutcome(null, new RuntimeException(cause));
        }
    }

    private GraphInterrupt mergeInterrupts(GraphInterrupt left, GraphInterrupt right) {
        List<Object> merged = new ArrayList<>();
        appendInterruptValue(merged, left.getValue());
        appendInterruptValue(merged, right.getValue());
        return new GraphInterrupt(merged);
    }

    private void appendInterruptValue(List<Object> target, Object value) {
        if (value instanceof List<?> list) {
            target.addAll(list);
        } else if (value != null) {
            target.add(value);
        }
    }

    private record TaskOutcome(Object result, Exception error) {
    }
}
