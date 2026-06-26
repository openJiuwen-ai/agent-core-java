/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

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
import java.util.concurrent.Executors;

/**
 * Runs Pregel node tasks and collects success/failure state.
 *
 * <p>Mirrors Python's {@code TaskExecutorPool} in
 * {@code openjiuwen/core/graph/pregel/task.py}.</p>
 */
public class TaskExecutorPool {

    private final PregelConfig config;
    private final ExecutorService executorService;
    private final List<Message> succeedMessages = new ArrayList<>();
    private final Map<String, PendingNode> failed = new LinkedHashMap<>();
    private final Map<CompletableFuture<Object>, PregelNode> runningTasks = new ConcurrentHashMap<>();

    public TaskExecutorPool(PregelConfig config) {
        this.config = config;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Submit a node for asynchronous execution.
     *
     * @param node node to execute
     * @param version node version
     */
    public void submit(PregelNode node, int version) {
        CompletableFuture<Object> task = CompletableFuture.supplyAsync(() -> {
            try {
                return new NodeTask(node, config, version).run();
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        }, executorService);
        runningTasks.put(task, node);
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
                PregelNode node = runningTasks.remove(task);
                TaskOutcome outcome = readOutcome(task);
                if (outcome.error() != null) {
                    commitFailure(node, outcome.error());
                    if (firstError == null) {
                        firstError = outcome.error();
                    }
                } else if (outcome.result() instanceof GraphInterrupt graphInterrupt) {
                    commitFailure(node, graphInterrupt);
                    if (interrupt == null) {
                        interrupt = graphInterrupt;
                    }
                } else {
                    @SuppressWarnings("unchecked")
                    List<Message> messages = (List<Message>) outcome.result();
                    if (messages != null) {
                        succeedMessages.addAll(messages);
                    }
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
     * Cancel all still-running tasks.
     */
    public void cancelAll() {
        cancelRemaining();
    }

    /**
     * Clear success, failure, and running task state.
     */
    public void clear() {
        succeedMessages.clear();
        failed.clear();
        runningTasks.clear();
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

    private void cancelRemaining() {
        for (Map.Entry<CompletableFuture<Object>, PregelNode> entry : new ArrayList<>(runningTasks.entrySet())) {
            CompletableFuture<Object> task = entry.getKey();
            PregelNode node = entry.getValue();
            if (!task.isDone() && !task.isCancelled()) {
                task.cancel(true);
                commitFailure(node, new CancellationException());
            }
            runningTasks.remove(task);
        }
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

    private record TaskOutcome(Object result, Exception error) {
    }
}
