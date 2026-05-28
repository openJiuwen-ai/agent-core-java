/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Dual-queue buffer for steer/follow_up messages.
 *
 * <p>Bridges EventHandler -> Executor/Loop by providing two
 * thread-safe queues:
 * - steering: drained by the executor before each inner invoke.
 * - follow_up: drained by outer task loop after each iteration completes.
 *
 * <p>Mirrors Python's {@code LoopQueues} in
 * {@code openjiuwen.harness.task_loop.loop_queues}.
 */
@Data
public class LoopQueues {

    private static final Logger LOG = LoggerFactory.getLogger(LoopQueues.class);

    /** Queue for steer messages, drained by executor before each invoke. */
    private final Queue<String> steering = new ConcurrentLinkedQueue<>();

    /** Queue for follow-up messages, drained by outer task loop. */
    private final Queue<String> followUp = new ConcurrentLinkedQueue<>();

    /**
     * Push a steering message.
     */
    public void pushSteer(String msg) {
        if (msg != null) {
            steering.offer(msg);
            LOG.debug("[LoopQueues] push_steer msg={}", msg);
        }
    }

    /**
     * Push a follow-up message.
     */
    public void pushFollowUp(String msg) {
        if (msg != null) {
            followUp.offer(msg);
            LOG.debug("[LoopQueues] push_follow_up msg={}", msg);
        }
    }

    /**
     * Return whether follow-up messages are pending.
     */
    public boolean hasFollowUp() {
        return !followUp.isEmpty();
    }

    /**
     * Return whether steering messages are pending.
     */
    public boolean hasSteering() {
        return !steering.isEmpty();
    }

    /**
     * Drain all pending steering messages.
     */
    public List<String> drainSteering() {
        List<String> msgs = new ArrayList<>();
        String msg;
        while ((msg = steering.poll()) != null) {
            msgs.add(msg);
        }
        LOG.debug("[LoopQueues] drain_steering count={}", msgs.size());
        return msgs;
    }

    /**
     * Drain all pending follow-up messages.
     */
    public List<String> drainFollowUp() {
        List<String> msgs = new ArrayList<>();
        String msg;
        while ((msg = followUp.poll()) != null) {
            msgs.add(msg);
        }
        LOG.debug("[LoopQueues] drain_follow_up count={}", msgs.size());
        return msgs;
    }

    /**
     * Clear both queues.
     */
    public void clear() {
        steering.clear();
        followUp.clear();
    }

    /**
     * Get pending steering message count.
     */
    public int steeringSize() {
        return steering.size();
    }

    /**
     * Get pending follow-up message count.
     */
    public int followUpSize() {
        return followUp.size();
    }
}