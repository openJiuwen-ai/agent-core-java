/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.taskloop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskCompletionEvent;
import com.openjiuwen.core.controller.schema.TaskInteractionEvent;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.task_loop.TaskLoopEventExecutor;
import com.openjiuwen.harness.task_loop.TaskLoopEventHandler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Structured interrupts complete only their owning round without entering steering.
 *
 * @since 0.1.17
 */
class TaskLoopInterruptTest {
    @Test
    void parallelInterruptsCompleteRoundAndKeepInputRequired() {
        var handler = new TaskLoopEventHandler((Object) null);
        int round = handler.prepareRound("session", false);
        var task = new Task("session", "task", TaskLoopEventExecutor.DEEP_TASK_TYPE);
        task.setStatus(TaskStatus.INPUT_REQUIRED);
        task.setMetadata(Map.of("_handler_round_id", round));
        var first = new InteractionOutput("first", "Confirm first");
        var second = new InteractionOutput("second", "Confirm second");
        var event = new TaskInteractionEvent(List.of(frame(first), frame(second)), task);

        handler.handleTaskInteraction(new EventHandlerInput(event, new AgentSession("session", null, null)));

        Map<String, Object> result = handler.waitCompletion(0.0D, "session");
        assertThat(result).containsEntry("result_type", "interrupt");
        List<?> states = assertInstanceOf(List.class, result.get("state"));
        assertThat(states).hasSize(2);
        assertThat(assertInstanceOf(OutputSchema.class, states.get(0)).getPayload()).isSameAs(first);
        assertThat(assertInstanceOf(OutputSchema.class, states.get(1)).getPayload()).isSameAs(second);
        assertThat(handler.getInteractionQueues().drainSteering()).isEmpty();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.INPUT_REQUIRED);
    }

    @Test
    void interruptEnvelopePreservesStateAndAdditionalFields() {
        var handler = new TaskLoopEventHandler((Object) null);
        int round = handler.prepareRound("session", false);
        var states = List.of(new OutputSchema("__interaction__", 0, new InteractionOutput("call", "Confirm")));
        Map<String, Object> envelope = Map.of("result_type", "interrupt", "state", states,
                "interrupt_ids", List.of("call"), "usage_metadata", Map.of("total_tokens", 7));
        var event = new TaskInteractionEvent(List.of(new DataFrame.JsonDataFrame(envelope)), null);
        event.setMetadata(Map.of("_handler_round_id", round));

        handler.handleTaskInteraction(new EventHandlerInput(event, new AgentSession("session", null, null)));

        assertThat(handler.waitCompletion(0.0D, "session")).containsAllEntriesOf(envelope);
        assertThat(handler.getInteractionQueues().drainSteering()).isEmpty();
    }

    @Test
    void steeringDoesNotCompleteRound() {
        var handler = new TaskLoopEventHandler((Object) null);
        int round = handler.prepareRound("session", false);
        var event = new TaskInteractionEvent(List.of(new DataFrame.TextDataFrame("change plan")), null);
        event.setMetadata(Map.of("_handler_round_id", round));
        handler.handleTaskInteraction(new EventHandlerInput(event, new AgentSession("session", null, null)));

        assertThat(handler.getInteractionQueues().drainSteering()).containsExactly("change plan");
        assertThat(handler.waitCompletion(0.0D, "session")).containsEntry("error", "completion_timeout");
        var completion = new TaskCompletionEvent(List.of(new DataFrame.JsonDataFrame(Map.of("output", "done"))), null);
        completion.setMetadata(Map.of("_handler_round_id", round));
        handler.handleTaskCompletion(new EventHandlerInput(completion, new AgentSession("session", null, null)));
        assertThat(handler.waitCompletion(0.0D, "session")).containsEntry("output", "done");
    }

    @Test
    void interruptDoesNotResolveAnotherSessionOrStaleRound() throws Exception {
        var handler = new TaskLoopEventHandler((Object) null);
        int staleRound = handler.prepareRound("first", false);
        int currentRound = handler.prepareRound("first", false);
        handler.prepareRound("second", false);
        var event = new TaskInteractionEvent(List.of(frame(new InteractionOutput("call", "Confirm"))), null);
        event.setMetadata(Map.of("_handler_round_id", staleRound));
        handler.handleTaskInteraction(new EventHandlerInput(event, new AgentSession("first", null, null)));
        assertThat(waitForSession(handler, "first")).containsEntry("error", "completion_timeout");

        event.setMetadata(Map.of("_handler_round_id", currentRound));
        handler.handleTaskInteraction(new EventHandlerInput(event, new AgentSession("first", null, null)));
        assertThat(waitForSession(handler, "first")).containsEntry("result_type", "interrupt");
        assertThat(waitForSession(handler, "second")).containsEntry("error", "completion_timeout");
    }

    @Test
    void untaggedInterruptUsesOnlyItsOwnSession() throws Exception {
        var handler = new TaskLoopEventHandler((Object) null);
        handler.prepareRound("first", false);
        handler.prepareRound("second", false);
        var event = new TaskInteractionEvent(List.of(frame(new InteractionOutput("call", "Confirm"))), null);
        handler.handleTaskInteraction(new EventHandlerInput(event, new AgentSession("unknown", null, null)));
        assertThat(waitForSession(handler, "second")).containsEntry("error", "completion_timeout");
        handler.handleTaskInteraction(new EventHandlerInput(event, new AgentSession("first", null, null)));
        assertThat(waitForSession(handler, "first")).containsEntry("result_type", "interrupt");
        assertThat(waitForSession(handler, "second")).containsEntry("error", "completion_timeout");
    }

    private static Map<String, Object> waitForSession(TaskLoopEventHandler handler, String session) throws Exception {
        // Event callbacks and request waiters run on different threads in the controller.
        return CompletableFuture.supplyAsync(() -> handler.waitCompletion(0.0D, session)).get(5, TimeUnit.SECONDS);
    }

    private static DataFrame frame(InteractionOutput payload) {
        return new DataFrame.JsonDataFrame(Map.of("type", "__interaction__", "payload", payload));
    }
}
