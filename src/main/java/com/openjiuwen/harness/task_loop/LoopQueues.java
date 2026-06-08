/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Mirrors Python's {@code LoopQueues} in
 * {@code openjiuwen/harness/task_loop/loop_queues.py}.
 */
public final class LoopQueues {

    private final Queue<String> steering;
    private final Queue<String> followUp;

    public LoopQueues() {
        this.steering = new ConcurrentLinkedQueue<>();
        this.followUp = new ConcurrentLinkedQueue<>();
    }

    public Queue<String> steering() {
        return steering;
    }

    public Queue<String> followUp() {
        return followUp;
    }

    public void pushSteer(String msg) {
        steering.add(msg);
    }

    public void pushFollowUp(String msg) {
        followUp.add(msg);
    }

    public boolean hasFollowUp() {
        return !followUp.isEmpty();
    }

    public List<String> drainSteering() {
        return drain(steering);
    }

    public List<String> drainFollowUp() {
        return drain(followUp);
    }

    private static List<String> drain(Queue<String> queue) {
        List<String> messages = new ArrayList<>();
        String next;
        while ((next = queue.poll()) != null) {
            messages.add(next);
        }
        return messages;
    }
}
