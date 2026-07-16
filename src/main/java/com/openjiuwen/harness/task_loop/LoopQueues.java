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
 * @since 0.1.7
 */
public class LoopQueues implements SteeringQueue {
    private final Queue<String> steering = new ArrayDeque<>();

    /**
     * ArrayDeque<>.
     * 
     * @since 0.1.7
     */
    private final Queue<String> isFollowUp = new ArrayDeque<>();

    /**
     * PriorityQueue<>.
     * 
     * @since 0.1.7
     */
    private final PriorityQueue<DeepLoopEvent> events = new PriorityQueue<>();
    private long sequence;

    /**
     * pushSteer.
     * 
     * @param message message
     * @since 0.1.7
     */
    public void pushSteer(String message) {
        steering.add(message);
    }

    /**
     * pushSteering.
     * 
     * @param message message
     * @since 0.1.7
     */
    @Override
    public void pushSteering(String message) {
        pushSteer(message);
    }

    /**
     * pushFollowUp.
     * 
     * @param message message
     * @since 0.1.7
     */
    public void pushFollowUp(String message) {
        isFollowUp.add(message);
    }

    /**
     * drainSteering.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<String> drainSteering() {
        List<String> result = new ArrayList<>(steering);
        steering.clear();
        return result;
    }

    /**
     * hasFollowUp.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasFollowUp() {
        return !isFollowUp.isEmpty();
    }

    /**
     * drainFollowUp.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> drainFollowUp() {
        List<String> result = new ArrayList<>(isFollowUp);
        isFollowUp.clear();
        return result;
    }

    /**
     * pushEvent.
     * 
     * @param eventType eventType
     * @param content content
     * @return the result
     * @since 0.1.7
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
     * hasEvents.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasEvents() {
        return !events.isEmpty();
    }

    /**
     * drainEvents.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<DeepLoopEvent> drainEvents() {
        List<DeepLoopEvent> result = new ArrayList<>();
        while (!events.isEmpty()) {
            result.add(events.poll());
        }
        return result;
    }
}
