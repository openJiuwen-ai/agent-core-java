/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

/**
 * Messager transport using pyzmq-equivalent ZeroMQ semantics.
 * <p>
 * Mirrors Python's {@code PyZmqMessager} in
 * {@code openjiuwen/agent_teams/messager/pyzmq_backend.py}.
 */
public class PyZmqMessager implements Messager {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final MessagerTransportConfig config;
    private final P2PLayer p2p;
    private final PubSubLayer pubsub;
    private final Map<String, SubscriptionHandle> topicHandles = new LinkedHashMap<>();

    private ZContext context;
    private boolean running;

    public PyZmqMessager(MessagerTransportConfig config) {
        this.config = config != null ? config : new MessagerTransportConfig();
        this.p2p = new P2PLayer(this.config);
        this.pubsub = new PubSubLayer(this.config);
    }

    public MessagerPeerConfig localPeer() {
        MessagerPeerConfig peer = new MessagerPeerConfig();
        peer.setAgentId(localNodeId());
        if (hasText(config.getDirectAddr())) {
            peer.setAddrs(List.of(config.getDirectAddr()));
        }
        return peer;
    }

    public void registerPeer(MessagerPeerConfig peer) {
        p2p.registerPeer(peer);
    }

    @Override
    public synchronized CompletionStage<Void> start() {
        if (running) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            ensureZmqAvailable();
            context = new ZContext();
            p2p.start(context);
            pubsub.start(context);
            running = true;
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException error) {
            if (context != null) {
                context.close();
                context = null;
            }
            p2p.stop();
            pubsub.stop();
            return CompletableFuture.failedFuture(error);
        }
    }

    @Override
    public synchronized CompletionStage<Void> stop() {
        if (!running) {
            return CompletableFuture.completedFuture(null);
        }
        running = false;
        p2p.stop();
        pubsub.stop();
        if (context != null) {
            context.close();
            context = null;
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> publish(String topicId, EventMessage message) {
        try {
            ensureRunning();
            EventMessage outgoing = stampSenderIfMissing(message);
            pubsub.publish(topicId, serializeEventMessage(outgoing, null));
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    @Override
    public CompletionStage<Void> subscribe(String topicId, MessagerHandler handler) {
        try {
            ensureRunning();
            SubscriptionHandle handle = pubsub.subscribe(topicId, handler);
            topicHandles.put(topicId, handle);
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    @Override
    public CompletionStage<Void> unsubscribe(String topicId) {
        try {
            SubscriptionHandle handle = topicHandles.remove(topicId);
            if (handle != null) {
                pubsub.unsubscribe(handle);
            }
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    @Override
    public CompletionStage<Void> send(String agentId, EventMessage message) {
        try {
            ensureRunning();
            p2p.send(agentId, serializeEventMessage(message, agentId));
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    @Override
    public CompletionStage<Void> registerDirectMessageHandler(MessagerHandler handler) {
        try {
            ensureRunning();
            p2p.registerHandler(localNodeId(), handler);
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    @Override
    public CompletionStage<Void> unregisterDirectMessageHandler() {
        p2p.unregisterHandler(localNodeId());
        return CompletableFuture.completedFuture(null);
    }

    private synchronized void ensureRunning() {
        if (!running) {
            start().toCompletableFuture().join();
        }
    }

    private String localNodeId() {
        return config.getNodeId() != null ? config.getNodeId() : "";
    }

    private EventMessage stampSenderIfMissing(EventMessage message) {
        if (message == null) {
            return null;
        }
        if (hasText(message.getSenderId())) {
            return message;
        }
        return new EventMessage(
                message.getEventType(),
                message.getPayloadData() != null ? new LinkedHashMap<>(message.getPayloadData()) : new LinkedHashMap<>(),
                localNodeId()
        );
    }

    private static void ensureZmqAvailable() {
        try {
            Class.forName("org.zeromq.ZContext");
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException(
                    "PyZmqMessagerTransport requires optional dependency 'pyzmq'.",
                    error
            );
        }
    }

    private static byte[] serializeEventMessage(EventMessage message, String recipientId) {
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event_type", message.getEventType());
        data.put("payload", message.getPayloadData() != null
                ? new LinkedHashMap<>(message.getPayloadData())
                : new LinkedHashMap<>());
        data.put("sender_id", message.getSenderId() != null ? message.getSenderId() : "");
        if (recipientId != null) {
            data.put("_recipient_id", recipientId);
        }
        try {
            return OBJECT_MAPPER.writeValueAsBytes(data);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to serialize EventMessage", error);
        }
    }

    private static EventMessage deserializeEventMessage(byte[] payload) {
        try {
            Map<String, Object> data = OBJECT_MAPPER.readValue(payload, MAP_TYPE);
            EventMessage message = new EventMessage();
            message.setEventType(stringValue(data.get("event_type")));
            message.setPayloadData(mapValue(data.get("payload")));
            message.setSenderId(stringValue(data.get("sender_id")));
            return message;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to deserialize EventMessage", error);
        }
    }

    private static Map<String, Object> mapValue(Object raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int timeoutMillis(double seconds) {
        return Math.max(1, (int) Math.round(seconds * 1000.0));
    }

    private static boolean metadataBoolean(MessagerTransportConfig config, String key) {
        Object value = config.getMetadata().get(key);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static void sendMultipart(ZMQ.Socket socket, List<byte[]> frames) {
        for (int index = 0; index < frames.size(); index++) {
            byte[] frame = frames.get(index);
            if (index < frames.size() - 1) {
                socket.sendMore(frame);
            } else {
                socket.send(frame);
            }
        }
    }

    private static List<byte[]> recvMultipart(ZMQ.Socket socket) {
        ZMsg message = ZMsg.recvMsg(socket);
        if (message == null) {
            return List.of();
        }
        try {
            List<byte[]> frames = new ArrayList<>();
            for (ZFrame frame : message) {
                frames.add(frame.getData().clone());
            }
            return frames;
        } finally {
            message.destroy();
        }
    }

    private static void joinQuietly(Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            thread.join(1000);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class P2PLayer {

        private final MessagerTransportConfig config;
        private final Map<String, MessagerPeerConfig> peerBook = new LinkedHashMap<>();
        private final Map<String, MessagerHandler> handlers = new LinkedHashMap<>();

        private ZContext context;
        private ZMQ.Socket router;
        private Thread routerThread;
        private boolean running;

        private P2PLayer(MessagerTransportConfig config) {
            this.config = config;
            for (MessagerPeerConfig peer : config.getBootstrapPeers()) {
                registerPeer(peer);
            }
            for (MessagerPeerConfig peer : config.getKnownPeers()) {
                registerPeer(peer);
            }
        }

        synchronized void registerPeer(MessagerPeerConfig peer) {
            if (peer != null && hasText(peer.getAgentId())) {
                peerBook.put(peer.getAgentId(), peer);
            }
        }

        synchronized void registerHandler(String agentId, MessagerHandler handler) {
            if (hasText(agentId) && handler != null) {
                handlers.put(agentId, handler);
            }
        }

        synchronized void unregisterHandler(String agentId) {
            if (agentId != null) {
                handlers.remove(agentId);
            }
        }

        synchronized void start(ZContext context) {
            if (running) {
                return;
            }
            this.context = context;
            running = true;
            router = context.createSocket(SocketType.ROUTER);
            router.setReceiveTimeOut(100);
            if (hasText(config.getDirectAddr())) {
                router.bind(config.getDirectAddr());
            }
            routerThread = new Thread(this::recvLoop, "pyzmq-p2p-" + stringValue(config.getNodeId()));
            routerThread.setDaemon(true);
            routerThread.start();
        }

        synchronized void stop() {
            running = false;
            joinQuietly(routerThread);
            if (router != null) {
                router.close();
            }
            routerThread = null;
            router = null;
            context = null;
        }

        void send(String agentId, byte[] payloadBytes) {
            MessagerPeerConfig peer;
            synchronized (this) {
                peer = peerBook.get(agentId);
            }
            if (peer == null || peer.getAddrs().isEmpty()) {
                throw new IllegalStateException(
                        "Unknown zmq route for recipient '" + agentId + "'. Provide a known peer entry.");
            }
            String nodeId = hasText(config.getNodeId()) ? config.getNodeId() : UUID.randomUUID().toString();
            ZMQ.Socket dealer = context.createSocket(SocketType.DEALER);
            dealer.setIdentity(nodeId.getBytes(StandardCharsets.UTF_8));
            dealer.setReceiveTimeOut(timeoutMillis(config.getRequestTimeout()));
            dealer.setSendTimeOut(timeoutMillis(config.getRequestTimeout()));
            dealer.connect(peer.getAddrs().get(0));
            try {
                dealer.send(payloadBytes);
                byte[] ack = dealer.recv();
                if (ack == null) {
                    throw new IllegalStateException(
                            "Timed out waiting for pyzmq ACK from recipient '" + agentId + "'");
                }
            } finally {
                dealer.close();
            }
        }

        private void recvLoop() {
            while (running) {
                try {
                    List<byte[]> frames = recvMultipart(router);
                    if (frames.size() < 2) {
                        continue;
                    }
                    byte[] identity = frames.get(0);
                    byte[] payload = frames.get(frames.size() - 1);
                    handleRequest(identity, payload);
                } catch (RuntimeException error) {
                    if (!running) {
                        return;
                    }
                }
            }
        }

        private void handleRequest(byte[] identity, byte[] payload) {
            try {
                Map<String, Object> data = OBJECT_MAPPER.readValue(payload, MAP_TYPE);
                String recipientId = stringValue(data.get("_recipient_id"));
                MessagerHandler handler;
                synchronized (this) {
                    handler = handlers.get(recipientId);
                }
                if (handler != null) {
                    handler.handle(deserializeEventMessage(payload)).toCompletableFuture().join();
                }
            } catch (Exception error) {
                TEAM_LOGGER.warning("P2P failed to parse incoming request");
            } finally {
                if (router != null) {
                    router.sendMore(identity);
                    router.send("ok");
                }
            }
        }
    }

    private static final class PubSubLayer {

        private final MessagerTransportConfig config;
        private final Map<String, SubscriptionHandle> subscriptions = new LinkedHashMap<>();
        private final Map<String, MessagerHandler> handlers = new LinkedHashMap<>();

        private ZMQ.Socket pub;
        private ZMQ.Socket sub;
        private ZMQ.Socket xpub;
        private ZMQ.Socket xsub;
        private Thread subThread;
        private Thread proxyThread;
        private boolean running;

        private PubSubLayer(MessagerTransportConfig config) {
            this.config = config;
        }

        synchronized void start(ZContext context) {
            if (running) {
                return;
            }
            String publishAddr = requirePublishAddr();
            String subscribeAddr = requireSubscribeAddr();
            running = true;

            if (metadataBoolean(config, "pubsub_bind")) {
                xpub = context.createSocket(SocketType.XPUB);
                xsub = context.createSocket(SocketType.XSUB);
                xpub.setReceiveTimeOut(50);
                xsub.setReceiveTimeOut(50);
                xpub.bind(subscribeAddr);
                xsub.bind(publishAddr);
                proxyThread = new Thread(this::proxyLoop, "pyzmq-pubsub-proxy-" + stringValue(config.getNodeId()));
                proxyThread.setDaemon(true);
                proxyThread.start();
            }

            pub = context.createSocket(SocketType.PUB);
            pub.connect(publishAddr);
            sub = context.createSocket(SocketType.SUB);
            sub.setReceiveTimeOut(100);
            sub.connect(subscribeAddr);
            subThread = new Thread(this::recvLoop, "pyzmq-pubsub-" + stringValue(config.getNodeId()));
            subThread.setDaemon(true);
            subThread.start();
        }

        synchronized void stop() {
            running = false;
            joinQuietly(subThread);
            joinQuietly(proxyThread);
            close(pub);
            close(sub);
            close(xpub);
            close(xsub);
            subThread = null;
            proxyThread = null;
            pub = null;
            sub = null;
            xpub = null;
            xsub = null;
        }

        void publish(String topic, byte[] payloadBytes) {
            pub.sendMore(topic.getBytes(StandardCharsets.UTF_8));
            pub.send(payloadBytes);
        }

        synchronized SubscriptionHandle subscribe(String topic, MessagerHandler handler) {
            boolean firstLocal = !handlers.containsKey(topic);
            handlers.put(topic, handler);
            if (firstLocal && sub != null) {
                sub.subscribe(topic.getBytes(StandardCharsets.UTF_8));
            }
            SubscriptionHandle handle = new SubscriptionHandle(UUID.randomUUID().toString(), topic);
            handle.setBackendMetadata(Map.of("backend", "pyzmq"));
            subscriptions.put(handle.getSubscriptionId(), handle);
            return handle;
        }

        synchronized void unsubscribe(SubscriptionHandle handle) {
            handlers.remove(handle.getTopic());
            if (sub != null) {
                sub.unsubscribe(handle.getTopic().getBytes(StandardCharsets.UTF_8));
            }
            subscriptions.remove(handle.getSubscriptionId());
        }

        private void recvLoop() {
            while (running) {
                try {
                    List<byte[]> frames = recvMultipart(sub);
                    if (frames.size() < 2) {
                        continue;
                    }
                    String topic = new String(frames.get(0), StandardCharsets.UTF_8);
                    MessagerHandler handler;
                    synchronized (this) {
                        handler = handlers.get(topic);
                    }
                    if (handler != null) {
                        handler.handle(deserializeEventMessage(frames.get(1))).toCompletableFuture().join();
                    }
                } catch (RuntimeException error) {
                    if (!running) {
                        return;
                    }
                }
            }
        }

        private void proxyLoop() {
            while (running) {
                forwardAvailable(xsub, xpub);
                forwardAvailable(xpub, xsub);
            }
        }

        private void forwardAvailable(ZMQ.Socket source, ZMQ.Socket target) {
            try {
                List<byte[]> frames = recvMultipart(source);
                if (!frames.isEmpty()) {
                    sendMultipart(target, frames);
                }
            } catch (RuntimeException error) {
                if (!running) {
                    throw error;
                }
            }
        }

        private String requirePublishAddr() {
            if (!hasText(config.getPubsubPublishAddr())) {
                throw new IllegalStateException("pubsub_publish_addr is required for pyzmq messager transport.");
            }
            return config.getPubsubPublishAddr();
        }

        private String requireSubscribeAddr() {
            if (!hasText(config.getPubsubSubscribeAddr())) {
                throw new IllegalStateException("pubsub_subscribe_addr is required for pyzmq messager transport.");
            }
            return config.getPubsubSubscribeAddr();
        }

        private void close(ZMQ.Socket socket) {
            if (socket != null) {
                socket.close();
            }
        }
    }
}
