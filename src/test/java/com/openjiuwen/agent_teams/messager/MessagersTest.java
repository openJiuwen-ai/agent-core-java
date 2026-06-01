/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import com.openjiuwen.agent_teams.schema.events.EventMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.test_messager}.
 * Tests for Messagers factory and transport backend selection.
 */
class MessagersTest {

    @AfterEach
    void cleanup() {
        InProcessMessager.cleanupBus();
    }

    @Test
    void createMessagerReturnsInProcessByDefault() {
        assertInstanceOf(InProcessMessager.class, Messagers.createMessager(new MessagerTransportConfig()));
    }

    @Test
    void createMessagerSupportsPyzmqBackend() {
        MessagerTransportConfig config = new MessagerTransportConfig();
        config.setBackend("pyzmq");
        assertInstanceOf(PyZmqMessager.class, Messagers.createMessager(config));
        assertInstanceOf(PyZmqMessager.class, MessagerFactory.createMessager(config));
    }

    @Test
    void inprocessMessagerIsMessager() {
        MessagerTransportConfig config = new MessagerTransportConfig();
        config.setBackend("inprocess");
        config.setTeamName("team-1");
        config.setNodeId("worker");

        Messager transport = new InProcessMessager(config);

        assertInstanceOf(Messager.class, transport);
    }

    @Test
    void inprocessPubsubDeliversToSubscriber() {
        List<EventMessage> received = new ArrayList<>();
        InProcessMessager leader = new InProcessMessager(inprocessConfig("leader"));
        InProcessMessager worker = new InProcessMessager(inprocessConfig("worker"));

        worker.subscribe("topic:team", received::add);
        EventMessage event = sampleEvent();
        leader.publish("topic:team", event);

        assertEquals(1, received.size());
        assertSame(event, received.get(0));
    }

    @Test
    void inprocessPublishStampsSenderId() {
        List<EventMessage> received = new ArrayList<>();
        InProcessMessager leader = new InProcessMessager(inprocessConfig("leader"));
        InProcessMessager worker = new InProcessMessager(inprocessConfig("worker"));

        worker.subscribe("topic:team", received::add);
        EventMessage message = new EventMessage("team_cleaned", Map.of("team_name", "t"));
        assertEquals("", message.getSenderId());
        leader.publish("topic:team", message);

        assertEquals(1, received.size());
        assertEquals("leader", received.get(0).getSenderId());
    }

    @Test
    void inprocessPubsubFansOutToEverySubscriber() {
        List<EventMessage> receivedA = new ArrayList<>();
        List<EventMessage> receivedB = new ArrayList<>();
        InProcessMessager publisher = new InProcessMessager(inprocessConfig("publisher"));
        InProcessMessager subA = new InProcessMessager(inprocessConfig("sub-a"));
        InProcessMessager subB = new InProcessMessager(inprocessConfig("sub-b"));

        subA.subscribe("topic", receivedA::add);
        subB.subscribe("topic", receivedB::add);
        publisher.publish("topic", sampleEvent());

        assertEquals(1, receivedA.size());
        assertEquals(1, receivedB.size());
    }

    @Test
    void inprocessUnsubscribeStopsDelivery() {
        List<EventMessage> received = new ArrayList<>();
        InProcessMessager messager = new InProcessMessager(inprocessConfig("a"));

        messager.subscribe("topic", received::add);
        messager.unsubscribe("topic");
        messager.publish("topic", sampleEvent());

        assertTrue(received.isEmpty());
    }

    @Test
    void inprocessP2pDeliversToRegisteredHandler() {
        List<EventMessage> received = new ArrayList<>();
        InProcessMessager receiver = new InProcessMessager(inprocessConfig("receiver"));
        InProcessMessager sender = new InProcessMessager(inprocessConfig("sender"));

        receiver.registerDirectMessageHandler(received::add);
        EventMessage event = sampleEvent();
        sender.send("receiver", event);

        assertEquals(1, received.size());
        assertSame(event, received.get(0));
    }

