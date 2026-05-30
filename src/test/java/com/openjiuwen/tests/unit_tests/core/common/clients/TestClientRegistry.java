/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.core.common.clients;

import com.openjiuwen.core.common.clients.ClientRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_client_registry} in
 * {@code tests.unit_tests.core.common.clients.test_client_registry}.
 */
class TestClientRegistry {

    private ClientRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ClientRegistry();
    }

    @Test
    @Tag("level0")
    void testRegisterClientDecorator() {
        registry.registerClient("test_client", "test", kwargs -> createMockClient());

        assertTrue(registry.listClients().contains("test_test_client"));
    }

    @Test
    @Tag("level0")
    void testRegisterClass() {
        registry.registerClass(TestClient.class);

        String fullName = "database_mysql";
        assertTrue(registry.listClients().contains(fullName));

        TestClient instance = (TestClient) registry.getClient(fullName, Map.of("host", "localhost"));
        assertEquals("localhost", instance.config.get("host"));
    }

    @Test
    @Tag("level0")
    void testGetClientByName() {
        Object mockInstance = createMockClient();
        registry.registerClient("redis", "cache", kwargs -> mockInstance);

        assertSame(mockInstance, registry.getClient("redis", "cache"));
        registry.unregister("redis", "cache");
    }

    @Test
    @Tag("level0")
    void testGetClientWithoutClientType() {
        Object mockInstance = createMockClient();
        registry.registerClient("default_client", null, kwargs -> mockInstance);

        assertSame(mockInstance, registry.getClient("default_client"));
        registry.unregister("default_client", null);
    }

    @Test
    @Tag("level0")
    void testGetClientEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> registry.getClient("", (String) null));
    }

    @Test
    @Tag("level0")
    void testGetClientUnknown() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> registry.getClient("unknown", (String) null));
        assertTrue(thrown.getMessage().contains("Unknown client type"));
    }

    @Test
    @Tag("level0")
    void testGetClientCreationFailure() {
        registry.registerClient("failing", "test", kwargs -> {
            throw new RuntimeException("Creation failed");
        });

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> registry.getClient("failing", "test"));
        assertTrue(thrown.getMessage().contains("Failed to create client"));
    }

    @Test
    @Tag("level0")
    void testUnregister() {
        registry.registerClient("redis", "cache", kwargs -> createMockClient());

        registry.unregister("redis", "cache");

        assertFalse(registry.listClients().contains("cache_redis"));
    }

    @Test
    @Tag("level0")
    void testUnregisterNotFound() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> registry.unregister("nonexistent"));
        assertTrue(thrown.getMessage().contains("not registered"));
    }

    @Test
    @Tag("level0")
    void testListClients() {
        registry.registerClient("client1", "type1", kwargs -> createMockClient());
        registry.registerClient("client2", "type2", kwargs -> createMockClient());

        List<String> clients = registry.listClients();
        assertTrue(clients.contains("type1_client1"));
        assertTrue(clients.contains("type2_client2"));
    }

    private Object createMockClient() {
        return new Object();
    }

    private static class TestClient {
        static final String __client_name__ = "mysql";
        static final String __client_type__ = "database";

        private final Map<String, Object> config;

        TestClient(Map<String, Object> kwargs) {
            this.config = Map.copyOf(kwargs);
        }
    }
}
