/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.rails;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Signals when the agent enters its first task-loop iteration.
 *
 * <p>Mirrors Python's {@code FirstIterationGate} in
 * {@code openjiuwen/agent_teams/rails/first_iteration_gate.py}.</p>
 */
public class FirstIterationGate {

    private CompletableFuture<Void> ready = new CompletableFuture<>();

    public synchronized CompletionStage<Void> waitReady() {
        return ready;
    }

    public synchronized boolean isReady() {
        return ready.isDone();
    }

    public CompletionStage<Void> beforeTaskIteration() {
        return open();
    }

    public CompletionStage<Void> beforeTaskIteration(Object ignoredContext) {
        return open();
    }

    public synchronized void reset() {
        if (ready.isDone()) {
            ready = new CompletableFuture<>();
        }
    }

    private synchronized CompletionStage<Void> open() {
        if (!ready.isDone()) {
            ready.complete(null);
        }
        return CompletableFuture.completedFuture(null);
    }
}
