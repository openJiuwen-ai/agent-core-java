/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lightweight asynchronous queue managing rollout tasks,
 * tracking in-processing tasks, and buffering completed rollout messages.
 * <p>
 * Mirrors Python's {@code TaskQueue} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.coordinator.task_queue}.
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
            RLTask task = taskQueue.poll();
            if (task == null) {
                return null;
            }
            inProcessingTask.put(task.getTaskId(), task);
            return task;
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
     * @param rollout completed rollout message
     * @return rollout identifier
     */
    public String addRollout(RolloutMessage rollout) {
        RolloutMessage completedRollout = Objects.requireNonNull(rollout, "rollout");
        String rolloutId = completedRollout.getRolloutId();
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
            rollouts.put(rolloutId, completedRollout);
            return rolloutId;
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

    public RolloutMessage getRollout(String rolloutId) {
        rolloutLock.lock();
        try {
            return rollouts.get(rolloutId);
        } finally {
            rolloutLock.unlock();
        }
    }

    public void clearRollouts() {
        rolloutLock.lock();
        try {
            rollouts.clear();
        } finally {
            rolloutLock.unlock();
        }
    }

    public boolean isQueueEmpty() {
        queueLock.lock();
        try {
            return taskQueue.isEmpty();
        } finally {
            queueLock.unlock();
        }
    }

    public int getQueueSize() {
        queueLock.lock();
        try {
            return taskQueue.size();
        } finally {
            queueLock.unlock();
        }
    }

    public int getInProcessingCount() {
        queueLock.lock();
        try {
            return inProcessingTask.size();
        } finally {
            queueLock.unlock();
        }
    }

    public int getRolloutCount() {
        rolloutLock.lock();
        try {
            return rollouts.size();
        } finally {
            rolloutLock.unlock();
        }
    }

    public RLTask getInProcessingTask(String taskId) {
        queueLock.lock();
        try {
            return inProcessingTask.get(taskId);
        } finally {
            queueLock.unlock();
        }
    }

    public List<RLTask> getPendingTasks() {
        queueLock.lock();
        try {
            return new ArrayList<>(taskQueue);
        } finally {
            queueLock.unlock();
        }
    }

    public void markInProcessing(String taskId) {
        queueLock.lock();
        try {
            RLTask matched = null;
            for (RLTask task : taskQueue) {
                if (Objects.equals(task.getTaskId(), taskId)) {
                    matched = task;
                    break;
                }
            }
            if (matched != null) {
                taskQueue.remove(matched);
                inProcessingTask.put(taskId, matched);
            }
        } finally {
            queueLock.unlock();
        }
    }
}
