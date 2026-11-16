/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.taskloop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.modules.EventQueue;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.modules.TaskScheduler;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskInteractionEvent;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.task_loop.CoreTaskLoopEventExecutor;
import com.openjiuwen.harness.task_loop.TaskLoopEventExecutor;
import com.openjiuwen.harness.task_loop.TaskLoopEventHandler;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Verifies task status and round completion across the executor, scheduler and native event queue. */
class TaskLoopInputRequiredTest {
    @Test
    void invokeInterruptRequiresInputAndPreservesEnvelope() throws ReflectiveOperationException {
        var state = List.of(new OutputSchema("__interaction__", 0, new InteractionOutput("call", "Confirm")));
        Map<String, Object> result = Map.of("result_type", "interrupt", "state", state,
                "interrupt_ids", List.of("call"), "usage_metadata", Map.of("total_tokens", 7));
        try (var fixture = new Fixture(result)) {
            fixture.execute();
            assertThat(fixture.status()).isEqualTo(TaskStatus.INPUT_REQUIRED);
            assertThat(fixture.handler.waitCompletion(0.0D, "session")).containsAllEntriesOf(result);
            assertThat(fixture.handler.getInteractionQueues().drainSteering()).isEmpty();
        }
    }

    @Test
    void publicExecutorInterruptRequiresInputAndPreservesEnvelope() throws ReflectiveOperationException {
        var state = List.of(new OutputSchema("__interaction__", 0, new InteractionOutput("call", "Confirm")));
        Map<String, Object> result = Map.of("result_type", "interrupt", "state", state,
                "interrupt_ids", List.of("call"), "workflow_execution_state", Map.of("pending", "call"));
        var card = new AgentCard("public-executor", "public-executor", "interrupt regression");
        try (var agent = new DeepAgent(card, DeepAgentConfig.builder().build(), null);
                var fixture = new Fixture(result)) {
            agent.setReactAgent(new ReActAgent(card) {
                @Override
                public Object invoke(Object inputs, AgentSessionApi session) {
                    return result;
                }
            }, true);
            fixture.scheduler.getTaskExecutorRegistry().addTaskExecutor(TaskLoopEventExecutor.DEEP_TASK_TYPE,
                    dependencies -> TaskLoopEventExecutor.buildDeepExecutor(dependencies, agent));
            fixture.execute();
            assertThat(fixture.status()).isEqualTo(TaskStatus.INPUT_REQUIRED);
            assertThat(fixture.handler.waitCompletion(0.0D, "session")).containsAllEntriesOf(result);
            assertThat(fixture.handler.getInteractionQueues().drainSteering()).isEmpty();
        }
    }

    @Test
    void streamedInterruptsKeepEveryMemberAndRequireInput() throws ReflectiveOperationException {
        var first = new InteractionOutput("first", "Confirm first");
        var second = new InteractionOutput("second", "Confirm second");
        var outputs = List.of(new OutputSchema("__interaction__", 0, first),
                new OutputSchema("__interaction__", 1, second));
        try (var fixture = new Fixture(Map.of("result_type", "interrupt", "stream_chunks", outputs))) {
            fixture.execute();
            assertThat(fixture.status()).isEqualTo(TaskStatus.INPUT_REQUIRED);
            var result = fixture.handler.waitCompletion(0.0D, "session");
            assertThat(result).containsEntry("result_type", "interrupt");
            List<?> states = assertInstanceOf(List.class, result.get("state"));
            assertThat(states).hasSize(2);
            assertThat(assertInstanceOf(OutputSchema.class, states.get(0)).getPayload()).isSameAs(first);
            assertThat(assertInstanceOf(OutputSchema.class, states.get(1)).getPayload()).isSameAs(second);
        }
    }

