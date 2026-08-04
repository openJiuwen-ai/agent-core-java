/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.modules.EventQueue;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.SteeringEvent;
import com.openjiuwen.core.controller.schema.TaskInteractionEvent;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class TaskLoopEventHandlerTest {
    private static final String SESSION_ID = "interaction-session";

    @Test
    void structuredTaskInteractionShouldPreserveStateWithoutEnqueuingSteering() {
        TaskLoopController controller = new TaskLoopController();
        TaskLoopEventHandler handler = new TaskLoopEventHandler(controller);
        int round = controller.prepareRound(SESSION_ID, false);
        InteractionOutput payload = new InteractionOutput("approval", Map.of("question", "Continue?"));
        TaskInteractionEvent event = interactionEvent(round,
                new DataFrame.JsonDataFrame(Map.of("type", "__interaction__", "payload", payload)));

        Map<String, Object> result = handler.handleTaskInteraction(input(event));

        assertThat(controller.drainSteering(SESSION_ID)).isEmpty();
        assertThat(controller.isRoundActive(SESSION_ID)).isFalse();
        assertThat(result).containsEntry("status", "input_required")
                .containsEntry("result_type", "interrupt")
                .containsEntry("output", "");
        assertThat(result.get("interaction")).isEqualTo(event.getInteraction());
        assertThat(result.get("state")).asList().singleElement().isInstanceOf(OutputSchema.class);
        OutputSchema state = (OutputSchema) ((List<?>) result.get("state")).get(0);
        assertThat(state.getType()).isEqualTo("__interaction__");
        assertThat(state.getPayload()).isSameAs(payload);
    }

    @Test
    void terminalInterruptTaskInteractionShouldPreserveStateWithoutEnqueuingSteering() {
        TaskLoopController controller = new TaskLoopController();
        TaskLoopEventHandler handler = new TaskLoopEventHandler(controller);
        int round = controller.prepareRound(SESSION_ID, false);
        OutputSchema state = new OutputSchema("__interaction__", 0,
                new InteractionOutput("approval", "Continue?"));
        TaskInteractionEvent event = interactionEvent(round, new DataFrame.JsonDataFrame(Map.of(
                "result_type", "interrupt", "message", "approval required", "state", List.of(state))));

        Map<String, Object> result = handler.handleTaskInteraction(input(event));

        assertThat(controller.drainSteering(SESSION_ID)).isEmpty();
        assertThat(result.get("state")).asList().containsExactly(state);
    }

    @Test
    void legacyTextTaskInteractionShouldNotBeTreatedAsSteering() {
        TaskLoopController controller = new TaskLoopController();
        TaskLoopEventHandler handler = new TaskLoopEventHandler(controller);
        int round = controller.prepareRound(SESSION_ID, false);
        TaskInteractionEvent event = interactionEvent(round, new DataFrame.TextDataFrame("focus on tests"));

        Map<String, Object> result = handler.handleTaskInteraction(input(event));

        assertThat(controller.drainSteering(SESSION_ID)).isEmpty();
        assertThat(result).containsEntry("status", "input_required");
        assertThat(result.get("interaction")).isEqualTo(event.getInteraction());
    }

    @Test
    void steeringEventShouldEnqueueMessageWithoutCompletingRound() {
        TaskLoopController controller = new TaskLoopController();
        TaskLoopEventHandler handler = new TaskLoopEventHandler(controller);
        controller.prepareRound(SESSION_ID, false);
        SteeringEvent event = new SteeringEvent("focus on tests", "task-1");

        Map<String, Object> result = handler.handleSteering(input(event));

        assertThat(controller.drainSteering(SESSION_ID)).containsExactly("focus on tests");
        assertThat(controller.isRoundActive(SESSION_ID)).isTrue();
        assertThat(result).containsEntry("status", "steering_enqueued")
                .containsEntry("message", "focus on tests")
                .containsEntry("target_task_id", "task-1");
    }

    @Test
    void blankSteeringMessageShouldBeRejected() {
        TaskLoopEventHandler handler = new TaskLoopEventHandler(new TaskLoopController());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> handler.handleSteering(input(new SteeringEvent("  "))))
                .withMessage("Steering message must not be blank");
    }

    @Test
    void eventQueueShouldDispatchSteeringOnItsOwnTopic() {
        TaskLoopController controller = new TaskLoopController();
        TaskLoopEventHandler handler = new TaskLoopEventHandler(controller);
        EventQueue eventQueue = new EventQueue(new ControllerConfig());
        AgentSessionApi session = new AgentSessionApi(SESSION_ID);
        eventQueue.setEventHandler(handler);
        eventQueue.subscribe("agent-1", SESSION_ID);

        eventQueue.publishEvent("agent-1", session, new SteeringEvent("inspect the failure"));

        assertThat(controller.drainSteering(SESSION_ID)).containsExactly("inspect the failure");
    }

    @Test
    void steeringEventShouldUseIndependentEventType() {
        SteeringEvent event = new SteeringEvent("inspect", "task-1");

        assertThat(event.getEventType()).isEqualTo(EventType.STEERING);
        assertThat(EventType.fromValue("steering")).isEqualTo(EventType.STEERING);
        assertThat(event.getMessage()).isEqualTo("inspect");
        assertThat(event.getTargetTaskId()).isEqualTo("task-1");
    }

    private static EventHandlerInput input(com.openjiuwen.core.controller.schema.Event event) {
        return new EventHandlerInput(event, new AgentSessionApi(SESSION_ID));
    }

    private static TaskInteractionEvent interactionEvent(int round, DataFrame frame) {
        TaskInteractionEvent event = new TaskInteractionEvent(List.of(frame), null);
        event.setMetadata(Map.of("_handler_round_id", round));
        return event;
    }
}