/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's in-process messager behavior in
 * {@code openjiuwen/agent_teams/messager/inprocess.py}.
 */
class InProcessMessagerTest {

    @AfterEach
    void cleanupBus() {
        InProcessMessager.cleanupInprocessBus();
    }

    @Test
    void publishAndDirectSendMirrorPythonDeliverySemantics() {
        MessagerTransportConfig leaderConfig = new MessagerTransportConfig();
        leaderConfig.setNodeId("leader");
        InProcessMessager leader = new InProcessMessager(leaderConfig);

        MessagerTransportConfig workerConfig = new MessagerTransportConfig();
        workerConfig.setNodeId("worker");
        InProcessMessager worker = new InProcessMessager(workerConfig);

        AtomicReference<EventMessage> published = new AtomicReference<>();
        AtomicReference<EventMessage> direct = new AtomicReference<>();

        worker.subscribe(
                "team:demo:broadcast",
                message -> completed(message, published)
        ).toCompletableFuture().join();
        worker.registerDirectMessageHandler(
                message -> completed(message, direct)
        ).toCompletableFuture().join();

        leader.publish(
                "team:demo:broadcast",
                new EventMessage("team_created", Map.of("team_name", "demo"), "")
        ).toCompletableFuture().join();
        leader.send(
                "worker",
                new EventMessage("message", Map.of("body", "hello"), "")
        ).toCompletableFuture().join();

        assertThat(published.get()).isNotNull();
        assertThat(published.get().getSenderId()).isEqualTo("leader");
        assertThat(direct.get()).isNotNull();
        assertThat(direct.get().getSenderId()).isEmpty();

        worker.unsubscribe("team:demo:broadcast").toCompletableFuture().join();
        published.set(null);
        leader.publish(
                "team:demo:broadcast",
                new EventMessage("team_created", Map.of("team_name", "demo"), "")
        ).toCompletableFuture().join();
        assertThat(published.get()).isNull();
    }

    private CompletionStage<Void> completed(EventMessage message, AtomicReference<EventMessage> target) {
        target.set(message);
        return CompletableFuture.completedFuture(null);
    }
}
