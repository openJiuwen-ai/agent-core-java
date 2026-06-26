/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's task queue tests in
 * {@code tests/unit_tests/agent_evolving/agent_rl/offline/coordinator/test_task_queue.py}.
 */
class TaskQueueTest {

    @Test
    void queueTaskGetTaskAddRolloutGetRolloutsFullFlow() {
        TaskQueue queue = new TaskQueue();
        RLTask task = sampleTask();
        RolloutMessage rolloutMessage = sampleRolloutMessage(task);

        String taskId = queue.queueTask(task);
        assertEquals(task.getTaskId(), taskId);

        RLTask processingTask = queue.getTask();
        assertNotNull(processingTask);
        assertEquals(task.getTaskId(), processingTask.getTaskId());
        assertFalse(queue.isFinished());

        String rolloutId = queue.addRollout(rolloutMessage);
        assertEquals(rolloutMessage.getRolloutId(), rolloutId);

        Map<String, RolloutMessage> rollouts = queue.getRollouts();
        assertEquals(Map.of(rolloutMessage.getRolloutId(), rolloutMessage), rollouts);
        assertTrue(queue.isFinished());
        assertEquals(Map.of(), queue.getRollouts());
    }

    @Test
    void getTaskEmptyReturnsNone() {
        TaskQueue queue = new TaskQueue();
        assertNull(queue.getTask());
        assertTrue(queue.isFinished());
    }

    @Test
    void isFinishedFalseWhenTaskInProcessing() {
        TaskQueue queue = new TaskQueue();
        RLTask task = sampleTask();
        queue.queueTask(task);
        assertFalse(queue.isFinished());

        RLTask processingTask = queue.getTask();
        assertNotNull(processingTask);
        assertFalse(queue.isFinished());

        RolloutMessage rollout = new RolloutMessage();
        rollout.setTaskId(task.getTaskId());
        rollout.setOriginTaskId(task.getOriginTaskId());
        rollout.setRolloutId("r1");
        rollout.setRolloutInfo(List.of());
        rollout.setRewardList(List.of());
        rollout.setTurnCount(0);
        rollout.setRoundNum(0);
        queue.addRollout(rollout);

        assertTrue(queue.isFinished());
    }

    @Test
    void clearResetsAllState() {
        TaskQueue queue = new TaskQueue();
        RLTask task = sampleTask();
        RolloutMessage rolloutMessage = sampleRolloutMessage(task);

        queue.queueTask(task);
        assertNotNull(queue.getTask());
        queue.addRollout(rolloutMessage);

        queue.clear();

        assertEquals(Map.of(), queue.getRollouts());
        assertNull(queue.getTask());
        assertTrue(queue.isFinished());
    }

    @Test
    void deleteTaskRemovesFromInProcessing() {
        TaskQueue queue = new TaskQueue();
        RLTask task = sampleTask();
        queue.queueTask(task);
        RLTask processingTask = queue.getTask();
        assertNotNull(processingTask);

        queue.deleteTask(processingTask);
        assertTrue(queue.isFinished());
    }

    @Test
    void concurrentQueueGetAddRolloutsNoDuplicateOrLoss() {
        TaskQueue queue = new TaskQueue();
        List<RLTask> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            RLTask task = new RLTask();
            task.setTaskId("t-" + i);
            task.setOriginTaskId("o-" + i);
            task.setTaskSample(Map.of());
            task.setRoundNum(0);
            tasks.add(task);
            queue.queueTask(task);
        }

        List<CompletableFuture<Void>> workers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            workers.add(CompletableFuture.runAsync(() -> {
                while (true) {
                    RLTask task = queue.getTask();
                    if (task == null) {
                        if (queue.isFinished()) {
                            break;
                        }
                        try {
                            Thread.sleep(10L);
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            return;
                        }
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
            }));
        }
        CompletableFuture.allOf(workers.toArray(new CompletableFuture[0])).join();

        Map<String, RolloutMessage> rollouts = queue.getRollouts();
        assertEquals(10, rollouts.size());
        Set<String> expected = ConcurrentHashMap.newKeySet();
        tasks.forEach(task -> expected.add(task.getTaskId()));
        assertEquals(expected, rollouts.keySet());
    }

    private static RLTask sampleTask() {
        RLTask task = new RLTask();
        task.setTaskId("task-1");
        task.setOriginTaskId("origin-1");
        task.setTaskSample(new HashMap<>(Map.of("prompt", "hello")));
        task.setRoundNum(0);
        return task;
    }

    private static RolloutMessage sampleRolloutMessage(RLTask task) {
        Rollout rollout = new Rollout();
        rollout.setTurnId(0);
        rollout.setInputPrompt(Map.of("message", List.of()));
        rollout.setOutputResponse(Map.of());

        RolloutMessage message = new RolloutMessage();
        message.setTaskId(task.getTaskId());
        message.setOriginTaskId(task.getOriginTaskId());
        message.setRolloutId("rollout-1");
        message.setRolloutInfo(List.of(rollout));
        message.setRewardList(List.of(0.5d));
        message.setGlobalReward(0.5d);
        message.setTurnCount(1);
        message.setRoundNum(0);
        return message;
    }
}
