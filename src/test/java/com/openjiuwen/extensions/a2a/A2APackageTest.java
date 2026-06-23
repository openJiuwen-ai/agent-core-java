/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.runner.drunner.remote_client.RemoteClient;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteClientConfig;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteClientFactory;
import com.openjiuwen.core.runner.drunner.server_adapter.ServerAdapterRegistry;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.a2a.A2AClient.A2AClientTransport;
import com.openjiuwen.extensions.a2a.A2AClient.A2AEventStream;
import com.openjiuwen.extensions.a2a.A2AClient.CancelTaskRequest;
import com.openjiuwen.extensions.a2a.A2AClient.ClientConfig;
import com.openjiuwen.extensions.a2a.A2ATransformer.SendMessageRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code openjiuwen.extensions.a2a} module in
 * {@code openjiuwen/extensions/a2a/__init__.py}.
 */
class A2APackageTest {

    @AfterEach
    void cleanup() {
        RemoteClientFactory.clearCustomRemoteClientsForTest();
        clearServerAdaptersForTest();
    }

    @Test
    void exposesPythonModuleAndRegisteredFactoryNames() {
        assertThat(A2APackage.PYTHON_MODULE).isEqualTo("openjiuwen/extensions/a2a/__init__.py");
        assertThat(A2APackage.PROTOCOL).isEqualTo("A2A");
        assertThat(A2APackage.REGISTERED_FACTORIES)
                .containsExactly("create_a2a_remote_client", "create_a2a_server_adapter");
    }

    @Test
    void createA2aRemoteClientBuildsTranslatedClientFromConfig() {
        RecordingFactory factory = new RecordingFactory();
        RemoteClientConfig config = RemoteClientConfig.builder()
                .id("a2a-agent")
                .url("http://127.0.0.1:41241")
                .kwargs(Map.of("card", card(), "clientFactory", factory))
                .build();

        A2ARemoteClient client = A2APackage.createA2aRemoteClient(config);

        assertThat(client).isInstanceOf(A2ARemoteClient.class);
        assertThat(factory.card).isInstanceOf(A2AAgentCardAdapter.A2aAgentCard.class);
    }

    @Test
    void createA2aServerAdapterBuildsTranslatedAdapterFromKwargs() {
        A2AServerAdapter adapter = A2APackage.createA2aServerAdapter(Map.of(
                "adapter_id", "adapter-a2a",
                "agent_card", card(),
                "interface_url", "http://127.0.0.1:8123/a2a/rest"
        ));

        assertThat(adapter).isInstanceOf(A2AServerAdapter.class);
        assertThat(adapter.getAdapterId()).isEqualTo("adapter-a2a");
        assertThat(adapter.getProtocolBinding()).isEqualTo("HTTP+JSON");
        assertThat(adapter.getServeHost()).isEqualTo("127.0.0.1");
        assertThat(adapter.getServePort()).isEqualTo(8123);
    }

    @Test
    void registerAllRegistersRemoteClientAndServerAdapterFactories() {
        RemoteClientFactory.clearCustomRemoteClientsForTest();
        A2APackage.registerAll();
        RecordingFactory factory = new RecordingFactory();

        RemoteClient remoteClient = RemoteClientFactory.createRemoteClient("A2A", RemoteClientConfig.builder()
                .id("a2a-agent")
                .url("http://127.0.0.1:41241")
                .kwargs(Map.of("card", card(), "clientFactory", factory))
                .build());
        Object serverAdapter = ServerAdapterRegistry.createServerAdapter("A2A", Map.of(
                "adapter_id", "adapter-a2a",
                "agent_card", card()
        ));

        assertThat(remoteClient).isInstanceOf(A2ARemoteClient.class);
        assertThat(serverAdapter).isInstanceOf(A2AServerAdapter.class);
    }

    private static AgentCard card() {
        return new AgentCard("a2a-agent", "A2A Agent", "package test");
    }

    @SuppressWarnings("unchecked")
    private static void clearServerAdaptersForTest() {
        try {
            Field field = ServerAdapterRegistry.class.getDeclaredField("CUSTOM_SERVER_ADAPTERS");
            field.setAccessible(true);
            ((Map<String, ?>) field.get(null)).clear();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class RecordingFactory implements A2AClient.A2AClientFactory {
        private Object card;

        @Override
        public A2AClientTransport create(ClientConfig config, Object card) {
            this.card = card;
            return new NoopTransport();
        }
    }

    private static final class NoopTransport implements A2AClientTransport {
        @Override
        public A2AEventStream sendMessage(SendMessageRequest request) {
            return new EmptyEventStream();
        }

        @Override
        public CompletionStage<Object> cancelTask(CancelTaskRequest request) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> close() {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class EmptyEventStream implements A2AEventStream {
        private final Iterator<Object> delegate = List.of().iterator();

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Object next() {
            return delegate.next();
        }

        @Override
        public void close() {
        }
    }
}
