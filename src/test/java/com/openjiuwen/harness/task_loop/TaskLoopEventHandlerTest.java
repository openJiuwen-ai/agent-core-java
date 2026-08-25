/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.TaskInteractionEvent;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class TaskLoopEventHandlerTest {
    private static final String SESSION_ID = "task-loop-interaction-session";

    @Test
    void structuredInteractionShouldNotEnterSteering() {
        TaskLoopController controller = new TaskLoopController();
        TaskLoopEventHandler handler = new TaskLoopEventHandler(controller);
        InteractionOutput payload = new InteractionOutput("ask-user-call", Map.of("question", "Continue?"));
        TaskInteractionEvent event = interactionEvent(controller,
                List.of(new DataFrame.JsonDataFrame(Map.of(
                        "type", Constant.INTERACTION,
                        "payload", payload))));

        Map<String, Object> result = handler.handleTaskInteraction(input(event));

        assertThat(result).containsEntry("msg", "").containsEntry("result_type", "interrupt");
        assertThat(controller.drainSteering(SESSION_ID)).isEmpty();
    }

    @Test
    void interruptResultShouldNotEnterSteering() {
        TaskLoopController controller = new TaskLoopController();
        TaskLoopEventHandler handler = new TaskLoopEventHandler(controller);
        TaskInteractionEvent event = interactionEvent(controller,
                List.of(new DataFrame.JsonDataFrame(Map.of(
                        "result_type", "interrupt",
                        "message", "approval required"))));

        Map<String, Object> result = handler.handleTaskInteraction(input(event));

        assertThat(result).containsEntry("msg", "").doesNotContainKey("message");
        assertThat(controller.drainSteering(SESSION_ID)).isEmpty();
    }

    @Test
    void textInteractionShouldStillEnterSteering() {
        TaskLoopController controller = new TaskLoopController();
        TaskLoopEventHandler handler = new TaskLoopEventHandler(controller);
        TaskInteractionEvent event = interactionEvent(controller,
                List.of(new DataFrame.TextDataFrame("focus on the latest request")));

        Map<String, Object> result = handler.handleTaskInteraction(input(event));

        assertThat(result).containsEntry("msg", "focus on the latest request");
        assertThat(controller.drainSteering(SESSION_ID)).containsExactly("focus on the latest request");
    }

    private static TaskInteractionEvent interactionEvent(TaskLoopController controller, List<DataFrame> frames) {
        int round = controller.prepareRound(SESSION_ID, false);
        TaskInteractionEvent event = new TaskInteractionEvent(frames, null);
        event.setMetadata(Map.of("_handler_round_id", round));
        return event;
    }

    private static EventHandlerInput input(TaskInteractionEvent event) {
        return new EventHandlerInput(event, new AgentSessionApi(SESSION_ID));
    }
}
