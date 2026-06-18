/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.single_agent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code A2AServerAdapter} in
 * {@code openjiuwen/extensions/a2a/a2a_server_adapter.py}.
 */
class A2AServerAdapterTest {

    @Test
    void constructorShouldRequireAgentCard() {
        assertThatThrownBy(() -> new A2AServerAdapter("adapter", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agent_card is required");
    }

    @Test
    void adapterShouldInferJsonrpcFromMissingInterfaceUrl() {
        A2AServerAdapter adapter = new A2AServerAdapter("adapter-jsonrpc", agentCard(null));

        assertThat(adapter.getProtocolBinding()).isEqualTo("JSONRPC");
        assertThat(adapter.getServeHost()).isNull();
        assertThat(adapter.getServePort()).isNull();
    }

    @Test
    void adapterShouldInferRestAndParseInterfaceUrlFromAgentCard() {
        A2AServerAdapter adapter = new A2AServerAdapter(
                "adapter-rest",
                "",
                agentCard("http://127.0.0.1:8123/a2a/rest"),
                null,
                null,
                null,
                A2AServerAdapter.DEFAULT_RPC_URL,
                A2AServerAdapter.DEFAULT_REST_URL);

        assertThat(adapter.getProtocolBinding()).isEqualTo("HTTP+JSON");
        assertThat(adapter.getServeHost()).isEqualTo("127.0.0.1");
        assertThat(adapter.getServePort()).isEqualTo(8123);
        assertThat(adapter.getServer().getTransportProtocols()).containsExactly(A2AServer.TransportProtocol.HTTP_JSON);
    }

    @Test
    void adapterShouldDefaultParsedPortTo8000() {
        A2AServerAdapter.HostPort hostPort = A2AServerAdapter.parseInterfaceUrl("http://example.com/a2a/jsonrpc/");

        assertThat(hostPort.host()).isEqualTo("example.com");
        assertThat(hostPort.port()).isEqualTo(8000);
    }

    @Test
    void adapterShouldRejectGrpcPath() {
        assertThatThrownBy(() -> A2AServerAdapter.inferProtocolBinding("http://example.com/a2a/grpc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gRPC transport is not supported");
    }

    @Test
    void startAndStopShouldManageServerStateAndThread() {
        A2AServerAdapter adapter = new A2AServerAdapter("adapter-start", agentCard(null));

        adapter.start();

        assertThat(adapter.isActive()).isTrue();
        assertThat(adapter.getApp()).isNotNull();
        assertThat(adapter.getRestApp()).isNull();
        assertThat(adapter.getServeThread()).isNotNull();

        adapter.stop().toCompletableFuture().join();

        assertThat(adapter.isActive()).isFalse();
        assertThat(adapter.getServeThread()).isNull();
    }

    private static AgentCard agentCard(String interfaceUrl) {
        AgentCard card = new AgentCard("a2a-agent", "A2A Agent", "adapter test");
        card.setInterfaceUrl(interfaceUrl);
        return card;
    }
}
