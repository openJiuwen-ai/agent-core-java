/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.reactive;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link ReactiveAdapters} 的核心契约：
 * <ul>
 *   <li>{@code fromCallable} 异常原样抛出，不被 Reactor 包装</li>
 *   <li>{@code fromIterator} 在 COMPLETE/ERROR/CANCEL 三种终态都触发 cleanup</li>
 *   <li>取消时立刻停止迭代，不继续消费迭代器</li>
 *   <li>{@code fromAutoCloseableIterator} 在三种终态都调用 {@code close()}</li>
 * </ul>
 */
class ReactiveAdaptersTest {

    /** Mono 正常发出值并 complete。 */
    @Test
    void fromCallableEmitsValueAndCompletes() {
        Mono<String> mono = ReactiveAdapters.fromCallable(() -> "hello");
        StepVerifier.create(mono).expectNext("hello").verifyComplete();
    }

    /** callable 抛出的异常对象身份不变（同一引用），不被包装。 */
    @Test
    void fromCallablePropagatesCheckedExceptionUnwrapped() {
        Exception boom = new IllegalStateException("boom");
        Mono<String> mono = ReactiveAdapters.fromCallable(() -> {
            throw boom;
        });
        StepVerifier.create(mono).expectErrorMatches(t -> t == boom).verify();
    }

    /** runnable 副作用执行，Mono 空 complete。 */
    @Test
    void fromRunnableCompletesEmpty() {
        AtomicBoolean ran = new AtomicBoolean(false);
        Mono<Void> mono = ReactiveAdapters.fromRunnable(() -> ran.set(true));
        StepVerifier.create(mono).verifyComplete();
        assertTrue(ran.get(), "runnable should have executed");
    }

    /** Flux 按序发射迭代器全部元素后 complete。 */
    @Test
    void fromIteratorEmitsAllElementsInOrder() {
        List<Integer> source = Arrays.asList(1, 2, 3, 4, 5);
        Flux<Integer> flux = ReactiveAdapters.fromIterator(source.iterator());
        StepVerifier.create(flux).expectNext(1, 2, 3, 4, 5).verifyComplete();
    }

    /** 正常读完后 cleanup 必须执行恰好一次。 */
    @Test
    void fromIteratorFiresCleanupOnCompleteSignal() throws Exception {
        List<Integer> source = Arrays.asList(1, 2, 3);
        AtomicInteger cleanupCount = new AtomicInteger(0);
        CountDownLatch cleanupFired = new CountDownLatch(1);
        Flux<Integer> flux = ReactiveAdapters.fromIterator(source.iterator(), () -> {
            cleanupCount.incrementAndGet();
            cleanupFired.countDown();
        });
        StepVerifier.create(flux).expectNext(1, 2, 3).verifyComplete();
        // doFinally 在 onComplete 之后异步触发，需要短暂等待。
        assertTrue(cleanupFired.await(2, TimeUnit.SECONDS),
                "cleanup must fire after COMPLETE (within timeout)");
        assertEquals(1, cleanupCount.get(), "cleanup should fire exactly once on COMPLETE");
    }

    /** 迭代过程中抛异常，cleanup 也必须触发。 */
    @Test
    void fromIteratorFiresCleanupOnErrorSignal() throws Exception {
        AtomicInteger cleanupCount = new AtomicInteger(0);
        CountDownLatch cleanupFired = new CountDownLatch(1);
        Iterator<Integer> throwing = new Iterator<>() {
            int n = 0;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Integer next() {
                if (n++ >= 2) {
                    throw new RuntimeException("iterator boom");
                }
                return n;
            }
        };
        Flux<Integer> flux = ReactiveAdapters.fromIterator(throwing, () -> {
            cleanupCount.incrementAndGet();
            cleanupFired.countDown();
        });
        StepVerifier.create(flux)
                .expectNext(1, 2)
                .expectErrorMessage("iterator boom")
                .verify();
        assertTrue(cleanupFired.await(2, TimeUnit.SECONDS),
                "cleanup must fire after ERROR (within timeout)");
        assertEquals(1, cleanupCount.get(), "cleanup should fire exactly once on ERROR");
    }

    /** 下游取消订阅后 cleanup 必须触发——用无限迭代器证伪"漏触发会挂死"。 */
    @Test
    void fromIteratorFiresCleanupOnCancelSignal() throws Exception {
        AtomicInteger emitted = new AtomicInteger(0);
        AtomicInteger cleanupCount = new AtomicInteger(0);
        CountDownLatch cleanupFired = new CountDownLatch(1);

        Iterator<Integer> infinite = new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Integer next() {
                return emitted.incrementAndGet();
            }
        };
        Flux<Integer> flux = ReactiveAdapters.fromIterator(infinite, () -> {
            cleanupCount.incrementAndGet();
            cleanupFired.countDown();
        });

        StepVerifier.create(flux, 3)
                .expectNextCount(3)
                .thenCancel()
                .verify(Duration.ofSeconds(5));

