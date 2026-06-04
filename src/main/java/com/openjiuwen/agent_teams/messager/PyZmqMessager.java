/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMsg;
import org.zeromq.ZMQ;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Messager transport using ZeroMQ through JeroMQ.
 *
 * <p>Mirrors Python's {@code PyZmqMessager} in
 * {@code openjiuwen.agent_teams.messager.pyzmq_backend}.</p>
 */
public class PyZmqMessager implements Messager {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final MessagerTransportConfig config;
    private final P2PLayer p2p;
    private final PubSubLayer pubsub;
    private final Map<String, SubscriptionHandle> topicHandles = new LinkedHashMap<>();

    private ZContext context;
    private volatile boolean running;

    public PyZmqMessager(MessagerTransportConfig config) {
        this.config = config != null ? config : new MessagerTransportConfig();
        this.p2p = new P2PLayer(this.config);
        this.pubsub = new PubSubLayer(this.config);
    }

    public MessagerTransportConfig getConfig() {
        return config;
    }

    public MessagerPeerConfig localPeer() {
        MessagerPeerConfig peer = new MessagerPeerConfig();
        peer.setAgentId(nonNull(config.getNodeId()));
        if (hasText(config.getDirectAddr())) {
            peer.setAddrs(List.of(config.getDirectAddr()));
        }
        return peer;
    }

    public void registerPeer(MessagerPeerConfig peer) {
        p2p.registerPeer(peer);
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        ZContext newContext = new ZContext();
        try {
            p2p.start(newContext);
            pubsub.start(newContext);
            context = newContext;
            running = true;
        } catch (RuntimeException error) {
            p2p.stop();
            pubsub.stop();
            newContext.close();
            throw error;
        }
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        p2p.stop();
        pubsub.stop();
        if (context != null) {
            context.close();
            context = null;
        }
    }

    @Override
    public void publish(String topicId, EventMessage message) {
        ensureRunning();
        stampSender(message);
        pubsub.publish(topicId, serialize(message, null));
    }

    @Override
    public void subscribe(String topicId, MessagerHandler handler) {
        ensureRunning();
        SubscriptionHandle handle = pubsub.subscribe(topicId, handler);
        topicHandles.put(topicId, handle);
    }

    @Override
    public void unsubscribe(String topicId) {
        SubscriptionHandle handle = topicHandles.remove(topicId);
        if (handle != null) {
            pubsub.unsubscribe(handle);
        }
    }

    @Override
    public void send(String agentId, EventMessage message) {
        ensureRunning();
        stampSender(message);
        p2p.send(agentId, serialize(message, agentId));
    }

    @Override
    public void registerDirectMessageHandler(MessagerHandler handler) {
        ensureRunning();
        p2p.registerHandler(nonNull(config.getNodeId()), handler);
    }

    @Override
    public void unregisterDirectMessageHandler() {
        p2p.unregisterHandler(nonNull(config.getNodeId()));
    }

    private void ensureRunning() {
        if (!running) {
            start();
        }
    }

    private void stampSender(EventMessage message) {
        if (message != null && !hasText(message.getSenderId())) {
            message.setSenderId(nonNull(config.getNodeId()));
        }
    }

    private static byte[] serialize(EventMessage message, String recipientId) {
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event_type", message.getEventType());
        data.put("eventType", message.getEventType());
        data.put("payload", message.getPayload());
        data.put("sender_id", message.getSenderId());
        data.put("senderId", message.getSenderId());
        if (recipientId != null) {
            data.put("_recipient_id", recipientId);
        }
        try {
            return MAPPER.writeValueAsBytes(data);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to serialize event message", error);
        }
    }

    private static EventMessage deserialize(byte[] payload) {
        try {
            Map<String, Object> data = MAPPER.readValue(payload, MAP_TYPE);
            EventMessage message = new EventMessage(
                    firstString(data, "event_type", "eventType"),
                    mapValue(data.get("payload")));
            message.setSenderId(firstString(data, "sender_id", "senderId"));
            return message;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to deserialize event message", error);
        }
    }

