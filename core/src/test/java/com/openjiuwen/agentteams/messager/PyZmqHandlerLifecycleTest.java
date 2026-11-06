/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import com.openjiuwen.agentteams.schema.events.EventMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks socket responsiveness, handler draining, and explicit restart after shutdown.
 *
 * @since 2026-11-06
 */
@Timeout(20)
class PyZmqHandlerLifecycleTest {
    private final List<PyZmqMessager> transports = new ArrayList<>();

    @AfterEach
    void stopTransports() {
        transports.forEach(transport -> transport.stop().orTimeout(10, TimeUnit.SECONDS).join());
    }

    @Test
    void blockingHandlerCannotDelayTransportAcknowledgement() throws Exception {
        PyZmqMessager sender = transport("sender", 1.0);
        PyZmqMessager receiver = transport("receiver", 1.0);
        sender.registerPeer(receiver.getLocalPeer());
        CountDownLatch entered = new CountDownLatch(1);
        CompletableFuture<Void> release = new CompletableFuture<>();
        AtomicReference<String> thread = new AtomicReference<>();
        receiver.registerDirectMessageHandler(message -> {
            thread.set(Thread.currentThread().getName());
            entered.countDown();
            release.join();
            return CompletableFuture.completedFuture(null);
        }).join();
        try {
            sender.send("receiver", event()).get(2, TimeUnit.SECONDS);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(thread.get()).startsWith("agent-team-handlers-");
            assertThat(release).isNotDone();
        } finally {
            release.complete(null);
        }
    }

    @Test
    void stopDrainsAsynchronousHandlerAndRejectsLateImplicitRestart() throws Exception {
        PyZmqMessager sender = transport("sender", 2.0);
        PyZmqMessager receiver = transport("receiver", 2.0);
        sender.registerPeer(receiver.getLocalPeer());
        CountDownLatch entered = new CountDownLatch(1);
        CompletableFuture<Void> response = new CompletableFuture<>();
        receiver.registerDirectMessageHandler(message -> {
            entered.countDown();
            return response;
        }).join();
        sender.send("receiver", event()).get(2, TimeUnit.SECONDS);
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> stopped = receiver.stop();
        assertThat(stopped).isNotDone();
        response.complete(null);
        stopped.get(3, TimeUnit.SECONDS);
        assertThatThrownBy(() -> receiver.send("sender", event()).join())
                .hasRootCauseMessage("PyZmq transport stopped; call start() explicitly");
        receiver.start().get(3, TimeUnit.SECONDS);
        CountDownLatch restarted = new CountDownLatch(1);
        receiver.registerDirectMessageHandler(message -> {
            restarted.countDown();
            return CompletableFuture.completedFuture(null);
        }).join();
        sender.registerPeer(receiver.getLocalPeer());
        sender.send("receiver", event()).get(2, TimeUnit.SECONDS);
        assertThat(restarted.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void stopCancelsHandlerFutureThatNeverCompletes() throws Exception {
        PyZmqMessager sender = transport("sender", 2.0);
        PyZmqMessager receiver = transport("receiver", 0.1);
        sender.registerPeer(receiver.getLocalPeer());
        CountDownLatch entered = new CountDownLatch(1);
        CompletableFuture<Void> response = new CompletableFuture<>();
        receiver.registerDirectMessageHandler(message -> {
            entered.countDown();
            return response;
        }).join();
        sender.send("receiver", event()).get(2, TimeUnit.SECONDS);
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        receiver.stop().get(3, TimeUnit.SECONDS);

        assertThat(response).isCancelled();
    }

    @Test
    void acknowledgementTimeoutIdentifiesPhaseAndNodes() {
        PyZmqMessager sender = transport("sender", 0.1);
        PyZmqMessager stoppedPeer = transport("offline", 0.1);
        sender.registerPeer(stoppedPeer.getLocalPeer());
        stoppedPeer.stop().join();

        assertThatThrownBy(() -> sender.send("offline", event()).join())
                .hasRootCauseInstanceOf(java.util.concurrent.TimeoutException.class)
                .hasStackTraceContaining("phase=ack, message=")
                .hasStackTraceContaining("from=sender, to=offline");
    }

    @Test
    void stopReleasesEndpointWithoutWaitingAnotherBudgetForUncooperativeHandler() throws Exception {
        PyZmqMessager sender = transport("sender", 2.0);
        PyZmqMessager receiver = transport("receiver", 0.1);
        sender.registerPeer(receiver.getLocalPeer());
        CountDownLatch entered = new CountDownLatch(1);
        CompletableFuture<Void> release = new CompletableFuture<>();
        CompletableFuture<Void> response = new CompletableFuture<>();
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        response.whenComplete((ignored, failure) -> cancelled.complete(null));
        receiver.registerDirectMessageHandler(message -> {
            entered.countDown();
            release.join();
            return response;
        }).join();
        try {
            sender.send("receiver", event()).get(2, TimeUnit.SECONDS);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            receiver.stop().get(3, TimeUnit.SECONDS);
            PyZmqMessager replacement = new PyZmqMessager(MessagerTransportConfig.builder()
                    .nodeId("replacement").directAddr(receiver.getLocalPeer().getAddrs().get(0)).build());
            transports.add(replacement);
            replacement.start().get(3, TimeUnit.SECONDS);
            assertThat(release).isNotDone();
        } finally {
            release.complete(null);
        }
        cancelled.get(3, TimeUnit.SECONDS);
        assertThat(response).isCancelled();
    }

    private PyZmqMessager transport(String nodeId, double timeout) {
        PyZmqMessager transport = new PyZmqMessager(MessagerTransportConfig.builder()
                .nodeId(nodeId).directAddr("tcp://127.0.0.1:*").requestTimeout(timeout).build());
        transports.add(transport);
        transport.start().join();
        return transport;
    }

    private static EventMessage event() {
        return EventMessage.builder().eventType("ping").build();
    }
}
