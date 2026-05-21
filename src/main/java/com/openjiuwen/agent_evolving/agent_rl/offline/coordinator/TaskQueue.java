/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lightweight asynchronous queue managing rollout tasks,
 * tracking in-processing tasks, and buffering completed rollout messages.
 * <p>
 * Mirrors Python's {@code TaskQueue} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.coordinator.task_queue}.
 */
public class TaskQueue {

    private final List<RLTask> taskQueue = new ArrayList<>();
    private final Map<String, RLTask> inProcessingTask = new ConcurrentHashMap<>();
    private final Map<String, RolloutMessage> rollouts = new ConcurrentHashMap<>();
    private final ReentrantLock queueLock = new ReentrantLock();
    private final ReentrantLock rolloutLock = new ReentrantLock();

    /**
     * Initialize an empty task queue and rollout buffer.
     */
    public TaskQueue() {
    }

    /**
     * Enqueue a new task and return its task identifier.
     * 
     * @param task Task to queue
     * @return Task identifier
     */
    public String queueTask(RLTask task) {
        queueLock.lock();
        try {
            taskQueue.add(task);
            return task.getTaskId();
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Retrieve the next task for processing.
     * 
     * @return Next task or null if queue is empty
     */
    public RLTask getTask() {
        queueLock.lock();
        try {
            if (taskQueue.isEmpty()) {
                return null;
            }
            RLTask task = taskQueue.remove(0);
            inProcessingTask.put(task.getTaskId(), task);
            return task;
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Remove a task from the in-processing pool directly.
     * 
     * @param task Task to delete
     */
    public void deleteTask(RLTask task) {
        queueLock.lock();
        try {
            if (task != null && task.getTaskId() != null) {
                inProcessingTask.remove(task.getTaskId());
            }
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Store a completed rollout and clear its in-processing entry.
     * 
     * @param rollout Completed rollout message
     * @return Rollout identifier
     */
    public String addRollout(RolloutMessage rollout) {
        if (rollout == null) {
            return null;
        }
        
        String rolloutId = rollout.getRolloutId();
        String taskId = rollout.getTaskId();
        
        queueLock.lock();
        try {
            if (taskId != null && inProcessingTask.containsKey(taskId)) {
                inProcessingTask.remove(taskId);
            }
        } finally {
            queueLock.unlock();
        }

        rolloutLock.lock();
        try {
            rollouts.put(rolloutId, rollout);
            return rolloutId;
        } finally {
            rolloutLock.unlock();
        }
    }

    /**
     * Get all completed rollouts.
     * 
     * @return Map of rollout_id to rollout
     */
    public Map<String, RolloutMessage> getRollouts() {
        return new HashMap<>(rollouts);
    }

    /**
     * Get a specific rollout by ID.
     * 
     * @param rolloutId Rollout identifier
     * @return Rollout message or null
     */
    public RolloutMessage getRollout(String rolloutId) {
        return rollouts.get(rolloutId);
    }

    /**
     * Clear all rollouts.
     */
    public void clearRollouts() {
        rolloutLock.lock();
        try {
            rollouts.clear();
        } finally {
            rolloutLock.unlock();
        }
    }

    /**
     * Check if task queue is empty.
     * 
     * @return true if empty
     */
    public boolean isQueueEmpty() {
        return taskQueue.isEmpty();
    }

    /**
     * Get number of pending tasks in queue.
     * 
     * @return Count of pending tasks
     */
    public int getQueueSize() {
        return taskQueue.size();
    }

    /**
     * Get number of in-processing tasks.
     * 
     * @return Count of in-processing tasks
     */
    public int getInProcessingCount() {
        return inProcessingTask.size();
    }

    /**
     * Get number of completed rollouts.
     * 
     * @return Count of rollouts
     */
    public int getRolloutCount() {
        return rollouts.size();
    }

    /**
     * Get in-processing task by ID.
     * 
     * @param taskId Task identifier
     * @return Task or null
     */
    public RLTask getInProcessingTask(String taskId) {
        return inProcessingTask.get(taskId);
    }
}