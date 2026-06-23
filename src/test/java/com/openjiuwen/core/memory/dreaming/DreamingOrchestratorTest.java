/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.dreaming;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the idle-aware dreaming orchestrator.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/memory/dreaming/test_orchestrator.py}.</p>
 *
 * <p>Mirrors Python's {@code DreamingOrchestrator} in
 * {@code openjiuwen/core/memory/dreaming/orchestrator.py}.</p>
 */
class DreamingOrchestratorTest {

    private static final Logger ORCHESTRATOR_LOGGER = Logger.getLogger(DreamingOrchestrator.class.getName());

    @Test
    void initDefaults() throws Exception {
        DreamingOrchestrator orchestrator = new DreamingOrchestrator(
                () -> CompletableFuture.completedFuture(null),
                3600.0);

        assertThat(getField(orchestrator, "name")).isEqualTo("dreaming");
        assertFalse((Boolean) getField(orchestrator, "running"));
        assertNull(getField(orchestrator, "task"));
        assertNull(getField(orchestrator, "busyChecker"));
    }

    @Test
    void initIntervalClamped() {
        DreamingOrchestrator low = new DreamingOrchestrator(() -> CompletableFuture.completedFuture(null), 10.0);
        DreamingOrchestrator ok = new DreamingOrchestrator(() -> CompletableFuture.completedFuture(null), 120.0);

        assertThat(low.getHealth()).containsEntry("interval_seconds", 60.0d);
        assertThat(ok.getHealth()).containsEntry("interval_seconds", 120.0d);
    }

    @Test
    void healthProperty() {
        DreamingOrchestrator orchestrator = new DreamingOrchestrator(
                () -> CompletableFuture.completedFuture(null),
                3600.0);

        assertThat(orchestrator.getHealth())
                .containsEntry("running", false)
                .containsEntry("interval_seconds", 3600.0d);
    }

    @Test
    void startCreatesTaskAndSetsRunning() throws Exception {
        DreamingOrchestrator orchestrator = new DreamingOrchestrator(
                () -> CompletableFuture.completedFuture(null),
                60.0);

        orchestrator.start().join();

        assertThat(orchestrator.getHealth()).containsEntry("running", true);
        assertNotNull(getField(orchestrator, "task"));
        assertNotNull(getField(orchestrator, "executor"));

        orchestrator.stop().join();
    }

    @Test
    void startIdempotent() throws Exception {
        DreamingOrchestrator orchestrator = new DreamingOrchestrator(
                () -> CompletableFuture.completedFuture(null),
                60.0);

        orchestrator.start().join();
        Object firstTask = getField(orchestrator, "task");
        orchestrator.start().join();

        assertSame(firstTask, getField(orchestrator, "task"));

        orchestrator.stop().join();
    }

    @Test
    void stopCancelsTaskAndClearsRunning() throws Exception {
        DreamingOrchestrator orchestrator = new DreamingOrchestrator(
                () -> CompletableFuture.completedFuture(null),
                60.0);

        orchestrator.start().join();
        orchestrator.stop().join();

        assertThat(orchestrator.getHealth()).containsEntry("running", false);
        assertNull(getField(orchestrator, "task"));
    }

    @Test
    void stopIdempotent() {
        DreamingOrchestrator orchestrator = new DreamingOrchestrator(
                () -> CompletableFuture.completedFuture(null),
                60.0);

        orchestrator.start().join();
        orchestrator.stop().join();
        orchestrator.stop().join();

        assertThat(orchestrator.getHealth()).containsEntry("running", false);
    }

    @Test
    void stopWhenNeverStarted() {
        DreamingOrchestrator orchestrator = new DreamingOrchestrator(
                () -> CompletableFuture.completedFuture(null),
                60.0);

        orchestrator.stop().join();

        assertThat(orchestrator.getHealth()).containsEntry("running", false);
    }

    @Test
    void tickRunsSweepWhenNoBusyChecker() {
        AtomicInteger sweeps = new AtomicInteger();
        DreamingOrchestrator orchestrator = orchestrator(
                () -> {
                    sweeps.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                },
                null,
                "dreaming");

        orchestrator.tick().join();

        assertThat(sweeps).hasValue(1);
    }

    @Test
    void tickSkipsSweepWhenBusy() {
        AtomicInteger sweeps = new AtomicInteger();
        DreamingOrchestrator orchestrator = orchestrator(
                () -> {
                    sweeps.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                },
                () -> true,
                "busy");

        orchestrator.tick().join();

        assertThat(sweeps).hasValue(0);
    }

    @Test
    void tickRunsSweepWhenNotBusy() {
        AtomicInteger sweeps = new AtomicInteger();
        AtomicInteger busyCalls = new AtomicInteger();
        DreamingOrchestrator orchestrator = orchestrator(
                () -> {
                    sweeps.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                },
                () -> {
                    busyCalls.incrementAndGet();
                    return false;
                },
                "available");

        orchestrator.tick().join();

        assertThat(busyCalls).hasValue(1);
        assertThat(sweeps).hasValue(1);
    }

