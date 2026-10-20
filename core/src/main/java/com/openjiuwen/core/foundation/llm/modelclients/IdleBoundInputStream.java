/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Response body stream whose blocking reads are bounded by the idle (peer silence)
 * window. Bytes delivered by the HTTP client are queued; each {@code read} waits at most
 * {@code idleSeconds} for the next chunk and raises {@link IdleTimeoutIOException} when the
 * peer stays silent beyond the window, so dead connections are detected inside the window
 * even though JDK {@code ofInputStream} reads cannot be interrupted by {@code close()}.
 *
 * <p>The subscriber requests {@code Long.MAX_VALUE} and the queue is unbounded, trading the
 * demand-driven backpressure of {@code ofInputStream} for simplicity; the memory ceiling is
 * the whole response body, which is bounded by model output limits in LLM scenarios.</p>
 *
 * @since 2026-10-09
 */
final class IdleBoundInputStream extends InputStream {
    private static final Object END_OF_STREAM = new Object();

    private final BlockingQueue<Object> chunks = new LinkedBlockingQueue<>();
    private final long idleNanos;
    private final double idleSeconds;

    private volatile Flow.Subscription subscription;
    private volatile boolean isClosed;
    private byte[] current;
    private int offset;

    private IdleBoundInputStream(double idleSeconds) {
        this.idleSeconds = idleSeconds;
        this.idleNanos = BigDecimal.valueOf(idleSeconds)
                .multiply(BigDecimal.valueOf(1_000_000_000L)).longValue();
    }

    /**
     * Builds a body handler equivalent to {@code ofInputStream} but with idle-bounded reads.
     *
     * @param idleSeconds peer silence window in seconds
     * @return the body handler producing idle-bounded streams
     */
    static HttpResponse.BodyHandler<InputStream> bodyHandler(double idleSeconds) {
        return responseInfo -> new QueueingBodySubscriber(new IdleBoundInputStream(idleSeconds));
    }

    @Override
    public int read() throws IOException {
        if (!advance()) {
            return -1;
        }
        int value = current[offset] & 0xFF;
        offset++;
        return value;
    }

    @Override
    public int read(byte[] target, int targetOffset, int length) throws IOException {
        if (target == null) {
            throw new NullPointerException("target buffer must not be null");
        }
        if (targetOffset < 0 || length < 0 || targetOffset + length > target.length) {
            throw new IndexOutOfBoundsException("invalid target range");
        }
        if (length == 0) {
            return 0;
        }
        if (!advance()) {
            return -1;
        }
        int count = Math.min(length, current.length - offset);
        System.arraycopy(current, offset, target, targetOffset, count);
        offset += count;
        return count;
    }

    @Override
    public void close() {
        isClosed = true;
        Flow.Subscription active = subscription;
        if (active != null) {
            active.cancel();
        }
        chunks.offer(END_OF_STREAM);
    }

    private boolean advance() throws IOException {
        while (current == null || offset >= current.length) {
            Object next = pollNext();
            if (next == END_OF_STREAM) {
                current = null;
                return false;
            }
            if (next instanceof IOException ioFailure) {
                current = null;
                throw ioFailure;
            }
            if (next instanceof RuntimeException failure) {
                current = null;
                throw failure;
            }
            current = (byte[]) next;
            offset = 0;
        }
        return true;
    }

    private Object pollNext() throws IOException {
        if (isClosed && chunks.isEmpty()) {
            return END_OF_STREAM;
        }
        Object next;
        try {
            next = chunks.poll(idleNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            InterruptedIOException interrupted = new InterruptedIOException(
                    "model response body read interrupted");
            interrupted.initCause(exception);
            throw interrupted;
        }
        if (next == null) {
            throw new IdleTimeoutIOException(idleSeconds);
        }
        return next;
    }

    private void offer(List<ByteBuffer> buffers) {
        for (ByteBuffer buffer : buffers) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            if (bytes.length > 0) {
                chunks.offer(bytes);
            }
        }
    }

    private void fail(Throwable throwable) {
        Object failure;
        if (throwable instanceof IOException || throwable instanceof RuntimeException) {
            failure = throwable;
        } else {
            failure = new IOException("model response body stream failed", throwable);
        }
        chunks.offer(failure);
    }

    /**
     * Raised when the peer stays silent beyond the idle window during a body read.
     */
    static final class IdleTimeoutIOException extends IOException {
        private static final long serialVersionUID = 1L;

        private final transient double idleSeconds;

        IdleTimeoutIOException(double idleSeconds) {
            super("model response idle timeout after " + idleSeconds + "s of peer silence");
            this.idleSeconds = idleSeconds;
        }

        double idleSeconds() {
            return idleSeconds;
        }
    }

    private static final class QueueingBodySubscriber implements HttpResponse.BodySubscriber<InputStream> {
        private final IdleBoundInputStream stream;
        private final CompletableFuture<InputStream> body;

        private QueueingBodySubscriber(IdleBoundInputStream stream) {
            this.stream = stream;
            this.body = CompletableFuture.completedFuture(stream);
        }

        @Override
        public CompletionStage<InputStream> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription newSubscription) {
            stream.subscription = newSubscription;
            newSubscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            stream.offer(item);
        }

        @Override
        public void onError(Throwable throwable) {
            stream.fail(throwable);
        }

        @Override
        public void onComplete() {
            stream.chunks.offer(END_OF_STREAM);
        }
    }
}
