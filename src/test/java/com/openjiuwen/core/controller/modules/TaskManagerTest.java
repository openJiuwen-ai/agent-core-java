/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Focused tests for controller task manager behavior.
 *
 * <p>Mirrors Python's {@code TaskManager}, {@code TaskFilter}, and
 * {@code TaskManagerState} in
 * {@code openjiuwen/core/controller/modules/task_manager.py}.</p>
 */
class TaskManagerTest {

    @Test
    void getTaskWithoutFilterReturnsInternalTaskReferences() {
        TaskManager taskManager = new TaskManager(new ControllerConfig());
        taskManager.addTask(task("session-1", "task-1", 1, TaskStatus.SUBMITTED, null));

        List<Task> allTasks = taskManager.getTask(null);
        allTasks.get(0).setStatus(TaskStatus.WORKING);

        assertThat(taskManager.getTask(TaskFilter.byTaskId("task-1")).get(0).getStatus())
                .isEqualTo(TaskStatus.WORKING);
    }

    @Test
    void submittedCallbackRunsForAddedOrUpdatedSubmittedTasks() {
        TaskManager taskManager = new TaskManager(new ControllerConfig());
        AtomicInteger callbackCount = new AtomicInteger();

        taskManager.setOnTaskSubmitted(callbackCount::incrementAndGet);
        taskManager.addTask(task("session-1", "submitted", 1, TaskStatus.SUBMITTED, null));
        taskManager.addTask(task("session-1", "working", 1, TaskStatus.WORKING, null));

        taskManager.updateTaskStatus("working", TaskStatus.SUBMITTED);

        taskManager.setOnTaskSubmitted(null);
        taskManager.addTask(task("session-1", "submitted-after-clear", 1, TaskStatus.SUBMITTED, null));

        assertThat(callbackCount).hasValue(2);
    }

    @Test
    void highestPriorityPopKeepsWithChildrenFilter() {
        TaskManager taskManager = new TaskManager(new ControllerConfig());
        taskManager.addTask(List.of(
                task("session-1", "parent", 10, TaskStatus.SUBMITTED, null),
                task("session-1", "child", 1, TaskStatus.SUBMITTED, "parent")
        ));

        List<Task> popped = taskManager.popTask(TaskFilter.builder()
                .highestPriority()
                .withChildren(true)
                .build());

        assertThat(popped).extracting(Task::getTaskId)
                .containsExactlyInAnyOrder("parent", "child");
        assertThat(taskManager.getTask(null)).isEmpty();
    }

    @Test
    void filterValidationAndStringPriorityMatchPythonModelBehavior() {
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> TaskFilter.builder().build());
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> TaskFilter.byTaskId(null));

        TaskManager taskManager = new TaskManager(new ControllerConfig());
        taskManager.addTask(task("session-1", "priority-3", 3, TaskStatus.SUBMITTED, null));

        assertThat(taskManager.getTask(TaskFilter.builder().priority("3").build()))
                .extracting(Task::getTaskId)
                .containsExactly("priority-3");
    }

    @Test
    void stateMapUsesPythonSerializationNames() {
        TaskManager taskManager = new TaskManager(new ControllerConfig());
        taskManager.addTask(task("session-1", "task-1", 1, TaskStatus.SUBMITTED, null));

        Map<String, Object> stateMap = taskManager.getState().toMap();

        assertThat(stateMap).containsKeys(
                "tasks",
                "priority_index",
                "parent_to_children",
                "children_to_parent",
                "root_tasks"
        );
    }

    private Task task(String sessionId, String taskId, int priority, TaskStatus status, String parentTaskId) {
        Task task = new Task(sessionId, taskId, "test_task");
        task.setPriority(priority);
        task.setStatus(status);
        task.setParentTaskId(parentTaskId);
        return task;
    }
}
