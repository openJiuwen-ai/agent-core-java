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
 *
 * <p>Mirrors Python FirstIterationGate: external code can
 * {@code gate.await()} to block until the agent is actually inside
 * its loop and ready to receive steer / follow_up inputs.</p>
 */
public class FirstIterationGate extends DeepAgentRail {

    private final AtomicBoolean event = new AtomicBoolean(false);
    private volatile CountDownLatch latch = new CountDownLatch(1);

    /**
     * Auto-generated for codecheck compliance.
     */
    public FirstIterationGate() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    public int priority() {
        return 10;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void await() throws InterruptedException {
        latch.await();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return latch.await(timeout, unit);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isReady() {
        return event.get();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (event.compareAndSet(false, true)) {
            latch.countDown();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void reset() {
        event.set(false);
        latch = new CountDownLatch(1);
    }
}
