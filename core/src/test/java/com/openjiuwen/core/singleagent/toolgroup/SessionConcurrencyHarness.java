/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.toolgroup;

import com.openjiuwen.core.session.AgentSessionApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

/** Runs isolated session actions in parallel for ToolGroup state tests. */
public final class SessionConcurrencyHarness {
    private static final int THREAD_KEEP_ALIVE_SECONDS = 30;
    private static final int TASK_TIMEOUT_SECONDS = 30;

    private SessionConcurrencyHarness() {}

    /**
     * Runs isolated session actions concurrently and waits for all results.
     *
     * @param sessionCount number of sessions to create
     * @param sessionFactory factory that creates each session
     * @param sessionAction action to execute against each session
     * @throws InterruptedException when the test thread is interrupted
     * @throws ExecutionException when a session action fails
     * @throws TimeoutException when a session action exceeds the timeout
     */
    public static void runInParallel(
            int sessionCount,
            IntFunction<AgentSessionApi> sessionFactory,
            BiConsumer<Integer, AgentSessionApi> sessionAction)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (sessionCount <= 0) {
            throw new IllegalArgumentException("sessionCount must be positive");
        }
        ThreadPoolExecutor executor = createExecutor(sessionCount);
        try {
            runSessions(executor, sessionCount, sessionFactory, sessionAction);
        } finally {
            shutdownExecutor(executor);
        }
    }

    private static ThreadPoolExecutor createExecutor(int sessionCount) {
        return new ThreadPoolExecutor(
                sessionCount,
                sessionCount,
                THREAD_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(sessionCount),
                Executors.defaultThreadFactory());
    }

    private static void runSessions(
            ThreadPoolExecutor executor,
            int sessionCount,
            IntFunction<AgentSessionApi> sessionFactory,
            BiConsumer<Integer, AgentSessionApi> sessionAction)
            throws InterruptedException, ExecutionException, TimeoutException {
        List<Future<Void>> futures = new ArrayList<>(sessionCount);
        for (int sessionIndex = 0; sessionIndex < sessionCount; sessionIndex++) {
            int index = sessionIndex;
            futures.add(
                    executor.submit(
                            () -> {
                                sessionAction.accept(index, sessionFactory.apply(index));
                                return null;
                            }));
        }
        for (Future<Void> future : futures) {
            future.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static void shutdownExecutor(ThreadPoolExecutor executor) throws InterruptedException {
        executor.shutdown();
        boolean isTerminated = executor.awaitTermination(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!isTerminated) {
            executor.shutdownNow();
        }
    }
}