    @Test
    void inprocessUnregisterP2pStopsDelivery() {
        List<EventMessage> received = new ArrayList<>();
        InProcessMessager messager = new InProcessMessager(inprocessConfig("x"));

        messager.registerDirectMessageHandler(received::add);
        messager.unregisterDirectMessageHandler();
        messager.send("x", sampleEvent());

        assertTrue(received.isEmpty());
    }

    @Test
    void inprocessPubsubHandlerErrorDoesNotBlockOthers() {
        List<EventMessage> received = new ArrayList<>();
        InProcessMessager bad = new InProcessMessager(inprocessConfig("bad"));
        InProcessMessager good = new InProcessMessager(inprocessConfig("good"));
        InProcessMessager publisher = new InProcessMessager(inprocessConfig("publisher"));

        bad.subscribe("topic", message -> {
            throw new IllegalStateException("boom");
        });
        good.subscribe("topic", received::add);
        publisher.publish("topic", sampleEvent());

        assertEquals(1, received.size());
    }

    @Test
    void createMessagerBuildsExplicitInprocess() {
        MessagerTransportConfig config = new MessagerTransportConfig();
        config.setBackend("inprocess");

        assertInstanceOf(InProcessMessager.class, Messagers.createMessager(config));
        assertInstanceOf(InProcessMessager.class, MessagerFactory.createMessager(config));
    }

    @Test
    void subscriptionHandleRoundtripKeepsAgentId() {
        SubscriptionHandle subscription = new SubscriptionHandle("sub-1", "topic");
        subscription.setAgentId("worker");
        subscription.setBackendMetadata(Map.of("partition", "local"));

        SubscriptionHandle roundtrip = new SubscriptionHandle(subscription.getSubscriptionId(), subscription.getTopic());
        roundtrip.setAgentId(subscription.getAgentId());
        roundtrip.setBackendMetadata(subscription.getBackendMetadata());

        assertEquals("worker", roundtrip.getAgentId());
        assertEquals("sub-1", roundtrip.getSubscriptionId());
        assertEquals("topic", roundtrip.getTopic());
        assertEquals("local", roundtrip.getBackendMetadata().get("partition"));
    }

    @Test
    void pyzmqStartRequiresPubsubAddressesLikePython() {
        PyZmqMessager messager = new PyZmqMessager(new MessagerTransportConfig());

        assertThrows(IllegalStateException.class, messager::start);
        assertDoesNotThrow(messager::stop);
        assertDoesNotThrow(() -> messager.unsubscribe("topic:team"));
    }

    @Test
    void pyzmqP2pDeliversToRegisteredHandler() throws Exception {
        String directAddr = freeTcpAddress();
        String publishAddr = freeTcpAddress();
        String subscribeAddr = freeTcpAddress();
        PyZmqMessager receiver = new PyZmqMessager(config(
                "receiver", directAddr, publishAddr, subscribeAddr, true, List.of()));
        PyZmqMessager sender = new PyZmqMessager(config(
                "sender", null, publishAddr, subscribeAddr, false, List.of(peer("receiver", directAddr))));
        CountDownLatch received = new CountDownLatch(1);
        EventMessage[] holder = new EventMessage[1];

        try {
            receiver.registerDirectMessageHandler(message -> {
                holder[0] = message;
                received.countDown();
            });

            sender.send("receiver", new EventMessage("direct", Map.of("value", 7)));

            assertTrue(received.await(2, TimeUnit.SECONDS));
            assertEquals("direct", holder[0].getEventType());
            assertEquals(7, holder[0].getPayload().get("value"));
            assertEquals("sender", holder[0].getSenderId());
        } finally {
            sender.stop();
            receiver.stop();
        }
    }

