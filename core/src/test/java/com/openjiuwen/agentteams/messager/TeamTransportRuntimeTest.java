/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentteams.schema.events.EventMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Exercises real owned team networks, subscription readiness, peer routing, and shutdown without external services.
 *
 * @since 2026-11-09
 */
@Timeout(20)
class TeamTransportRuntimeTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void defaultTransportRemainsInProcess() {
        try (TeamTransportRuntime leader = TeamTransportRuntime.open(name(), "leader", null);
                TeamTransportRuntime member = leader.fork("member")) {
            assertThat(leader.messager()).isInstanceOf(InProcessMessager.class);
            assertThat(member.messager()).isInstanceOf(InProcessMessager.class);
        }
    }

    @Test
    void firstPublishedApplicationMessageArrivesAfterSubscriptionReadiness() throws Exception {
        MessagerTransportConfig source = zmq();
        try (TeamTransportRuntime leader = TeamTransportRuntime.open(name(), "leader", source);
                TeamTransportRuntime member = leader.fork("worker")) {
            List<String> received = new CopyOnWriteArrayList<>();
            CompletableFuture<Void> delivered = new CompletableFuture<>();
            member.messager().subscribe("team:first", event -> {
                received.add(event.getEventType());
                delivered.complete(null);
                return CompletableFuture.completedFuture(null);
            }).orTimeout(5, TimeUnit.SECONDS).join();

            leader.messager().publish("team:first", event("application-event")).join();

            delivered.get(5, TimeUnit.SECONDS);
            assertThat(received).containsExactly("application-event");
            assertThat(source.getDirectAddr()).isNull();
            assertThat(source.getPubsubPublishAddr()).isNull();
            assertThat(source.getMetadata()).isEmpty();
        }
    }

    @Test
    void membersDiscoverEachOtherForRealDirectRequestReply() throws Exception {
        try (TeamTransportRuntime leader = TeamTransportRuntime.open(name(), "leader", zmq());
                TeamTransportRuntime member = leader.fork("worker")) {
            member.messager().registerDirectMessageHandler(message -> {
                String reply = String.valueOf(message.getPayload().get("reply_to"));
                return member.messager().send(reply, EventMessage.builder().eventType("reply")
                        .payload(Map.of("value", message.getPayload().get("value"))).build());
            }).join();

            Map<String, Object> reply = leader.messager().sendAndWait("worker", Map.of("value", 7),
                    java.time.Duration.ofSeconds(5)).get(5, TimeUnit.SECONDS);

            assertThat(reply).containsEntry("value", 7);
        }
    }

    @Test
    void unrelatedTeamsWithIdenticalMemberAndTopicNamesDoNotShareMessages() throws Exception {
        try (TeamTransportRuntime first = TeamTransportRuntime.open(name(), "leader", zmq());
                TeamTransportRuntime second = TeamTransportRuntime.open(name(), "leader", zmq());
                TeamTransportRuntime firstMember = first.fork("worker");
                TeamTransportRuntime secondMember = second.fork("worker")) {
            CompletableFuture<Void> delivered = new CompletableFuture<>();
            List<String> foreign = new CopyOnWriteArrayList<>();
            firstMember.messager().subscribe("same-topic", message -> {
                delivered.complete(null);
                return CompletableFuture.completedFuture(null);
            }).join();
            secondMember.messager().subscribe("same-topic", message -> {
                foreign.add(message.getEventType());
                return CompletableFuture.completedFuture(null);
            }).join();

            first.messager().publish("same-topic", event("first-only")).join();
            delivered.get(5, TimeUnit.SECONDS);

            assertThat(foreign).isEmpty();
            assertThat(first.memberConfiguration("other").getPubsubPublishAddr())
                    .isNotEqualTo(second.memberConfiguration("other").getPubsubPublishAddr());
        }
    }

    @Test
    void closingLeaderKeepsProxyAliveUntilLastMemberCloses() throws Exception {
        try (TeamTransportRuntime leader = TeamTransportRuntime.open(name(), "leader", zmq());
                TeamTransportRuntime member = leader.fork("worker")) {
            leader.close();
            CompletableFuture<Void> delivered = new CompletableFuture<>();
            member.messager().subscribe("still-running", message -> {
                delivered.complete(null);
                return CompletableFuture.completedFuture(null);
            }).join();
            member.messager().publish("still-running", event("after-leader-close")).join();
            delivered.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> leader.fork("late")).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void proxyAndDirectPortsCanBeReboundAfterShutdown() {
        MessagerTransportConfig reusable;
        try (TeamTransportRuntime first = TeamTransportRuntime.open(name(), "leader", zmq())) {
            reusable = first.memberConfiguration("leader");
            reusable.setDirectAddr(((PyZmqMessager) first.messager()).getLocalPeer().getAddrs().get(0));
            reusable.setKnownPeers(List.of());
            reusable.setMetadata(Map.of("pubsub_bind", true));
        }
        try (TeamTransportRuntime replacement = TeamTransportRuntime.open(name(), "leader", reusable)) {
            replacement.messager().subscribe("rebound", ignored -> CompletableFuture.completedFuture(null)).join();
            assertThat(replacement.messager()).isInstanceOf(PyZmqMessager.class);
        }
    }

    @Test
    void duplicateMemberIsRejectedWithoutReplacingItsTransport() {
        try (TeamTransportRuntime leader = TeamTransportRuntime.open(name(), "leader", zmq());
                TeamTransportRuntime member = leader.fork("worker")) {
            assertThatThrownBy(() -> leader.fork("worker")).isInstanceOf(IllegalStateException.class);
            member.messager().subscribe("original", ignored -> CompletableFuture.completedFuture(null)).join();
        }
    }

    @Test
    void missingAndInvalidEndpointSettingsFailBeforeStartingSockets() {
        assertThatThrownBy(() -> TeamTransportRuntime.connect(name(), "worker", zmq()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("endpoints");
        MessagerTransportConfig partial = zmq();
        partial.setPubsubPublishAddr("tcp://127.0.0.1:*");
        assertThatThrownBy(() -> TeamTransportRuntime.open(name(), "leader", partial))
                .isInstanceOf(IllegalArgumentException.class);
        MessagerTransportConfig invalid = zmq();
        invalid.setDirectAddr("tcp://user:secret@127.0.0.1:12?token=private");
        assertThatThrownBy(() -> TeamTransportRuntime.open(name(), "leader", invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("secret").hasMessageNotContaining("private");
    }

    @Test
    void anUnreachableProxyFailsReadinessRatherThanClaimingSuccessfulSubscription() throws Exception {
        try (ServerSocket silent = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
            String endpoint = "tcp://127.0.0.1:" + silent.getLocalPort();
            MessagerTransportConfig config = zmq();
            config.setPubsubPublishAddr(endpoint);
            config.setPubsubSubscribeAddr(endpoint);
            config.setRequestTimeout(1.0d);
            config.setMetadata(Map.of("pubsub_bind", false));
            try (TeamTransportRuntime member = TeamTransportRuntime.connect(name(), "worker", config)) {
                CompletableFuture<Void> readiness = member.messager().subscribe("unreachable",
                        ignored -> CompletableFuture.completedFuture(null));
                assertThatThrownBy(readiness::join).isInstanceOf(CompletionException.class)
                        .hasCauseInstanceOf(TimeoutException.class);
            }
        }
    }

    @Test
    void snakeCaseSettingsAndCopiesPreserveIndependentPeerMetadata() {
        MessagerTransportConfig config = MAPPER.convertValue(Map.of(
                "backend", "pyzmq", "direct_addr", "tcp://127.0.0.1:*", "request_timeout", 2.0,
                "known_peers", List.of(Map.of("agent_id", "worker", "addrs", List.of("tcp://127.0.0.1:23456"))),
                "metadata", Map.of("nested", new ArrayList<>(List.of("original")))), MessagerTransportConfig.class);
        MessagerTransportConfig copy = config.copy();
        copy.getKnownPeers().get(0).setAgentId("changed");
        copy.getMetadata().put("nested", List.of("changed"));
        assertThat(config.getDirectAddr()).isEqualTo("tcp://127.0.0.1:*");
        assertThat(config.getRequestTimeout()).isEqualTo(2.0d);
        assertThat(config.getKnownPeers().get(0).getAgentId()).isEqualTo("worker");
        assertThat(config.getMetadata().get("nested")).isEqualTo(List.of("original"));
    }

    private static MessagerTransportConfig zmq() {
        return MessagerTransportConfig.builder().backend("pyzmq").build();
    }

    private static String name() {
        return "transport-test-" + UUID.randomUUID();
    }

    private static EventMessage event(String type) {
        return EventMessage.builder().eventType(type).build();
    }
}
