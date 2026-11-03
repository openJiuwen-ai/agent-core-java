/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remote_client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Focused tests for remote-client package factory behavior.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/core/runner/drunner/remote_client/__init__.py} and facade behavior in
 * {@code openjiuwen/core/runner/drunner/remote_client/remote_agent.py}.</p>
 */
class RemoteClientFactoryTest {

    @AfterEach
    void cleanup() {
        RemoteClientFactory.clearCustomRemoteClientsForTest();
    }

    @Test
    void builtinMqProtocolCreatesMqRemoteClient() {
        RemoteClientConfig config = RemoteClientConfig.builder()
                .id("agent-1")
                .topic("topic-1")
                .build();

        RemoteClient client = RemoteClientFactory.createRemoteClient(ProtocolEnum.MQ, config);

        assertThat(client).isInstanceOf(MqRemoteClient.class);
    }

    @Test
    void customRegistrationTakesPriorityAfterBootstrapAttempt() {
        RemoteClient expected = client();
        RemoteClientFactory.registerRemoteClient("A2A", ignored -> expected);

        RemoteClient actual = RemoteClientFactory.createRemoteClient(ProtocolEnum.A2A, RemoteClientConfig.builder().build());

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void remoteAgentBuildsDefaultTopicAndDelegatesInvoke() {
        RemoteClient expected = client();
        RemoteClientFactory.registerRemoteClient("A2A", ignored -> expected);
        RemoteAgent agent = new RemoteAgent("agent-1", "v1", null, null, ProtocolEnum.A2A, null);

        assertThat(agent.getTopic()).isEqualTo("openjiuwen.single_agent.agent-1.v1");
        assertThat(agent.invoke(Map.of("input", true)).toCompletableFuture().join())
                .containsEntry("ok", true);
    }

    @Test
    void cancelTaskIsRestrictedToA2aProtocol() {
        RemoteAgent agent = new RemoteAgent("agent-1", "", null, "topic", ProtocolEnum.MQ, null);

        assertThatThrownBy(() -> agent.cancelTask("task-1"))
                .isInstanceOf(com.openjiuwen.core.common.exception.BaseError.class)
                .hasMessageContaining("cancel_task is only supported for A2A remote agents");
    }

    private static RemoteClient client() {
        return new RemoteClient() {
            @Override
            public CompletionStage<Void> start() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> stop() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public boolean isStarted() {
                return true;
            }

            @Override
            public CompletionStage<Map<String, Object>> invoke(Map<String, Object> inputs, Double timeoutSeconds) {
                return CompletableFuture.completedFuture(Map.of("ok", true, "inputs", inputs));
            }

            @Override
            public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) {
                return java.util.List.of().iterator();
            }
        };
    }
}
