/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import com.openjiuwen.core.singleagent.rail.TaskIterationInputs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's loop event schema tests in
 * {@code tests/unit_tests/harness/test_loop_event_schema.py}.</p>
 */
class LoopEventSchemaPythonParityTest {

    @Test
    void deepLoopEventPriorityOrder() {
        PriorityQueue<DeepLoopEvent> queue = new PriorityQueue<>();
        queue.add(DeepLoopEvent.builder()
                .priority(10)
                .seq(2)
                .eventType(DeepLoopEventType.FOLLOWUP)
                .content("follow-up")
                .build());
        queue.add(DeepLoopEvent.builder()
                .priority(1)
                .seq(1)
                .eventType(DeepLoopEventType.STEER)
                .content("steer")
                .build());
        queue.add(DeepLoopEvent.builder()
                .priority(10)
                .seq(1)
                .eventType(DeepLoopEventType.FOLLOWUP)
                .content("follow-up-2")
                .build());

        List<String> contents = new ArrayList<>();
        while (!queue.isEmpty()) {
            contents.add(queue.remove().getContent());
        }

        assertThat(contents).containsExactly("steer", "follow-up-2", "follow-up");
    }

    @Test
    void taskIterationInputsDefaults() {
        DeepLoopEvent loopEvent = DeepLoopEvent.builder()
                .priority(10)
                .seq(1)
                .eventType(DeepLoopEventType.FOLLOWUP)
                .content("task")
                .build();
        TaskIterationInputs inputs = new TaskIterationInputs();
        inputs.setIteration(1);
        inputs.setLoopEvent(loopEvent);

        assertThat(inputs.getIteration()).isEqualTo(1);
        assertThat(((DeepLoopEvent) inputs.getLoopEvent()).getContent()).isEqualTo("task");
        assertThat(inputs.getConversationId()).isNull();
        assertThat(inputs.getResult()).isNull();
    }

    @Test
    void createLoopEventDefaultPriorities() {
        DeepLoopEvent abortEvent = HarnessSchemaPackage.createLoopEvent(
                1,
                DeepLoopEventType.ABORT,
                "stop",
                null,
                null,
                null);
        DeepLoopEvent steerEvent = HarnessSchemaPackage.createLoopEvent(
                2,
                DeepLoopEventType.STEER,
                "guide",
                null,
                null,
                null);
        DeepLoopEvent followupEvent = HarnessSchemaPackage.createLoopEvent(
                3,
                DeepLoopEventType.FOLLOWUP,
                "next",
                null,
                null,
                null);

        assertThat(abortEvent.getPriority()).isLessThan(steerEvent.getPriority());
        assertThat(steerEvent.getPriority()).isLessThan(followupEvent.getPriority());
    }
}
