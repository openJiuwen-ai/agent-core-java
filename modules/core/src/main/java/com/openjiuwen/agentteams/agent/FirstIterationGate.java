/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Signals when the agent enters its first task-loop iteration.
 * <p>
 * Mirrors Python FirstIterationGate: external code can
 * {@code gate.await()} to block until the agent is actually inside
 * its loop and ready to receive steer / follow_up inputs.
 * </p>
 * 
 * @since 0.1.7
 */
public class FirstIterationGate extends DeepAgentRail {
    private final AtomicBoolean event = new AtomicBoolean(false);

    /**
     * CountDownLatch.
     * 
     * @since 0.1.7
     */
    private volatile CountDownLatch latch = new CountDownLatch(1);

    /**
     * FirstIterationGate.
     * 
     * @since 0.1.7
     */
    public FirstIterationGate() {
    }

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 10;
    }

    /**
     * await.
     * 
     * @throws InterruptedException InterruptedException
     * @since 0.1.7
     */
    public void await() throws InterruptedException {
        latch.await();
    }

    /**
     * await.
     * 
     * @param timeout timeout
     * @param unit unit
     * @return the result
     * @throws InterruptedException InterruptedException
     * @since 0.1.7
     */
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return latch.await(timeout, unit);
    }

    /**
     * isReady.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isReady() {
        return event.get();
    }

    /**
     * beforeModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (event.compareAndSet(false, true)) {
            latch.countDown();
        }
    }

    /**
     * reset.
     * 
     * @since 0.1.7
     */
    public void reset() {
        event.set(false);
        latch = new CountDownLatch(1);
    }
}
