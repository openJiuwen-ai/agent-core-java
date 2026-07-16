/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.store.PendingNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Pool for executing Pregel node tasks concurrently using virtual threads.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.task.TaskExecutorPool}.
 * 
 * @since 0.1.7
 */
public class TaskExecutorPool {
    private static final LoggerProtocol logger = Loggers.GRAPH;
    private static final long CANCEL_GRACE_TIMEOUT_MS = 5000;

    private final PregelConfig config;
    private final ExecutorService executor;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<Message> succeedMessages = new ArrayList<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, PendingNode> failed = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<CompletableFuture<Object>, RunningTask> runningTasks = new ConcurrentHashMap<>();

    /**
     * TaskExecutorPool.
     * 
     * @param config config
     * @since 0.1.7
     */
    public TaskExecutorPool(PregelConfig config) {
        this.config = config;
        this.executor = OpenJiuwenExecutors.newCachedThreadPool("pregel-task", false);
    }

    /**
     * Submit a node for execution.
     * 
     * @param node node
     * @param version version
     * @since 0.1.7
     */
    public void submit(PregelNode node, int version) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                return new NodeTask(node, config, version).call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                completion.complete(null);
            }
        }, executor);
        runningTasks.put(future, new RunningTask(node, completion));
    }

    /**
     * waitAll.
     * 
     * @throws Exception Exception
     * @since 0.1.7
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

        List<CompletableFuture<Object>> pendingFutures = new ArrayList<>();
        for (CompletableFuture<Object> future : futures) {
            if (!future.isDone()) {
                pendingFutures.add(future);
            }
        }
        if (!pendingFutures.isEmpty() && !allDone.isDone()) {
            cancelPendingFutures(pendingFutures);
            awaitActualCompletion(pendingFutures);
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
            cancelPendingFutures(pendingFutures);
            awaitActualCompletion(pendingFutures);
        }

        // Process completed futures and cancel pending ones
        for (CompletableFuture<Object> future : futures) {
            RunningTask runningTask = runningTasks.remove(future);
            if (runningTask == null) {
                continue;
            }
            PregelNode node = runningTask.node();

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
                        } else if (isCancellation(cause)) {
                            commitFailure(node, new CancellationException());
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
                    } else {
                        // no-op
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
        } else {
            // no-op
        }
    }

    /**
     * Cancel all running tasks.
     * 
     * @since 0.1.7
     */
    public void cancelAll() {
        for (Map.Entry<CompletableFuture<Object>, RunningTask> entry : runningTasks.entrySet()) {
            entry.getKey().cancel(true);
            commitFailure(entry.getValue().node(), new CancellationException());
        }
        runningTasks.clear();
    }

    /**
     * Clear all result collections.
     * 
     * @since 0.1.7
     */
    public void clear() {
        succeedMessages.clear();
        failed.clear();
        runningTasks.clear();
    }

    /**
     * getSucceedMessages.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Message> getSucceedMessages() {
        return succeedMessages;
    }

    /**
     * getFailed.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, PendingNode> getFailed() {
        return failed;
    }

    /**
     * commitFailure.
     * 
     * @param node node
     * @param exc exc
     * @since 0.1.7
     */
    private void commitFailure(PregelNode node, Exception exc) {
        String name = node.getName();
        if (!failed.containsKey(name)) {
            String status = (exc instanceof GraphInterrupt)
                    ? PregelConstants.TASK_STATUS_INTERRUPT
                    : PregelConstants.TASK_STATUS_ERROR;
            failed.put(name, new PendingNode(name, status, List.of(exc)));
        }
    }

    /**
     * unwrapException.
     * 
     * @param t t
     * @return the result
     * @since 0.1.7
     */
    private static Throwable unwrapException(Throwable t) {
        while (t instanceof RuntimeException && t.getCause() != null && t != t.getCause()) {
            t = t.getCause();
        }
        if (t instanceof java.util.concurrent.CompletionException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    /**
     * cancelPendingFutures.
     * 
     * @param pendingFutures pendingFutures
     * @since 0.1.7
     */
    private void cancelPendingFutures(List<CompletableFuture<Object>> pendingFutures) {
        for (CompletableFuture<Object> future : pendingFutures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    /**
     * awaitActualCompletion.
     * 
     * @param pendingFutures pendingFutures
     * @since 0.1.7
     */
    private void awaitActualCompletion(List<CompletableFuture<Object>> pendingFutures) {
        if (pendingFutures.isEmpty()) {
            return;
        }
        List<CompletableFuture<Void>> completions = new ArrayList<>();
        for (CompletableFuture<Object> future : pendingFutures) {
            RunningTask runningTask = runningTasks.get(future);
            if (runningTask != null) {
                completions.add(runningTask.completion());
            }
        }
        if (completions.isEmpty()) {
            return;
        }
        try {
            CompletableFuture.allOf(completions.toArray(new CompletableFuture[0])).get(CANCEL_GRACE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.warning("Timed out waiting for cancelled graph tasks to stop");
        } catch (Exception e) {
            logger.warning("Cancelled graph task finished with cleanup error", e);
        }
    }

    /**
     * isCancellation.
     * 
     * @param throwable throwable
     * @return the result
     * @since 0.1.7
     */
    private static boolean isCancellation(Throwable throwable) {
        return throwable instanceof CancellationException || throwable instanceof InterruptedException;
    }

    /**
     * RunningTask.
     * 
     * @param node node
     * @param completion completion
     * @since 0.1.7
     */
    private record RunningTask(PregelNode node, CompletableFuture<Void> completion) {
    }
}