    private static String firstString(Map<String, Object> data, String first, String second) {
        Object value = data.get(first);
        if (value == null) {
            value = data.get(second);
        }
        return value != null ? String.valueOf(value) : "";
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

    private static int timeoutMillis(double seconds) {
        return Math.max(1, (int) Math.round(seconds * 1000.0));
    }

    private static boolean metadataBoolean(MessagerTransportConfig config, String key) {
        Object value = config.getMetadata().get(key);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String nonNull(String value) {
        return value != null ? value : "";
    }

    private static void sendMultipart(ZMQ.Socket socket, List<byte[]> frames) {
        if (socket == null || frames == null || frames.isEmpty()) {
            return;
        }
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
        private volatile boolean running;

        private P2PLayer(MessagerTransportConfig config) {
            this.config = config;
            for (MessagerPeerConfig peer : config.getBootstrapPeers()) {
                registerPeer(peer);
            }
            for (MessagerPeerConfig peer : config.getKnownPeers()) {
                registerPeer(peer);
            }
        }

        void registerPeer(MessagerPeerConfig peer) {
            if (peer != null && hasText(peer.getAgentId())) {
                peerBook.put(peer.getAgentId(), peer);
            }
        }

        void start(ZContext newContext) {
            if (running) {
                return;
            }
            context = newContext;
            running = true;
            if (hasText(config.getDirectAddr())) {
                router = context.createSocket(SocketType.ROUTER);
                router.setReceiveTimeOut(100);
                router.bind(config.getDirectAddr());
                routerThread = new Thread(this::recvLoop, "pyzmq-p2p-" + nonNull(config.getNodeId()));
                routerThread.setDaemon(true);
                routerThread.start();
            }
        }

        void stop() {
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
            MessagerPeerConfig peer = resolvePeer(agentId);
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
                    throw new IllegalStateException("Timed out waiting for pyzmq ACK from recipient '" + agentId + "'");
                }
            } finally {
                dealer.close();
            }
        }

        void registerHandler(String agentId, MessagerHandler handler) {
            if (hasText(agentId) && handler != null) {
                handlers.put(agentId, handler);
            }
        }

        void unregisterHandler(String agentId) {
            if (agentId != null) {
                handlers.remove(agentId);
            }
        }

        private void recvLoop() {
            while (running && router != null) {
                try {
                    List<byte[]> frames = recvMultipart(router);
                    if (frames.size() < 2) {
                        continue;
                    }
                    byte[] identity = frames.get(0);
                    byte[] payload = frames.get(frames.size() - 1);
                    handleRequest(identity, payload);
                } catch (RuntimeException ignored) {
                    if (!running) {
                        break;
                    }
                }
            }
        }

        private void handleRequest(byte[] identity, byte[] payload) {
            try {
                Map<String, Object> data = MAPPER.readValue(payload, MAP_TYPE);
                String recipientId = String.valueOf(data.getOrDefault("_recipient_id", ""));
                MessagerHandler handler = handlers.get(recipientId);
                if (handler != null) {
                    handler.handle(deserialize(payload));
                }
            } catch (Exception ignored) {
                // Python suppresses malformed inbound P2P payloads and still ACKs.
            } finally {
                if (router != null) {
                    router.sendMore(identity);
                    router.send("ok");
                }
            }
        }

        private MessagerPeerConfig resolvePeer(String agentId) {
            MessagerPeerConfig peer = peerBook.get(agentId);
            if (peer != null && !peer.getAddrs().isEmpty()) {
                return peer;
            }
            throw new IllegalStateException(
                    "Unknown zmq route for recipient '" + agentId + "'. Provide a known peer entry.");
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
        private volatile boolean running;

        private PubSubLayer(MessagerTransportConfig config) {
            this.config = config;
        }

        void start(ZContext context) {
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
                proxyThread = new Thread(this::proxyLoop, "pyzmq-pubsub-proxy-" + nonNull(config.getNodeId()));
                proxyThread.setDaemon(true);
                proxyThread.start();
            }

            pub = context.createSocket(SocketType.PUB);
            pub.connect(publishAddr);
            sub = context.createSocket(SocketType.SUB);
            sub.setReceiveTimeOut(100);
            sub.connect(subscribeAddr);
            subThread = new Thread(this::recvLoop, "pyzmq-pubsub-" + nonNull(config.getNodeId()));
            subThread.setDaemon(true);
            subThread.start();
        }

        void stop() {
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

        SubscriptionHandle subscribe(String topic, MessagerHandler handler) {
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

        void unsubscribe(SubscriptionHandle handle) {
            handlers.remove(handle.getTopic());
            if (sub != null) {
                sub.unsubscribe(handle.getTopic().getBytes(StandardCharsets.UTF_8));
            }
            subscriptions.remove(handle.getSubscriptionId());
        }

        private void recvLoop() {
            while (running && sub != null) {
                try {
                    List<byte[]> frames = recvMultipart(sub);
                    if (frames.size() < 2) {
                        continue;
                    }
                    String topic = new String(frames.get(0), StandardCharsets.UTF_8);
                    MessagerHandler handler = handlers.get(topic);
                    if (handler != null) {
                        handler.handle(deserialize(frames.get(1)));
                    }
                } catch (RuntimeException ignored) {
                    if (!running) {
                        break;
                    }
                }
            }
        }

        private void proxyLoop() {
            while (running && xsub != null && xpub != null) {
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
            } catch (RuntimeException ignored) {
                if (!running) {
                    throw ignored;
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

        private static void close(ZMQ.Socket socket) {
            if (socket != null) {
                socket.close();
            }
        }
    }
}
