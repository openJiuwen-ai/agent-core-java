// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue;

import com.openjiuwen.core.runner.AsyncMessageHandler;
import com.openjiuwen.core.runner.SubscriptionBase;

import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * In-memory subscription for FakeMQ.
 * 
 * 对应Python: drunner/dmessage_queue/message_queue_fake.py - FakeSubscription
 */
public class FakeSubscription extends SubscriptionBase {

    private static final Logger LOG = Logger.getLogger(FakeSubscription.class.getName());

    private final String topic;
    private AsyncMessageHandler handler;
    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(10000);
    private volatile boolean active = false;
    private Future<?> task;
    private final ExecutorService executor;

    public FakeSubscription(String topic) {
        this.topic = topic;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FakeSubscription-" + this.topic);
            t.setDaemon(true);
            return t;
        });
    }

    public String getTopic() {
        return topic;
    }

    @Override
    public void setMessageHandler(AsyncMessageHandler handler) {
        this.handler = handler;
    }

    @Override
    public void activate() {
        if (!active) {
            active = true;
            task = executor.submit(this::consumeLoop);
        }
    }

    @Override
    public CompletableFuture<Void> deactivate() {
        active = false;
        if (task != null) {
            task.cancel(true);
            task = null;
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isActive() {
        return active;
    }

    /**
     * Push a raw message (serialized bytes) to this subscription's queue.
     */
    public CompletableFuture<Void> push(byte[] msg) {
        if (active) {
            queue.offer(msg);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Get the current queue size (for testing).
     */
    public int getQueueSize() {
        return queue.size();
    }

    private void consumeLoop() {
        try {
            while (active) {
                try {
                    byte[] raw = queue.poll(200, TimeUnit.MILLISECONDS);
                    if (raw == null) continue;
                    if (!active) break;

                    Object payload = MessageSerializer.deserializeMessage(raw);
                    if (handler != null) {
                        handler.apply(payload).join();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            if (active) {
                LOG.log(Level.SEVERE, "[FakeSubscription] consume_loop error", e);
            }
        }
    }

    /**
     * Shutdown the executor (for cleanup).
     */
    public void shutdown() {
        active = false;
        executor.shutdownNow();
    }
}

