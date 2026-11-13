/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * Mirrors Python's {@code LoopQueues} in
 * {@code openjiuwen/harness/task_loop/loop_queues.py}.
 */
public final class LoopQueues {

    private final Queue<String> steering;
    private final Queue<String> followUp;
    private final Queue<Object> input;
    private final Queue<Object> output;

    /**
     * Per-session steering buckets. A message steered for a named session lands in
     * that session's bucket and stays invisible to other sessions, so one session's
     * steering can neither extend another session's outer loop nor leak into its
     * model context. The shared {@link #steering} queue keeps broadcast semantics
     * for session-less steer calls.
     */
    private final ConcurrentMap<String, Queue<String>> sessionSteering = new ConcurrentHashMap<>();

    public LoopQueues() {
        this.steering = new ConcurrentLinkedQueue<>();
        this.followUp = new ConcurrentLinkedQueue<>();
        this.input = new ConcurrentLinkedQueue<>();
        this.output = new ConcurrentLinkedQueue<>();
    }

    public Queue<String> steering() {
        return steering;
    }

    public Queue<String> followUp() {
        return followUp;
    }

    public Queue<Object> input() {
        return input;
    }

    public Queue<Object> output() {
        return output;
    }

    public void pushSteer(String msg) {
        steering.add(msg);
    }

    /**
     * Push a steering message for a specific session.
     *
     * <p>Named sessions get their own bucket; a null/blank/default session id falls
     * back to the shared broadcast queue, keeping the legacy session-less steer
     * semantics (every draining session sees broadcast messages).</p>
     *
     * @param sessionId the session id the message belongs to
     * @param msg       the steering message
     * @since 0.1.17
     */
    public void pushSteer(String sessionId, String msg) {
        if (msg == null) {
            return;
        }
        if (isBroadcastSession(sessionId)) {
            steering.add(msg);
            return;
        }
        sessionSteering.computeIfAbsent(sessionId, key -> new ConcurrentLinkedQueue<>()).add(msg);
    }

    public void pushFollowUp(String msg) {
        followUp.add(msg);
    }

    public boolean hasFollowUp() {
        return !followUp.isEmpty();
    }

    /**
     * Reports whether the steering queue holds any message.
     *
     * @return true if at least one steering message is queued
     * @since 0.1.17
     */
    public boolean hasSteering() {
        return !steering.isEmpty() || sessionSteering.values().stream().anyMatch(q -> !q.isEmpty());
    }

    /**
     * Reports whether the given session has any steering message visible to it:
     * its own bucket plus broadcast messages.
     *
     * @param sessionId the session id
     * @return true if the session can drain at least one steering message
     * @since 0.1.17
     */
    public boolean hasSteering(String sessionId) {
        if (isBroadcastSession(sessionId)) {
            return !steering.isEmpty();
        }
        Queue<String> bucket = sessionSteering.get(sessionId);
        return !steering.isEmpty() || (bucket != null && !bucket.isEmpty());
    }

    /**
     * Returns a live steering queue view scoped to the given session.
     *
     * <p>Polling drains the session bucket first, then broadcast messages; offering
     * lands in the session bucket. A null/blank/default session id returns the raw
     * broadcast queue. The view keeps the {@code Queue<String>} binding contract, so
     * consumers such as {@code AgentCallbackContext.bindSteeringQueue} need no change
     * while other sessions' named messages stay invisible.</p>
     *
     * @param sessionId the session id the view is scoped to
     * @return the session-filtered steering queue view
     * @since 0.1.17
     */
    public Queue<String> steeringView(String sessionId) {
        if (isBroadcastSession(sessionId)) {
            return steering;
        }
        return new SessionSteeringView(sessionId);
    }

    public List<String> drainSteering() {
        List<String> messages = new ArrayList<>();
        drainInto(steering, messages);
        for (Queue<String> bucket : sessionSteering.values()) {
            drainInto(bucket, messages);
        }
        return messages;
    }

    /**
     * Drain steering messages visible to the given session: its own bucket first,
     * then broadcast messages.
     *
     * @param sessionId the session id
     * @return list of steering messages visible to the session
     * @since 0.1.17
     */
    public List<String> drainSteering(String sessionId) {
        if (isBroadcastSession(sessionId)) {
            return drainSteering();
        }
        List<String> messages = new ArrayList<>();
        Queue<String> bucket = sessionSteering.get(sessionId);
        if (bucket != null) {
            drainInto(bucket, messages);
        }
        drainInto(steering, messages);
        return messages;
    }

    public List<String> drainFollowUp() {
        return drain(followUp);
    }

    private static List<String> drain(Queue<String> queue) {
        List<String> messages = new ArrayList<>();
        drainInto(queue, messages);
        return messages;
    }

    private static void drainInto(Queue<String> queue, List<String> messages) {
        String next;
        while ((next = queue.poll()) != null) {
            messages.add(next);
        }
    }

    private static boolean isBroadcastSession(String sessionId) {
        return sessionId == null || sessionId.isBlank()
                || TaskLoopController.DEFAULT_SESSION_ID.equals(sessionId);
    }

    /**
     * Session-scoped steering view: the queue contract over "own bucket + broadcast".
     * Poll prefers the session bucket so named messages keep FIFO against broadcast
     * ones; offer always lands in the session bucket.
     */
    private final class SessionSteeringView extends AbstractQueue<String> {
        private final String sessionId;

        SessionSteeringView(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public boolean offer(String message) {
            return message != null && bucket().offer(message);
        }

        @Override
        public String poll() {
            String message = bucket().poll();
            return message != null ? message : steering.poll();
        }

        @Override
        public String peek() {
            String message = bucket().peek();
            return message != null ? message : steering.peek();
        }

        @Override
        public int size() {
            return bucket().size() + steering.size();
        }

        @Override
        public Iterator<String> iterator() {
            List<String> snapshot = new ArrayList<>(bucket());
            snapshot.addAll(steering);
            return snapshot.iterator();
        }

        private Queue<String> bucket() {
            return sessionSteering.computeIfAbsent(sessionId, key -> new ConcurrentLinkedQueue<>());
        }
    }
}
