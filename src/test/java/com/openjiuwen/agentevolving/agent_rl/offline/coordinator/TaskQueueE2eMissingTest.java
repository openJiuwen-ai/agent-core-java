/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.offline.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agentevolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agentevolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agentevolving.agent_rl.schemas.RolloutMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests.system_tests.agent_evolving.agent_rl.offline.coordinator.test_task_queue_e2e}
 * in {@code tests/system_tests/agent_evolving/agent_rl/offline/coordinator/test_task_queue_e2e.py}.
 */
class TaskQueueE2eMissingTest {

    @Test
    void taskQueueE2eFullFlow() {
        TaskQueue queue = new TaskQueue();
        RLTask task = sampleTask();
        RolloutMessage rollout = sampleRollout(task);

        String taskId = queue.queueTask(task);
        assertThat(taskId).isEqualTo(task.getTaskId());

        RLTask processingTask = queue.getTask();
        assertNotNull(processingTask);
        assertThat(processingTask.getTaskId()).isEqualTo(task.getTaskId());
        assertFalse(queue.isFinished());

        String rolloutId = queue.addRollout(rollout);
        assertThat(rolloutId).isEqualTo(rollout.getRolloutId());

        Map<String, RolloutMessage> rollouts = queue.getRollouts();
        assertThat(rollouts).containsKey(rollout.getRolloutId());
        assertThat(rollouts.get(rollout.getRolloutId()).getGlobalReward()).isEqualTo(0.8D);
        assertTrue(queue.isFinished());
    }

    @Test
    void taskQueueE2eMultipleTasks() {
        TaskQueue queue = new TaskQueue();
        List<RLTask> tasks = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            RLTask task = new RLTask();
            task.setTaskId("t" + index);
            task.setOriginTaskId("o" + index);
            task.setTaskSample(Map.of());
            task.setRoundNum(0);
            tasks.add(task);
            queue.queueTask(task);
        }

        while (!queue.isFinished()) {
            RLTask task = queue.getTask();
            if (task == null) {
                continue;
            }
            RolloutMessage message = new RolloutMessage();
            message.setTaskId(task.getTaskId());
            message.setOriginTaskId(task.getOriginTaskId());
            message.setRolloutId(task.getTaskId());
            message.setRolloutInfo(List.of());
            message.setRewardList(List.of());
            message.setTurnCount(0);
            message.setRoundNum(0);
            queue.addRollout(message);
        }

        Map<String, RolloutMessage> rollouts = queue.getRollouts();
        assertThat(rollouts).hasSize(3);
        assertThat(rollouts.keySet()).containsExactlyInAnyOrderElementsOf(
                tasks.stream().map(RLTask::getTaskId).toList());
    }

    private static RLTask sampleTask() {
        RLTask task = new RLTask();
        task.setTaskId("task-e2e-1");
        task.setOriginTaskId("origin-e2e-1");
        task.setTaskSample(new HashMap<>(Map.of("prompt", "calculate 1+1")));
        task.setRoundNum(0);
        return task;
    }

    private static RolloutMessage sampleRollout(RLTask task) {
        Rollout rollout = new Rollout();
        rollout.setTurnId(0);
        rollout.setInputPrompt(Map.of("message", List.of()));
        rollout.setOutputResponse(Map.of());

        RolloutMessage message = new RolloutMessage();
        message.setTaskId(task.getTaskId());
        message.setOriginTaskId(task.getOriginTaskId());
        message.setRolloutId("rollout-e2e-1");
        message.setRolloutInfo(List.of(rollout));
        message.setRewardList(List.of(0.8D));
        message.setGlobalReward(0.8D);
        message.setTurnCount(1);
        message.setRoundNum(0);
        return message;
    }
}
