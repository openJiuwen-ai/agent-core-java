/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.concurrent.InterruptSignals;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies concurrent child trace registration and detached snapshots.
 *
 * @since 2026-10-28
 */
class SpanConcurrencyTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void unsetAndExplicitlyEmptyChildrenKeepDifferentJsonRepresentations() {
        Span span = new Span();
        assertThat(span.getChildInvokesId()).isNull();
        assertThat(OBJECT_MAPPER.valueToTree(span).has("childInvokes")).isFalse();
        span.setChildInvokesId(List.of());
        assertThat(span.getChildInvokesId()).isEmpty();
        assertThat(OBJECT_MAPPER.valueToTree(span).get("childInvokes").isArray()).isTrue();
        span.setChildInvokesId(null);
        assertThat(OBJECT_MAPPER.valueToTree(span).has("childInvokes")).isFalse();
    }

    @Test
    void parallelChildrenAndSnapshotsDoNotLoseOrCorruptTraceIds() throws Exception {
        AtomicInteger threadIds = new AtomicInteger();
        ThreadPoolExecutor workers = new ThreadPoolExecutor(8, 8, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8), task ->
                        new Thread(task, "span-regression-" + threadIds.incrementAndGet()));
        try {
            for (int round = 0; round < 10; round++) {
                Span span = new Span();
                CyclicBarrier start = new CyclicBarrier(8);
                List<CompletableFuture<Void>> tasks = new ArrayList<>();
                for (int worker = 0; worker < 8; worker++) {
                    int id = worker;
                    tasks.add(CompletableFuture.runAsync(() -> {
                        try {
                            start.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException error) {
                            InterruptSignals.restoreCurrentThread();
                            throw new IllegalStateException(error);
                        } catch (BrokenBarrierException | TimeoutException error) {
                            throw new IllegalStateException(error);
                        }
                        for (int child = 0; child < 250; child++) {
                            span.appendChildInvokeId(id + ":" + child);
                            assertThat(span.snapshot().getChildInvokesId()).doesNotContainNull();
                        }
                    }, workers));
                }
                CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
                assertThat(span.getChildInvokesId()).hasSize(2000).doesNotHaveDuplicates().doesNotContainNull();
            }
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void childIdListsAndSnapshotsAreDetachedFromCallers() {
        Span span = new Span();
        List<String> supplied = new ArrayList<>(List.of("first"));
        span.setChildInvokesId(supplied);
        supplied.clear();
        Span snapshot = span.snapshot();
        span.appendChildInvokeId("second");
        span.getChildInvokesId().clear();

        assertThat(span.getChildInvokesId()).containsExactly("first", "second");
        assertThat(snapshot.getChildInvokesId()).containsExactly("first");
    }
}
