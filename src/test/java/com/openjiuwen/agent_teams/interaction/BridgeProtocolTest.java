/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Missing-test parity coverage for bridge protocol sentinels, errors, and adapter shape.
 *
 * <p>Mirrors Python's {@code test_bridge_protocol} in
 * {@code tests/unit_tests/agent_teams/interaction/test_bridge_protocol.py}.</p>
 */
class BridgeProtocolTest {

    @Test
    void remoteUnavailableSentinelIsStableString() {
        Object sentinel = BridgeProtocol.REMOTE_UNAVAILABLE_SENTINEL;

        assertThat(sentinel).isInstanceOf(String.class);
        assertThat((String) sentinel).isNotBlank();
    }

    @Test
    void bridgeNotEnabledIsRuntimeError() {
        assertThat(BridgeAgentNotEnabledError.class).isAssignableTo(RuntimeException.class);
    }

    @Test
    void unknownBridgeAgentIsRuntimeError() {
        assertThat(UnknownBridgeAgentError.class).isAssignableTo(RuntimeException.class);
    }

    @Test
    void conformingAdapterPassesShapeCheck() {
        assertThat(BridgeProtocolAdapter.isAdapter(new ConformingAdapter())).isTrue();
    }

    @Test
    void missingMethodFailsShapeCheck() {
        assertThat(BridgeProtocolAdapter.isAdapter(new MissingRelayAdapter())).isFalse();
    }

    @Test
    void plainObjectFailsShapeCheck() {
        assertThat(BridgeProtocolAdapter.isAdapter(new Object())).isFalse();
    }

    @Test
    void implementingAdapterRelayReturnsText() {
        BridgeProtocolAdapter adapter = new ImplementingAdapter();

        String reply = adapter.relay("codex", "hello").toCompletableFuture().join();

        assertThat(reply).isEqualTo("echo: hello");
    }

    static final class ConformingAdapter {

        public CompletionStage<Void> connect(
                String memberName,
                Map<String, Object> adapterConfig,
                String bridgePersona,
                String teamOverview
        ) {
            return CompletableFuture.completedFuture(null);
        }

        public CompletionStage<String> relay(String memberName, String text) {
            return CompletableFuture.completedFuture("echo: " + text);
        }

        public CompletionStage<Void> close() {
            return CompletableFuture.completedFuture(null);
        }
    }

    static final class MissingRelayAdapter {

        public CompletionStage<Void> connect(
                String memberName,
                Map<String, Object> adapterConfig,
                String bridgePersona,
                String teamOverview
        ) {
            return CompletableFuture.completedFuture(null);
        }

        public CompletionStage<Void> close() {
            return CompletableFuture.completedFuture(null);
        }
    }

    static final class ImplementingAdapter implements BridgeProtocolAdapter {

        @Override
        public CompletionStage<Void> connect(
                String memberName,
                Map<String, Object> adapterConfig,
                String bridgePersona,
                String teamOverview
        ) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<String> relay(String memberName, String text) {
            return CompletableFuture.completedFuture("echo: " + text);
        }

        @Override
        public CompletionStage<Void> close() {
            return CompletableFuture.completedFuture(null);
        }
    }
}
