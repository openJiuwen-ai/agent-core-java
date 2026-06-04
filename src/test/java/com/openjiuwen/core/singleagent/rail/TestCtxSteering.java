/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.rail;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AgentCallbackContext steering methods.
 *
 * <p>Mirrors Python's tests/unit_tests/core/single_agent/rail/test_ctx_steering.py.</p>
 */
@DisplayName("CtxSteering queue tests")
class TestCtxSteering {

    @Test
    @DisplayName("test_drain_returns_empty_by_default")
    void testDrainReturnsEmptyByDefault() {
        SteeringApi api = requireSteeringApi();
        AgentCallbackContext ctx = makeCtx();

        assertThat(drain(api, ctx)).isEmpty();
    }

    @Test
    @DisplayName("test_push_without_bind_is_noop")
    void testPushWithoutBindIsNoop() {
        SteeringApi api = requireSteeringApi();
        AgentCallbackContext ctx = makeCtx();

        push(api, ctx, "ignored");

        assertThat(drain(api, ctx)).isEmpty();
    }

    @Test
    @DisplayName("test_bind_push_and_drain")
    void testBindPushAndDrain() {
        SteeringApi api = requireSteeringApi();
        AgentCallbackContext ctx = makeCtx();

        bind(api, ctx, newQueue(api.bindQueueType()));
        push(api, ctx, "msg1");
        push(api, ctx, "msg2");

        assertThat(drain(api, ctx)).containsExactly("msg1", "msg2");
    }

    @Test
    @DisplayName("test_drain_clears_queue")
    void testDrainClearsQueue() {
        SteeringApi api = requireSteeringApi();
        AgentCallbackContext ctx = makeCtx();

        bind(api, ctx, newQueue(api.bindQueueType()));
        push(api, ctx, "once");

        assertThat(drain(api, ctx)).containsExactly("once");
        assertThat(drain(api, ctx)).isEmpty();
    }

    @Test
    @DisplayName("test_shared_queue_with_external_writer")
    void testSharedQueueWithExternalWriter() {
        SteeringApi api = requireSteeringApi();
        AgentCallbackContext ctx = makeCtx();
        Queue<Object> queue = newQueue(api.bindQueueType());

        bind(api, ctx, queue);
        queue.add("external_msg");

        assertThat(drain(api, ctx)).containsExactly("external_msg");
    }

    @Test
    @DisplayName("test_multiple_drain_cycles")
    void testMultipleDrainCycles() {
        SteeringApi api = requireSteeringApi();
        AgentCallbackContext ctx = makeCtx();

        bind(api, ctx, newQueue(api.bindQueueType()));

        push(api, ctx, "a");
        assertThat(drain(api, ctx)).containsExactly("a");

        push(api, ctx, "b");
        push(api, ctx, "c");
        assertThat(drain(api, ctx)).containsExactly("b", "c");
    }

    private static AgentCallbackContext makeCtx() {
        return AgentCallbackContext.builder()
                .agent(new Object())
                .build();
    }

    private static SteeringApi requireSteeringApi() {
        Method bind = findMethod("bindSteeringQueue", "bind_steering_queue", 1);
        Method push = findMethod("pushSteering", "push_steering", 1);
        Method drain = findMethod("drainSteering", "drain_steering", 0);

        Assumptions.assumeTrue(
                bind != null && push != null && drain != null,
                "AgentCallbackContext has no steering queue API matching Python "
                        + "bind_steering_queue/push_steering/drain_steering."
        );

        return new SteeringApi(bind, push, drain);
    }

    private static Method findMethod(String camelName, String snakeName, int parameterCount) {
        for (Method method : AgentCallbackContext.class.getMethods()) {
            if ((method.getName().equals(camelName) || method.getName().equals(snakeName))
                    && method.getParameterCount() == parameterCount) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Queue<Object> newQueue(Class<?> bindQueueType) {
        if (BlockingQueue.class.isAssignableFrom(bindQueueType)) {
            return new LinkedBlockingQueue<>();
        }
        if (Queue.class.isAssignableFrom(bindQueueType) || Collection.class.isAssignableFrom(bindQueueType)
                || Object.class.equals(bindQueueType)) {
            return new ConcurrentLinkedQueue<>();
        }
        Assumptions.abort("Unsupported Java steering queue parameter type: " + bindQueueType.getName());
        return new ConcurrentLinkedQueue<>();
    }

    private static void bind(SteeringApi api, AgentCallbackContext ctx, Queue<Object> queue) {
        invoke(api.bind(), ctx, queue);
    }

    private static void push(SteeringApi api, AgentCallbackContext ctx, String message) {
        invoke(api.push(), ctx, message);
    }

    private static List<String> drain(SteeringApi api, AgentCallbackContext ctx) {
        Object result = invoke(api.drain(), ctx);
        assertThat(result).isInstanceOf(List.class);
        List<String> messages = new ArrayList<>();
        for (Object item : (List<?>) result) {
            messages.add((String) item);
        }
        return messages;
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke steering method " + method.getName(), e);
        }
    }

    private record SteeringApi(Method bind, Method push, Method drain) {
        private Class<?> bindQueueType() {
            return bind.getParameterTypes()[0];
        }
    }
}