        assertTrue(cleanupFired.await(2, TimeUnit.SECONDS), "cleanup must run on CANCEL");
        assertEquals(1, cleanupCount.get(), "cleanup should fire exactly once on CANCEL");
    }

    /** 取消后必须立刻停止迭代——允许至多 1 个已在途的多余发射。 */
    @Test
    void fromIteratorCancellationStopsIterationPromptly() throws Exception {
        AtomicInteger emitted = new AtomicInteger(0);
        Iterator<Integer> infinite = new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Integer next() {
                return emitted.incrementAndGet();
            }
        };

        Flux<Integer> flux = ReactiveAdapters.fromIterator(infinite, null);

        StepVerifier.create(flux, 5)
                .expectNextCount(5)
                .thenCancel()
                .verify(Duration.ofSeconds(5));

        int countAtCancel = emitted.get();
        // 给迭代器线程留 200ms——若取消未生效，计数会持续攀升。
        Thread.sleep(200);
        int countLater = emitted.get();
        assertTrue(countLater - countAtCancel <= 1,
                "iteration should stop on cancel; saw " + (countLater - countAtCancel) + " extra emissions");
    }

    /** cleanup 传 null 时各信号下都不能 NPE。 */
    @Test
    void fromIteratorWithoutCleanupDoesNotThrow() {
        List<String> source = Arrays.asList("a", "b");
        Flux<String> flux = ReactiveAdapters.fromIterator(source.iterator(), null);
        StepVerifier.create(flux).expectNext("a", "b").verifyComplete();
    }

    // ==================== fromAutoCloseableIterator ====================

    /** 自然 complete 时触发 close()。 */
    @Test
    void fromAutoCloseableIterator_close_fires_on_complete() {
        BlockingIterator it = new BlockingIterator(3, false);

        StepVerifier.create(ReactiveAdapters.fromAutoCloseableIterator(() -> it))
                .expectNext(0, 1, 2)
                .verifyComplete();

        assertThat(it.closed.get())
                .as("close() must fire on natural completion of the stream")
                .isTrue();
    }

    /** 下游取消时触发 close()，进而解开阻塞在 hasNext() 的 worker。 */
    @Test
    void fromAutoCloseableIterator_close_fires_on_cancel_and_unblocks_worker() throws Exception {
        BlockingIterator it = new BlockingIterator(Integer.MAX_VALUE, true);

        Flux<Integer> flux = ReactiveAdapters.fromAutoCloseableIterator(() -> it);
        var disposable = flux.subscribe();
        awaitTrue(() -> it.hasNextEntries.get() > 0, Duration.ofSeconds(2),
                "worker thread must enter hasNext() before we cancel");
        disposable.dispose();

        awaitTrue(it.closed::get, Duration.ofSeconds(2),
                "downstream cancel must trigger close() on the AutoCloseable iterator");
    }

    /** 出错终止时也必须触发 close()。 */
    @Test
    void fromAutoCloseableIterator_close_fires_on_error() {
        BlockingIterator it = new BlockingIterator(2, false);
        it.throwAfter = 1;

        StepVerifier.create(ReactiveAdapters.fromAutoCloseableIterator(() -> it))
                .expectNext(0)
                .expectError(IllegalStateException.class)
                .verify();

        assertThat(it.closed.get())
                .as("close() must fire on error termination")
                .isTrue();
    }

    /** 传入普通 Iterator（非 AutoCloseable）时安全降级，不 NPE。 */
    @Test
    void fromAutoCloseableIterator_non_autocloseable_degrades_gracefully() {
        Iterator<Integer> plain = Arrays.asList(1, 2, 3).iterator();

        StepVerifier.create(ReactiveAdapters.fromAutoCloseableIterator(() -> plain))
                .expectNext(1, 2, 3)
                .verifyComplete();
    }

    /** 轮询条件直到 true 或超时，替代未引入的 awaitility。 */
    private static void awaitTrue(BooleanSupplier condition,
                                  Duration timeout, String message) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        if (!condition.getAsBoolean()) {
            throw new AssertionError(message);
        }
    }

    /**
     * 阻塞型迭代器：hasNext() 会一直 park，直到外部调用 {@link #close()}
     * 翻转标志位（模拟 SSE 的 BufferedReader.readLine——只有 socket 关闭才返回）。
     */
    private static final class BlockingIterator implements Iterator<Integer>, AutoCloseable {
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicInteger hasNextEntries = new AtomicInteger();
        final int limit;
        final boolean parkForever;
        int throwAfter = -1;
        int next;

        BlockingIterator(int limit, boolean parkForever) {
            this.limit = limit;
            this.parkForever = parkForever;
        }

        @Override
        public boolean hasNext() {
            hasNextEntries.incrementAndGet();
            if (closed.get()) {
                return false;
            }
            if (next >= limit) {
                return false;
            }
            if (throwAfter >= 0 && next >= throwAfter) {
                throw new IllegalStateException("simulated read failure");
            }
            if (parkForever && next > 0) {
                while (!closed.get()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                return false;
            }
            return true;
        }

        @Override
        public Integer next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return next++;
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
