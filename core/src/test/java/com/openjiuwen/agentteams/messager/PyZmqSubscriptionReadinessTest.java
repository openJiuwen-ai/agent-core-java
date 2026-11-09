/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agentteams.schema.events.EventMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Verifies bounded readiness recovery over real sockets without retrying application messages.
 *
 * @since 2026-11-09
 */
@Timeout(20)
class PyZmqSubscriptionReadinessTest {
    @Test
    void rejectedProbeRecoversAndDeliversTheFirstApplicationEvent() throws Exception {
        try (TeamTransportRuntime proxy = proxy();
                ReadinessProbePeer peer = new ReadinessProbePeer(settings(proxy).getPubsubPublishAddr());
                TeamTransportRuntime client = client(proxy, peer, 3.0d)) {
            CompletableFuture<EventMessage> delivered = new CompletableFuture<>();
            CompletableFuture<Void> ready = client.messager().subscribe("readiness-topic", message -> {
                delivered.complete(message);
                return CompletableFuture.completedFuture(null);
            });
            peer.awaitRejection();
            peer.accept();
            ready.get(5, TimeUnit.SECONDS);

            proxy.messager().publish("readiness-topic", EventMessage.builder().eventType("application").build())
                    .get(3, TimeUnit.SECONDS);
            assertThat(delivered.get(3, TimeUnit.SECONDS).getEventType()).isEqualTo("application");
            assertThat(peer.requests()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void repeatedRejectionsExpireAtTheOriginalSubscriptionDeadline() throws Exception {
        try (TeamTransportRuntime proxy = proxy();
                ReadinessProbePeer peer = new ReadinessProbePeer(settings(proxy).getPubsubPublishAddr());
                TeamTransportRuntime client = client(proxy, peer, 0.5d)) {
            CompletableFuture<Void> ready = client.messager().subscribe("deadline", ignored ->
                    CompletableFuture.completedFuture(null));
            assertThatThrownBy(() -> ready.get(3, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(TimeoutException.class).hasRootCauseInstanceOf(IllegalStateException.class);
            assertThat(peer.requests()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void permanentPeerRejectionFailsWithoutRetrying() throws Exception {
        try (TeamTransportRuntime proxy = proxy();
                ReadinessProbePeer peer = new ReadinessProbePeer(settings(proxy).getPubsubPublishAddr())) {
            peer.rejectPermanently();
            try (TeamTransportRuntime client = client(proxy, peer, 3.0d)) {
                CompletableFuture<Void> ready = client.messager().subscribe("invalid", ignored ->
                        CompletableFuture.completedFuture(null));
                assertThatThrownBy(ready::join).isInstanceOf(CompletionException.class)
                        .hasCauseInstanceOf(IllegalStateException.class);
                assertThat(peer.requests()).isEqualTo(1);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationOrShutdownTerminatesPendingReadiness(boolean shouldStop) throws Exception {
        try (TeamTransportRuntime proxy = proxy();
                ReadinessProbePeer peer = new ReadinessProbePeer(settings(proxy).getPubsubPublishAddr());
                TeamTransportRuntime client = client(proxy, peer, 3.0d)) {
            CompletableFuture<Void> ready = client.messager().subscribe("closing", ignored ->
                    CompletableFuture.completedFuture(null));
            peer.awaitRejection();
            if (shouldStop) {
                client.messager().stop().get(5, TimeUnit.SECONDS);
                assertThatThrownBy(ready::join).isInstanceOf(CompletionException.class)
                        .hasCauseInstanceOf(IllegalStateException.class);
            } else {
                assertThat(ready.cancel(false)).isTrue();
                client.messager().stop().get(5, TimeUnit.SECONDS);
                assertThat(ready).isCancelled();
            }
        }
    }

    private static TeamTransportRuntime proxy() {
        return TeamTransportRuntime.open("readiness-" + UUID.randomUUID(), "proxy-owner",
                MessagerTransportConfig.builder().backend("pyzmq").build());
    }

    private static MessagerTransportConfig settings(TeamTransportRuntime proxy) {
        return proxy.memberConfiguration("client");
    }

    private static TeamTransportRuntime client(TeamTransportRuntime proxy, ReadinessProbePeer peer, double timeout)
            throws ExecutionException, InterruptedException, TimeoutException {
        MessagerTransportConfig config = settings(proxy);
        config.setKnownPeers(List.of(peer.peer()));
        config.setRequestTimeout(timeout);
        return TeamTransportRuntime.connect("readiness-client", "client", config);
    }
}
