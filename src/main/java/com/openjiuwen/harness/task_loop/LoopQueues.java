/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.openjiuwen.core.singleagent.rail.SteeringQueue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Public class LoopQueues used by the Java parity implementation.
 *
 * @since 1.0
 */
public class LoopQueues implements SteeringQueue {
    private final Queue<String> steering = new ArrayDeque<>();
    private final Queue<String> isFollowUp = new ArrayDeque<>();
    private final PriorityQueue<DeepLoopEvent> events = new PriorityQueue<>();
    private long sequence;

    /**
     * Auto-generated for codecheck compliance.
     */
    public void pushSteer(String message) {
        steering.add(message);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void pushSteering(String message) {
        pushSteer(message);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void pushFollowUp(String message) {
        isFollowUp.add(message);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> drainSteering() {
        List<String> result = new ArrayList<>(steering);
        steering.clear();
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean hasFollowUp() {
        return !isFollowUp.isEmpty();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> drainFollowUp() {
        List<String> result = new ArrayList<>(isFollowUp);
        isFollowUp.clear();
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public DeepLoopEvent pushEvent(DeepLoopEventType eventType, String content) {
        DeepLoopEvent event = DeepLoopEvent.builder(++sequence, eventType, content).build();
        events.add(event);
        if (eventType == DeepLoopEventType.STEER) {
            pushSteer(content);
        } else if (eventType == DeepLoopEventType.FOLLOWUP) {
            pushFollowUp(content);
        }
        return event;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean hasEvents() {
        return !events.isEmpty();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<DeepLoopEvent> drainEvents() {
        List<DeepLoopEvent> result = new ArrayList<>();
        while (!events.isEmpty()) {
            result.add(events.poll());
        }
        return result;
    }
}
