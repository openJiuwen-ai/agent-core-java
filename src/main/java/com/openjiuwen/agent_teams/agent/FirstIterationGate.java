/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Minimal first-iteration gate.
 *
 * <p>Mirrors Python's {@code FirstIterationGate} in
 * {@code openjiuwen.agent_teams.agent.rails}.
 */
public class FirstIterationGate extends AgentRail {

    private final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        if (latch.getCount() > 0) {
            latch.countDown();
        }
    }

    public boolean awaitReady(long timeoutMillis) throws InterruptedException {
        return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public boolean isReady() {
        return latch.getCount() == 0;
    }

    public void reset() {
        while (latch.getCount() > 0) {
            latch.countDown();
        }
    }
}
