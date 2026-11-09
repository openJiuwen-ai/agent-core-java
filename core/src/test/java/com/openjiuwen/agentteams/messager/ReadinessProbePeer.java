/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import org.zeromq.ZMsg;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns a real ROUTER and PUB socket with controlled readiness acknowledgements on a dedicated thread.
 *
 * @since 2026-11-09
 */
final class ReadinessProbePeer implements AutoCloseable {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final CompletableFuture<String> endpoint = new CompletableFuture<>();

    private final CompletableFuture<Void> rejected = new CompletableFuture<>();

    private final AtomicReference<String> acknowledgement = new AtomicReference<>("error:handler_unavailable");

    private final AtomicBoolean isClosed = new AtomicBoolean();

    private final AtomicInteger requests = new AtomicInteger();

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), action -> {
                Thread thread = new Thread(action, "readiness-peer-fixture");
                thread.setDaemon(true);
                return thread;
            });

    private final Future<?> serving;

    ReadinessProbePeer(String publishEndpoint) {
        serving = executor.submit(() -> serve(publishEndpoint));
    }

    MessagerPeerConfig peer() throws ExecutionException, InterruptedException, TimeoutException {
        return MessagerPeerConfig.builder().agentId("controlled-peer")
                .addrs(List.of(endpoint.get(3, TimeUnit.SECONDS)))
                .metadata(Map.of("readiness_publisher", true)).build();
    }

    void awaitRejection() throws ExecutionException, InterruptedException, TimeoutException {
        rejected.get(3, TimeUnit.SECONDS);
    }

    void accept() {
        acknowledgement.set("ok");
    }

    void rejectPermanently() {
        acknowledgement.set("error:invalid_configuration");
    }

    int requests() {
        return requests.get();
    }

    @Override
    public void close() throws ExecutionException, InterruptedException, TimeoutException {
        isClosed.set(true);
        executor.shutdown();
        try {
            serving.get(3, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Readiness peer fixture did not stop");
        }
    }

    private void serve(String publishEndpoint) {
        try (ZContext context = new ZContext()) {
            context.setLinger(0);
            ZMQ.Socket router = context.createSocket(SocketType.ROUTER);
            router.setReceiveTimeOut(50);
            int port = router.bindToRandomPort("tcp://127.0.0.1");
            ZMQ.Socket publisher = context.createSocket(SocketType.PUB);
            if (port < 1 || !publisher.connect(publishEndpoint)) {
                throw new IllegalStateException("Readiness peer fixture could not bind or connect");
            }
            endpoint.complete("tcp://127.0.0.1:" + port);
            while (!isClosed.get()) {
                receive(router, publisher);
            }
        } catch (IOException | IllegalStateException | IllegalArgumentException | ZMQException failure) {
            endpoint.completeExceptionally(failure);
            rejected.completeExceptionally(failure);
            throw new IllegalStateException("Readiness peer fixture failed", failure);
        }
    }

    private void receive(ZMQ.Socket router, ZMQ.Socket publisher) throws IOException {
        ZMsg frames = ZMsg.recvMsg(router);
        if (frames == null) {
            return;
        }
        try {
            Map<String, Object> wire = MAPPER.readValue(frames.getLast().getData(), MAP);
            requests.incrementAndGet();
            String reply = acknowledgement.get();
            if (!router.sendMore(frames.getFirst().getData()) || !router.send(reply)) {
                throw new IllegalStateException("Readiness peer acknowledgement failed");
            }
            if (reply.startsWith("error:")) {
                rejected.complete(null);
            } else {
                publishProbe(publisher, wire);
            }
        } finally {
            frames.destroy();
        }
    }

    private void publishProbe(ZMQ.Socket publisher, Map<String, Object> wire) throws IOException {
        if (!(wire.get("payload") instanceof Map<?, ?> payload)
                || !(payload.get("topic") instanceof String topic)) {
            throw new IllegalStateException("Readiness request has no topic");
        }
        Map<String, Object> message = Map.of("event_type", "__jiuwen_subscription_ready_v1",
                "sender_id", "controlled-peer", "payload",
                Map.of("nonce", payload.get("nonce"), "subscriber", payload.get("subscriber")));
        if (!publisher.sendMore(topic) || !publisher.send(MAPPER.writeValueAsBytes(message))) {
            throw new IllegalStateException("Readiness peer publication failed");
        }
    }
}
