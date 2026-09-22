/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Rail whose first init entry blocks on a caller-held gate, so tests
 * deterministically hold an in-flight initialization at the registration
 * section. The gate is
 * released by the test to let the initializer commit or roll back while
 * destroy is already waiting. Later init entries (a retry after rollback,
 * or the redundant second registration call DeepAgent makes for
 * DeepAgentRails) pass through without blocking, and only the first
 * entry throws when failure injection is enabled.
 *
 * @since 0.1.16
 */
public class BlockingInitRail extends DeepAgentRail {
    private final CountDownLatch initEntered = new CountDownLatch(1);

    private final CountDownLatch releaseGate;

    private final boolean shouldFailAfterRelease;

    private final AtomicInteger initCounter = new AtomicInteger();

    private final AtomicInteger uninitCounter = new AtomicInteger();

    private final AtomicInteger fireCounter = new AtomicInteger();

    private final AtomicBoolean firstInitConsumed = new AtomicBoolean(false);

    /**
     * Creates the blocking rail.
     *
     * @param releaseGate latch the initializer waits on; the test counts it
     *        down to let initialization proceed
     * @param shouldFailAfterRelease when true, init throws after the gate opens
     *        (failure-rollback round)
     */
    public BlockingInitRail(CountDownLatch releaseGate, boolean shouldFailAfterRelease) {
        this.releaseGate = releaseGate;
        this.shouldFailAfterRelease = shouldFailAfterRelease;
    }

    @Override
    public void init(Object agent) {
        initCounter.incrementAndGet();
        initEntered.countDown();
        if (!firstInitConsumed.compareAndSet(false, true)) {
            // Later entries (retry after rollback, or the redundant second
            // registration call DeepAgent makes for DeepAgentRails) pass
            // through: only the first entry is controllable by the test.
            return;
        }
        try {
            releaseGate.await();
        } catch (InterruptedException e) {
            // Cooperative exit (G.CON.10): abort this init attempt; the
            // state machine rolls back to NEW and the caller observes ISE.
            throw new IllegalStateException("blocking rail init interrupted", e);
        }
        if (shouldFailAfterRelease) {
            throw new IllegalStateException("simulated rail init failure");
        }
    }

    @Override
    public void uninit(Object agent) {
        uninitCounter.incrementAndGet();
        releaseGate.countDown();
    }

    /**
     * Registers one benign counting callback so the rail participates in
     * the global callback lifecycle like business rails do.
     *
     * @return callback map with one BEFORE_INVOKE consumer
     */
    @Override
    public Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> getCallbacks() {
        Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> callbacks = new LinkedHashMap<>();
        callbacks.put(AgentCallbackEvent.BEFORE_INVOKE, ctx -> fireCounter.incrementAndGet());
        return callbacks;
    }

    /**
     * Waits until the initializer thread has entered this rail's init.
     *
     * @param timeout wait bound
     * @param unit timeout unit
     * @return true when init was entered in time
     * @throws InterruptedException when the waiting thread is interrupted
     */
    public boolean awaitInitEntered(long timeout, TimeUnit unit) throws InterruptedException {
        return initEntered.await(timeout, unit);
    }

    /**
     * Returns how often init was entered.
     *
     * @return init entry count
     */
    public int initCount() {
        return initCounter.get();
    }

    /**
     * Returns how often uninit ran.
     *
     * @return uninit count
     */
    public int uninitCount() {
        return uninitCounter.get();
    }

    /**
     * Returns how often the registered callback fired.
     *
     * @return callback fire count
     */
    public int callbackFires() {
        return fireCounter.get();
    }
}
