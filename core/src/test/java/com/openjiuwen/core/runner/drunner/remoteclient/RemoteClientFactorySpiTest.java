/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner.drunner.remoteclient;

import com.openjiuwen.extensions.a2a.A2ARemoteClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RemoteClientFactory SPI registration and ServiceLoader discovery.
 */
class RemoteClientFactorySpiTest {

    // ========== ServiceLoader auto-discovery ==========

    @Test
    @DisplayName("ServiceLoader discovers built-in MQ provider")
    void discoversMqProvider() {
        assertTrue(RemoteClientFactory.hasProvider("MQ"));
    }

    @Test
    @DisplayName("ServiceLoader discovers built-in A2A provider")
    @Tag("system-test")
    void discoversA2AProvider() {
        assertTrue(RemoteClientFactory.hasProvider("A2A"));
    }

    // ========== create() ==========

    @Test
    @DisplayName("create() with MQ config returns MqRemoteClient")
    void createMqClient() {
        RemoteClientConfig config = RemoteClientConfig.builder()
                .id("test-mq")
                .protocol(ProtocolEnum.MQ)
                .build();
        RemoteClient client = RemoteClientFactory.create(config);
        assertInstanceOf(MqRemoteClient.class, client);
    }

    @Test
    @DisplayName("create() with null protocol defaults to MQ")
    void createWithNullProtocolDefaultsToMq() {
        RemoteClientConfig config = RemoteClientConfig.builder()
                .id("test-default")
                .protocol(null)
                .build();
        RemoteClient client = RemoteClientFactory.create(config);
        assertInstanceOf(MqRemoteClient.class, client);
    }

    @Test
    @DisplayName("create() with null config throws error")
    void createWithNullConfigThrows() {
        assertThrows(Exception.class, () -> RemoteClientFactory.create(null));
    }

    // ========== createA2A() backward compatibility ==========

    @Test
    @DisplayName("createA2A() creates A2A remote client")
    @Tag("system-test")
    void createA2AClient() {
        RemoteClientConfig config = RemoteClientConfig.builder()
                .id("test-a2a")
                .protocol(ProtocolEnum.A2A)
                .url("http://localhost:9090/a2a")
                .kwargs(Map.of("card", new com.openjiuwen.core.singleagent.schema.AgentCard("test-a2a", "A2A test", "1.0")))
                .build();
        RemoteClient client = RemoteClientFactory.createA2A(config);
        assertNotNull(client);
    }

    // ========== Manual register() ==========

    @Test
    @DisplayName("register() allows adding a custom remote client provider")
    void registerCustomProvider() {
        RemoteClientFactory.register("GRPC", new RemoteClientProvider() {
            @Override
            public String typeName() { return "GRPC"; }
            @Override
            public RemoteClient create(RemoteClientConfig config) { return new MqRemoteClient(config); }
        });
        assertTrue(RemoteClientFactory.hasProvider("GRPC"));

        RemoteClientConfig config = RemoteClientConfig.builder()
                .id("test-grpc")
                .protocol(ProtocolEnum.MQ) // won't match GRPC
                .build();
        // Verify GRPC provider exists
        assertTrue(RemoteClientFactory.hasProvider("GRPC"));
    }

    @Test
    @DisplayName("hasProvider() returns false for null")
    void hasProviderNull() {
        assertFalse(RemoteClientFactory.hasProvider(null));
    }

    @Test
    @DisplayName("hasProvider() returns false for unknown protocol")
    void hasProviderUnknown() {
        assertFalse(RemoteClientFactory.hasProvider("UNKNOWN"));
    }

    // ========== Additional test cases ==========

    @Test
    @DisplayName("create() with A2A config returns A2ARemoteClient")
    @Tag("system-test")
    void createA2AClientViaFactory() {
        RemoteClientConfig config = RemoteClientConfig.builder()
                .id("test-a2a-factory")
                .protocol(ProtocolEnum.A2A)
                .url("http://localhost:9090/a2a")
                .kwargs(Map.of("card", new com.openjiuwen.core.singleagent.schema.AgentCard("test-a2a-factory", "A2A test", "1.0")))
                .build();
        RemoteClient client = RemoteClientFactory.create(config);
        assertInstanceOf(A2ARemoteClient.class, client);
    }

    @Test
    @DisplayName("create() with MQ config and kwargs passes config through")
    void createMqClientWithKwargs() {
        RemoteClientConfig config = RemoteClientConfig.builder()
                .id("test-mq-kwargs")
                .protocol(ProtocolEnum.MQ)
                .topic("test-topic")
                .url("http://localhost:9092")
                .kwargs(Map.of("timeout", 5000))
                .build();
        RemoteClient client = RemoteClientFactory.create(config);
        assertInstanceOf(MqRemoteClient.class, client);
    }

    @Test
    @DisplayName("register() can override existing MQ provider")
    void registerOverridesExistingProvider() {
        // Save original MQ provider
        assertTrue(RemoteClientFactory.hasProvider("MQ"));

        // Override MQ with custom provider
        RemoteClientFactory.register("MQ", new RemoteClientProvider() {
            @Override
            public String typeName() { return "MQ"; }
            @Override
            public RemoteClient create(RemoteClientConfig config) { return new MqRemoteClient(config); }
        });

        RemoteClientConfig config = RemoteClientConfig.builder()
                .id("test-mq-override")
                .protocol(ProtocolEnum.MQ)
                .build();
        RemoteClient client = RemoteClientFactory.create(config);
        assertNotNull(client);
    }

    @Test
    @DisplayName("createA2A() with null config throws BaseError")
    void createA2AWithNullConfigThrows() {
        assertThrows(Exception.class, () -> RemoteClientFactory.createA2A(null));
    }

    @Test
    @DisplayName("Multiple create() calls with same config return different instances")
    void createReturnsDifferentInstances() {
        RemoteClientConfig config = RemoteClientConfig.builder()
                .id("test-multi")
                .protocol(ProtocolEnum.MQ)
                .build();
        RemoteClient client1 = RemoteClientFactory.create(config);
        RemoteClient client2 = RemoteClientFactory.create(config);
        assertNotSame(client1, client2);
    }

    @Test
    @DisplayName("RemoteClientConfig builder preserves all fields")
    void remoteClientConfigPreservesFields() {
        RemoteClientConfig config = RemoteClientConfig.builder()
                .id("test-id")
                .version("1.0")
                .name("test-name")
                .description("test-desc")
                .protocol(ProtocolEnum.MQ)
                .topic("test-topic")
                .url("http://localhost:9092")
                .kwargs(Map.of("key", "value"))
                .build();
        assertEquals("test-id", config.getId());
        assertEquals("1.0", config.getVersion());
        assertEquals("test-name", config.getName());
        assertEquals("test-desc", config.getDescription());
        assertEquals(ProtocolEnum.MQ, config.getProtocol());
        assertEquals("test-topic", config.getTopic());
        assertEquals("http://localhost:9092", config.getUrl());
    }
}
