/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.single_agent.rail;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * <p>Mirrors Python's {@code TestCtxSteeringQueue} in
 * {@code tests/unit_tests/core/single_agent/rail/test_ctx_steering.py}.</p>
 */
class CtxSteeringMissingTest {

    @Test
    void drainReturnsEmptyByDefault() {
        AgentCallbackContext ctx = makeCtx();

        assertEquals(List.of(), ctx.drainSteering());
    }

    @Test
    void pushWithoutBindIsNoop() {
        AgentCallbackContext ctx = makeCtx();

        ctx.pushSteering("ignored");

        assertEquals(List.of(), ctx.drainSteering());
    }

    @Test
    void bindPushAndDrain() {
        Queue<String> queue = new ArrayDeque<>();
        AgentCallbackContext ctx = makeCtx();
        ctx.bindSteeringQueue(queue);

        ctx.pushSteering("msg1");
        ctx.pushSteering("msg2");

        assertEquals(List.of("msg1", "msg2"), ctx.drainSteering());
    }

    @Test
    void drainClearsQueue() {
        Queue<String> queue = new ArrayDeque<>();
        AgentCallbackContext ctx = makeCtx();
        ctx.bindSteeringQueue(queue);

        ctx.pushSteering("once");

        assertEquals(List.of("once"), ctx.drainSteering());
        assertEquals(List.of(), ctx.drainSteering());
    }

    @Test
    void sharedQueueWithExternalWriter() {
        Queue<String> queue = new ArrayDeque<>();
        AgentCallbackContext ctx = makeCtx();
        ctx.bindSteeringQueue(queue);

        queue.offer("external_msg");

        assertEquals(List.of("external_msg"), ctx.drainSteering());
    }

    @Test
    void multipleDrainCycles() {
        Queue<String> queue = new ArrayDeque<>();
        AgentCallbackContext ctx = makeCtx();
        ctx.bindSteeringQueue(queue);

        ctx.pushSteering("a");
        assertEquals(List.of("a"), ctx.drainSteering());

        ctx.pushSteering("b");
        ctx.pushSteering("c");
        assertEquals(List.of("b", "c"), ctx.drainSteering());
    }

    private static AgentCallbackContext makeCtx() {
        return new AgentCallbackContext();
    }
}
