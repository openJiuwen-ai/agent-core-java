/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.runner.mq.AsyncMessageHandler;
import com.openjiuwen.core.runner.mq.SubscriptionBase;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory subscription for fake distributed MQ.
 *
 * <p>Mirrors Python's {@code FakeSubscription} in
 * {@code openjiuwen/core/runner/drunner/dmessage_queue/message_queue_fake.py}.</p>
 */
public class FakeSubscription extends SubscriptionBase {

    private static final int QUEUE_CAPACITY = 10_000;
    private static final LoggerProtocol LOGGER = Loggers.RUNNER;

    private final String topic;
    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean active = new AtomicBoolean(false);
    private volatile AsyncMessageHandler<Object, Object> handler;
    private volatile ExecutorService executor;

    public FakeSubscription(String topic) {
        this.topic = topic;
    }

    @Override
    public void setMessageHandler(AsyncMessageHandler<Object, Object> handler) {
        this.handler = handler;
    }

    @Override
    public void activate() {
        if (!active.compareAndSet(false, true)) {
            return;
        }
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fake-mq-consume-" + topic);
            thread.setDaemon(true);
            return thread;
        });
        executor.submit(this::consumeLoop);
    }

    @Override
    public void deactivate() {
        active.set(false);
        ExecutorService current = executor;
        executor = null;
        if (current != null) {
            current.shutdownNow();
        }
    }

    @Override
    public boolean isActive() {
        return active.get();
    }

    public String getTopic() {
        return topic;
    }

    public CompletableFuture<Void> push(byte[] message) {
        if (active.get()) {
            queue.offer(message);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void consumeLoop() {
        try {
            while (active.get()) {
                byte[] raw = queue.poll(200L, TimeUnit.MILLISECONDS);
                if (raw == null || !active.get()) {
                    continue;
                }
                Object payload = MessageSerializer.deserializeMessage(raw);
                AsyncMessageHandler<Object, Object> currentHandler = handler;
                if (currentHandler != null) {
                    currentHandler.handle(payload).join();
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.exception("[FakeSubscription] consume_loop error: " + e.getMessage(), e);
        }
    }
}