    @Test
    void tickBusyCheckerExceptionDoesNotBlockSweep() {
        AtomicInteger sweeps = new AtomicInteger();
        DreamingOrchestrator orchestrator = orchestrator(
                () -> {
                    sweeps.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                },
                () -> {
                    throw new IllegalStateException("checker crash");
                },
                "fallback");

        LogCapture logs = captureLogs();
        try {
            orchestrator.tick().join();
        } finally {
            logs.close();
        }

        assertThat(sweeps).hasValue(1);
        assertTrue(logs.contains(Level.WARNING, "busy_checker raised exception"));
    }

    @Test
    void tickSweepExceptionLogged() {
        DreamingOrchestrator orchestrator = orchestrator(
                () -> CompletableFuture.failedFuture(new IllegalArgumentException("sweep failure")),
                null,
                "failing");

        LogCapture logs = captureLogs();
        try {
            assertDoesNotThrow(() -> orchestrator.tick().join());
        } finally {
            logs.close();
        }

        assertTrue(logs.contains(Level.SEVERE, "sweep exception"));
    }

    @Test
    void tickCancelledErrorPropagates() {
        DreamingOrchestrator orchestrator = orchestrator(
                () -> CompletableFuture.failedFuture(new CancellationException("cancelled")),
                null,
                "cancelled");

        RuntimeException error = assertThrows(RuntimeException.class, () -> orchestrator.tick().join());

        assertTrue(rootCause(error) instanceof CancellationException);
    }

    @Test
    void loopExitsWhenStoppedDuringInitialSleep() {
        AtomicInteger sweeps = new AtomicInteger();
        DreamingOrchestrator orchestrator = new DreamingOrchestrator(
                () -> {
                    sweeps.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                },
                60.0);

        orchestrator.start().join();
        orchestrator.stop().join();

        assertThat(orchestrator.getHealth()).containsEntry("running", false);
        assertThat(sweeps).hasValue(0);
    }

    @Test
    void loopCancelledTaskCompletesExceptionally() throws Exception {
        DreamingOrchestrator orchestrator = new DreamingOrchestrator(
                () -> CompletableFuture.completedFuture(null),
                60.0);

        orchestrator.start().join();
        @SuppressWarnings("unchecked")
        CompletableFuture<Void> task = (CompletableFuture<Void>) getField(orchestrator, "task");

        task.cancel(true);

        assertTrue(task.isCancelled() || task.isCompletedExceptionally());
        orchestrator.stop().join();
    }

    @Test
    void startAndStopAreIdempotent() {
        DreamingOrchestrator orchestrator = new DreamingOrchestrator(
                () -> CompletableFuture.completedFuture(null),
                90.0);

        orchestrator.start().join();
        orchestrator.start().join();
        assertThat(orchestrator.getHealth()).containsEntry("running", true);

        orchestrator.stop().join();
        orchestrator.stop().join();
        assertThat(orchestrator.getHealth()).containsEntry("running", false);
    }

    @Test
    void customNamePropagates() throws Exception {
        DreamingOrchestrator orchestrator = new DreamingOrchestrator(
                () -> CompletableFuture.completedFuture(null),
                60.0,
                null,
                "code-dreaming");

        assertThat(getField(orchestrator, "name")).isEqualTo("code-dreaming");
        orchestrator.start().join();

        assertTrue(Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> "code-dreaming-loop".equals(thread.getName())));

        orchestrator.stop().join();
    }

    @Test
    void executorClearedAfterStop() throws Exception {
        DreamingOrchestrator orchestrator = new DreamingOrchestrator(
                () -> CompletableFuture.completedFuture(null),
                60.0);

        orchestrator.start().join();
        ExecutorService executor = (ExecutorService) getField(orchestrator, "executor");
        orchestrator.stop().join();

        assertTrue(executor.isShutdown());
        assertNull(getField(orchestrator, "executor"));
    }

    private static DreamingOrchestrator orchestrator(
            Callable<CompletableFuture<Void>> sweepFn,
            Supplier<Boolean> busyChecker,
            String name) {
        return new DreamingOrchestrator(sweepFn, 60.0, busyChecker, name);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static LogCapture captureLogs() {
        LogCapture capture = new LogCapture(ORCHESTRATOR_LOGGER);
        ORCHESTRATOR_LOGGER.addHandler(capture);
        ORCHESTRATOR_LOGGER.setLevel(Level.ALL);
        return capture;
    }

    private static final class LogCapture extends Handler implements AutoCloseable {
        private final Logger logger;
        private final Level originalLevel;
        private final List<LogRecord> records = new java.util.concurrent.CopyOnWriteArrayList<>();

        private LogCapture(Logger logger) {
            this.logger = logger;
            this.originalLevel = logger.getLevel();
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
            // No buffered output.
        }

        @Override
        public void close() {
            logger.removeHandler(this);
            logger.setLevel(originalLevel);
        }

        private boolean contains(Level level, String text) {
            return records.stream().anyMatch(record ->
                    record.getLevel().intValue() >= level.intValue()
                            && record.getMessage() != null
                            && record.getMessage().contains(text));
        }
    }
}
