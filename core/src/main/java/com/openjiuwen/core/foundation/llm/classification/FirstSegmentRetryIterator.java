/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.ModelRetryListener;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Iterator implementing the lazy streaming first-segment retry window. The
 * open phase already succeeded eagerly (see
 * {@link ModelCallRetrier#openStream}); the first advance of this iterator
 * drives consumption up to the first effective data segment. A classified
 * failure before that segment (error frame, idle timeout, read failure)
 * triggers a whole-call retry with the remaining per-call budget; a failure
 * after the first effective segment, or a permanent-category failure, is
 * surfaced as-is. Closing before or during the first advance releases the
 * in-flight attempt so a blocked read is unblocked, preserving the streaming
 * cancellation contract.
 *
 * @param <T> chunk type
 * @since 2026-10-19
 */
public final class FirstSegmentRetryIterator<T> implements Iterator<T>, Closeable {
    private final RetryContext context;
    private final ModelCallRetrier.StreamAttempt<T> attempt;
    private final ModelCallRetrier retrier;
    private final ModelRetryListener listener;
    private volatile ModelCallRetrier.StreamHandle<T> current;
    private volatile T head;
    private volatile boolean isAdvanced;
    private volatile boolean isClosed;
    private volatile BaseError pendingFailure;

    /**
     * Creates the lazy first-segment window.
     *
     * @param context per-call retry budget shared with the open phase, must not be null
     * @param attempt stream open attempt used when the window retries, must not be null
     * @param firstHandle handle of the eagerly opened first attempt, must not be null
     * @param retrier retry executor shared with the open phase, must not be null
     * @param listener retry event listener, may be null
     */
    public FirstSegmentRetryIterator(RetryContext context, ModelCallRetrier.StreamAttempt<T> attempt,
        ModelCallRetrier.StreamHandle<T> firstHandle, ModelCallRetrier retrier,
        ModelRetryListener listener) {
        this.context = context;
        this.attempt = attempt;
        this.current = firstHandle;
        this.retrier = retrier;
        this.listener = listener;
    }

    @Override
    public boolean hasNext() {
        if (pendingFailure != null) {
            throw pendingFailure;
        }
        if (head != null) {
            return true;
        }
        if (isClosed) {
            return false;
        }
        if (!isAdvanced) {
            advanceFirst();
        }
        if (pendingFailure != null) {
            throw pendingFailure;
        }
        if (isClosed) {
            // Closed while the first advance was in flight: report end of stream
            // without touching the abandoned attempt's iterator.
            return false;
        }
        return head != null || current.iterator().hasNext();
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException("no more elements");
        }
        if (head != null) {
            T item = head;
            head = null;
            return item;
        }
        return current.iterator().next();
    }

    @Override
    public void close() {
        if (isClosed) {
            return;
        }
        isClosed = true;
        closeCurrentQuietly();
    }

    private void advanceFirst() {
        isAdvanced = true;
        while (true) {
            if (isClosed) {
                // Closed while a reopen was in flight: the close signal wins, and the
                // freshly opened stream must not leak.
                closeCurrentQuietly();
                return;
            }
            try {
                Optional<T> first = advanceToFirstSegment(current.iterator());
                if (first.isPresent()) {
                    head = first.get();
                }
                return;
            } catch (BaseError failure) {
                closeCurrentQuietly();
                if (isClosed) {
                    // Closed concurrently while the advance was blocked: the close signal
                    // wins, no retry and no new stream is opened.
                    return;
                }
                if (ModelCallRetrier.isAfterFirstSegment(failure)
                    || !retrier.prepareRetry(context, failure, listener)) {
                    current.onWindowFailure(failure);
                    pendingFailure = failure;
                    return;
                }
                if (!reopen()) {
                    return;
                }
            }
        }
    }

    private boolean reopen() {
        try {
            current = retrier.openStream(context, attempt, listener);
            return true;
        } catch (IOException ioFailure) {
            BaseError classified = classifyReopenFailure(ioFailure);
            current.onWindowFailure(classified);
            pendingFailure = classified;
            return false;
        } catch (BaseError failure) {
            current.onWindowFailure(failure);
            pendingFailure = failure;
            return false;
        }
    }

    private BaseError classifyReopenFailure(IOException ioFailure) {
        ModelErrorFacts facts = ModelErrorFacts.ofThrowable(ioFailure, true);
        ModelCallClassification classification =
            ModelErrorClassifiers.defaultClassifier().classify(facts);
        if (classification.category() == ModelErrorCategory.USER_CANCELLED) {
            throw ModelCallRetrier.cancellation("model stream cancelled");
        }
        return ClassifiedModelErrors.toError(classification, facts,
            "stream transport failure: " + ioFailure.getMessage(), ioFailure);
    }

    private void closeCurrentQuietly() {
        if (current != null) {
            current.closeQuietly();
        }
    }

    private static <T> Optional<T> advanceToFirstSegment(Iterator<T> iterator) {
        while (iterator.hasNext()) {
            T item = iterator.next();
            if (item != null) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }
}
