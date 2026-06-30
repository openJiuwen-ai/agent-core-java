package com.openjiuwen.core.common.clients;

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientRegistryTest {

    @AfterEach
    void tearDown() {
        ClientRegistry.getInstance().resetForTests();
    }

    @Test
    void shouldRegisterAndResolveFactory() throws Exception {
        ClientRegistry registry = ClientRegistry.getInstance();
        registry.registerClient("sample", "test", kwargs -> Map.of("ok", true, "kwargs", kwargs));

        Object created = registry.getClient("sample", "test", Map.of("value", 1));
        assertThat(created).isEqualTo(Map.of("ok", true, "kwargs", Map.of("value", 1)));
        assertThat(registry.listClients()).contains("test_sample");
    }

    @Test
    void shouldRegisterClientClassByStaticMetadata() throws Exception {
        ClientRegistry registry = ClientRegistry.getInstance();
        registry.registerClass(TestClient.class);

        Object created = registry.getClient("mysql", "database", Map.of("host", "localhost"));
        assertThat(created).isInstanceOf(TestClient.class);
        assertThat(((TestClient) created).receivedConfig).containsEntry("host", "localhost");
    }

    @Test
    void builtinsShouldBeAvailable() throws Exception {
        ClientRegistry registry = ClientRegistry.getInstance();

        Object openAi = registry.getClient("openai", "common", Map.of(
                "config", Map.of(
                        "client_provider", "openai",
                        "api_key", "test-key",
                        "api_base", "https://example.invalid/v1"
                )
        ));

        assertThat(registry.listClients()).contains("common_http", "common_httpx", "common_openai", "common_async_openai");
        assertThat(openAi).isInstanceOf(OpenAIClient.class);
    }

    @Test
    void facadeShouldExposeSingletonsAndFactories() {
        assertThat(Clients.getClientRegistry()).isSameAs(ClientRegistry.getInstance());
        assertThat(Clients.getConnectorPoolManager()).isSameAs(ConnectorPoolManager.getInstance());
        assertThat(Clients.getHttpSessionManager()).isSameAs(HttpSessionManager.getInstance());
        assertThat(Clients.createOpenAiClient(
                com.openjiuwen.core.foundation.llm.schema.ModelClientConfig.builder()
                        .clientProvider("openai")
                        .apiKey("test")
                        .apiBase("https://example.invalid/v1")
                        .build()
        )).isInstanceOf(OpenAIClient.class);
    }

    @Test
    void shouldRejectUnknownClient() {
        assertThatThrownBy(() -> ClientRegistry.getInstance().getClient("missing", "common"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown client type");
    }

    public static final class TestClient extends BaseClient {
        public static final String __client_name__ = "mysql";
        public static final String __client_type__ = "database";

        private final Map<String, Object> receivedConfig;

        public TestClient(Map<String, Object> config) {
            this.receivedConfig = config;
        }
    }
}
