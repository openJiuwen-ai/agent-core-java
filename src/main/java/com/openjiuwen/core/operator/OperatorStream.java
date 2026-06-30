/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator;

import java.lang.ref.Cleaner;
import java.util.Iterator;

/**
 * Iterator-like stream with an explicit close hook for early termination.
 *
 * @param <T> streamed chunk type
 */
public interface OperatorStream<T> extends Iterator<T>, AutoCloseable {

    @Override
    default void close() {
    }

    /**
     * Wrap a delegate iterator with automatic context cleanup.
     * <p>
     * The returned stream guarantees that {@code onClose} is invoked exactly once
     * when any of the following occurs:
     * <ul>
     *     <li>All elements are consumed</li>
     *     <li>{@link #close()} is called explicitly</li>
     *     <li>An exception occurs during iteration</li>
     *     <li>The stream is garbage collected without being isClosed (safety net)</li>
     * </ul>
     * This mirrors Python's generator close-on-GC behavior for {@code async for} loops.
     *
     * @param delegate the underlying iterator
     * @param onClose  cleanup action to run on termination
     * @param <T>      element type
     * @return wrapped operator stream with cleanup guarantees
     */
    static <T> OperatorStream<T> wrap(Iterator<T> delegate, Runnable onClose) {
        return new ContextClosingStream<>(delegate, onClose);
    }
}

/**
 * OperatorStream implementation that closes operator context on termination.
 * <p>
 * Uses {@link Cleaner} as a safety net to ensure cleanup when the stream is
 * abandoned without explicit {@code close()} — mirroring Python's generator
 * close-on-GC behavior for {@code async for} loops.
 */
final class ContextClosingStream<T> implements OperatorStream<T> {

    private static final Cleaner CLEANER = Cleaner.create();

    private final Iterator<T> delegate;
    private final Cleaner.Cleanable cleanable;

    ContextClosingStream(Iterator<T> delegate, Runnable onClose) {
        this.delegate = delegate;
        this.cleanable = CLEANER.register(this, new CleanupAction(onClose));
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean hasNext() {
        try {
            boolean hasNext = delegate.hasNext();
            if (!hasNext) {
                close();
            }
            return hasNext;
        } catch (RuntimeException | Error ex) {
            close();
            throw ex;
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public T next() {
        try {
            T value = delegate.next();
            if (!delegate.hasNext()) {
                close();
            }
            return value;
        } catch (RuntimeException | Error ex) {
            close();
            throw ex;
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void close() {
        cleanable.clean();
    }

    /**
     * Cleanup state held separately from the stream to allow GC-driven cleanup.
     * Must NOT reference the ContextClosingStream instance.
     */
    private static final class CleanupAction implements Runnable {
        private final Runnable onClose;
        private volatile boolean isClosed;

        CleanupAction(Runnable onClose) {
            this.onClose = onClose;
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public void run() {
            if (!isClosed) {
                isClosed = true;
                onClose.run();
            }
        }
    }
}
