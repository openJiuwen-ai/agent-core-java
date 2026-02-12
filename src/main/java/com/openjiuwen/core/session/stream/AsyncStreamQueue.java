/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async stream queue for streaming data between components.
 * 
 * <p>Provides async send/receive operations with retry and timeout support.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class AsyncStreamQueue {
    
    private static final LoggerProtocol logger = LogManager.getLogger("session");
    
    /**
     * Default timeout for each send attempt in seconds.
     */
    public static final double DEFAULT_SEND_ATTEMPT_TIMEOUT = 0.2;
    
    /**
     * Maximum number of retries for sending data.
     */
    public static final int DEFAULT_MAX_SEND_RETRIES = 5;
    
    /**
     * Default timeout for receiving data in seconds, -1 means no timeout.
     */
    public static final double DEFAULT_RECEIVE_TIMEOUT = -1;
    
    /**
     * Default timeout for closing the queue in seconds.
     */
    public static final double DEFAULT_CLOSE_TIMEOUT = 5.0;
    
    private final BlockingQueue<Object> streamQueue;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger unfinishedTasks = new AtomicInteger(0);
    
    /**
     * Creates a new AsyncStreamQueue with the given maximum size.
     * 
     * @param maxsize the maximum size, or 0 for unbounded
     * @throws TypeError if maxsize is not an integer
     * @throws IllegalArgumentException if maxsize is negative
     */
    public AsyncStreamQueue(int maxsize) {
        if (maxsize < 0) {
            throw new IllegalArgumentException("maxsize must be >= 0");
        }
        
        if (maxsize == 0) {
            this.streamQueue = new LinkedBlockingQueue<>();
        } else {
            this.streamQueue = new ArrayBlockingQueue<>(maxsize);
        }
    }
    
    /**
     * Creates a new unbounded AsyncStreamQueue.
     */
    public AsyncStreamQueue() {
        this(0);
    }
    
    /**
     * Checks if the queue is closed.
     * 
     * @return true if closed
     */
    public boolean isClosed() {
        return closed.get();
    }
    
    /**
     * Sends data to the queue.
     * 
     * @param data the data to send
     * @return a CompletableFuture that completes when the data is sent
     */
    public CompletableFuture<Void> send(Object data) {
        return send(data, DEFAULT_SEND_ATTEMPT_TIMEOUT, DEFAULT_MAX_SEND_RETRIES);
    }
    
    /**
     * Sends data to the queue with retry support.
     * 
     * @param data the data to send
     * @param attemptTimeout timeout for each attempt in seconds
     * @param maxRetries maximum number of retries
     * @return a CompletableFuture that completes when the data is sent
     */
    public CompletableFuture<Void> send(Object data, double attemptTimeout, int maxRetries) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                new RuntimeException("StreamQueue is already closed"));
        }
        
        return CompletableFuture.runAsync(() -> {
            for (int attempt = 0; attempt < maxRetries; attempt++) {
                try {
                    boolean success = streamQueue.offer(data, 
                        (long)(attemptTimeout * 1000), TimeUnit.MILLISECONDS);
                    if (success) {
                        unfinishedTasks.incrementAndGet();
                        logger.debug("Sending stream data success, timeout: {}, attempt: {}", 
                            attemptTimeout, attempt + 1);
                        return;
                    }
                    logger.error("Sending stream data timeout error, timeout: {}, attempt: {}",
                        attemptTimeout, attempt + 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while sending", e);
                }
            }
            logger.error("Failed to send stream data after {} attempts, timeout: {}",
                maxRetries, attemptTimeout);
        });
    }
    
    /**
     * Receives data from the queue.
     * 
     * @return a CompletableFuture containing the received data
     */
    public CompletableFuture<Object> receive() {
        return receive(DEFAULT_RECEIVE_TIMEOUT);
    }
    
    /**
     * Receives data from the queue with timeout.
     * 
     * @param timeout timeout in seconds, -1 for no timeout
     * @return a CompletableFuture containing the received data
     */
    public CompletableFuture<Object> receive(double timeout) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                new RuntimeException("StreamQueue is already closed"));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Object data;
                if (timeout < 0) {
                    data = streamQueue.take();
                } else {
                    data = streamQueue.poll((long)(timeout * 1000), TimeUnit.MILLISECONDS);
                    if (data == null) {
                        throw new RuntimeException(new TimeoutException("Receive timeout"));
                    }
                }
                unfinishedTasks.decrementAndGet();
                logger.debug("Receiving stream data success, stream frame: {}", data);
                return data;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while receiving", e);
            }
        });
    }
    
    /**
     * Closes the queue.
     * 
     * @return a CompletableFuture that completes when the queue is closed
     */
    public CompletableFuture<Void> close() {
        return close(DEFAULT_CLOSE_TIMEOUT);
    }
    
    /**
     * Closes the queue with timeout.
     * 
     * @param timeout timeout in seconds
     * @return a CompletableFuture that completes when the queue is closed
     */
    public CompletableFuture<Void> close(double timeout) {
        if (closed.getAndSet(true)) {
            logger.debug("StreamQueue is already closed");
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            long timeoutMs = (long)(timeout * 1000);
            
            while (unfinishedTasks.get() > 0) {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    logger.error("Closing StreamQueue timeout error, timeout: {}, force clear stream queue.",
                        timeout);
                    forceClear();
                    return;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            logger.info("StreamQueue closed successfully, timeout: {}", timeout);
        });
    }
    
    /**
     * Force clears the queue.
     */
    private void forceClear() {
        int clearedItems = 0;
        while (!streamQueue.isEmpty()) {
            if (streamQueue.poll() != null) {
                clearedItems++;
                unfinishedTasks.decrementAndGet();
            }
        }
        logger.info("Force cleared {} items from StreamQueue.", clearedItems);
    }
}

