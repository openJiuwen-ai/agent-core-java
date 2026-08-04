/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class LoopQueuesTest {
    @Test
    void concurrentSteeringProducersAndConsumerShouldNotLoseMessages() throws Exception {
        int producerCount = 4;
        int messagesPerProducer = 100;
        LoopQueues queues = new LoopQueues();
        ExecutorService executor = Executors.newFixedThreadPool(producerCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> producers = new ArrayList<>();

        try {
            for (int producer = 0; producer < producerCount; producer++) {
                int producerId = producer;
                producers.add(executor.submit(() -> {
                    start.await();
                    for (int index = 0; index < messagesPerProducer; index++) {
                        queues.pushSteering(producerId + "-" + index);
                    }
                    return null;
                }));
            }

            start.countDown();
            List<String> drained = new ArrayList<>();
            while (producers.stream().anyMatch(producer -> !producer.isDone())) {
                drained.addAll(queues.drainSteering());
            }
            for (Future<?> producer : producers) {
                producer.get();
            }
            drained.addAll(queues.drainSteering());

            assertThat(drained).hasSize(producerCount * messagesPerProducer);
            assertThat(new HashSet<>(drained)).hasSize(producerCount * messagesPerProducer);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentEventProducersShouldReceiveUniqueOrderedSequences() throws Exception {
        int eventCount = 100;
        LoopQueues queues = new LoopQueues();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<?>> producers = new ArrayList<>();

        try {
            for (int index = 0; index < eventCount; index++) {
                int eventId = index;
                producers.add(executor.submit(() -> queues.pushEvent(DeepLoopEventType.STEER,
                        "event-" + eventId)));
            }
            for (Future<?> producer : producers) {
                producer.get();
            }

            List<DeepLoopEvent> events = queues.drainEvents();
            Set<Long> sequences = new HashSet<>();
            events.forEach(event -> sequences.add(event.getSequence()));
            assertThat(events).hasSize(eventCount);
            assertThat(sequences).hasSize(eventCount);
            assertThat(events).isSorted();
        } finally {
            executor.shutdownNow();
        }
    }
}