/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.openjiuwen.core.singleagent.rail.SteeringQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Public class LoopQueues used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class LoopQueues implements SteeringQueue {
    private final ConcurrentLinkedQueue<String> steering = new ConcurrentLinkedQueue<>();

    /**
     * ConcurrentLinkedQueue<>.
     * 
     * @since 0.1.7
     */
    private final ConcurrentLinkedQueue<String> isFollowUp = new ConcurrentLinkedQueue<>();

    /**
     * PriorityBlockingQueue<>.
     * 
     * @since 0.1.7
     */
    private final PriorityBlockingQueue<DeepLoopEvent> events = new PriorityBlockingQueue<>();
    private final AtomicLong sequence = new AtomicLong();

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
        List<String> result = new ArrayList<>();
        String message;
        while ((message = steering.poll()) != null) {
            result.add(message);
        }
        return result;
    }

    /**
     * Whether at least one steering instruction is pending without consuming it.
     *
     * @return the result
     * @since 0.1.15
     */
    @Override
    public boolean hasPending() {
        return !steering.isEmpty();
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
        List<String> result = new ArrayList<>();
        String message;
        while ((message = isFollowUp.poll()) != null) {
            result.add(message);
        }
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
        DeepLoopEvent event = DeepLoopEvent.builder(sequence.incrementAndGet(), eventType, content).build();
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
        DeepLoopEvent event;
        while ((event = events.poll()) != null) {
            result.add(event);
        }
        return result;
    }
}
