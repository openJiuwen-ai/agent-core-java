/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * UT-W: lazy first-segment window of the streaming retry path: empty streams,
 * retry and reopen, terminal failure reporting, and the close-during-advance
 * cancellation contract.
 *
 * @since 2026-10-20
 */
class FirstSegmentRetryIteratorTest {
    private final ModelCallRetrier retrier = new ModelCallRetrier(delay -> { }, () -> 0.0D);

    @Test
    void emptyStream_completesWithoutRetry() {
        // Given a stream that ends immediately ([DONE] only), When iterated,
        // Then it completes as an empty stream without consuming the retry budget
        AtomicInteger opens = new AtomicInteger();
        ModelCallRetrier.StreamAttempt<String> attempt = () -> {
            opens.incrementAndGet();
            return new FakeHandle(List.<String>of().iterator(), () -> { });
        };
        FirstSegmentRetryIterator<String> iterator = new FirstSegmentRetryIterator<>(context(2), attempt,
            new FakeHandle(List.<String>of().iterator(), () -> { }), retrier, null);

        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
        assertThat(opens).hasValue(0);
    }

    @Test
    void windowRetryableFailure_reopensAndStreams() {
        // Given a temporary failure before the first segment, When iterated,
        // Then the whole call is re-initiated once and the new stream flows
        AtomicInteger opens = new AtomicInteger();
        ModelCallRetrier.StreamAttempt<String> attempt = () -> {
            opens.incrementAndGet();
            return new FakeHandle(List.of("recovered").iterator(), () -> { });
        };
        FirstSegmentRetryIterator<String> iterator = new FirstSegmentRetryIterator<>(context(2), attempt,
            failingHandle(temporaryFailure(), () -> { }), retrier, null);

        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isEqualTo("recovered");
        assertThat(opens).hasValue(1);
    }

    @Test
    void windowTerminalFailure_reportedToHandleAndSurfaced() {
        // Given a permanent failure before the first segment, When iterated,
        // Then it is reported to the handle (breaker accounting) and surfaced as-is
        AtomicInteger opens = new AtomicInteger();
        ModelCallRetrier.StreamAttempt<String> attempt = () -> {
            opens.incrementAndGet();
            return new FakeHandle(List.of("never").iterator(), () -> { });
        };
        FakeHandle first = failingHandle(permanentFailure(), () -> { });
        FirstSegmentRetryIterator<String> iterator = new FirstSegmentRetryIterator<>(context(2), attempt,
            first, retrier, null);

        assertThatThrownBy(iterator::hasNext).isInstanceOf(BaseError.class);
        assertThat(first.windowFailures).hasSize(1);
        assertThat(opens).hasValue(0);
    }

    @Test
    void closeDuringFirstAdvance_closeWinsWithoutRetry() {
        // Given a close arriving while the first advance is blocked, When the blocked
        // read then fails, Then the close signal wins: no retry, no reopen, no
        // further touch of the abandoned attempt, and the stream reports its end
        AtomicReference<FirstSegmentRetryIterator<String>> self = new AtomicReference<>();
        AtomicInteger opens = new AtomicInteger();
        ModelCallRetrier.StreamAttempt<String> attempt = () -> {
            opens.incrementAndGet();
            return new FakeHandle(List.of("never").iterator(), () -> { });
        };
        FakeHandle first = failingHandle(temporaryFailure(), () -> closeQuietly(self.get()));
        FirstSegmentRetryIterator<String> iterator = new FirstSegmentRetryIterator<>(context(2), attempt,
            first, retrier, null);
        self.set(iterator);

        assertThat(iterator.hasNext()).isFalse();
        assertThat(opens).hasValue(0);
    }

    @Test
    void closeDuringReopen_newStreamClosedWithoutAdvance() {
        // Given a close arriving while the reopen is in flight, When the reopen then
        // completes, Then the freshly opened stream is closed immediately, never read,
        // and the stream reports its end
        AtomicReference<FirstSegmentRetryIterator<String>> self = new AtomicReference<>();
        AtomicInteger opens = new AtomicInteger();
        AtomicBoolean isReopenedClosed = new AtomicBoolean();
        ModelCallRetrier.StreamAttempt<String> attempt = () -> {
            opens.incrementAndGet();
            closeQuietly(self.get());
            return new FakeHandle(List.of("leaked").iterator(), () -> isReopenedClosed.set(true));
        };
        FirstSegmentRetryIterator<String> iterator = new FirstSegmentRetryIterator<>(context(2), attempt,
            failingHandle(temporaryFailure(), () -> { }), retrier, null);
        self.set(iterator);

        assertThat(iterator.hasNext()).isFalse();
        assertThat(opens).hasValue(1);
        assertThat(isReopenedClosed).isTrue();
    }

    private static void closeQuietly(FirstSegmentRetryIterator<String> iterator) {
        if (iterator != null) {
            iterator.close();
        }
    }

    private static RetryContext context(int maxRetries) {
        return new RetryContext(new ModelCallRetryPolicy(maxRetries, true, true));
    }

    private static FakeHandle failingHandle(BaseError failure, Runnable onClose) {
        return new FakeHandle(new Iterator<>() {
            @Override
            public boolean hasNext() {
                throw failure;
            }

            @Override
            public String next() {
                throw failure;
            }
        }, onClose);
    }

    private static BaseError temporaryFailure() {
        return classified(503);
    }

    private static BaseError permanentFailure() {
        return classified(402);
    }

    private static BaseError classified(int status) {
        ModelErrorFacts facts = ModelErrorFacts.Builder.create()
            .httpStatus(status)
            .stream(true)
            .build();
        ModelCallClassification classification = new RuleBasedModelErrorClassifier().classify(facts);
        return ClassifiedModelErrors.toError(classification, facts, "HTTP " + status, null);
    }

    private static final class FakeHandle implements ModelCallRetrier.StreamHandle<String> {
        private final Iterator<String> iterator;
        private final Runnable onClose;
        private final List<BaseError> windowFailures = new ArrayList<>();

        private FakeHandle(Iterator<String> iterator, Runnable onClose) {
            this.iterator = iterator;
            this.onClose = onClose;
        }

        @Override
        public Iterator<String> iterator() {
            return iterator;
        }

        @Override
        public void closeQuietly() {
            onClose.run();
        }

        @Override
        public void onWindowFailure(BaseError failure) {
            windowFailures.add(failure);
        }
    }
}
