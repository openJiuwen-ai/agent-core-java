/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for spawned process lifecycle management.
 *
 * <p>Mirrors Python's {@code SpawnConfig}, {@code SpawnedProcessHandle}, and {@code spawn_process} in
 * {@code openjiuwen/core/runner/spawn/process_manager.py}.</p>
 */
class SpawnedProcessHandleTest {

    @AfterEach
    void resetHooks() {
        SpawnProcesses.resetTestHooks();
    }

    @Test
    void spawnConfigDefaultsMatchPythonDataclass() {
        SpawnConfig config = new SpawnConfig();

        assertEquals(5.0D, config.getHealthCheckInterval());
        assertEquals(10.0D, config.getShutdownTimeout());
        assertEquals(3.0D, config.getHealthCheckTimeout());
    }

    @Test
    void sendAndReceiveUseLineDelimitedProtocolStreams() {
        FakeProcess process = new FakeProcess(protocolLine(new SpawnMessage(
                SpawnMessageType.DONE,
                Map.of("ok", true),
                Instant.parse("2026-06-15T00:00:00Z"),
                "done-1"
        )));
        SpawnedProcessHandle handle = new SpawnedProcessHandle("proc-1", process);

        handle.sendMessage(new SpawnMessage(
                SpawnMessageType.INPUT,
                Map.of("query", "hello"),
                Instant.parse("2026-06-15T00:00:01Z"),
                "input-1"
        )).toCompletableFuture().join();
        SpawnMessage received = handle.receiveMessage().toCompletableFuture().join();

        assertEquals(SpawnMessageType.INPUT, process.writtenMessages().get(0).getType());
        assertEquals("input-1", process.writtenMessages().get(0).getMessageId());
        assertNotNull(received);
        assertEquals(SpawnMessageType.DONE, received.getType());
        assertEquals("done-1", received.getMessageId());
    }

    @Test
    void sendMessageRejectsStoppedProcessLikePythonRuntimeError() {
        FakeProcess process = new FakeProcess("");
        process.complete(17);
        SpawnedProcessHandle handle = new SpawnedProcessHandle("proc-stopped", process);

        CompletionException error = assertThrows(
                CompletionException.class,
                () -> handle.sendMessage(new SpawnMessage(SpawnMessageType.INPUT, Map.of()))
                        .toCompletableFuture()
                        .join()
        );

        assertTrue(error.getCause().getMessage().contains("is not running"));
        assertFalse(handle.isAlive());
        assertEquals(17, handle.getExitCode());
    }

    @Test
    void healthCheckAcceptsFirstHealthResponseAndDoesNotRequireMatchingMessageId() {
        FakeProcess process = new FakeProcess(protocolLine(new SpawnMessage(
                SpawnMessageType.HEALTH_CHECK_RESPONSE,
                Map.of("status", "healthy"),
                Instant.now(),
                "different-id"
        )));
        SpawnConfig config = new SpawnConfig(5.0D, 10.0D, 1.0D);
        SpawnedProcessHandle handle = new SpawnedProcessHandle("proc-health", process, config);

        boolean result = handle.performHealthCheck().toCompletableFuture().join();

        assertTrue(result);
        assertTrue(handle.isHealthy());
        assertEquals(SpawnMessageType.HEALTH_CHECK, process.writtenMessages().get(0).getType());
    }

    @Test
    void healthFailuresFireUnhealthyCallbackOnceAtThreshold() {
        FakeProcess process = new FakeProcess("");
        AtomicInteger unhealthyCalls = new AtomicInteger();
        SpawnedProcessHandle handle = new SpawnedProcessHandle(
                "proc-unhealthy",
                process,
                new SpawnConfig(5.0D, 10.0D, 1.0D),
                unhealthyCalls::incrementAndGet,
                2
        );

        assertFalse(handle.performHealthCheck().toCompletableFuture().join());
        assertFalse(handle.performHealthCheck().toCompletableFuture().join());
        assertFalse(handle.performHealthCheck().toCompletableFuture().join());

        assertEquals(1, unhealthyCalls.get());
        assertFalse(handle.isHealthy());
    }

    @Test
    void shutdownSendsShutdownMessageAndReturnsTrueAfterAckAndProcessExit() {
        FakeProcess process = new FakeProcess(protocolLine(new SpawnMessage(
                SpawnMessageType.SHUTDOWN_ACK,
                Map.of("status", "acknowledged"),
                Instant.now(),
                "ack-1"
        )));
        process.setCompleteWhenWaited(true);
        SpawnedProcessHandle handle = new SpawnedProcessHandle("proc-shutdown", process);

        boolean graceful = handle.shutdown(1.0D).toCompletableFuture().join();

        assertTrue(graceful);
        assertFalse(process.destroyed);
        assertEquals(SpawnMessageType.SHUTDOWN, process.writtenMessages().get(0).getType());
        assertEquals(Map.of("reason", "parent_initiated"), process.writtenMessages().get(0).getPayload());
        assertFalse(handle.isAlive());
    }

