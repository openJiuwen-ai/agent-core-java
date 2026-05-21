/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent_evolving.agent_rl.offline.coordinator;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

/**
 * Mirrors Python's test_task_queue_e2e.py.
 */
class TaskQueueE2eTest {

    static class RLTask {
        String taskId;
        String originTaskId;
        Map<String, Object> taskSample;
        int roundNum;

        RLTask(String taskId, String originTaskId, Map<String, Object> taskSample, int roundNum) {
            this.taskId = taskId;
            this.originTaskId = originTaskId;
            this.taskSample = taskSample;
            this.roundNum = roundNum;
        }
    }

    static class TaskQueue {
        Map<String, RLTask> tasks = new LinkedHashMap<>();
        Map<String, Map<String, Object>> rollouts = new LinkedHashMap<>();
        List<String> taskOrder = new ArrayList<>();
        int taskIndex = 0;
        boolean finished = false;

        String queueTask(RLTask task) {
            tasks.put(task.taskId, task);
            taskOrder.add(task.taskId);
            return task.taskId;
        }

        RLTask getTask() {
            if (taskIndex >= taskOrder.size()) return null;
            return tasks.get(taskOrder.get(taskIndex++));
        }

        String addRollout(Map<String, Object> rollout) {
            String rolloutId = (String) rollout.get("rollout_id");
            rollouts.put(rolloutId, rollout);
            if (rollouts.size() >= tasks.size()) finished = true;
            return rolloutId;
        }

        Map<String, Map<String, Object>> getRollouts() {
            return rollouts;
        }

        boolean isFinished() {
            return finished;
        }
    }

    TaskQueue queue = new TaskQueue();

    @Test
    void testTaskQueueE2eFullFlow() {
        RLTask sampleTask = new RLTask("task-e2e-1", "origin-e2e-1",
                Map.of("prompt", "calculate 1+1"), 0);

        String tid = queue.queueTask(sampleTask);
        assertEquals("task-e2e-1", tid);

        RLTask t = queue.getTask();
        assertNotNull(t);
        assertEquals("task-e2e-1", t.taskId);
        assertFalse(queue.isFinished());

        Map<String, Object> sampleRollout = new LinkedHashMap<>();
        sampleRollout.put("rollout_id", "rollout-e2e-1");
        sampleRollout.put("task_id", "task-e2e-1");
        sampleRollout.put("global_reward", 0.8);

        String rid = queue.addRollout(sampleRollout);
        assertEquals("rollout-e2e-1", rid);

        Map<String, Map<String, Object>> rollouts = queue.getRollouts();
        assertTrue(rollouts.containsKey("rollout-e2e-1"));
        assertEquals(0.8, rollouts.get("rollout-e2e-1").get("global_reward"));
        assertTrue(queue.isFinished());
    }

    @Test
    void testTaskQueueE2eMultipleTasks() {
        List<RLTask> tasks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            tasks.add(new RLTask("t" + i, "o" + i, new HashMap<>(), 0));
        }
        for (RLTask t : tasks) {
            queue.queueTask(t);
        }

        while (!queue.isFinished()) {
            RLTask t = queue.getTask();
            if (t == null) continue;
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("rollout_id", t.taskId);
            msg.put("task_id", t.taskId);
            msg.put("origin_task_id", t.originTaskId);
            queue.addRollout(msg);
        }

        Map<String, Map<String, Object>> rollouts = queue.getRollouts();
        assertEquals(3, rollouts.size());
        Set<String> expectedKeys = new HashSet<>();
        for (RLTask t : tasks) expectedKeys.add(t.taskId);
        assertEquals(expectedKeys, rollouts.keySet());
    }
}
