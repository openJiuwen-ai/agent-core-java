/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskCompletionEvent;
import com.openjiuwen.core.controller.schema.TaskFailedEvent;
import com.openjiuwen.core.controller.schema.TaskInteractionEvent;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code test_deep_agent_event_handler.py} in
 * {@code tests/unit_tests/harness/test_deep_agent_event_handler.py}.
 */
class TaskLoopEventHandlerTest {

    @Test
    void handleInputCreatesTask() {
        DeepAgent agent = makeAgent();
        TaskLoopEventHandler handler = new TaskLoopEventHandler(agent);
        RecordingTaskManager taskManager = new RecordingTaskManager();
        handler.setTaskManager(taskManager);

        InputEvent event = InputEvent.fromUserInput("hello world");
        int roundId = handler.prepareRound();
        event.setMetadata(Map.of("_handler_round_id", roundId));

        Map<String, Object> ack = handler.handleInput(new EventHandlerInput(event, new FakeSession("s1")));

        handler.resolveFuture(Map.of("output", "done:hello world"), roundId);
        Map<String, Object> result = handler.waitCompletion(1.0D);

        assertThat(taskManager.addedTasks).hasSize(1);
        Task coreTask = taskManager.addedTasks.get(0);
        assertThat(coreTask.getTaskType()).isEqualTo(TaskLoopEventExecutor.DEEP_TASK_TYPE);
        assertThat(coreTask.getDescription()).isEqualTo("hello world");
        assertThat(coreTask.getStatus()).isEqualTo(TaskStatus.SUBMITTED);
        assertThat(coreTask.getMetadata()).containsEntry("_handler_round_id", roundId);
        assertThat(result).containsEntry("output", "done:hello world");
        assertThat(handler.getLastResult()).containsEntry("output", "done:hello world");
        assertThat(ack).containsEntry("status", "submitted");
        assertThat(agent.loopCoordinator().getCurrentIteration()).isZero();
    }

    @Test
    void handleInputNoCoordinator() {
        TaskLoopEventHandler handler = new TaskLoopEventHandler(new NoCoordinatorAgent());
        InputEvent event = InputEvent.fromUserInput("test");

        Map<String, Object> result = handler.handleInput(new EventHandlerInput(event, new FakeSession("s1")));

        assertThat(result).containsEntry("status", "failed");
    }

    @Test
    void handleTaskInteraction() {
        TaskLoopEventHandler handler = new TaskLoopEventHandler(makeAgent());
        LoopQueues queues = new LoopQueues();
        handler.setInteractionQueues(queues);
        TaskInteractionEvent event = new TaskInteractionEvent(
                List.of(new DataFrame.TextDataFrame("change plan")),
                null
        );

        Map<String, Object> result = handler.handleTaskInteraction(
                new EventHandlerInput(event, new FakeSession("s1"))
        );

        assertThat(result).containsEntry("status", "steer_injected")
                .containsEntry("msg", "change plan");
        assertThat(queues.drainSteering()).containsExactly("change plan");
    }

    @Test
    void handleTaskCompletionSignals() {
        TaskLoopEventHandler handler = new TaskLoopEventHandler(makeAgent());
        int roundId = handler.prepareRound();
        TaskCompletionEvent event = new TaskCompletionEvent(List.of(), null);
        event.setMetadata(Map.of("task_id", "t1", "_handler_round_id", roundId));

        Map<String, Object> result = handler.handleTaskCompletion(
                new EventHandlerInput(event, new FakeSession("s1"))
        );

        assertThat(result).containsEntry("status", "completed");
        assertThat(handler.waitCompletion(1.0D)).containsEntry("status", "completed");
    }

    @Test
    void handleTaskFailedSignals() {
        TaskLoopEventHandler handler = new TaskLoopEventHandler(makeAgent());
        int roundId = handler.prepareRound();
        TaskFailedEvent event = new TaskFailedEvent("timeout", null);
        event.setMetadata(Map.of("task_id", "t2", "_handler_round_id", roundId));

        Map<String, Object> result = handler.handleTaskFailed(
                new EventHandlerInput(event, new FakeSession("s1"))
        );

        assertThat(result).containsEntry("status", "failed")
                .containsEntry("error", "timeout");
        assertThat(handler.waitCompletion(1.0D)).containsEntry("error", "timeout");
    }

    @Test
    void handleInputWaitsForCompletion() {
        TaskLoopEventHandler handler = new TaskLoopEventHandler(makeAgent());
        handler.setTaskManager(new RecordingTaskManager());
        InputEvent event = InputEvent.fromUserInput("wait test");
        int roundId = handler.prepareRound();
        event.setMetadata(Map.of("_handler_round_id", roundId));

        CompletableFuture<Void> signal = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            handler.resolveFuture(Map.of("output", "waited"), roundId);
        });

        Map<String, Object> ack = handler.handleInput(new EventHandlerInput(event, new FakeSession("s1")));
        Map<String, Object> result = handler.waitCompletion(2.0D);
        signal.join();

        assertThat(ack).containsEntry("status", "submitted");
        assertThat(result).containsEntry("output", "waited");
    }

    private static DeepAgent makeAgent() {
        DeepAgent agent = new DeepAgent(new AgentCard("test", "test", "t"));
        agent.loopCoordinator().reset();
        return agent;
    }

    private static final class NoCoordinatorAgent extends DeepAgent {
        private NoCoordinatorAgent() {
            super(new AgentCard("test", "test", "t"));
        }

        @Override
        public LoopCoordinator loopCoordinator() {
            return null;
        }
    }

    private static final class RecordingTaskManager extends TaskManager {
        private final List<Task> addedTasks = new ArrayList<>();

        private RecordingTaskManager() {
            super(new ControllerConfig());
        }

        @Override
        public void addTask(Task task) {
            addedTasks.add(task.copy());
            super.addTask(task);
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
