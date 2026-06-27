/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.server_adapter;

import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.a2a.A2AServer;
import com.openjiuwen.extensions.a2a.A2AServerAdapter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TestA2AServerAdapter} in
 * {@code tests/unit_tests/core/runner/dunner/test_a2a_server_adapter.py}.
 */
class A2AServerAdapterPythonParityTest {

    private final List<RecordingMqServerAdapter> mqAdapters = new ArrayList<>();
    private Map<String, ServerAdapterFactory> registrySnapshot;

    @BeforeEach
    void setUp() {
        RunnerConfig.setRunnerConfig(RunnerConfig.builder().build());
        registrySnapshot = new LinkedHashMap<>(serverAdapters());
        serverAdapters().clear();
        AgentAdapter.setMqServerAdapterFactoryForTest((adapterId, topic, invokeHandler, streamHandler) -> {
            RecordingMqServerAdapter adapter = new RecordingMqServerAdapter(
                    adapterId, topic, invokeHandler, streamHandler);
            mqAdapters.add(adapter);
            return adapter;
        });
    }

    @AfterEach
    void tearDown() {
        RunnerConfig.setRunnerConfig(null);
        AgentAdapter.resetMqServerAdapterFactoryForTest();
        serverAdapters().clear();
        serverAdapters().putAll(registrySnapshot);
    }

    @Test
    void agentAdapterShouldSkipA2aServerAdapterWhenDisabled() {
        AgentAdapter adapter = new AgentAdapter("agent-0");

        adapter.start();

        assertThat(mqAdapters).hasSize(1);
        assertThat(mqAdapters.get(0).started).isTrue();
        assertThat(adapter.getA2aServer()).isNull();
        assertThat(serverAdapters()).doesNotContainKey("A2A");
    }

    @Test
    void agentAdapterShouldCreateA2aServerAdapter() {
        RecordingA2AServerAdapter fakeA2a = registerRecordingA2aFactory();
        RunnerConfig.setRunnerConfig(RunnerConfig.builder().enableA2a(true).build());

        AgentAdapter adapter = new AgentAdapter("agent-1", "", agentCard("agent-1", null));
        adapter.start();

        assertThat(mqAdapters.get(0).adapterId).isEqualTo("agent-1");
        assertThat(fakeA2a.kwargs).containsEntry("adapter_id", "agent-1");
        assertThat(fakeA2a.kwargs).containsEntry("version", "");
        assertThat(((AgentCard) fakeA2a.kwargs.get("agent_card")).getId()).isEqualTo("agent-1");
        assertThat(fakeA2a.protocol).isEqualTo("A2A");
        assertThat(fakeA2a.started).isTrue();
        assertThat(adapter.getA2aServer()).isSameAs(fakeA2a);
    }

