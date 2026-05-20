package com.openjiuwen.core.runner.spawn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SpawnProcessCompatibilityTest {

    @TempDir
    Path tempDir;

    @Test
    void messageProtocolShouldRoundTripJsonLineMessagesAndSkipLogs() throws Exception {
        Path file = tempDir.resolve("messages.jsonl");
        Files.writeString(file, "plain log line\n"
                + "{\"type\":\"DONE\",\"payload\":{\"ok\":true},\"timestamp\":\"2026-01-01T00:00:00Z\","
                + "\"message_id\":\"m1\"}\n");

        Message message = MessageProtocol.deserializeMessageFromStream(Files.newBufferedReader(file));

        assertThat(message.getType()).isEqualTo(MessageType.DONE);
        assertThat(message.getMessageId()).isEqualTo("m1");
        assertThat(message.getPayload()).isInstanceOf(java.util.Map.class);
    }

    @Test
    void spawnedProcessHandleShouldShutdownWithAckLikePythonHandle() throws Exception {
        SpawnedProcessHandle handle = new SpawnedProcessHandle(
                "ack-child",
                startJavaFixture("ack"),
                SpawnConfig.builder().shutdownTimeout(1.0).build()
        );

        boolean graceful = handle.shutdown(1.0);

        assertThat(graceful).isTrue();
        assertThat(handle.isAlive()).isFalse();
    }

    @Test
    void spawnedProcessHandleShouldKeepStderrSeparateFromProtocolStdout() throws Exception {
        SpawnedProcessHandle handle = new SpawnedProcessHandle(
                "stderr-child",
                startJavaFixture("stderr"),
                SpawnConfig.builder().shutdownTimeout(1.0).build()
        );

        Message done = handle.receiveMessage();

        assertThat(done.getType()).isEqualTo(MessageType.DONE);
        assertThat(done.getPayload()).asString().contains("ok");
        assertThat(handle.readStderrLine()).isEqualTo("diagnostic on stderr");
        assertThat(handle.shutdown(1.0)).isTrue();
    }

    @Test
    void spawnedProcessHealthCheckShouldFireUnhealthyOnceAfterFailures() throws Exception {
        SpawnedProcessHandle handle = new SpawnedProcessHandle(
                "silent-child",
                startJavaFixture("silent"),
                SpawnConfig.builder().healthCheckInterval(0.05).healthCheckTimeout(0.05).build()
        );
        handle.setMaxHealthFailures(1);
        AtomicInteger callbacks = new AtomicInteger();
        handle.setOnUnhealthy(callbacks::incrementAndGet);

        handle.startHealthCheck();
        waitForCallback(callbacks);
        Thread.sleep(120L);

        assertThat(callbacks.get()).isEqualTo(1);
        assertThat(handle.isHealthy()).isFalse();

        handle.forceKill();
    }

    @Test
    void runnerSpawnAgentShouldLaunchChildProcessAndReturnDoneMessage() {
        SpawnedProcessHandle handle = com.openjiuwen.core.runner.Runner.spawnAgent(
                new ClassAgentSpawnConfig(
                        "",
                        EchoAgent.class.getName(),
                        java.util.Map.of()
                ),
                java.util.Map.of("query", "hello child"),
                null,
                null
        );

        Message done = handle.receiveMessage();
        int exitCode = handle.waitForCompletion();

        assertThat(done.getType()).isEqualTo(MessageType.DONE);
        assertThat(done.getPayload()).asString().contains("hello child");
        assertThat(exitCode).isZero();
    }

    @Test
    void childProcessShouldHandleMultipleInputMessagesBeforeShutdown() {
        SpawnedProcessHandle handle = com.openjiuwen.core.runner.Runner.spawnAgent(
                new ClassAgentSpawnConfig(
                        "",
                        EchoAgent.class.getName(),
                        java.util.Map.of()
                ),
                java.util.Map.of("query", "first"),
                null,
                null
        );

        Message first = handle.receiveMessage();
        handle.sendMessage(Message.builder()
                .type(MessageType.INPUT)
                .payload(java.util.Map.of(
                        "agent_config", java.util.Map.of(
                                "agent_kind", "class_agent",
                                "agent_class", EchoAgent.class.getName()),
                        "inputs", java.util.Map.of("query", "second")))
                .build());
        Message second = handle.receiveMessage();

        assertThat(first.getType()).isEqualTo(MessageType.DONE);
        assertThat(first.getPayload()).asString().contains("first");
        assertThat(second.getType()).isEqualTo(MessageType.DONE);
        assertThat(second.getPayload()).asString().contains("second");
        assertThat(handle.shutdown(1.0)).isTrue();
    }

    @Test
    void childProcessShouldEmitStreamChunksBeforeDoneForStreamingInput() {
        SpawnedProcessHandle handle = com.openjiuwen.core.runner.Runner.spawnAgent(
                new ClassAgentSpawnConfig(
                        "",
                        StreamingAgent.class.getName(),
                        java.util.Map.of()
                ),
                java.util.Map.of("query", "first"),
                null,
                null
        );
        assertThat(handle.receiveMessage().getType()).isEqualTo(MessageType.DONE);

        handle.sendMessage(Message.builder()
                .type(MessageType.INPUT)
                .payload(java.util.Map.of(
                        "agent_config", java.util.Map.of(
                                "agent_kind", "class_agent",
                                "agent_class", StreamingAgent.class.getName()),
                        "inputs", java.util.Map.of("query", "streamed"),
                        "streaming", true))
                .build());

        Message firstChunk = handle.receiveMessage();
        Message secondChunk = handle.receiveMessage();
        Message done = handle.receiveMessage();

        assertThat(firstChunk.getType()).isEqualTo(MessageType.STREAM_CHUNK);
        assertThat(firstChunk.getPayload()).asString().contains("streamed-1");
        assertThat(secondChunk.getType()).isEqualTo(MessageType.STREAM_CHUNK);
        assertThat(secondChunk.getPayload()).asString().contains("streamed-2");
        assertThat(done.getType()).isEqualTo(MessageType.DONE);
        assertThat(done.getPayload()).asString().contains("streamed-1").contains("streamed-2");
        assertThat(handle.shutdown(1.0)).isTrue();
    }

    @Test
    void childProcessShouldStreamTeamAgentInputsLikePythonTeamAgentStreaming() {
        SpawnedProcessHandle handle = com.openjiuwen.core.runner.Runner.spawnAgent(
                SpawnAgentConfig.builder()
                        .agentKind(SpawnAgentKind.TEAM_AGENT)
                        .payload(java.util.Map.of(
                                "spec", java.util.Map.of(
                                        "name", "stream-team",
                                        "members", java.util.List.of(java.util.Map.of(
                                                "name", "leader",
                                                "role", "leader"))),
                                "context", java.util.Map.of(
                                        "team_id", "stream-team",
                                        "member_name", "leader",
                                        "role", "leader",
                                        "metadata", java.util.Map.of())))
                        .build(),
                java.util.Map.of("query", "initial"),
                null,
                null
        );
        assertThat(handle.receiveMessage().getType()).isEqualTo(MessageType.DONE);

        handle.sendMessage(Message.builder()
                .type(MessageType.INPUT)
                .payload(java.util.Map.of(
                        "agent_config", java.util.Map.of(
                                "agent_kind", "team_agent",
                                "payload", java.util.Map.of(
                                        "spec", java.util.Map.of(
                                                "name", "stream-team",
                                                "members", java.util.List.of(java.util.Map.of(
                                                        "name", "leader",
                                                        "role", "leader"))),
                                        "context", java.util.Map.of(
                                                "team_id", "stream-team",
                                                "member_name", "leader",
                                                "role", "leader",
                                                "metadata", java.util.Map.of()))),
                        "inputs", java.util.Map.of("query", "stream this"),
                        "streaming", true))
                .build());

        Message chunk = handle.receiveMessage();
        Message done = chunk;
        while (done.getType() == MessageType.STREAM_CHUNK) {
            done = handle.receiveMessage();
        }

        assertThat(chunk.getType()).isEqualTo(MessageType.STREAM_CHUNK);
        assertThat(chunk.getPayload()).asString()
                .contains("controller_output")
                .contains("model_client_config is required");
        assertThat(done.getType()).isEqualTo(MessageType.DONE);
        assertThat(done.getPayload()).asString()
                .contains("controller_output")
                .contains("model_client_config is required");
        assertThat(handle.shutdown(1.0)).isTrue();
    }

    @Test
    void runnerSpawnAgentShouldStartHealthCheckWhenSpawnConfigIsProvided() {
        SpawnedProcessHandle handle = com.openjiuwen.core.runner.Runner.spawnAgent(
                new ClassAgentSpawnConfig(
                        "",
                        LongRunningAgent.class.getName(),
                        java.util.Map.of()
                ),
                java.util.Map.of("query", "wait"),
                "spawn-session",
                SpawnConfig.builder().healthCheckInterval(0.05).healthCheckTimeout(0.2).build()
        );

        assertThat(handle.isHealthCheckRunning()).isTrue();
        assertThat(handle.isAlive()).isTrue();

        handle.shutdown(1.0);
    }

    @Test
    void childProcessShouldRespondToHealthCheckWhileAgentIsRunningLikePythonLoop() throws Exception {
        SpawnedProcessHandle handle = com.openjiuwen.core.runner.Runner.spawnAgent(
                new ClassAgentSpawnConfig(
                        "",
                        LongRunningAgent.class.getName(),
                        java.util.Map.of()
                ),
                java.util.Map.of("query", "wait"),
                "spawn-session",
                SpawnConfig.builder().healthCheckInterval(0.05).healthCheckTimeout(0.2).build()
        );
        handle.setMaxHealthFailures(1);
        AtomicInteger callbacks = new AtomicInteger();
        handle.setOnUnhealthy(callbacks::incrementAndGet);
        handle.startHealthCheck(0.05);

        waitUntilHealthy(handle);

        assertThat(handle.isAlive()).isTrue();
        assertThat(handle.isHealthy()).isTrue();
        assertThat(callbacks.get()).isZero();
        assertThat(handle.shutdown(1.0)).isTrue();
    }

    @Test
    void childProcessShouldRedirectPlainStdoutAwayFromProtocolStreamLikePythonSpawnedProcess() throws Exception {
        SpawnedProcessHandle handle = com.openjiuwen.core.runner.Runner.spawnAgent(
                new ClassAgentSpawnConfig(
                        "",
                        NoisyStdoutAgent.class.getName(),
                        java.util.Map.of()
                ),
                java.util.Map.of("query", "noise"),
                null,
                null
        );

        Message done = handle.receiveMessage();

        assertThat(done.getType()).isEqualTo(MessageType.DONE);
        assertThat(done.getPayload()).asString().contains("noise");
        assertThat(readStderrUntil(handle, "plain stdout noise")).contains("plain stdout noise");
        assertThat(handle.waitForCompletion()).isZero();
    }

    @Test
    void spawnProcessShouldPassLoggingConfigAsJsonEnvLikePythonProcessManager() {
        SpawnAgentConfig config = new ClassAgentSpawnConfig(
                "",
                EchoAgent.class.getName(),
                java.util.Map.of()
        );
        config.setLoggingConfig(java.util.Map.of("member_name", "worker-1", "level", "INFO"));

        SpawnedProcessHandle handle = SpawnProcesses.spawnProcess(
                config.toPayload(),
                java.util.Map.of("query", "json-env"),
                null
        );
        Message done = handle.receiveMessage();

        assertThat(done.getType()).isEqualTo(MessageType.DONE);
        assertThat(done.getPayload()).asString().contains("json-env");
        assertThat(handle.waitForCompletion()).isZero();
    }

    @Test
    void healthCheckShouldNotConsumeNonHealthOutputLikePythonHandleWaitLoop() throws Exception {
        SpawnedProcessHandle handle = new SpawnedProcessHandle(
                "done-then-health",
                startJavaFixture("done-then-health"),
                SpawnConfig.builder().healthCheckTimeout(0.05).build()
        );

        Message healthCheck = Message.builder()
                .type(MessageType.HEALTH_CHECK)
                .payload(java.util.Map.of())
                .build();
        handle.sendMessage(healthCheck);
        Thread.sleep(80L);

        Message done = handle.receiveMessage();

        assertThat(done.getType()).isEqualTo(MessageType.DONE);
        assertThat(done.getPayload()).asString().contains("early");
        handle.forceKill();
    }

    private Process startJavaFixture(String mode) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        return new ProcessBuilder(List.of(
                java,
                "-cp",
                classpath,
                SpawnProcessCompatibilityTest.FixtureChild.class.getName(),
                mode
        )).start();
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

    private static String readStderrUntil(SpawnedProcessHandle handle, String needle) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        String lastLine = null;
        while (System.nanoTime() < deadline) {
            String line = handle.readStderrLine();
            if (line != null) {
                lastLine = line;
                if (line.contains(needle)) {
                    return line;
                }
            }
            Thread.sleep(10L);
        }
        return lastLine;
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
        public java.util.Map<String, Object> invoke(Object inputs) {
            return java.util.Map.of("echo", inputs);
        }
    }

    public static final class NoisyStdoutAgent {
        public java.util.Map<String, Object> invoke(Object inputs) {
            System.out.println("plain stdout noise");
            System.out.flush();
            return java.util.Map.of("echo", inputs);
        }
    }

    public static final class LongRunningAgent {
        public java.util.Map<String, Object> invoke(Object inputs) {
            try {
                Thread.sleep(2_000L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            return java.util.Map.of("done", true);
        }
    }

    public static final class StreamingAgent {
        public java.util.Map<String, Object> invoke(Object inputs) {
            return java.util.Map.of("invoke", inputs);
        }

        @SuppressWarnings("unchecked")
        public java.util.Iterator<Object> stream(java.util.Map<String, Object> inputs, com.openjiuwen.core.session.AgentSessionApi session) {
            String query = String.valueOf(inputs.get("query"));
            return java.util.List.<Object>of(
                    java.util.Map.of("chunk", query + "-1"),
                    java.util.Map.of("chunk", query + "-2")
            ).iterator();
        }
    }
}
