/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.prompts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the concurrent data race in {@link SystemPromptBuilder}.
 *
 * <p>The builder is shared as an application-wide singleton (e.g. {@code perf-unified-deep-agent}):
 * writer threads perform structural modifications ({@code addSection} on new keys,
 * {@code removeSection}, {@code clearTransient}) while reader threads call {@code build()}
 * without any lock. The unlocked read of {@code sections.values()} inside
 * {@code LinkedHashMap.LinkedValues.toArray} snapshots the size first and then walks the
 * linked list without a modCount check, so a concurrent structural write makes the walk
 * overrun the snapshot array: {@code ArrayIndexOutOfBoundsException} (or
 * {@code ConcurrentModificationException} when the read goes through an iterator).
 *
 * <p>Expected failure before the fix (0.1.17 regression): AIOOBE/CME collected in
 * {@code firstError}. After the fix: all threads complete without error.
 */
class SystemPromptBuilderConcurrencyTest {

    private static final int WRITER_THREADS = 8;
    private static final int READER_THREADS = 4;
    private static final int WRITER_ITERATIONS = 20_000;
    private static final int READER_ITERATIONS = 50_000;

    @Test
    @DisplayName("concurrent structural writes + build() reads must not corrupt LinkedHashMap state")
    void testConcurrentWriteAndBuild() throws Exception {
        // 2 persistent sections survive clearTransient, transient churn comes from writers.
        SystemPromptBuilder builder = new SystemPromptBuilder("en");
        builder.addPersistentSection(new PromptSection("identity", Map.of("en", "identity"), 10));
        builder.addPersistentSection(new PromptSection("safety", Map.of("en", "safety"), 20));

        AtomicReference<Throwable> firstError = new AtomicReference<>();
        AtomicInteger completedBuilds = new AtomicInteger();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(WRITER_THREADS + READER_THREADS);
        List<ExecutorService> pools = new ArrayList<>();

        // Writers: structural churn - remove+add same key (re-links node to tail), removeIf in
        // clearTransient, plus brand-new keys to grow the map (size snapshot vs. walk mismatch).
        for (int w = 0; w < WRITER_THREADS; w++) {
            final int writerId = w;
            ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "writer-" + writerId);
                t.setDaemon(true);
                return t;
            });
            pools.add(pool);
            pool.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < WRITER_ITERATIONS; i++) {
                        String name = "transient-" + writerId;
                        builder.removeSection(name);
                        builder.addSection(new PromptSection(name, Map.of("en", "t" + i), 30 + writerId));
                        if (i % 500 == 0) {
                            builder.clearTransient();
                        }
                    }
                } catch (Throwable t) {
                    firstError.compareAndSet(null, t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Readers: the production crash path - new ArrayList<>(sections.values()) inside build().
        for (int r = 0; r < READER_THREADS; r++) {
            final int readerId = r;
            ExecutorService pool = Executors.newSingleThreadExecutor(x -> {
                Thread t = new Thread(x, "reader-" + readerId);
                t.setDaemon(true);
                return t;
            });
            pools.add(pool);
            pool.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < READER_ITERATIONS; i++) {
                        String prompt = builder.build();
                        if (prompt.isBlank()) {
                            firstError.compareAndSet(null,
                                    new IllegalStateException("build() rendered an empty prompt"));
                        }
                        completedBuilds.incrementAndGet();
                    }
                } catch (Throwable t) {
                    firstError.compareAndSet(null, t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        try {
            startLatch.countDown();
            // Bounded workloads: 160k structural writes + 200k locked reads. Pre-fix this fails
            // within the first second (racy toArray snapshot); post-fix everything is serialized
            // on the builder monitor and completes well inside the JUnit 60s method timeout.
            assertTrue(doneLatch.await(120, TimeUnit.SECONDS),
                    "threads did not finish in time; firstError=" + firstError.get());
        } finally {
            for (ExecutorService pool : pools) {
                pool.shutdownNow();
            }
        }

        assertNull(firstError.get(),
                () -> "data race reproduced: " + firstError.get()
                        + " at " + java.util.Arrays.toString(firstError.get().getStackTrace()));
        assertTrue(completedBuilds.get() == READER_THREADS * READER_ITERATIONS,
                () -> "readers completed " + completedBuilds.get() + " builds, expected "
                        + READER_THREADS * READER_ITERATIONS);
        // After writers stop, the map must be consistent: only the 2 persistent sections remain.
        builder.clearTransient();
        assertEquals(2, builder.getAllSections().size());
        assertEquals("identity\n\nsafety", builder.build());
    }

    @Test
    @DisplayName("getSection/hasSection concurrent with writes must not throw")
    void testConcurrentLookupWhileWriting() throws Exception {
        SystemPromptBuilder builder = new SystemPromptBuilder("en");
        builder.addPersistentSection(new PromptSection("identity", Map.of("en", "identity"), 10));

        AtomicReference<Throwable> firstError = new AtomicReference<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "lookup-writer");
            t.setDaemon(true);
            return t;
        });
        ExecutorService reader = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "lookup-reader");
            t.setDaemon(true);
            return t;
        });

        writer.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 20_000; i++) {
                    builder.removeSection("x");
                    builder.addSection(new PromptSection("x", Map.of("en", "v" + i), 10));
                    builder.clearTransient();
                }
            } catch (Throwable t) {
                firstError.compareAndSet(null, t);
            } finally {
                doneLatch.countDown();
            }
        });
        reader.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 20_000; i++) {
                    builder.hasSection("x");
                    builder.getSection("x");
                    builder.getAllSections();
                }
            } catch (Throwable t) {
                firstError.compareAndSet(null, t);
            } finally {
                doneLatch.countDown();
            }
        });

        try {
            startLatch.countDown();
            assertTrue(doneLatch.await(120, TimeUnit.SECONDS), "threads did not finish in time");
        } finally {
            writer.shutdownNow();
            reader.shutdownNow();
        }

        assertNull(firstError.get(), () -> "data race reproduced: " + firstError.get());
    }
}