    @Test
    void shutdownFallsBackToTerminateWhenAckIsMissing() {
        FakeProcess process = new FakeProcess("");
        process.setCompleteOnDestroy(true);
        SpawnedProcessHandle handle = new SpawnedProcessHandle("proc-timeout", process);

        boolean graceful = handle.shutdown(1.0D).toCompletableFuture().join();

        assertFalse(graceful);
        assertTrue(process.destroyed);
        assertFalse(process.destroyForciblyCalled);
    }

    @Test
    void forceKillDestroysProcessForciblyAndWaits() {
        FakeProcess process = new FakeProcess("");
        SpawnedProcessHandle handle = new SpawnedProcessHandle("proc-kill", process);

        handle.forceKill().toCompletableFuture().join();

        assertTrue(process.destroyForciblyCalled);
        assertFalse(handle.isAlive());
    }

    @Test
    void waitForCompletionClosesStdinAndReturnsExitCode() {
        FakeProcess process = new FakeProcess("");
        process.setCompleteWhenWaited(true);
        SpawnedProcessHandle handle = new SpawnedProcessHandle("proc-wait", process);

        int exitCode = handle.waitForCompletion().toCompletableFuture().join();

        assertEquals(0, exitCode);
        assertTrue(process.stdinClosed);
        assertFalse(handle.isAlive());
    }

    @Test
    void spawnProcessLaunchesChildWithLoggingEnvAndSendsInitialInput() {
        FakeProcess process = new FakeProcess("");
        AtomicReference<List<String>> commandRef = new AtomicReference<>();
        AtomicReference<Map<String, String>> envRef = new AtomicReference<>();
        SpawnProcesses.setProcessLauncherForTesting((command, environment) -> {
            commandRef.set(new ArrayList<>(command));
            envRef.set(new LinkedHashMap<>(environment));
            return process;
        });
        ClassAgentSpawnConfig agentConfig = new ClassAgentSpawnConfig("tests.mock_agents", "MockSimpleAgent");
        agentConfig.setLoggingConfig(Map.of("backend", "default"));

        SpawnedProcessHandle handle = SpawnProcesses.spawnProcess(
                agentConfig,
                Map.of("query", "hello"),
                new SpawnConfig()
        ).toCompletableFuture().join();

        assertNotNull(handle.getProcessId());
        assertTrue(commandRef.get().contains(SpawnChildProcess.class.getName()));
        assertEquals("{\"backend\":\"default\"}", envRef.get().get(SpawnProcesses.LOGGING_CONFIG_ENV));
        SpawnMessage init = process.writtenMessages().get(0);
        assertEquals(SpawnMessageType.INPUT, init.getType());
        Map<?, ?> payload = assertInstanceOf(Map.class, init.getPayload());
        assertEquals(Map.of("query", "hello"), payload.get("inputs"));
        Map<?, ?> sentAgentConfig = assertInstanceOf(Map.class, payload.get("agent_config"));
        assertEquals("class_agent", sentAgentConfig.get("agent_kind"));
        assertEquals("tests.mock_agents", sentAgentConfig.get("agent_module"));
    }

    private static String protocolLine(SpawnMessage message) {
        return new String(SpawnMessage.serializeMessage(message), StandardCharsets.UTF_8) + "\n";
    }

    private static final class FakeProcess extends Process {
        private final ByteArrayInputStream stdout;
        private final RecordingOutputStream stdin = new RecordingOutputStream();
        private final CountDownLatch completed = new CountDownLatch(1);
        private volatile boolean alive = true;
        private volatile int exitCode;
        private volatile boolean completeWhenWaited;
        private volatile boolean completeOnDestroy;
        private volatile boolean destroyed;
        private volatile boolean destroyForciblyCalled;
        private volatile boolean stdinClosed;

        private FakeProcess(String stdoutText) {
            this.stdout = new ByteArrayInputStream(stdoutText.getBytes(StandardCharsets.UTF_8));
        }

        void setCompleteWhenWaited(boolean completeWhenWaited) {
            this.completeWhenWaited = completeWhenWaited;
        }

        void setCompleteOnDestroy(boolean completeOnDestroy) {
            this.completeOnDestroy = completeOnDestroy;
        }

        void complete(int exitCode) {
            this.exitCode = exitCode;
            this.alive = false;
            completed.countDown();
        }

        List<SpawnMessage> writtenMessages() {
            return stdin.lines().stream()
                    .filter(line -> !line.isBlank())
                    .map(line -> SpawnMessage.deserializeMessage(line.getBytes(StandardCharsets.UTF_8)))
                    .toList();
        }

        @Override
        public OutputStream getOutputStream() {
            return stdin;
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() throws InterruptedException {
            if (completeWhenWaited && alive) {
                complete(0);
            }
            completed.await();
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            if (completeWhenWaited && alive) {
                complete(0);
            }
            return completed.await(timeout, unit);
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException("process has not exited");
            }
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyed = true;
            if (completeOnDestroy) {
                complete(143);
            }
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCalled = true;
            complete(137);
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public long pid() {
            return 4242L;
        }

        private final class RecordingOutputStream extends ByteArrayOutputStream {
            @Override
            public void close() {
                stdinClosed = true;
            }

            List<String> lines() {
                return toString(StandardCharsets.UTF_8).lines().toList();
            }
        }
    }
}
