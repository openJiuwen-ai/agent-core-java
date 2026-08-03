/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lightweight asynchronous queue managing rollout tasks,
 * tracking in-processing tasks, and buffering completed rollout messages.
 *
 * <p>Mirrors Python's {@code TaskQueue} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/coordinator/task_queue.py}.</p>
 */
public class TaskQueue {

    private final Queue<RLTask> taskQueue = new ArrayDeque<>();
    private final Map<String, RLTask> inProcessingTask = new LinkedHashMap<>();
    private final Map<String, RolloutMessage> rollouts = new LinkedHashMap<>();
    private final ReentrantLock queueLock = new ReentrantLock();
    private final ReentrantLock rolloutLock = new ReentrantLock();

    /**
     * Enqueue a new task and return its task identifier.
     *
     * @param task task to queue
     * @return task identifier
     */
    public String queueTask(RLTask task) {
        RLTask queuedTask = Objects.requireNonNull(task, "task");
        queueLock.lock();
        try {
            taskQueue.add(queuedTask);
            return queuedTask.getTaskId();
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Retrieve the next task for processing.
     *
     * @return next task, or null when the queue is empty
     */
    public RLTask getTask() {
        queueLock.lock();
        try {
            RLTask processingTask = taskQueue.poll();
            if (processingTask == null) {
                return null;
            }
            inProcessingTask.put(processingTask.getTaskId(), processingTask);
            return processingTask;
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Remove a task from the in-processing pool directly.
     *
     * @param task task to delete
     */
    public void deleteTask(RLTask task) {
        if (task == null) {
            return;
        }
        queueLock.lock();
        try {
            inProcessingTask.remove(task.getTaskId());
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Store a completed rollout and clear its in-processing entry.
     *
     * @param rollout completed rollout
     * @return rollout identifier
     */
    public String addRollout(RolloutMessage rollout) {
        RolloutMessage completedRollout = Objects.requireNonNull(rollout, "rollout");
        String taskId = completedRollout.getTaskId();

        queueLock.lock();
        try {
            if (taskId != null) {
                inProcessingTask.remove(taskId);
            }
        } finally {
            queueLock.unlock();
        }

        rolloutLock.lock();
        try {
            rollouts.put(completedRollout.getRolloutId(), completedRollout);
            return completedRollout.getRolloutId();
        } finally {
            rolloutLock.unlock();
        }
    }

    /**
     * Retrieve and clear all cached rollouts atomically.
     *
     * @return copied rollout map
     */
    public Map<String, RolloutMessage> getRollouts() {
        rolloutLock.lock();
        try {
            Map<String, RolloutMessage> copied = new LinkedHashMap<>(rollouts);
            rollouts.clear();
            return copied;
        } finally {
            rolloutLock.unlock();
        }
    }

    /**
     * Check whether all tasks have been processed.
     *
     * @return true when no queued or in-processing tasks remain
     */
    public boolean isFinished() {
        queueLock.lock();
        try {
            return taskQueue.isEmpty() && inProcessingTask.isEmpty();
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Fully reset the queue and rollout buffer.
     */
    public void clear() {
        queueLock.lock();
        try {
            taskQueue.clear();
            inProcessingTask.clear();
        } finally {
            queueLock.unlock();
        }
        rolloutLock.lock();
        try {
            rollouts.clear();
        } finally {
            rolloutLock.unlock();
        }
    }
}
