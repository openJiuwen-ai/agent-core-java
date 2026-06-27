/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.EventQueue;
import com.openjiuwen.core.controller.modules.TaskExecutor;
import com.openjiuwen.core.controller.modules.TaskExecutorDependencies;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.harness.test_deep_agent_event_executor} in
 * {@code tests/unit_tests/harness/test_deep_agent_event_executor.py}.</p>
 */
class TaskLoopEventExecutorPythonParityTest {

    @Test
    void executorInitWithDeps() throws Exception {
        DeepAgent agent = makeAgent(false);
        Fixture fixture = fixture(agent);

        TaskLoopEventExecutor executor = new TaskLoopEventExecutor(fixture.dependencies(), agent);

        assertThat(readField(executor, "deepAgent")).isSameAs(agent);
        assertThat(readField(executor, TaskExecutor.class, "taskManager")).isSameAs(fixture.taskManager());
        assertThat(readField(executor, TaskExecutor.class, "config")).isSameAs(fixture.dependencies().getConfig());
    }

    @Test
    void executeAbilityYieldsCompletion() {
        FakeReactAgent fake = new FakeReactAgent(false);
        DeepAgent agent = makeAgent(fake);
        Fixture fixture = fixture(agent);
        Task task = coreTask("s1", "t1", "hello world");
        fixture.taskManager().addTask(task);
        TaskLoopEventExecutor executor = new TaskLoopEventExecutor(fixture.dependencies(), agent);

        List<ControllerOutputChunk> chunks = toList(executor.executeAbility("t1", new FakeSession("s1")));

        assertThat(chunks).hasSize(1);
        ControllerOutputPayload payload = chunks.get(0).getPayload();
        assertThat(payload.getType()).isEqualTo(EventType.TASK_COMPLETION.getValue());
        assertThat(payload.getMetadata()).containsEntry("task_id", "t1");
        DataFrame.JsonDataFrame frame = (DataFrame.JsonDataFrame) payload.getData().get(0);
        assertThat(frame.data()).containsEntry("output", "done:hello world");
        assertThat(fake.invokeCalls).hasSize(1);
        assertThat(fake.invokeCalls.get(0)).containsEntry("query", "hello world")
                .containsEntry("conversation_id", "s1");
    }

    @Test
    void executeAbilityYieldsFailure() {
        DeepAgent agent = makeAgent(true);
        Fixture fixture = fixture(agent);
        fixture.taskManager().addTask(coreTask("s1", "t2", "will fail"));
        TaskLoopEventExecutor executor = new TaskLoopEventExecutor(fixture.dependencies(), agent);

        List<ControllerOutputChunk> chunks = toList(executor.executeAbility("t2", new FakeSession("s1")));

        assertThat(chunks).hasSize(1);
        ControllerOutputPayload payload = chunks.get(0).getPayload();
        assertThat(payload.getType()).isEqualTo(EventType.TASK_FAILED.getValue());
        assertThat(payload.getMetadata()).containsEntry("task_id", "t2");
        DataFrame.TextDataFrame frame = (DataFrame.TextDataFrame) payload.getData().get(0);
        assertThat(frame.text()).contains("invoke failed");
    }

    @Test
    void cancelMarksFailedAndAborts() {
        DeepAgent agent = makeAgent(false);
        Fixture fixture = fixture(agent);
        TaskLoopEventExecutor executor = new TaskLoopEventExecutor(fixture.dependencies(), agent);

        boolean result = executor.cancel("t1", new FakeSession("s1"));

        assertThat(result).isTrue();
        assertThat(agent.loopCoordinator().isAborted()).isTrue();
    }

    @Test
    void buildDeepExecutorFactory() {
        DeepAgent agent = makeAgent(false);
        Fixture fixture = fixture(agent);

        TaskLoopEventExecutor executor = TaskLoopEventExecutor.buildDeepExecutor(fixture.dependencies(), agent);

        assertThat(executor).isInstanceOf(TaskLoopEventExecutor.class);
    }

    private static DeepAgent makeAgent(boolean fail) {
        return makeAgent(new FakeReactAgent(fail));
    }

    private static DeepAgent makeAgent(FakeReactAgent reactAgent) {
        DeepAgent agent = new DeepAgent(new AgentCard("test", "test", "t"));
        agent.configure(new com.openjiuwen.harness.schema.DeepAgentConfig());
        agent.setReactAgent(reactAgent, true);
        agent.loopCoordinator().reset();
        return agent;
    }

    private static Fixture fixture(DeepAgent agent) {
        ControllerConfig config = new ControllerConfig();
        TaskManager taskManager = new TaskManager(config);
        TaskExecutorDependencies deps = new TaskExecutorDependencies(
                config,
                agent.getAbilityManager(),
                new ContextEngine(),
                taskManager,
                new EventQueue(config)
        );
        return new Fixture(deps, taskManager);
    }

    private static Task coreTask(String sessionId, String taskId, String description) {
        Task task = new Task(sessionId, taskId, TaskLoopEventExecutor.DEEP_TASK_TYPE);
        task.setDescription(description);
        task.setStatus(TaskStatus.SUBMITTED);
        return task;
    }

    private static List<ControllerOutputChunk> toList(Iterator<ControllerOutputChunk> iterator) {
        List<ControllerOutputChunk> chunks = new ArrayList<>();
        iterator.forEachRemaining(chunks::add);
        return chunks;
    }

    private static Object readField(Object target, String name) throws ReflectiveOperationException {
        return readField(target, target.getClass(), name);
    }

    private static Object readField(Object target, Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private record Fixture(TaskExecutorDependencies dependencies, TaskManager taskManager) {
    }

    private static final class FakeReactAgent {
        private final boolean fail;
        private final List<Map<String, Object>> invokeCalls = new ArrayList<>();

        private FakeReactAgent(boolean fail) {
            this.fail = fail;
        }

        Map<String, Object> invoke(Map<String, Object> inputs, AgentSessionApi session, boolean streaming) {
            invokeCalls.add(new LinkedHashMap<>(inputs));
            if (fail) {
                throw new RuntimeException("invoke failed");
            }
            return Map.of("output", "done:" + inputs.get("query"));
        }
    }

    private static final class FakeSession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private FakeSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            if (key == null) {
                return new LinkedHashMap<>(state);
            }
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public Iterator<Object> streamIterator() {
            return Collections.emptyIterator();
        }
    }
}
