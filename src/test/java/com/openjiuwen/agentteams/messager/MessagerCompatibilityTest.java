
package com.openjiuwen.agentteams.messager;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.schema.events.EventMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

class MessagerCompatibilityTest {
    @AfterEach
    void cleanup() {
        InProcessMessager.cleanupInprocessBus();
    }

    @Test
    void inprocessPubsubShouldDeliverToSubscriberAndStampSenderId() {
        List<EventMessage> received = new ArrayList<>();
        InProcessMessager leader = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        InProcessMessager worker = new InProcessMessager(MessagerTransportConfig.builder().nodeId("worker").build());

        worker.subscribe("topic:team", msg -> {
            received.add(msg);
            return CompletableFuture.completedFuture(null);
        }).join();

        leader.publish("topic:team", EventMessage.builder().eventType("team_cleaned").build()).join();

        assertThat(received).hasSize(1);
        assertThat(received.get(0).getSenderId()).isEqualTo("leader");
    }

    @Test
    void inprocessSendShouldDeliverToDirectHandler() {
        List<EventMessage> received = new ArrayList<>();
        InProcessMessager receiver =
            new InProcessMessager(MessagerTransportConfig.builder().nodeId("receiver").build());
        InProcessMessager sender = new InProcessMessager(MessagerTransportConfig.builder().nodeId("sender").build());

        receiver.registerDirectMessageHandler(msg -> {
            received.add(msg);
            return CompletableFuture.completedFuture(null);
        }).join();

        sender.send("receiver", EventMessage.builder().eventType("message").build()).join();

        assertThat(received).hasSize(1);
    }

    @Test
    void inprocessSendAndWaitShouldRoundTripDirectRequestResponseLikePythonMessager() {
        InProcessMessager receiver =
            new InProcessMessager(MessagerTransportConfig.builder().nodeId("receiver").build());
        InProcessMessager sender = new InProcessMessager(MessagerTransportConfig.builder().nodeId("sender").build());

        receiver.registerDirectMessageHandler(
                msg -> receiver.send(String.valueOf(msg.getPayload().get("reply_to")),
                        EventMessage.builder().eventType("response").payload(Map.of("ok", true, "action",
                                msg.getPayload().get("action"), "request_id", msg.getPayload().get("request_id")))
                                .build()))
                .join();

        Map<String, Object> response =
            sender.sendAndWait("receiver", Map.of("action", "exists"), java.time.Duration.ofSeconds(1)).join();

        assertThat(response).containsEntry("ok", true);
        assertThat(response).containsEntry("action", "exists");
        assertThat(response).containsKey("request_id");
    }

    @Test
    void factoryShouldCreateInprocessMessagerAndSubscriptionHandle() {
        Messager messager = MessagerFactory
                .createMessager(MessagerTransportConfig.builder().backend("inprocess").nodeId("node-a").build());
        assertThat(messager).isInstanceOf(InProcessMessager.class);

        SubscriptionHandle handle = ((InProcessMessager) messager).subscriptionHandle("topic");
        assertThat(handle.getTopic()).isEqualTo("topic");
        assertThat(handle.getAgentId()).isEqualTo("node-a");
    }
}
