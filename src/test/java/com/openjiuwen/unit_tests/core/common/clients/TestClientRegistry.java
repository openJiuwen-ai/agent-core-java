/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.common.clients;

import com.openjiuwen.core.common.clients.ClientRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * Tests for client registry.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.common.clients.test_client_registry}.
 * Validates client registration, retrieval, and error handling.
 */
class TestClientRegistry {

    private ClientRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ClientRegistry();
    }

    @Test
    @Tag("level0")
    @DisplayName("Test register client decorator")
    void testRegisterClientDecorator() {
        registry.registerClient("test_client", "test", kwargs -> createMockClient());

        List<String> clients = registry.listClients();
        assertTrue(clients.contains("test_test_client"), "Client should be registered");
    }

    @Test
    @Tag("level0")
    @DisplayName("Test register class")
    void testRegisterClass() {
        registry.registerClass(TestClient.class);

        String fullName = "database_mysql";
        List<String> clients = registry.listClients();
        assertTrue(clients.contains(fullName), "Class should be registered");
        assertTrue(clients.contains(fullName), "Class should stay registered");

        TestClient instance = (TestClient) registry.getClient(fullName, Map.of("host", "localhost"));
        assertEquals("localhost", instance.config.get("host"), "Config should have host parameter");
    }

    @Test
    @Tag("level0")
    @DisplayName("Test get client by name")
    void testGetClientByName() {
        Object mockInstance = createMockClient();
        registry.registerClient("redis", "cache", kwargs -> mockInstance);

        Object result = registry.getClient("redis", "cache");
        assertSame(mockInstance, result, "Should return the registered instance");

        registry.unregister("redis", "cache");
    }

    @Test
    @Tag("level0")
    @DisplayName("Test get client without client type")
    void testGetClientWithoutClientType() {
        Object mockInstance = createMockClient();
        registry.registerClient("default_client", null, kwargs -> mockInstance);

        Object result = registry.getClient("default_client");
        assertSame(mockInstance, result, "Should return the registered instance without client type");

        registry.unregister("default_client", null);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test get client empty name")
    void testGetClientEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> registry.getClient("", (String) null),
                "Should throw exception for empty name");
    }

    @Test
    @Tag("level0")
    @DisplayName("Test get client unknown")
    void testGetClientUnknown() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> registry.getClient("unknown_client", (String) null),
                "Should throw exception for unknown client");
        assertTrue(thrown.getMessage().contains("Unknown client type"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test get client creation failure")
    void testGetClientCreationFailure() {
        registry.registerClient("failing", "test", kwargs -> {
            throw new RuntimeException("Creation failed");
        });

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> registry.getClient("failing", "test"),
                "Should throw exception when creation fails");
        assertTrue(thrown.getMessage().contains("Failed to create client"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test unregister")
    void testUnregister() {
        registry.registerClient("redis", "cache", kwargs -> createMockClient());

        registry.unregister("redis", "cache");

        assertFalse(registry.listClients().contains("cache_redis"), "Client should be unregistered");
    }

    @Test
    @Tag("level0")
    @DisplayName("Test unregister not found")
    void testUnregisterNotFound() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> registry.unregister("nonexistent"),
                "Should throw when unregistering a missing client");
        assertTrue(thrown.getMessage().contains("not registered"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test list clients")
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
