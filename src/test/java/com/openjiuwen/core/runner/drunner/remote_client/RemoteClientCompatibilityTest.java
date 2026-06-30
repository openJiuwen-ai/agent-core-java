package com.openjiuwen.core.runner.drunner.remote_client;

import com.openjiuwen.extensions.a2a.A2ARemoteClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteClientCompatibilityTest {

    @Test
    void remoteClientFactoryShouldCreateMqByDefault() {
        RemoteClient client = RemoteClientFactory.create(RemoteClientConfig.builder()
                .id("agent-1")
                .topic("agent.agent-1")
                .build());

        assertThat(client).isInstanceOf(MqRemoteClient.class);
        assertThat(client.isStarted()).isFalse();
        assertThat(client.isStopped()).isTrue();
    }

    @Test
    void remoteClientFactoryShouldCreateA2AClient() {
        RemoteClient client = RemoteClientFactory.createA2A(RemoteClientConfig.builder()
                .id("agent-a2a")
                .protocol(ProtocolEnum.A2A)
                .url("http://127.0.0.1:8080")
                .build());

        assertThat(client).isInstanceOf(A2ARemoteClient.class);
        assertThat(client.isStarted()).isFalse();
    }

    @Test
    void remoteClientFactoryShouldRejectNullConfig() {
        assertThatThrownBy(() -> RemoteClientFactory.create(null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("remote client");
    }

    @Test
    void remoteAgentShouldExposeStartedAndStoppedLifecycle() {
        RemoteAgent agent = new RemoteAgent(
                "agent-2",
                "",
                null,
                "agent.agent-2",
                ProtocolEnum.MQ,
                Map.of()
        );

        assertThat(agent.isStarted()).isFalse();
        assertThat(agent.isStopped()).isTrue();

        agent.stop();

        assertThat(agent.isStarted()).isFalse();
        assertThat(agent.isStopped()).isTrue();
    }
}
