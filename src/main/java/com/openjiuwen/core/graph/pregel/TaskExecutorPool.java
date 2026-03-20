/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.store.PendingNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pool for executing Pregel node tasks concurrently using virtual threads.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.task.TaskExecutorPool}.
 */
public class TaskExecutorPool {

    private static final LoggerProtocol logger = Loggers.GRAPH;

    private final PregelConfig config;
    private final ExecutorService executor;
    private final List<Message> succeedMessages = new ArrayList<>();
    private final Map<String, PendingNode> failed = new ConcurrentHashMap<>();
    private final Map<CompletableFuture<Object>, PregelNode> runningTasks = new ConcurrentHashMap<>();

    public TaskExecutorPool(PregelConfig config) {
        this.config = config;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Submit a node for execution.
     */
    public void submit(PregelNode node, int version) {
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                return new NodeTask(node, config, version).call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
        runningTasks.put(future, node);
    }

    /**
     * Wait for all submitted tasks to complete.
     * Uses FIRST_EXCEPTION semantics: once the first task fails, cancel remaining tasks.
     * <p>
     * Mirrors Python's {@code asyncio.wait(tasks, return_when=asyncio.FIRST_EXCEPTION)}.
     */
    @SuppressWarnings("unchecked")
    public void waitAll() throws Exception {
        if (runningTasks.isEmpty()) {
            return;
        }

        List<CompletableFuture<Object>> futures = new ArrayList<>(runningTasks.keySet());
        Exception firstErrExc = null;
        GraphInterrupt interruptExc = null;

        // Signal that fires when the first task completes exceptionally
        CompletableFuture<Void> firstFailure = new CompletableFuture<>();
        for (CompletableFuture<Object> future : futures) {
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    firstFailure.complete(null);
                }
            });
        }

        // Wait for either all-done or first-exception (FIRST_EXCEPTION semantics)
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            CompletableFuture.anyOf(allDone, firstFailure).join();
        } catch (Exception ignored) {
            // Individual errors will be handled below
        }

        // Check if the first failure is only a GraphInterrupt (not a real exception).
        // If so, allow remaining tasks to finish rather than cancelling them immediately.
        boolean onlyInterrupts = true;
        for (CompletableFuture<Object> future : futures) {
            if (future.isDone() && !future.isCancelled() && future.isCompletedExceptionally()) {
                try {
                    future.join();
                } catch (Exception e) {
                    Throwable cause = unwrapException(e);
                    if (!(cause instanceof GraphInterrupt)) {
                        onlyInterrupts = false;
                        break;
                    }
                }
            }
        }
        if (onlyInterrupts && !allDone.isDone()) {
            // Wait for remaining tasks to complete (they should finish quickly
            // since they are either completing or will also interrupt).
            try {
                allDone.join();
            } catch (Exception ignored) {
                // Individual errors will be handled below
            }
        }

        // Process completed futures and cancel pending ones
        for (CompletableFuture<Object> future : futures) {
            PregelNode node = runningTasks.remove(future);
            if (node == null) {
                continue;
            }

            if (future.isDone() && !future.isCancelled()) {
                if (future.isCompletedExceptionally()) {
                    try {
                        future.join();
                    } catch (Exception e) {
                        Throwable cause = unwrapException(e);
                        if (cause instanceof GraphInterrupt gi) {
                            commitFailure(node, gi);
                            if (interruptExc == null) {
                                interruptExc = gi;
                            }
                        } else {
                            Exception exc = cause instanceof Exception ex ? ex : new RuntimeException(cause);
                            commitFailure(node, exc);
                            if (firstErrExc == null) {
                                firstErrExc = exc;
                            }
                        }
                    }
                } else {
                    Object result = future.join();
                    if (result instanceof GraphInterrupt gi) {
                        commitFailure(node, gi);
                        if (interruptExc == null) {
                            interruptExc = gi;
                        }
                    } else if (result instanceof List<?> msgs) {
                        succeedMessages.addAll((List<Message>) msgs);
                    }
                }
            } else {
                // Cancel pending task
                future.cancel(true);
                commitFailure(node, new CancellationException());
            }
        }

        // Priority: normal exception > interrupt exception
        if (firstErrExc != null) {
            throw firstErrExc;
        } else if (interruptExc != null) {
            throw interruptExc;
        }
    }

    /**
     * Cancel all running tasks.
     */
    public void cancelAll() {
        for (Map.Entry<CompletableFuture<Object>, PregelNode> entry : runningTasks.entrySet()) {
            entry.getKey().cancel(true);
            commitFailure(entry.getValue(), new CancellationException());
        }
        runningTasks.clear();
    }

    /**
     * Clear all result collections.
     */
    public void clear() {
        succeedMessages.clear();
        failed.clear();
        runningTasks.clear();
    }

    public List<Message> getSucceedMessages() {
        return succeedMessages;
    }

    public Map<String, PendingNode> getFailed() {
        return failed;
    }

    private void commitFailure(PregelNode node, Exception exc) {
        String name = node.getName();
        if (!failed.containsKey(name)) {
            String status = (exc instanceof GraphInterrupt)
                    ? PregelConstants.TASK_STATUS_INTERRUPT
                    : PregelConstants.TASK_STATUS_ERROR;
            failed.put(name, new PendingNode(name, status, List.of(exc)));
        }
    }

    private static Throwable unwrapException(Throwable t) {
        while (t instanceof RuntimeException && t.getCause() != null && t != t.getCause()) {
            t = t.getCause();
        }
        if (t instanceof java.util.concurrent.CompletionException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }
}
