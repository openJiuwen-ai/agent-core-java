/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.schema.events.EventMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PyZmqMessagerCompatibilityTest {
    private static final String LOOPBACK_WILDCARD_ENDPOINT = "tcp://127.0.0.1:*";

    private final List<PyZmqMessager> liveMessagers = new ArrayList<>();

    private final List<Process> liveProcesses = new ArrayList<>();

    @AfterEach
    void tearDown() {
        List<RuntimeException> cleanupFailures = new ArrayList<>();
        for (PyZmqMessager messager : liveMessagers) {
            stopMessager(messager, cleanupFailures);
        }
        for (Process process : liveProcesses) {
            stopProcess(process, cleanupFailures);
        }
        if (!cleanupFailures.isEmpty()) {
            IllegalStateException cleanupFailure = new IllegalStateException(
                    "Failed to clean up PyZmq compatibility test", cleanupFailures.get(0));
            for (int index = 1; index < cleanupFailures.size(); index++) {
                cleanupFailure.addSuppressed(cleanupFailures.get(index));
            }
            throw cleanupFailure;
        }
    }

    private static void stopMessager(PyZmqMessager messager, List<RuntimeException> cleanupFailures) {
        try {
            messager.stop().orTimeout(5L, TimeUnit.SECONDS).join();
        } catch (java.util.concurrent.CompletionException | IllegalStateException exception) {
            cleanupFailures.add(exception);
        }
    }

    private static void stopProcess(Process process, List<RuntimeException> cleanupFailures) {
        if (!process.isAlive()) {
            return;
        }
        process.destroyForcibly();
        try {
            process.onExit().orTimeout(5L, TimeUnit.SECONDS).join();
        } catch (java.util.concurrent.CompletionException | IllegalStateException exception) {
            cleanupFailures.add(exception);
        }
    }

    @Test
    void directMessagesShouldRoundTripOverTcpBetweenIndependentComponents() throws InterruptedException {
        PyZmqMessager leader = startDirectMessager("leader");
        PyZmqMessager worker = startDirectMessager("worker");
        connectPeers(leader, worker);
        CountDownLatch responseLatch = new CountDownLatch(1);
        AtomicReference<EventMessage> response = new AtomicReference<>();
        leader.registerDirectMessageHandler(message -> {
            response.set(message);
            responseLatch.countDown();
            return CompletableFuture.completedFuture(null);
        }).join();
        worker.registerDirectMessageHandler(message -> worker.send(message.getSenderId(), EventMessage.builder()
                .eventType("pong")
                .payload(Map.of("request", message.getPayload().get("value")))
                .build())).join();

        leader.send("worker", EventMessage.builder().eventType("ping").payload(Map.of("value", 7)).build()).join();

        assertThat(responseLatch.await(5L, TimeUnit.SECONDS)).isTrue();
        assertThat(response.get().getEventType()).isEqualTo("pong");
        assertThat(response.get().getPayload()).containsEntry("request", 7);
        assertThat(response.get().getSenderId()).isEqualTo("worker");
    }

    @Test
    void sendAndWaitShouldCompleteAfterTemporaryReplyHandlerCleanup() {
        PyZmqMessager requester = startDirectMessager("requester");
        PyZmqMessager responder = startDirectMessager("responder");
        connectPeers(requester, responder);
        responder.registerDirectMessageHandler(message -> {
            String replyTo = String.valueOf(message.getPayload().get("reply_to"));
            return responder.send(replyTo, EventMessage.builder()
                    .eventType("response")
                    .payload(Map.of("result", message.getPayload().get("value")))
                    .build());
        }).join();

        Map<String, Object> response = requester.sendAndWait(
                "responder", Map.of("value", 42), Duration.ofSeconds(5L)).join();

        assertThat(response).containsEntry("result", 42);
    }

    @Test
    void topicMessagesShouldFlowThroughJeroMqProxy() throws IOException, InterruptedException {
        String publishEndpoint = reserveLoopbackEndpoint();
        String subscribeEndpoint = reserveLoopbackEndpoint();
        PyZmqMessager broker = track(new PyZmqMessager(pubSubConfig(
                "broker", publishEndpoint, subscribeEndpoint, true)));
        PyZmqMessager subscriber = track(new PyZmqMessager(pubSubConfig(
                "subscriber", publishEndpoint, subscribeEndpoint, false)));
        broker.start().join();
        subscriber.start().join();
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<EventMessage> received = new AtomicReference<>();
        subscriber.subscribe("team:test:events", message -> {
            received.set(message);
            delivered.countDown();
            return CompletableFuture.completedFuture(null);
        }).join();

        publishUntilDelivered(broker, delivered);

        assertThat(received.get().getEventType()).isEqualTo("task_completed");
        assertThat(received.get().getSenderId()).isEqualTo("broker");
    }

    @Test
    void directTransportShouldRestartWithoutReusingClosedSockets() throws InterruptedException {
        PyZmqMessager receiver = startDirectMessager("restart-receiver");
        receiver.stop().join();
        receiver.start().join();
        PyZmqMessager sender = startDirectMessager("restart-sender");
        connectPeers(sender, receiver);
        CountDownLatch delivered = new CountDownLatch(1);
        receiver.registerDirectMessageHandler(message -> {
            delivered.countDown();
            return CompletableFuture.completedFuture(null);
        }).join();

        sender.send("restart-receiver", EventMessage.builder().eventType("after_restart").build()).join();

        assertThat(delivered.await(5L, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void directMessagesShouldCrossSeparateJvmBoundary() throws IOException, InterruptedException {
        PyZmqMessager parent = startDirectMessager("parent");
        String childEndpoint = reserveLoopbackEndpoint();
        parent.registerPeer(peer("child", childEndpoint));
        CountDownLatch responseLatch = new CountDownLatch(1);
        AtomicReference<EventMessage> response = new AtomicReference<>();
        parent.registerDirectMessageHandler(message -> {
            response.set(message);
            responseLatch.countDown();
            return CompletableFuture.completedFuture(null);
        }).join();
        Process child = startChildProcess(parent.getLocalPeer().getAddrs().get(0), childEndpoint);

        parent.send("child", EventMessage.builder().eventType("ping").payload(Map.of("origin", "parent")).build())
                .join();

        assertThat(responseLatch.await(10L, TimeUnit.SECONDS)).isTrue();
        assertThat(response.get().getEventType()).isEqualTo("pong");
        assertThat(response.get().getPayload()).containsEntry("origin", "parent");
        assertThat(child.waitFor(10L, TimeUnit.SECONDS)).isTrue();
        assertThat(child.exitValue()).isZero();
    }

    private PyZmqMessager startDirectMessager(String nodeId) {
        PyZmqMessager messager = track(new PyZmqMessager(MessagerTransportConfig.builder()
                .backend("pyzmq")
                .nodeId(nodeId)
                .directAddr(LOOPBACK_WILDCARD_ENDPOINT)
                .requestTimeout(5.0d)
                .build()));
        messager.start().join();
        return messager;
    }

    private PyZmqMessager track(PyZmqMessager messager) {
        liveMessagers.add(messager);
        return messager;
    }

    private void connectPeers(PyZmqMessager first, PyZmqMessager second) {
        first.registerPeer(second.getLocalPeer());
        second.registerPeer(first.getLocalPeer());
    }

    private MessagerTransportConfig pubSubConfig(String nodeId, String publishEndpoint, String subscribeEndpoint,
            boolean isBroker) {
        return MessagerTransportConfig.builder()
                .backend("pyzmq")
                .nodeId(nodeId)
                .pubsubPublishAddr(publishEndpoint)
                .pubsubSubscribeAddr(subscribeEndpoint)
                .metadata(Map.of("pubsub_bind", isBroker))
                .build();
    }

    private void publishUntilDelivered(PyZmqMessager broker, CountDownLatch delivered) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (delivered.getCount() > 0L && System.nanoTime() < deadline) {
            broker.publish("team:test:events", EventMessage.builder().eventType("task_completed").build()).join();
            delivered.await(25L, TimeUnit.MILLISECONDS);
        }
        assertThat(delivered.getCount()).isZero();
    }

    private Process startChildProcess(String parentEndpoint, String childEndpoint) throws IOException {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        ProcessBuilder builder = new ProcessBuilder(List.of(
                javaExecutable,
                "-cp",
                classpath,
                DirectTransportChild.class.getName(),
                parentEndpoint,
                childEndpoint));
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        liveProcesses.add(process);
        return process;
    }

    private static MessagerPeerConfig peer(String agentId, String endpoint) {
        return MessagerPeerConfig.builder().agentId(agentId).addrs(List.of(endpoint)).build();
    }

    private static String reserveLoopbackEndpoint() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return "tcp://127.0.0.1:" + socket.getLocalPort();
        }
    }

    /**
     * Test child that proves the transport works outside the parent JVM.
     *
     * @since 0.1.15
     */
    public static final class DirectTransportChild {
        private DirectTransportChild() {
        }

        /**
         * Starts a transport in an independent JVM and responds to one parent request.
         *
         * @param args parent and child transport endpoints
         * @throws InterruptedException if the child is interrupted while awaiting the request
         * @since 0.1.15
         */
        public static void main(String[] args) throws InterruptedException {
            if (args.length != 2) {
                throw new IllegalArgumentException("Parent and child endpoints are required");
            }
            MessagerTransportConfig config = MessagerTransportConfig.builder()
                    .backend("pyzmq")
                    .nodeId("child")
                    .directAddr(args[1])
                    .knownPeers(List.of(peer("parent", args[0])))
                    .requestTimeout(5.0d)
                    .build();
            PyZmqMessager child = new PyZmqMessager(config);
            CountDownLatch completed = new CountDownLatch(1);
            child.start().join();
            try {
                child.registerDirectMessageHandler(message -> child.send("parent", EventMessage.builder()
                        .eventType("pong")
                        .payload(Map.of("origin", message.getPayload().get("origin")))
                        .build()).whenComplete((ignored, throwable) -> completed.countDown())).join();
                if (!completed.await(15L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Child did not receive a direct transport message");
                }
            } finally {
                child.stop().join();
            }
        }
    }
}