    @Test
    void agentAdapterShouldRequireAgentCardWhenA2aEnabled() {
        RunnerConfig.setRunnerConfig(RunnerConfig.builder().enableA2a(true).build());

        assertThatThrownBy(() -> new AgentAdapter("agent-a2a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agent_card is required when enable_a2a is True");
    }

    @Test
    void agentAdapterShouldNotPassInterfaceUrlToCreateServerAdapter() {
        RecordingA2AServerAdapter fakeA2a = registerRecordingA2aFactory();
        RunnerConfig.setRunnerConfig(RunnerConfig.builder().enableA2a(true).build());
        String url = "http://127.0.0.1:8000/a2a/jsonrpc";
        AgentCard card = agentCard("agent-1", url);

        AgentAdapter adapter = new AgentAdapter("agent-1", "", card);
        adapter.start();

        assertThat(fakeA2a.kwargs).doesNotContainKey("interface_url");
        assertThat(((AgentCard) fakeA2a.kwargs.get("agent_card")).getInterfaceUrl()).isEqualTo(url);
        assertThat(adapter.getInterfaceUrl()).isEqualTo(url);
        assertThat(((AgentCard) fakeA2a.kwargs.get("agent_card")).getId()).isEqualTo("agent-1");
        assertThat(fakeA2a.protocol).isEqualTo("A2A");
        assertThat(fakeA2a.started).isTrue();
    }

    @Test
    void agentAdapterShouldUseInterfaceUrlFromAgentCard() {
        RecordingA2AServerAdapter fakeA2a = registerRecordingA2aFactory();
        RunnerConfig.setRunnerConfig(RunnerConfig.builder().enableA2a(true).build());
        String url = "http://127.0.0.1:8010/a2a/jsonrpc/";

        AgentAdapter adapter = new AgentAdapter("agent-1", "", agentCard("agent-1", url));
        adapter.start();

        assertThat(fakeA2a.kwargs).doesNotContainKey("interface_url");
        assertThat(((AgentCard) fakeA2a.kwargs.get("agent_card")).getInterfaceUrl()).isEqualTo(url);
    }

    @Test
    void a2aServerAdapterShouldInferJsonrpcProtocolBinding() {
        A2AServerAdapter adapter = new A2AServerAdapter(
                "agent-1",
                "",
                agentCard("agent-1", null),
                null,
                null,
                "http://127.0.0.1:8000/a2a/jsonrpc",
                A2AServerAdapter.DEFAULT_RPC_URL,
                A2AServerAdapter.DEFAULT_REST_URL);

        assertThat(adapter.getProtocolBinding()).isEqualTo(A2AServer.TransportProtocol.JSONRPC.value());
    }

    @Test
    void a2aServerAdapterShouldRejectGrpcInterfaceUrl() {
        assertThatThrownBy(() -> new A2AServerAdapter(
                "agent-1",
                "",
                agentCard("agent-1", null),
                null,
                null,
                "http://127.0.0.1:8000/a2a/grpc",
                A2AServerAdapter.DEFAULT_RPC_URL,
                A2AServerAdapter.DEFAULT_REST_URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gRPC transport is not supported");
    }

    @Test
    void createServerAdapterShouldBootstrapA2aRegistrationWithoutPreimport() {
        serverAdapters().clear();

        Object adapter = ServerAdapterRegistry.createServerAdapter("A2A", Map.of(
                "adapter_id", "agent-bootstrap",
                "agent_card", agentCard("agent-bootstrap", null),
                "interface_url", "http://127.0.0.1:8000/a2a/jsonrpc"
        ));

        assertThat(serverAdapters()).containsKey("A2A");
        assertThat(adapter).isInstanceOf(A2AServerAdapter.class);
        A2AServerAdapter typedAdapter = (A2AServerAdapter) adapter;
        assertThat(typedAdapter.getAdapterId()).isEqualTo("agent-bootstrap");
        assertThat(typedAdapter.getAgentCard().getId()).isEqualTo("agent-bootstrap");
        assertThat(typedAdapter.getProtocolBinding()).isEqualTo(A2AServer.TransportProtocol.JSONRPC.value());
    }

    @Test
    void agentAdapterStopShouldStopBothAdapters() {
        RecordingA2AServerAdapter fakeA2a = registerRecordingA2aFactory();
        RunnerConfig.setRunnerConfig(RunnerConfig.builder().enableA2a(true).build());
        AgentAdapter adapter = new AgentAdapter("agent-2", "", agentCard("agent-2", null));

        adapter.start();
        adapter.stop().toCompletableFuture().join();

        assertThat(mqAdapters.get(0).stopped).isTrue();
        assertThat(fakeA2a.stopped).isTrue();
    }

    private RecordingA2AServerAdapter registerRecordingA2aFactory() {
        RecordingA2AServerAdapter fakeA2a = new RecordingA2AServerAdapter();
        ServerAdapterRegistry.registerServerAdapter("A2A", kwargs -> {
            fakeA2a.protocol = "A2A";
            fakeA2a.kwargs = new LinkedHashMap<>(kwargs);
            return fakeA2a;
        });
        return fakeA2a;
    }

    private static AgentCard agentCard(String id, String interfaceUrl) {
        AgentCard card = new AgentCard(id, id, "test agent");
        card.setInterfaceUrl(interfaceUrl);
        return card;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ServerAdapterFactory> serverAdapters() {
        try {
            Field field = ServerAdapterRegistry.class.getDeclaredField("CUSTOM_SERVER_ADAPTERS");
            field.setAccessible(true);
            return (Map<String, ServerAdapterFactory>) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class RecordingMqServerAdapter extends MqServerAdapter {
        private final String adapterId;
        private boolean started;
        private boolean stopped;

        private RecordingMqServerAdapter(String adapterId,
                                         String topic,
                                         Function<Map<String, Object>, Object> invokeHandler,
                                         Function<Map<String, Object>, Object> streamHandler) {
            super(adapterId, topic, invokeHandler, streamHandler);
            this.adapterId = adapterId;
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public CompletionStage<Void> stop() {
            stopped = true;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingA2AServerAdapter {
        private String protocol;
        private Map<String, Object> kwargs = Map.of();
        private boolean started;
        private boolean stopped;

        public void start() {
            started = true;
        }

        public CompletionStage<Void> stop() {
            stopped = true;
            return CompletableFuture.completedFuture(null);
        }
    }
}
