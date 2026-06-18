/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.reasoner;

import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.controller.legacy.config.PlannerConfig;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.controller.legacy.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests legacy planner behavior.
 *
 * <p>Mirrors Python's {@code Planner} in
 * {@code openjiuwen/core/controller/legacy/reasoner/planner.py}.</p>
 */
class PlannerTest {

    @Test
    void constructorStoresConfigContextEngineAndSessionReferences() {
        PlannerConfig config = new PlannerConfig();
        ContextEngine contextEngine = new ContextEngine();
        Object session = new Object();

        Planner planner = new Planner(config, contextEngine, session);

        assertThat(planner.getConfig()).isSameAs(config);
        assertThat(planner.getContextEngine()).isSameAs(contextEngine);
        assertThat(planner.getSession()).isSameAs(session);
    }

    @Test
    void processMessageReturnsDefaultPendingUndefinedPlannerTask() {
        Planner planner = new Planner(new PlannerConfig(), null, null);
        Event event = Event.createUserEvent("book a meeting", "conv-1", "user-1", null);
        event.setEventId("event-1");

        List<Task> tasks = planner.processMessage(event).toCompletableFuture().join();

        assertThat(tasks).hasSize(1);
        Task task = tasks.get(0);
        assertThat(task.getTaskType()).isEqualTo(TaskType.UNDEFINED);
        assertThat(task.getDescription()).isEqualTo("Planner task for message: book a meeting");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(task.getMetadata())
                .containsEntry("original_message_id", "event-1")
                .containsEntry("task_source", "planner");
    }

    @Test
    void processMessageUsesNoContentWhenEventContentIsNull() {
        Planner planner = new Planner(new PlannerConfig(), null, null);
        Event event = Event.builder()
                .eventId("event-without-content")
                .content(null)
                .build();

        List<Task> tasks = planner.processMessage(event).toCompletableFuture().join();

        assertThat(tasks).singleElement()
                .extracting(Task::getDescription)
                .isEqualTo("Planner task for message: No content");
    }

    @Test
    void processMessageReturnsMutableListLikePythonList() {
        Planner planner = new Planner(new PlannerConfig(), null, null);
        Event event = Event.createUserEvent("hello", "conv-1", "user-1", null);

        List<Task> tasks = planner.processMessage(event).toCompletableFuture().join();
        tasks.add(new Task());

        assertThat(tasks).hasSize(2);
    }
}