    @Test
    void pyzmqPubsubDeliversToSubscriberAndUnsubscribeStopsDelivery() throws Exception {
        String publishAddr = freeTcpAddress();
        String subscribeAddr = freeTcpAddress();
        PyZmqMessager subscriber = new PyZmqMessager(config(
                "subscriber", null, publishAddr, subscribeAddr, true, List.of()));
        PyZmqMessager publisher = new PyZmqMessager(config(
                "publisher", null, publishAddr, subscribeAddr, false, List.of()));
        CountDownLatch received = new CountDownLatch(1);
        EventMessage[] holder = new EventMessage[1];

        try {
            subscriber.subscribe("topic:team", message -> {
                holder[0] = message;
                received.countDown();
            });
            eventuallyPublish(publisher, "topic:team", new EventMessage("broadcast", Map.of("team", "a")), received);

            assertTrue(received.await(2, TimeUnit.SECONDS));
            assertEquals("broadcast", holder[0].getEventType());
            assertEquals("publisher", holder[0].getSenderId());
            assertEquals("a", holder[0].getPayload().get("team"));

            subscriber.unsubscribe("topic:team");
            CountDownLatch afterUnsubscribe = new CountDownLatch(1);
            EventMessage[] dropped = new EventMessage[1];
            subscriber.subscribe("topic:other", message -> {
                dropped[0] = message;
                afterUnsubscribe.countDown();
            });
            publisher.publish("topic:team", new EventMessage("dropped", Map.of()));
            assertTrue(!afterUnsubscribe.await(300, TimeUnit.MILLISECONDS));
            assertNull(dropped[0]);
        } finally {
            publisher.stop();
            subscriber.stop();
        }
    }

    @Test
    void pyzmqLocalPeerMirrorsPythonProperty() {
        MessagerTransportConfig config = new MessagerTransportConfig();
        config.setNodeId("node-a");
        config.setDirectAddr("tcp://127.0.0.1:19001");

        MessagerPeerConfig peer = new PyZmqMessager(config).localPeer();

        assertEquals("node-a", peer.getAgentId());
        assertEquals(List.of("tcp://127.0.0.1:19001"), peer.getAddrs());
    }

    @Test
    void pyzmqSendRejectsUnknownPeer() {
        String publishAddr = freeTcpAddress();
        String subscribeAddr = freeTcpAddress();
        PyZmqMessager sender = new PyZmqMessager(config(
                "sender", null, publishAddr, subscribeAddr, true, List.of()));

        try {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> sender.send("missing", new EventMessage("direct", Map.of())));
            assertTrue(error.getMessage().contains("Unknown zmq route"));
        } finally {
            sender.stop();
        }
    }

    private static MessagerTransportConfig config(
            String nodeId,
            String directAddr,
            String publishAddr,
            String subscribeAddr,
            boolean bindPubsub,
            List<MessagerPeerConfig> knownPeers) {
        MessagerTransportConfig config = new MessagerTransportConfig();
        config.setBackend("pyzmq");
        config.setTeamName("team-1");
        config.setNodeId(nodeId);
        config.setDirectAddr(directAddr);
        config.setPubsubPublishAddr(publishAddr);
        config.setPubsubSubscribeAddr(subscribeAddr);
        config.setRequestTimeout(1.0);
        config.setMetadata(Map.of("pubsub_bind", bindPubsub));
        config.setKnownPeers(knownPeers);
        return config;
    }

    private static MessagerPeerConfig peer(String agentId, String addr) {
        MessagerPeerConfig peer = new MessagerPeerConfig();
        peer.setAgentId(agentId);
        peer.setAddrs(List.of(addr));
        return peer;
    }

    private static MessagerTransportConfig inprocessConfig(String nodeId) {
        MessagerTransportConfig config = new MessagerTransportConfig();
        config.setBackend("inprocess");
        config.setTeamName("team-1");
        config.setNodeId(nodeId);
        return config;
    }

    private static EventMessage sampleEvent() {
        return new EventMessage("sample", Map.of("team_name", "team-1", "detail", "test"));
    }

    private static String freeTcpAddress() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return "tcp://127.0.0.1:" + socket.getLocalPort();
        } catch (IOException error) {
            throw new IllegalStateException("failed to allocate test port", error);
        }
    }

    private static void eventuallyPublish(
            PyZmqMessager publisher,
            String topic,
            EventMessage message,
            CountDownLatch received) throws InterruptedException {
        for (int attempt = 0; attempt < 10 && received.getCount() > 0; attempt++) {
            publisher.publish(topic, message);
            received.await(100, TimeUnit.MILLISECONDS);
        }
    }
}
