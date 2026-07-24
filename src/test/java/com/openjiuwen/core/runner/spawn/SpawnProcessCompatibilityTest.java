package com.openjiuwen.core.runner.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spawn process compatibility tests.
 * <p>
 * Linux-specific note: {@code ChildProcess} remaps non-protocol stdout to stderr. An unread stderr
 * PIPE (~64KiB) deadlocks the child; {@code destroyForcibly()} may then not reap it until the pipe
 * is drained, so unbounded {@code waitFor()} hangs. Windows kill semantics usually hide this. Tests
 * therefore redirect stderr to DISCARD/file and bound every blocking wait.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class SpawnProcessCompatibilityTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private final List<SpawnedProcessHandle> liveHandles = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDownLiveHandles() {
        for (SpawnedProcessHandle handle : liveHandles) {
            safeForceKill(handle);
            releaseProcessResources(handle);
        }
        liveHandles.clear();
    }

    @Test
    void messageProtocolShouldRoundTripJsonLineMessagesAndSkipLogs() throws Exception {
        Path file = tempDir.resolve("messages.jsonl");
        Files.writeString(file,
                "plain log line\n"
                        + "{\"type\":\"DONE\",\"payload\":{\"ok\":true},\"timestamp\":\"2026-01-01T00:00:00Z\","
                        + "\"message_id\":\"m1\"}\n");

        Message message = MessageProtocol.deserializeMessageFromStream(Files.newBufferedReader(file));

        assertThat(message.getType()).isEqualTo(MessageType.DONE);
        assertThat(message.getMessageId()).isEqualTo("m1");
        assertThat(message.getPayload()).isInstanceOf(Map.class);
    }

    @Test
    void spawnedProcessHandleShouldShutdownWithAckLikePythonHandle() throws Exception {
        SpawnedProcessHandle handle = track(new SpawnedProcessHandle("ack-child", startJavaFixture("ack"),
                SpawnConfig.builder().shutdownTimeout(1.0).build()));

        boolean graceful = safeShutdown(handle, 1.0);

        assertThat(graceful).isTrue();
        assertThat(handle.isAlive()).isFalse();
    }

    @Test
    void spawnedProcessHandleShouldKeepStderrSeparateFromProtocolStdout() throws Exception {
        // Keep redirect target outside @TempDir: Windows holds the file lock until the child exits
        // and Process streams are closed, which otherwise fails JUnit temp-dir cleanup.
        Path stderrLog = Files.createTempFile("fixture-stderr-", ".log");
        try {
            SpawnedProcessHandle handle = track(new SpawnedProcessHandle("stderr-child",
                    startJavaFixture("stderr", stderrLog), SpawnConfig.builder().shutdownTimeout(1.0).build()));

            Message done = receiveMessage(handle, 15);

            assertThat(done.getType()).isEqualTo(MessageType.DONE);
            assertThat(done.getPayload()).asString().contains("ok");
            assertThat(readFileUntil(stderrLog, "diagnostic on stderr")).contains("diagnostic on stderr");
            assertThat(safeShutdown(handle, 1.0)).isTrue();
            releaseProcessResources(handle);
        } finally {
            deleteQuietly(stderrLog);
        }
    }

    @Test
    void spawnedProcessHealthCheckShouldFireUnhealthyOnceAfterFailures() throws Exception {
        SpawnedProcessHandle handle = track(new SpawnedProcessHandle("silent-child", startJavaFixture("silent"),
                SpawnConfig.builder().healthCheckInterval(0.05).healthCheckTimeout(0.05).build()));
        handle.setMaxHealthFailures(1);
        AtomicInteger callbacks = new AtomicInteger();
        handle.setOnUnhealthy(callbacks::incrementAndGet);

        handle.startHealthCheck();
        waitForCallback(callbacks);
        Thread.sleep(120L);

        assertThat(callbacks.get()).isEqualTo(1);
        assertThat(handle.isHealthy()).isFalse();

        safeForceKill(handle);
    }

    @Test
    void runnerSpawnAgentShouldLaunchChildProcessAndReturnDoneMessage() {
        SpawnedProcessHandle handle = spawnAgentForTest(
                new ClassAgentSpawnConfig("", EchoAgent.class.getName(), Map.of()), Map.of("query", "hello child"),
                null, null, null);

        Message done = receiveMessage(handle, 45);
        int exitCode = waitForCompletion(handle, 15);

        assertThat(done.getType()).isEqualTo(MessageType.DONE);
        assertThat(done.getPayload()).asString().contains("hello child");
        assertThat(exitCode).isZero();
    }

    @Test
    void childProcessShouldHandleMultipleInputMessagesBeforeShutdown() throws Exception {
        SpawnedProcessHandle handle = spawnAgentForTest(
                new ClassAgentSpawnConfig("", EchoAgent.class.getName(), Map.of()), Map.of("query", "first"), null, null,
                null);

        Message first = receiveMessage(handle, 45);
        assertThat(first.getType()).isEqualTo(MessageType.DONE);
        assertThat(first.getPayload()).asString().contains("first");

        // Child accepts the next INPUT only once agentTask.isDone(). Parent can observe DONE slightly
        // before the Future completes; retry so Linux scheduling does not drop the second INPUT.
        Message second = sendInputUntilDone(handle, Message.builder().type(MessageType.INPUT)
                .payload(Map.of("agent_config",
                        Map.of("agent_kind", "class_agent", "agent_class", EchoAgent.class.getName()), "inputs",
                        Map.of("query", "second")))
                .build(), 45);

        assertThat(second.getType()).isEqualTo(MessageType.DONE);
        assertThat(second.getPayload()).asString().contains("second");
        assertThat(safeShutdown(handle, 1.0)).isTrue();
    }

    @Test
    void childProcessShouldEmitStreamChunksBeforeDoneForStreamingInput() {
        // One streaming INPUT only: avoids a second INPUT race and halves JVM work on low-resource Linux CI.
        ClassAgentSpawnConfig agentConfig = new ClassAgentSpawnConfig("", StreamingAgent.class.getName(), Map.of());
        agentConfig.setSessionId("default_session");
        SpawnedProcessHandle handle = spawnProcessForTest(agentConfig.toPayload(), Map.of("query", "streamed"), null,
                null, true);

        List<Message> chunks = new ArrayList<>();
        Message done = receiveStreamingUntilDone(handle, 20, chunks);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getPayload()).asString().contains("streamed-1");
        assertThat(chunks.get(1).getPayload()).asString().contains("streamed-2");
        assertThat(done.getType()).isEqualTo(MessageType.DONE);
        assertThat(done.getPayload()).asString().contains("streamed-1").contains("streamed-2");
        assertThat(safeShutdown(handle, 1.0)).isTrue();
    }

    @Test
    void runnerSpawnAgentShouldStartHealthCheckWhenSpawnConfigIsProvided() {
        SpawnedProcessHandle handle = spawnAgentForTest(
                new ClassAgentSpawnConfig("", LongRunningAgent.class.getName(), Map.of()), Map.of("query", "wait"),
                "spawn-session", SpawnConfig.builder().healthCheckInterval(0.05).healthCheckTimeout(0.2).build(), null);

        assertThat(handle.isHealthCheckRunning()).isTrue();
        assertThat(handle.isAlive()).isTrue();

        safeShutdown(handle, 1.0);
    }

    @Test
    void childProcessShouldRespondToHealthCheckWhileAgentIsRunningLikePythonLoop() throws Exception {
        SpawnedProcessHandle handle = spawnAgentForTest(
                new ClassAgentSpawnConfig("", LongRunningAgent.class.getName(), Map.of()), Map.of("query", "wait"),
                "spawn-session", SpawnConfig.builder().healthCheckInterval(0.05).healthCheckTimeout(0.2).build(), null);
        handle.setMaxHealthFailures(1);
        AtomicInteger callbacks = new AtomicInteger();
        handle.setOnUnhealthy(callbacks::incrementAndGet);
        handle.startHealthCheck(0.05);

        waitUntilHealthy(handle);

        assertThat(handle.isAlive()).isTrue();
        assertThat(handle.isHealthy()).isTrue();
        assertThat(callbacks.get()).isZero();
        safeShutdown(handle, 3.0);
        assertThat(handle.isAlive()).isFalse();
    }

    @Test
    void childProcessShouldRedirectPlainStdoutAwayFromProtocolStreamLikePythonSpawnedProcess() throws Exception {
        Path stderrLog = Files.createTempFile("noisy-stderr-", ".log");
        try {
            SpawnedProcessHandle handle = spawnAgentForTest(
                    new ClassAgentSpawnConfig("", NoisyStdoutAgent.class.getName(), Map.of()), Map.of("query", "noise"),
                    null, null, stderrLog);

            Message done = receiveMessage(handle, 45);

            assertThat(done.getType()).isEqualTo(MessageType.DONE);
            assertThat(done.getPayload()).asString().contains("noise");
            assertThat(readFileUntil(stderrLog, "plain stdout noise")).contains("plain stdout noise");
            assertThat(waitForCompletion(handle, 15)).isZero();
            releaseProcessResources(handle);
        } finally {
            deleteQuietly(stderrLog);
        }
    }

    @Test
    void spawnProcessShouldPassLoggingConfigAsJsonEnvLikePythonProcessManager() {
        SpawnAgentConfig config = new ClassAgentSpawnConfig("", EchoAgent.class.getName(), Map.of());
        config.setLoggingConfig(Map.of("member_name", "worker-1", "level", "INFO"));

        SpawnedProcessHandle handle = spawnProcessForTest(config.toPayload(), Map.of("query", "json-env"), null, null);
        Message done = receiveMessage(handle, 45);

        assertThat(done.getType()).isEqualTo(MessageType.DONE);
        assertThat(done.getPayload()).asString().contains("json-env");
        assertThat(waitForCompletion(handle, 15)).isZero();
    }

    @Test
    void healthCheckShouldNotConsumeNonHealthOutputLikePythonHandleWaitLoop() throws Exception {
        SpawnedProcessHandle handle = track(new SpawnedProcessHandle("done-then-health",
                startJavaFixture("done-then-health"), SpawnConfig.builder().healthCheckTimeout(0.05).build()));

        Message healthCheck = Message.builder().type(MessageType.HEALTH_CHECK).payload(Map.of()).build();
        handle.sendMessage(healthCheck);
        Thread.sleep(80L);

        Message done = receiveMessage(handle, 15);

        assertThat(done.getType()).isEqualTo(MessageType.DONE);
        assertThat(done.getPayload()).asString().contains("early");
        safeForceKill(handle);
    }

    private SpawnedProcessHandle track(SpawnedProcessHandle handle) {
        liveHandles.add(handle);
        return handle;
    }

    private Process startJavaFixture(String mode) throws Exception {
        return startJavaFixture(mode, null);
    }

    private Process startJavaFixture(String mode, Path stderrFile) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        ProcessBuilder builder = new ProcessBuilder(List.of(java, "-cp", classpath,
                SpawnProcessCompatibilityTest.FixtureChild.class.getName(), mode));
        redirectStderr(builder, stderrFile);
        return builder.start();
    }

    /**
     * Test-only spawn that mirrors {@code Runner.spawnAgent} / {@code SpawnProcesses.spawnProcess}, but redirects
     * child stderr away from an unread PIPE.
     */
    private SpawnedProcessHandle spawnAgentForTest(SpawnAgentConfig agentConfig, Map<String, Object> inputs,
            Object session, SpawnConfig spawnConfig, Path stderrFile) {
        Map<String, Object> normalizedInputs = new LinkedHashMap<>(inputs);
        String sessionId = normalizedInputs.containsKey("conversation_id")
                ? String.valueOf(normalizedInputs.get("conversation_id"))
                : (session instanceof String ? String.valueOf(session) : "default_session");
        agentConfig.setSessionId(sessionId);
        SpawnedProcessHandle handle = spawnProcessForTest(agentConfig.toPayload(), normalizedInputs, spawnConfig,
                stderrFile);
        if (spawnConfig != null) {
            handle.startHealthCheck();
        }
        return handle;
    }

    private SpawnedProcessHandle spawnProcessForTest(Map<String, Object> agentConfig, Map<String, Object> inputs,
            SpawnConfig config, Path stderrFile) {
        return spawnProcessForTest(agentConfig, inputs, config, stderrFile, false);
    }

    private SpawnedProcessHandle spawnProcessForTest(Map<String, Object> agentConfig, Map<String, Object> inputs,
            SpawnConfig config, Path stderrFile, boolean streaming) {
        String processId = UUID.randomUUID().toString();
        ProcessBuilder builder = new ProcessBuilder(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                System.getProperty("java.class.path"), ChildProcess.class.getName()));
        builder.environment().put("OPENJIUWEN_SPAWN_PROCESS", "1");
        Object loggingConfig = agentConfig != null ? agentConfig.get("logging_config") : null;
        if (loggingConfig != null) {
            try {
                builder.environment().put("OPENJIUWEN_SPAWN_LOGGING_CONFIG",
                        OBJECT_MAPPER.writeValueAsString(loggingConfig));
            } catch (Exception exception) {
                throw new IllegalArgumentException("logging_config must be JSON serializable", exception);
            }
        }
        redirectStderr(builder, stderrFile);
        try {
            Process process = builder.start();
            SpawnedProcessHandle handle = track(new SpawnedProcessHandle(processId, process, config));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("agent_config", agentConfig != null ? agentConfig : Map.of());
            payload.put("inputs", inputs != null ? inputs : Map.of());
            if (streaming) {
                payload.put("streaming", true);
            }
            handle.sendMessage(Message.builder().type(MessageType.INPUT).payload(payload).build());
            return handle;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to spawn child process for test", exception);
        }
    }

    private void redirectStderr(ProcessBuilder builder, Path stderrFile) {
        if (stderrFile != null) {
            builder.redirectError(ProcessBuilder.Redirect.appendTo(stderrFile.toFile()));
        } else {
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        }
    }

    private static Message receiveMessage(SpawnedProcessHandle handle, long timeoutSeconds) {
        return callWithTimeout(handle::receiveMessage, timeoutSeconds, "receiveMessage", handle);
    }

    /**
     * Collect STREAM_CHUNK messages until DONE within a shared deadline (weak CI friendly).
     */
    private static Message receiveStreamingUntilDone(SpawnedProcessHandle handle, long timeoutSeconds,
            List<Message> chunks) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            long remainingSeconds = Math.max(1L, TimeUnit.NANOSECONDS.toSeconds(deadline - System.nanoTime()));
            Message message = receiveMessage(handle, Math.min(5L, remainingSeconds));
            if (message.getType() == MessageType.STREAM_CHUNK) {
                chunks.add(message);
                continue;
            }
            if (message.getType() == MessageType.DONE || message.getType() == MessageType.ERROR) {
                return message;
            }
        }
        throw new AssertionError("Timed out waiting for DONE after STREAM_CHUNK messages");
    }

    /**
     * Send INPUT and wait for DONE, retrying when the child is still finishing the previous task.
     */
    private static Message sendInputUntilDone(SpawnedProcessHandle handle, Message input, long timeoutSeconds)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (handle.isAlive() && System.nanoTime() < deadline) {
            handle.sendMessage(input);
            long retryDeadline = Math.min(deadline, System.nanoTime() + TimeUnit.SECONDS.toNanos(3L));
            while (handle.isAlive() && System.nanoTime() < retryDeadline) {
                if (!handle.getStdout().ready()) {
                    Thread.sleep(10L);
                    continue;
                }
                Message message = handle.receiveMessage();
                if (message != null && (message.getType() == MessageType.DONE || message.getType() == MessageType.ERROR
                        || message.getType() == MessageType.STREAM_CHUNK)) {
                    return message;
                }
            }
        }
        throw new AssertionError("Timed out waiting for DONE after INPUT");
    }

    private static int waitForCompletion(SpawnedProcessHandle handle, long timeoutSeconds) {
        return callWithTimeout(handle::waitForCompletion, timeoutSeconds, "waitForCompletion", handle);
    }

    private static boolean safeShutdown(SpawnedProcessHandle handle, double shutdownTimeoutSeconds) {
        try {
            return callWithTimeout(() -> handle.shutdown(shutdownTimeoutSeconds),
                    Math.max(5L, (long) Math.ceil(shutdownTimeoutSeconds) + 5L), "shutdown", handle);
        } catch (AssertionError error) {
            safeForceKill(handle);
            return false;
        }
    }

    private static void safeForceKill(SpawnedProcessHandle handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.stopHealthCheck();
        } catch (RuntimeException ignored) {
            // Best-effort cleanup.
        }
        Process process = handle.getProcess();
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            try {
                process.waitFor(5L, TimeUnit.SECONDS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void releaseProcessResources(SpawnedProcessHandle handle) {
        if (handle == null || handle.getProcess() == null) {
            return;
        }
        Process process = handle.getProcess();
        closeQuietly(process.getOutputStream());
        closeQuietly(process.getInputStream());
        closeQuietly(process.getErrorStream());
        try {
            process.waitFor(2L, TimeUnit.SECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Best-effort cleanup.
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                Files.deleteIfExists(path);
                return;
            } catch (Exception ignored) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static <T> T callWithTimeout(java.util.concurrent.Callable<T> callable, long timeoutSeconds, String label,
            SpawnedProcessHandle handle) {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "spawn-test-" + label);
            thread.setDaemon(true);
            return thread;
        });
        try {
            return executor.submit(callable).get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException timeoutException) {
            safeForceKill(handle);
            throw new AssertionError("Timed out in " + label + " after " + timeoutSeconds + "s", timeoutException);
        } catch (Exception exception) {
            safeForceKill(handle);
            throw new AssertionError("Failed in " + label + ": " + exception.getMessage(), exception);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void waitForCallback(AtomicInteger callbacks) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (callbacks.get() > 0) {
                return;
            }
            Thread.sleep(10L);
        }
    }

    private static void waitUntilHealthy(SpawnedProcessHandle handle) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (handle.isAlive() && handle.isHealthy()) {
                return;
            }
            Thread.sleep(20L);
        }
    }

    private static String readFileUntil(Path file, String needle) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        String content = "";
        while (System.nanoTime() < deadline) {
            if (Files.exists(file)) {
                content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.contains(needle)) {
                    return content;
                }
            }
            Thread.sleep(10L);
        }
        return content;
    }

    public static final class FixtureChild {
        public static void main(String[] args) throws Exception {
            String mode = args.length > 0 ? args[0] : "ack";
            if ("stderr".equals(mode)) {
                System.err.println("diagnostic on stderr");
                System.err.flush();
                System.out.println("{\"type\":\"DONE\",\"payload\":{\"result\":\"ok\"},"
                        + "\"timestamp\":\"2026-01-01T00:00:00Z\",\"message_id\":\"done\"}");
                System.out.flush();
            }
            if ("done-then-health".equals(mode)) {
                System.out.println("{\"type\":\"DONE\",\"payload\":{\"result\":\"early\"},"
                        + "\"timestamp\":\"2026-01-01T00:00:00Z\",\"message_id\":\"done\"}");
                System.out.flush();
            }
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if ("silent".equals(mode)) {
                    continue;
                }
                if (line.contains("\"type\":\"SHUTDOWN\"")) {
                    System.out.println("{\"type\":\"SHUTDOWN_ACK\",\"payload\":{},"
                            + "\"timestamp\":\"2026-01-01T00:00:00Z\",\"message_id\":\"ack\"}");
                    System.out.flush();
                    return;
                }
            }
        }
    }

    public static final class EchoAgent {
        public Map<String, Object> invoke(Object inputs) {
            return Map.of("echo", inputs);
        }
    }

    public static final class NoisyStdoutAgent {
        public Map<String, Object> invoke(Object inputs) {
            System.out.println("plain stdout noise");
            System.out.flush();
            return Map.of("echo", inputs);
        }
    }

    public static final class LongRunningAgent {
        public Map<String, Object> invoke(Object inputs) {
            try {
                Thread.sleep(2_000L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            return Map.of("done", true);
        }
    }

    public static final class StreamingAgent {
        public Map<String, Object> invoke(Object inputs) {
            return Map.of("invoke", inputs);
        }

        @SuppressWarnings("unchecked")
        public java.util.Iterator<Object> stream(Map<String, Object> inputs,
                com.openjiuwen.core.session.AgentSessionApi session) {
            String query = String.valueOf(inputs.get("query"));
            return java.util.List
                    .<Object>of(Map.of("chunk", query + "-1"), Map.of("chunk", query + "-2"))
                    .iterator();
        }
    }
}
