/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's pyzmq-backed messager transport in
 * {@code openjiuwen/agent_teams/messager/pyzmq_backend.py}.
 */
class PyZmqMessagerTest {

    @Test
    void pubSubAndDirectSendMirrorPythonTransportFlow() throws Exception {
        String directLeader = tcpAddr(freePort());
        String directWorker = tcpAddr(freePort());
        String pubAddr = tcpAddr(freePort());
        String subAddr = tcpAddr(freePort());

        MessagerTransportConfig leaderConfig = new MessagerTransportConfig();
        leaderConfig.setBackend("pyzmq");
        leaderConfig.setNodeId("leader");
        leaderConfig.setDirectAddr(directLeader);
        leaderConfig.setPubsubPublishAddr(pubAddr);
        leaderConfig.setPubsubSubscribeAddr(subAddr);
        leaderConfig.setMetadata(Map.of("pubsub_bind", true));

        MessagerTransportConfig workerConfig = new MessagerTransportConfig();
        workerConfig.setBackend("pyzmq");
        workerConfig.setNodeId("worker");
        workerConfig.setDirectAddr(directWorker);
        workerConfig.setPubsubPublishAddr(pubAddr);
        workerConfig.setPubsubSubscribeAddr(subAddr);

        PyZmqMessager leader = new PyZmqMessager(leaderConfig);
        PyZmqMessager worker = new PyZmqMessager(workerConfig);
        leader.registerPeer(worker.localPeer());
        worker.registerPeer(leader.localPeer());

        AtomicReference<EventMessage> broadcast = new AtomicReference<>();
        AtomicReference<EventMessage> direct = new AtomicReference<>();
        CountDownLatch broadcastLatch = new CountDownLatch(1);
        CountDownLatch directLatch = new CountDownLatch(1);

        leader.start().toCompletableFuture().join();
        worker.start().toCompletableFuture().join();
        try {
            worker.subscribe(
                    "team:demo:broadcast",
                    message -> completed(message, broadcast, broadcastLatch)
            ).toCompletableFuture().join();
            worker.registerDirectMessageHandler(
                    message -> completed(message, direct, directLatch)
            ).toCompletableFuture().join();

            Thread.sleep(250L);

            leader.publish(
                    "team:demo:broadcast",
                    new EventMessage("broadcast", Map.of("team_name", "demo"), "")
            ).toCompletableFuture().join();
            assertThat(broadcastLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(broadcast.get()).isNotNull();
            assertThat(broadcast.get().getSenderId()).isEqualTo("leader");

            leader.send(
                    "worker",
                    new EventMessage("message", Map.of("team_name", "demo", "body", "hello"), "")
            ).toCompletableFuture().join();
            assertThat(directLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(direct.get()).isNotNull();
            assertThat(direct.get().getSenderId()).isEmpty();
        } finally {
            worker.stop().toCompletableFuture().join();
            leader.stop().toCompletableFuture().join();
        }
    }

    private CompletionStage<Void> completed(
            EventMessage message,
            AtomicReference<EventMessage> target,
            CountDownLatch latch
    ) {
        target.set(message);
        latch.countDown();
        return CompletableFuture.completedFuture(null);
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String tcpAddr(int port) {
        return "tcp://127.0.0.1:" + port;
    }
}
