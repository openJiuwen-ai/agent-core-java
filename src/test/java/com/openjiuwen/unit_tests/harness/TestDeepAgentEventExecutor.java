/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.task_loop.LoopCoordinator;
import com.openjiuwen.harness.task_loop.TaskLoopEventExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_deep_agent_event_executor} in
 * {@code tests.unit_tests.harness.test_deep_agent_event_executor}.
 */
class TestDeepAgentEventExecutor {

    static final class TestDeepAgent extends DeepAgent {
        LoopCoordinator _loopCoordinator;

        TestDeepAgent(AgentCard card) {
            super(card);
        }
    }

    static class StubExecutor extends TaskLoopEventExecutor {
        private final Object stubResult;

        StubExecutor(Object dependencies, Object deepAgent, Object stubResult) {
            super(dependencies, deepAgent);
            this.stubResult = stubResult;
        }

        @Override
        protected Object invokeInnerAgent(Map<String, Object> input) {
            return stubResult;
        }
    }

    private static AgentCard card() {
        AgentCard card = new AgentCard();
        card.setName("test");
        card.setDescription("t");
        return card;
    }

    private static Object readField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("executor stores dependencies and deep agent reference")
    void testExecutorInitWithDeps() {
        Object deps = new Object();
        TestDeepAgent agent = new TestDeepAgent(card());

        TaskLoopEventExecutor executor = new TaskLoopEventExecutor(deps, agent);

        assertSame(deps, readField(executor, "dependencies"));
        assertSame(agent, readField(executor, "deepAgent"));
    }

    @Test
    @Tag("level0")
    @DisplayName("executeAbility returns success chunk on normal result")
    void testExecuteAbilityYieldsCompletion() throws ExecutionException, InterruptedException {
        StubExecutor executor = new StubExecutor(null, null, Map.of("output", "done:hello world"));

        TaskLoopEventExecutor.ExecutionChunk chunk = executor.executeAbility("t1", null).get();

        assertTrue(chunk.isSuccess());
        assertEquals("t1", chunk.getTaskId());
        assertTrue(chunk.getPayload().contains("done:hello world"));
    }

    @Test
    @Tag("level0")
    @DisplayName("executeAbility returns error chunk on invoke failure")
    void testExecuteAbilityYieldsFailure() throws ExecutionException, InterruptedException {
        StubExecutor executor = new StubExecutor(
                null,
                null,
                Map.of("result_type", "error", "error", "invoke failed"));

        TaskLoopEventExecutor.ExecutionChunk chunk = executor.executeAbility("t2", null).get();

        assertFalse(chunk.isSuccess());
        assertEquals("t2", chunk.getTaskId());
        assertTrue(chunk.getError().contains("invoke failed"));
    }

    @Test
    @Tag("level0")
    @DisplayName("cancel requests abort on loop coordinator")
    void testCancelMarksFailedAndAborts() {
        TestDeepAgent agent = new TestDeepAgent(card());
        agent._loopCoordinator = new LoopCoordinator();
        AgentSessionApi session = new AgentSessionApi("s1");

        TaskLoopEventExecutor executor = new TaskLoopEventExecutor(null, agent);

        assertTrue(executor.cancel("t1", session));
        assertTrue(agent._loopCoordinator.isAborted());
    }

    @Test
    @Tag("level0")
    @DisplayName("buildDeepExecutor factory creates executor instances")
    void testBuildDeepExecutorFactory() {
        TestDeepAgent agent = new TestDeepAgent(card());
        Function<Object, TaskLoopEventExecutor> builder = TaskLoopEventExecutor.buildDeepExecutor(agent);

        TaskLoopEventExecutor executor = builder.apply("deps");

        assertNotNull(executor);
        assertTrue(executor instanceof TaskLoopEventExecutor);
        assertSame(agent, readField(executor, "deepAgent"));
    }
}
