// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue.dsubscription;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessagequeue.ResultType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Responsible for collecting responses for specified requests, and supports cancellation and timeout.
 * 
 * <p>Each collector maintains a bounded blocking queue that receives {@link DmqResponseMessage} or 
 * {@link CancelEvent}. It supports single-result retrieval via {@link #result(Double)} and
 * streaming retrieval via {@link #stream(Double)}.
 * 
 * <p>A TTL timer automatically expires the collector after the configured timeout.
 * 
 * 对应Python: drunner/dmessage_queue/dsubscription/response_collector.py - ResponseCollector
 */
public class ResponseCollector {

    private static final Logger logger = LoggerFactory.getLogger(ResponseCollector.class);

    /** Max queue size per collector */
    public static final int MAX_QUEUE_SIZE = 10000;

    /** Shared scheduler for TTL expiration tasks */
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "ResponseCollector-TTL");
        t.setDaemon(true);
        return t;
    });

    private final String messageId;
    private final String receiverId;
    private final String requestId;
    private final double ttl;
    private final LinkedBlockingQueue<Object> queue;

    private volatile boolean cancelled = false;
    private volatile boolean expired = false;

    private final ScheduledFuture<?> expireTask;

    /**
     * Creates a new ResponseCollector.
     *
     * @param messageId  the message ID this collector is tracking
     * @param receiverId the receiver (remote) ID
     * @param requestId  optional request ID (can be null)
     * @param ttl        time-to-live in seconds (null defaults to 30.0)
     */
    public ResponseCollector(String messageId, String receiverId, String requestId, Double ttl) {
        this.messageId = messageId;
        this.receiverId = receiverId;
        this.requestId = requestId;
        this.ttl = ttl != null ? ttl : 30.0;
        this.queue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);

        // Start TTL expiration task
        this.expireTask = SCHEDULER.schedule(
            this::expireAfterTtl,
            (long) (this.ttl * 1000),
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * @return true if the collector has been cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * @return true if the collector has expired due to TTL
     */
    public boolean isExpired() {
        return expired;
    }

    /**
     * @return true if the collector is still active (not cancelled and not expired)
     */
    public boolean isActive() {
        return !(cancelled || expired);
    }

    /**
     * Automatically mark as expired when TTL expires.
     */
    private void expireAfterTtl() {
        if (!cancelled) {
            expired = true;
            cleanupQueue();
            logger.warn("[Collector:{}] expired after {:.1f}s", messageId, ttl);
            wakeWaiters(new CancelEvent(CancelReason.TTL_EXPIRE));
        }
    }

    /**
     * Receive message from replyTopic.
     *
     * @param msg the response message to enqueue
     */
    public void putMessage(DmqResponseMessage msg) {
        if (!isActive()) {
            logger.warn("[Collector:{}] inactive, discard message", messageId);
            return;
        }

        if (queue.remainingCapacity() == 0) {
            logger.warn("[Collector:{}] queue full({}), auto-cancelled", messageId, MAX_QUEUE_SIZE);
            close(CancelReason.QUEUE_FULL);
            return;
        }

        queue.offer(msg);
    }

    /**
     * Gets a single result. Blocks until a message arrives or timeout.
     *
     * @param timeout timeout in seconds (null defaults to TTL)
     * @return the payload of the response message
     * @throws TimeoutException     if timeout occurs or collector expired
     * @throws CancellationException if collector was cancelled
     * @throws JiuWenBaseException  for RUNNER_STOPPED or REMOTE_AGENT_PROCESS_ERROR
     */
    public Object result(Double timeout) throws TimeoutException {
        double effectiveTimeout = timeout != null ? timeout : ttl;

        if (cancelled) {
            throw new CancellationException("Collector(" + messageId + ") was cancelled before request send");
        }
        if (expired) {
            throw new TimeoutException("Collector(" + messageId + ") expired");
        }

        try {
            Object msg = queue.poll((long) (effectiveTimeout * 1000), TimeUnit.MILLISECONDS);
            if (msg == null) {
                expired = true;
                cleanupQueue();
                logger.warn("[Collector:{}] result timeout ({}s)", messageId, effectiveTimeout);
                throw new TimeoutException("Collector(" + messageId + ") timeout waiting for result");
            }
            checkMessage(msg);
            return ((DmqResponseMessage) msg).getPayload();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Collector(" + messageId + ") interrupted");
        } finally {
            close(CancelReason.FINISH);
        }
    }

    /**
     * Stream results. Collects all chunk payloads until last_chunk=true.
     * Blocks until all chunks are received or timeout/cancellation occurs.
     *
     * @param timeout timeout in seconds per chunk (null defaults to TTL)
     * @return list of payload objects from streaming chunks
     * @throws TimeoutException     if timeout occurs waiting for a chunk
     * @throws CancellationException if collector was cancelled
     * @throws JiuWenBaseException  for RUNNER_STOPPED or REMOTE_AGENT_PROCESS_ERROR
     */
    public List<Object> stream(Double timeout) throws TimeoutException {
        double effectiveTimeout = timeout != null ? timeout : ttl;
        List<Object> results = new ArrayList<>();
        try {
            while (true) {
                Object msg = queue.poll((long) (effectiveTimeout * 1000), TimeUnit.MILLISECONDS);
                if (msg == null) {
                    expired = true;
                    logger.warn("[Collector:{}] stream timeout ({}s)", messageId, effectiveTimeout);
                    throw new TimeoutException("Collector(" + messageId + ") stream timeout");
                }
                logger.debug("[Collector:{}] stream get message {}", messageId, msg);
                checkMessage(msg);
                if (msg instanceof DmqResponseMessage respMsg) {
                    if (respMsg.isLastChunk()) {
                        // Last message is MQ empty marker, do not return
                        break;
                    }
                    results.add(respMsg.getPayload());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Collector(" + messageId + ") interrupted");
        } finally {
            close(CancelReason.FINISH);
        }
        return results;
    }

    /**
     * Check message for cancel events or error responses.
     *
     * @param msg the message to check (DmqResponseMessage or CancelEvent)
     * @throws TimeoutException     if message is a TTL_EXPIRE CancelEvent
     * @throws CancellationException if message is a QUEUE_FULL CancelEvent
     * @throws JiuWenBaseException  if message is a RUNNER_STOPPED CancelEvent or ERROR response
     */
    public void checkMessage(Object msg) throws TimeoutException {
        if (msg instanceof CancelEvent cancelEvent) {
            logger.info("[Collector:{}] recv CancelEvent, stream cancelled by {}", messageId, cancelEvent.reason());
            switch (cancelEvent.reason()) {
                case TTL_EXPIRE:
                    throw new TimeoutException("Collector(" + messageId + ") timeout");
                case QUEUE_FULL:
                    throw new CancellationException("Collector(" + messageId + ") queue full");
                default:
                    throw new JiuWenBaseException(
                        StatusCode.RUNNER_STOPPED.getCode(),
                        StatusCode.RUNNER_STOPPED.getMessage());
            }
        }
        if (msg instanceof DmqResponseMessage respMsg) {
            if (respMsg.getResultType() == ResultType.ERROR) {
                String errorDetail = "error_code=" + respMsg.getErrorCode()
                    + ", error_msg=" + respMsg.getErrorMsg();
                throw new JiuWenBaseException(
                    StatusCode.REMOTE_AGENT_PROCESS_ERROR.getCode(),
                    StatusCode.REMOTE_AGENT_PROCESS_ERROR.getMessage()
                        .replace("{error_msg}", errorDetail));
            }
        }
    }

    /**
     * Active cancellation (including queue full, system shutdown).
     *
     * @param reason the cancellation reason
     */
    public void close(CancelReason reason) {
        if (cancelled) {
            return;
        }

        cancelled = true;
        if (expireTask != null && !expireTask.isDone()) {
            expireTask.cancel(false);
        }

        cleanupQueue();
        if (reason != CancelReason.FINISH) {
            wakeWaiters(new CancelEvent(reason));
            logger.info("[Collector:{}] cancelled by close({})", messageId, reason);
        }
        logger.info("[Collector:{}] cancelled by finished", messageId);
    }

    /**
     * Close with default reason RUNNER_STOPPED.
     */
    public void close() {
        close(CancelReason.RUNNER_STOPPED);
    }

    /**
     * Clear the queue.
     */
    private void cleanupQueue() {
        queue.clear();
    }

    /**
     * Put a cancel signal into the queue to wake up blocked result/stream callers.
     */
    private void wakeWaiters(CancelEvent cancelEvent) {
        queue.offer(cancelEvent);
    }

    /**
     * Get the internal queue (for testing purposes).
     */
    public LinkedBlockingQueue<Object> getQueue() {
        return queue;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public String getRequestId() {
        return requestId;
    }

    public double getTtl() {
        return ttl;
    }
}

