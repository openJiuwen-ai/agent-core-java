/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.offline.coordinator.TaskQueue;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TaskQueue: enqueue, get, add_rollout, get_rollouts.
 * <p>
 * Mirrors Python's {@code test_task_queue.py} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/offline/coordinator/}.
 */
@DisplayName("TaskQueue Tests")
class TestTaskQueue {

    @Nested
    @DisplayName("Queue Operations")
    class QueueOperations {

        @Test
        @DisplayName("queue task and get task")
        void testQueueTaskGetTask() {
            TaskQueue queue = new TaskQueue();
            RLTask task = new RLTask("task-1", "origin-1");

            assertThat(queue.queueTask(task)).isEqualTo("task-1");
            assertThat(queue.getQueueSize()).isEqualTo(1);

            RLTask processing = queue.getTask();

            assertThat(processing).isSameAs(task);
            assertThat(queue.getQueueSize()).isZero();
            assertThat(queue.getInProcessingTask("task-1")).isSameAs(task);
            assertThat(queue.getInProcessingCount()).isEqualTo(1);
            assertThat(queue.isFinished()).isFalse();
        }

        @Test
        @DisplayName("get task empty returns null")
        void testGetTaskEmptyReturnsNull() {
            TaskQueue queue = new TaskQueue();

            assertThat(queue.getTask()).isNull();
            assertThat(queue.isFinished()).isTrue();
        }

        @Test
        @DisplayName("delete task clears in processing")
        void testDeleteTaskClearsInProcessing() {
            TaskQueue queue = new TaskQueue();
            RLTask task = new RLTask("task-1", "origin-1");
            queue.queueTask(task);
            queue.getTask();

            queue.deleteTask(task);

            assertThat(queue.getInProcessingCount()).isZero();
            assertThat(queue.isFinished()).isTrue();
        }
    }

    @Nested
    @DisplayName("Rollout Operations")
    class RolloutOperations {

        @Test
        @DisplayName("add rollout and get rollouts")
        void testAddRolloutGetRollouts() {
            TaskQueue queue = new TaskQueue();
            RLTask task = new RLTask("task-1", "origin-1");
            queue.queueTask(task);
            queue.getTask();
            RolloutMessage rollout = rollout("rollout-1", "task-1");

            assertThat(queue.addRollout(rollout)).isEqualTo("rollout-1");
            assertThat(queue.getInProcessingCount()).isZero();
            assertThat(queue.getRolloutCount()).isEqualTo(1);

            Map<String, RolloutMessage> rollouts = queue.getRollouts();

            assertThat(rollouts).containsEntry("rollout-1", rollout);
            assertThat(queue.getRolloutCount()).isZero();
            assertThat(queue.getRollouts()).isEmpty();
            assertThat(queue.isFinished()).isTrue();
        }

        @Test
        @DisplayName("clear resets queue processing and rollouts")
        void testClearResetsAllState() {
            TaskQueue queue = new TaskQueue();
            queue.queueTask(new RLTask("task-1", "origin-1"));
            RLTask task2 = new RLTask("task-2", "origin-2");
            queue.queueTask(task2);
            queue.getTask();
            queue.addRollout(rollout("rollout-2", "task-2"));

            queue.clear();

            assertThat(queue.getQueueSize()).isZero();
            assertThat(queue.getInProcessingCount()).isZero();
            assertThat(queue.getRolloutCount()).isZero();
            assertThat(queue.isFinished()).isTrue();
        }
    }

    private static RolloutMessage rollout(String rolloutId, String taskId) {
        RolloutMessage rollout = new RolloutMessage();
        rollout.setRolloutId(rolloutId);
        rollout.setTaskId(taskId);
        return rollout;
    }
}
