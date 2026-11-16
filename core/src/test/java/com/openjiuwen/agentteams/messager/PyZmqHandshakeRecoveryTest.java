/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agentteams.schema.events.EventMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies recovery from an accepted TCP connection that never completes the ZeroMQ greeting.
 *
 * @since 2026-11-10
 */
@Timeout(20)
class PyZmqHandshakeRecoveryTest {
    @Test
    void stalledHandshakeReconnectsWithinTheOriginalSendBudget() throws Exception {
        PyZmqMessager receiver = transport("receiver");
        PyZmqMessager sender = transport("sender");
        try (StalledHandshakeProxy proxy = new StalledHandshakeProxy(receiver.getLocalPeer().getAddrs().get(0))) {
            AtomicInteger deliveries = new AtomicInteger();
            CompletableFuture<EventMessage> received = new CompletableFuture<>();
            receiver.registerDirectMessageHandler(message -> {
                deliveries.incrementAndGet();
                received.complete(message);
                return CompletableFuture.completedFuture(null);
            }).get(2L, TimeUnit.SECONDS);
            sender.registerPeer(MessagerPeerConfig.builder().agentId("receiver")
                    .addrs(List.of(proxy.endpoint())).build());

            sender.send("receiver", EventMessage.builder().eventType("ping").build()).get(6L, TimeUnit.SECONDS);

            assertThat(received.get(1L, TimeUnit.SECONDS).getEventType()).isEqualTo("ping");
            assertThat(proxy.connections()).as("the stalled connection must be replaced").isGreaterThanOrEqualTo(2);
            assertThat(deliveries.get()).as("reconnect must not duplicate application delivery").isEqualTo(1);
        } finally {
            try {
                sender.stop().get(5L, TimeUnit.SECONDS);
            } finally {
                receiver.stop().get(5L, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void missingAcknowledgementDoesNotReplayDeliveredMessage() throws Exception {
        PyZmqMessager sender = transport("sender");
        try (ZContext context = new ZContext()) {
            context.setLinger(0);
            ZMQ.Socket router = context.createSocket(SocketType.ROUTER);
            router.setReceiveTimeOut(3_000);
            int port = router.bindToRandomPort("tcp://127.0.0.1");
            assertThat(port).isPositive();
            sender.registerPeer(MessagerPeerConfig.builder().agentId("silent-receiver")
                    .addrs(List.of("tcp://127.0.0.1:" + port)).build());

            CompletableFuture<Void> sent = sender.send("silent-receiver",
                    EventMessage.builder().eventType("one-application-event").build());
            ZMsg delivered = ZMsg.recvMsg(router);
            try {
                assertThat(delivered).isNotNull();
            } finally {
                destroy(delivered);
            }

            assertThatThrownBy(() -> sent.get(6L, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(TimeoutException.class)
                    .hasStackTraceContaining("request_timeout_ms=5000")
                    .hasStackTraceContaining("handshake_timeout_ms=1666");
            ZMsg duplicate = ZMsg.recvMsg(router, ZMQ.DONTWAIT);
            try {
                assertThat(duplicate).as("missing ACK must not replay the application message").isNull();
            } finally {
                destroy(duplicate);
            }
        } finally {
            sender.stop().get(5L, TimeUnit.SECONDS);
        }
    }

    private static void destroy(ZMsg frames) {
        if (frames != null) {
            frames.destroy();
        }
    }

    private static PyZmqMessager transport(String node) {
        PyZmqMessager transport = new PyZmqMessager(MessagerTransportConfig.builder()
                .nodeId(node).directAddr("tcp://127.0.0.1:*").requestTimeout(5.0d).build());
        transport.start().join();
        return transport;
    }
}