    @Test
    void textTaskInteractionEndsRoundWithoutInjectingSteering() throws ReflectiveOperationException {
        List<DataFrame> frames = List.of(new DataFrame.TextDataFrame("Please confirm"),
                new DataFrame.TextDataFrame("Additional details"));
        var chunk = new ControllerOutputChunk(0,
                new ControllerOutputPayload(EventType.TASK_INTERACTION.getValue(), frames, Map.of()), true);
        try (var fixture = new Fixture(Map.of("stream_chunks", List.of(chunk)))) {
            fixture.execute();
            assertThat(fixture.status()).isEqualTo(TaskStatus.INPUT_REQUIRED);
            assertThat(fixture.handler.waitCompletion(0.0D, "session"))
                    .containsEntry("result_type", "interrupt").containsEntry("message", "Please confirm")
                    .containsEntry("task_id", "task").containsEntry("interaction", frames);
            assertThat(fixture.handler.getInteractionQueues().drainSteering()).isEmpty();
        }
    }

    @Test
    void tasklessSteeringWaitsForRealCompletion() throws ReflectiveOperationException {
        try (var fixture = new Fixture(Map.of("output", "done"))) {
            fixture.queue.publishEvent("audit", fixture.session,
                    new TaskInteractionEvent(List.of(new DataFrame.TextDataFrame("change plan")), null));
            assertThat(fixture.handler.getInteractionQueues().drainSteering()).containsExactly("change plan");
            assertThat(fixture.handler.waitCompletion(0.0D, "session"))
                    .containsEntry("error", "completion_timeout");
            fixture.execute();
            assertThat(fixture.status()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(fixture.handler.waitCompletion(0.0D, "session")).containsEntry("output", "done");
        }
    }

    @Test
    void normalAnswerCompletesTask() throws ReflectiveOperationException {
        try (var fixture = new Fixture(Map.of("output", "done", "result_type", "answer"))) {
            fixture.execute();
            assertThat(fixture.status()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(fixture.handler.waitCompletion(0.0D, "session"))
                    .containsEntry("result_type", "answer").containsEntry("output", "done");
        }
    }

    @Test
    void workingTaskTextRemainsSteering() {
        try (var fixture = new Fixture(Map.of())) {
            var task = new Task("session", "running", TaskLoopEventExecutor.DEEP_TASK_TYPE);
            task.setStatus(TaskStatus.WORKING);
            var event = new TaskInteractionEvent(List.of(new DataFrame.TextDataFrame("change plan")), task);
            fixture.queue.publishEvent("audit", fixture.session, event);
            assertThat(fixture.handler.getInteractionQueues().drainSteering()).containsExactly("change plan");
            assertThat(fixture.handler.waitCompletion(0.0D, "session"))
                    .containsEntry("error", "completion_timeout");
        }
    }

    @Test
    void staleTextRequestDoesNotResolveCurrentRound() {
        try (var fixture = new Fixture(Map.of())) {
            var task = fixture.manager.getTask(TaskFilter.byTaskId("task")).get(0);
            task.setStatus(TaskStatus.INPUT_REQUIRED);
            fixture.handler.prepareRound("session", false);
            var event = new TaskInteractionEvent(List.of(new DataFrame.TextDataFrame("Old confirmation")), task);
            fixture.queue.publishEvent("audit", fixture.session, event);
            assertThat(fixture.handler.waitCompletion(0.0D, "session"))
                    .containsEntry("error", "completion_timeout");
            assertThat(fixture.handler.getInteractionQueues().drainSteering()).isEmpty();
        }
    }

    @Test
    void untaggedTextRequestDoesNotResolveAnotherSession() {
        try (var fixture = new Fixture(Map.of())) {
            var task = new Task("other-session", "other-task", TaskLoopEventExecutor.DEEP_TASK_TYPE);
            task.setStatus(TaskStatus.INPUT_REQUIRED);
            var event = new TaskInteractionEvent(List.of(new DataFrame.TextDataFrame("Other confirmation")), task);
            fixture.handler.handleTaskInteraction(new EventHandlerInput(event,
                    new AgentSession("other-session", null, null)));
            assertThat(fixture.handler.waitCompletion(0.0D, "session"))
                    .containsEntry("error", "completion_timeout");
        }
    }

    @Test
    void publicSteerDuringInvokeKeepsRoundOpenUntilAnswer() {
        String sessionId = UUID.randomUUID().toString();
        var card = new AgentCard("steer-" + sessionId, "steer", "steer regression");
        var config = DeepAgentConfig.builder().enableTaskLoop(true).enableTaskPlanning(false)
                .completionTimeout(1.0D).build();
        try (var agent = new DeepAgent(card, config, null)) {
            agent.setReactAgent(new SteeringAgent(card, agent), true);
            var result = agent.invoke(Map.of("query", "start", "conversation_id", sessionId),
                    new AgentSession(sessionId, null, card));
            assertThat(result).containsEntry("output", "done").containsEntry("result_type", "answer");
        } finally {
            CheckpointerFactory.getCheckpointer().release(sessionId);
        }
    }

    @Test
    void failedExecutionStillFailsTask() throws ReflectiveOperationException {
        try (var fixture = new Fixture(Map.of())) {
            fixture.scheduler.getTaskExecutorRegistry().addTaskExecutor(TaskLoopEventExecutor.DEEP_TASK_TYPE,
                    dependencies -> new CoreTaskLoopEventExecutor(dependencies, inputs -> {
                        throw new IllegalStateException("Expected task failure");
                    }));
            fixture.execute();
            assertThat(fixture.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(fixture.handler.waitCompletion(0.0D, "session"))
                    .containsEntry("error", "Expected task failure");
        }
    }

    private static final class SteeringAgent extends ReActAgent {
        private final DeepAgent owner;

        private SteeringAgent(AgentCard card, DeepAgent owner) {
            super(card);
            this.owner = owner;
        }

        @Override
        public Object invoke(Object inputs, AgentSessionApi session) {
            owner.steer("change plan", session);
            assertThat(owner.getEventHandler().getInteractionQueues().drainSteering()).containsExactly("change plan");
            assertThat(owner.getEventHandler().waitCompletion(0.0D, session.getSessionId()))
                    .containsEntry("error", "completion_timeout");
            return Map.of("result_type", "answer", "output", "done");
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final TaskLoopEventHandler handler = new TaskLoopEventHandler((Object) null);
        private final AgentSession session = new AgentSession("session", null, null);
        private final TaskManager manager;
        private final EventQueue queue;
        private final TaskScheduler scheduler;

        private Fixture(Map<String, Object> result) {
            var config = new ControllerConfig();
            manager = new TaskManager(config);
            queue = new EventQueue(config);
            queue.setEventHandler(handler);
            queue.subscribe("audit", "session");
            scheduler = new TaskScheduler(config, manager, new ContextEngine(), new AbilityManager(), queue,
                    new BaseCard("audit", "audit", "audit"));
            scheduler.getTaskExecutorRegistry().addTaskExecutor(TaskLoopEventExecutor.DEEP_TASK_TYPE,
                    dependencies -> new CoreTaskLoopEventExecutor(dependencies, inputs -> result));
            int round = handler.prepareRound("session", false);
            var task = new Task("session", "task", TaskLoopEventExecutor.DEEP_TASK_TYPE);
            task.setDescription("start");
            task.setMetadata(Map.of("_handler_round_id", round));
            manager.addTask(task);
        }

        private void execute() throws ReflectiveOperationException {
            // Exercise the real scheduling body deterministically without starting a background scanner.
            Method execute = TaskScheduler.class.getDeclaredMethod("executeTask", String.class,
                    AgentSessionApi.class);
            execute.setAccessible(true);
            execute.invoke(scheduler, "task", session);
        }

        private TaskStatus status() {
            return manager.getTask(TaskFilter.byTaskId("task")).get(0).getStatus();
        }

        @Override
        public void close() {
            queue.stop();
            handler.clearAllRounds();
        }
    }
}
