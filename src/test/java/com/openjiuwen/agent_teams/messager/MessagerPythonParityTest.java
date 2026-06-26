/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code test_messager.py} in
 * {@code tests/unit_tests/agent_teams/test_messager.py}.
 */
class MessagerPythonParityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @AfterEach
    void cleanupBus() {
        InProcessMessager.cleanupInprocessBus();
    }

    @Test
    void inprocessMessagerIsMessager() {
        MessagerTransportConfig config = new MessagerTransportConfig();
        config.setBackend("inprocess");
        config.setTeamName("team-1");
        config.setNodeId("worker");

        InProcessMessager transport = new InProcessMessager(config);

        assertThat(transport).isInstanceOf(Messager.class);
    }

    @Test
    void inprocessPubsubDeliversToSubscriber() {
        List<EventMessage> received = new ArrayList<>();
        InProcessMessager leader = inProcess("leader");
        InProcessMessager worker = inProcess("worker");

        worker.subscribe("topic:team", appendTo(received)).toCompletableFuture().join();
        EventMessage event = sampleEvent("external");
        leader.publish("topic:team", event).toCompletableFuture().join();

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).isSameAs(event);
    }

    @Test
    void inprocessPublishStampsSenderId() {
        List<EventMessage> received = new ArrayList<>();
        InProcessMessager leader = inProcess("leader");
        InProcessMessager worker = inProcess("worker");

        worker.subscribe("topic:team", appendTo(received)).toCompletableFuture().join();
        EventMessage message = new EventMessage("team_cleaned", Map.of("team_name", "t"), "");
        assertThat(message.getSenderId()).isEmpty();

        leader.publish("topic:team", message).toCompletableFuture().join();

        assertThat(received).hasSize(1);
        assertThat(received.get(0).getSenderId()).isEqualTo("leader");
    }

    @Test
    void inprocessPubsubFanOut() {
        List<EventMessage> receivedA = new ArrayList<>();
        List<EventMessage> receivedB = new ArrayList<>();
        InProcessMessager publisher = inProcess("pub");
        InProcessMessager subscriberA = inProcess("sub-a");
        InProcessMessager subscriberB = inProcess("sub-b");

        subscriberA.subscribe("t", appendTo(receivedA)).toCompletableFuture().join();
        subscriberB.subscribe("t", appendTo(receivedB)).toCompletableFuture().join();

        publisher.publish("t", sampleEvent("pub")).toCompletableFuture().join();

        assertThat(receivedA).hasSize(1);
        assertThat(receivedB).hasSize(1);
    }

    @Test
    void inprocessUnsubscribeStopsDelivery() {
        List<EventMessage> received = new ArrayList<>();
        InProcessMessager messager = inProcess("a");

        messager.subscribe("t", appendTo(received)).toCompletableFuture().join();
        messager.unsubscribe("t").toCompletableFuture().join();
        messager.publish("t", sampleEvent("a")).toCompletableFuture().join();

        assertThat(received).isEmpty();
    }

    @Test
    void inprocessP2pDeliversToHandler() {
        List<EventMessage> received = new ArrayList<>();
        InProcessMessager receiver = inProcess("receiver");
        InProcessMessager sender = inProcess("sender");

        receiver.registerDirectMessageHandler(appendTo(received)).toCompletableFuture().join();
        EventMessage event = sampleEvent("sender");
        sender.send("receiver", event).toCompletableFuture().join();

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).isSameAs(event);
    }

    @Test
    void inprocessUnregisterP2pStopsDelivery() {
        List<EventMessage> received = new ArrayList<>();
        InProcessMessager messager = inProcess("x");

        messager.registerDirectMessageHandler(appendTo(received)).toCompletableFuture().join();
        messager.unregisterDirectMessageHandler().toCompletableFuture().join();
        messager.send("x", sampleEvent("sender")).toCompletableFuture().join();

        assertThat(received).isEmpty();
    }

    @Test
    void inprocessPubsubHandlerErrorDoesNotBlockOthers() {
        List<EventMessage> received = new ArrayList<>();
        InProcessMessager bad = inProcess("bad");
        InProcessMessager good = inProcess("good");
        InProcessMessager publisher = inProcess("pub");

        bad.subscribe("t", message -> CompletableFuture.failedFuture(new RuntimeException("boom")))
                .toCompletableFuture().join();
        good.subscribe("t", appendTo(received)).toCompletableFuture().join();

        publisher.publish("t", sampleEvent("pub")).toCompletableFuture().join();

        assertThat(received).hasSize(1);
    }

    @Test
    void createMessagerBuildsInprocess() {
        MessagerTransportConfig config = new MessagerTransportConfig();
        config.setBackend("inprocess");

        Messager transport = Messagers.createMessager(config);

        assertThat(transport).isInstanceOf(InProcessMessager.class);
    }

    @Test
    void createMessagerBuildsPyzmq() {
        MessagerTransportConfig config = new MessagerTransportConfig();
        config.setBackend("pyzmq");
        config.setTeamName("team-1");
        config.setNodeId("leader");
        config.setDirectAddr("tcp://127.0.0.1:19001");
        config.setPubsubPublishAddr("tcp://127.0.0.1:19100");
        config.setPubsubSubscribeAddr("tcp://127.0.0.1:19101");

        Messager transport = Messagers.createMessager(config);

        assertThat(transport).isInstanceOf(PyZmqMessager.class);
        assertThat(transport).isInstanceOf(Messager.class);
    }

    @Test
    void modelsRoundtripWithJacksonSerialization() {
        SubscriptionHandle subscription = new SubscriptionHandle("sub-1", "topic");
        subscription.setAgentId("worker");

        Map<String, Object> dumped = OBJECT_MAPPER.convertValue(subscription, new TypeReference<>() {
        });
        SubscriptionHandle restored = OBJECT_MAPPER.convertValue(dumped, SubscriptionHandle.class);

        assertThat(dumped)
                .containsEntry("subscription_id", "sub-1")
                .containsEntry("topic", "topic")
                .containsEntry("agent_id", "worker");
        assertThat(restored.getAgentId()).isEqualTo("worker");
    }

    private static InProcessMessager inProcess(String nodeId) {
        MessagerTransportConfig config = new MessagerTransportConfig();
        config.setNodeId(nodeId);
        return new InProcessMessager(config);
    }

    private static EventMessage sampleEvent(String senderId) {
        return new EventMessage("team_cleaned", Map.of("team_name", "team-1", "detail", "test"), senderId);
    }

    private static MessagerHandler appendTo(List<EventMessage> received) {
        return message -> {
            received.add(message);
            return completed();
        };
    }

    private static CompletionStage<Void> completed() {
        return CompletableFuture.completedFuture(null);
    }
}
