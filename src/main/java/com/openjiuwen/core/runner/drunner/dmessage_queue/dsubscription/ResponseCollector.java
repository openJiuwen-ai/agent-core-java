/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription;

import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.ResultType;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Collects responses for one distributed request.
 */
public class ResponseCollector {

    private static final int MAX_QUEUE_SIZE = 10_000;

    private final String messageId;
    private final String receiverId;
    private final String requestId;
    private final double ttlSeconds;
    private final BlockingQueue<DmqResponseMessage> queue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);

    private volatile boolean closed;

    public ResponseCollector(String messageId, String receiverId, String requestId, Double ttlSeconds) {
        this.messageId = messageId;
        this.receiverId = receiverId;
        this.requestId = requestId;
        this.ttlSeconds = ttlSeconds != null ? ttlSeconds : 30.0;
    }

    public void putMessage(DmqResponseMessage message) {
        if (!closed) {
            queue.offer(message);
        }
    }

    public Object result(Double timeoutSeconds) throws Exception {
        try {
            DmqResponseMessage message = poll(timeoutSeconds);
            ensureMessage(message);
            return message.getBody();
        } finally {
            close();
        }
    }

    public Iterator<Object> stream(Double timeoutSeconds) {
        return new Iterator<>() {
            private Object next;
            private boolean done;

            @Override
            public boolean hasNext() {
                if (done) {
                    return false;
                }
                if (next != null) {
                    return true;
                }
                try {
                    DmqResponseMessage message = poll(timeoutSeconds);
                    ensureMessage(message);
                    if (message.isLastChunk()) {
                        done = true;
                        close();
                        return false;
                    }
                    next = message.getBody();
                    return true;
                } catch (Exception e) {
                    close();
                    throw new RuntimeException("Failed to read distributed stream response", e);
                }
            }

            @Override
            public Object next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Object current = next;
                next = null;
                return current;
            }
        };
    }

    public void close() {
        closed = true;
        queue.clear();
    }

    private DmqResponseMessage poll(Double timeoutSeconds) throws Exception {
        double effectiveTimeout = timeoutSeconds != null ? timeoutSeconds : ttlSeconds;
        DmqResponseMessage message = queue.poll((long) (effectiveTimeout * 1000), TimeUnit.MILLISECONDS);
        if (message == null) {
            throw new java.util.concurrent.TimeoutException(
                    "Collector(" + messageId + ") timed out waiting for remote response from " + receiverId);
        }
        return message;
    }

    private static void ensureMessage(DmqResponseMessage message) {
        if (message.getResultType() == ResultType.ERROR) {
            throw new IllegalStateException("Remote error " + message.getErrorCode() + ": " + message.getErrorMsg());
        }
    }
}
