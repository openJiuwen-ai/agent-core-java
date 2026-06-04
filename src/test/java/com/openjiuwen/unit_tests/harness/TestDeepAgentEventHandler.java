/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskCompletionEvent;
import com.openjiuwen.core.controller.schema.TaskFailedEvent;
import com.openjiuwen.core.controller.schema.TaskInteractionEvent;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.task_loop.LoopCoordinator;
import com.openjiuwen.harness.task_loop.TaskLoopEventHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_deep_agent_event_handler} in
 * {@code tests.unit_tests.harness.test_deep_agent_event_handler}.
 */
class TestDeepAgentEventHandler {

    static final class TestDeepAgent extends DeepAgent {
        LoopCoordinator _loopCoordinator;

        TestDeepAgent(AgentCard card) {
            super(card);
        }
    }

    static final class FakeTaskManager implements TaskLoopEventHandler.TaskManagerAdapter {
        final List<Task> addedTasks = new ArrayList<>();

        @Override
        public void addTask(Task task) {
            addedTasks.add(task);
        }
    }

    private static AgentCard card() {
        AgentCard card = new AgentCard();
        card.setName("test");
        card.setDescription("t");
        return card;
    }

    private static EventHandlerInput input(Object event, AgentSessionApi session) {
        return new EventHandlerInput((com.openjiuwen.core.controller.schema.Event) event, session);
    }

    @Test
    @Tag("level0")
    @DisplayName("handleInput creates submitted task and resolves waitCompletion")
    void testHandleInputCreatesTask() {
        TestDeepAgent agent = new TestDeepAgent(card());
        agent._loopCoordinator = new LoopCoordinator();
        TaskLoopEventHandler handler = new TaskLoopEventHandler(agent);
        FakeTaskManager taskManager = new FakeTaskManager();
        handler.setTaskManager(taskManager);

        String roundId = handler.prepareRound();
        InputEvent event = InputEvent.fromUserInput("hello world");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("_handler_round_id", roundId);
        event.setMetadata(metadata);

        AgentSessionApi session = new AgentSessionApi("s1");
        Map<String, Object> ack = handler.handleInput(input(event, session));

        handler.resolveFuture(Map.of("output", "done:hello world"), roundId);
        Map<String, Object> result = handler.waitCompletion(1000);

        assertEquals("submitted", ack.get("status"));
        assertEquals(1, taskManager.addedTasks.size());
        Task task = taskManager.addedTasks.get(0);
        assertEquals("deep_agent_task", task.getTaskType());
        assertEquals("hello world", task.getDescription());
        assertEquals(TaskStatus.SUBMITTED, task.getStatus());
        assertEquals("done:hello world", result.get("output"));
        assertEquals(result, handler.getLastResult());
    }

    @Test
    @Tag("level0")
    @DisplayName("handleInput fails when loop coordinator is missing")
    void testHandleInputNoCoordinator() {
        DeepAgent agent = new DeepAgent(card());
        TaskLoopEventHandler handler = new TaskLoopEventHandler(agent);

        InputEvent event = InputEvent.fromUserInput("test");
        event.setMetadata(Map.of("_handler_round_id", handler.prepareRound()));
        Map<String, Object> result = handler.handleInput(input(event, new AgentSessionApi("s1")));

        assertEquals("failed", result.get("status"));
    }

    @Test
    @Tag("level0")
    @DisplayName("handleTaskInteraction pushes steering message into queue")
    void testHandleTaskInteraction() {
        TaskLoopEventHandler handler = new TaskLoopEventHandler();
        TaskInteractionEvent event = new TaskInteractionEvent(List.of(new DataFrame.TextDataFrame("change plan")), null);

        Map<String, Object> result = handler.handleTaskInteraction(input(event, new AgentSessionApi("s1")));

        assertEquals("steer_injected", result.get("status"));
        assertEquals("change plan", result.get("msg"));
        assertEquals(List.of("change plan"), handler.getInteractionQueues().drainSteering());
    }

    @Test
    @Tag("level0")
    @DisplayName("handleTaskCompletion resolves current round future")
    void testHandleTaskCompletionSignals() {
        TaskLoopEventHandler handler = new TaskLoopEventHandler();
        String roundId = handler.prepareRound();

        TaskCompletionEvent event = new TaskCompletionEvent(List.of(), null);
        event.setMetadata(Map.of("task_id", "t1", "_handler_round_id", roundId));

        Map<String, Object> ack = handler.handleTaskCompletion(input(event, new AgentSessionApi("s1")));
        Map<String, Object> result = handler.waitCompletion(1000);

        assertEquals("completed", ack.get("status"));
        assertEquals("completed", result.get("status"));
    }

    @Test
    @Tag("level0")
    @DisplayName("handleTaskFailed resolves current round future with error")
    void testHandleTaskFailedSignals() {
        TaskLoopEventHandler handler = new TaskLoopEventHandler();
        String roundId = handler.prepareRound();

        TaskFailedEvent event = new TaskFailedEvent("timeout", null);
        event.setMetadata(Map.of("task_id", "t2", "_handler_round_id", roundId));

        Map<String, Object> ack = handler.handleTaskFailed(input(event, new AgentSessionApi("s1")));
        Map<String, Object> result = handler.waitCompletion(1000);

        assertEquals("failed", ack.get("status"));
        assertEquals("timeout", result.get("error"));
    }

    @Test
    @Tag("level0")
    @DisplayName("waitCompletion blocks until delayed round resolution")
    void testHandleInputWaitsForCompletion() {
        TestDeepAgent agent = new TestDeepAgent(card());
        agent._loopCoordinator = new LoopCoordinator();
        TaskLoopEventHandler handler = new TaskLoopEventHandler(agent);
        handler.setTaskManager(new FakeTaskManager());

        String roundId = handler.prepareRound();
        InputEvent event = InputEvent.fromUserInput("wait test");
        event.setMetadata(Map.of("_handler_round_id", roundId));

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            handler.resolveFuture(Map.of("output", "waited"), roundId);
        });

        Map<String, Object> ack = handler.handleInput(input(event, new AgentSessionApi("s1")));
        Map<String, Object> result = handler.waitCompletion(2000);

        assertEquals("submitted", ack.get("status"));
        assertEquals("waited", result.get("output"));
    }
}
