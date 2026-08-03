/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/core/common/clients/test_client_registry.py}.
 */
class ClientRegistryTest {

    @Test
    void registerClientFactory() {
        ClientRegistry registry = new ClientRegistry();
        registry.registerClient("test_client", "test", kwargs -> Map.of());

        assertTrue(registry.listClients().contains("test_test_client"));
    }

    @Test
    void registerClassAndInstantiate() {
        ClientRegistry registry = new ClientRegistry();
        registry.registerClass(TestDatabaseClient.class);

        assertTrue(registry.listClients().contains("database_mysql"));
        Object instance = registry.getClient("database_mysql", Map.of("host", "localhost"));
        TestDatabaseClient client = assertInstanceOf(TestDatabaseClient.class, instance);
        assertEquals("localhost", client.getConfig().get("host"));
    }

    @Test
    void getClientByNameAndType() {
        ClientRegistry registry = new ClientRegistry();
        Object sentinel = new Object();
        registry.registerClient("redis", "cache", kwargs -> sentinel);

        assertEquals(sentinel, registry.getClient("redis", "cache"));

        registry.unregister("redis", "cache");
    }

    @Test
    void getClientWithoutClientType() {
        ClientRegistry registry = new ClientRegistry();
        Object sentinel = new Object();
        registry.registerClient("default_client", null, kwargs -> sentinel);

        assertEquals(sentinel, registry.getClient("default_client", (String) null));
        registry.unregister("default_client", null);
    }

    @Test
    void getClientRejectsEmptyName() {
        ClientRegistry registry = new ClientRegistry();
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> registry.getClient(""));
        assertTrue(error.getMessage().contains("cannot be empty"));
    }

    @Test
    void getClientRejectsUnknownName() {
        ClientRegistry registry = new ClientRegistry();
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> registry.getClient("unknown"));
        assertTrue(error.getMessage().contains("Unknown client type"));
    }

    @Test
    void getClientSurfacesCreationFailure() {
        ClientRegistry registry = new ClientRegistry();
        registry.registerClient("failing", "test", kwargs -> {
            throw new Exception("Creation failed");
        });

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> registry.getClient("failing", "test"));
        assertTrue(error.getMessage().contains("Failed to create client"));
    }

    @Test
    void unregisterRemovesClient() {
        ClientRegistry registry = new ClientRegistry();
        registry.registerClient("redis", "cache", kwargs -> Map.of());

        registry.unregister("redis", "cache");

        assertTrue(registry.listClients().stream().noneMatch("cache_redis"::equals));
    }

    @Test
    void unregisterRejectsUnknownClient() {
        ClientRegistry registry = new ClientRegistry();
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> registry.unregister("nonexistent"));
        assertTrue(error.getMessage().contains("not registered"));
    }

    @Test
    void listClientsReturnsRegisteredEntries() {
        ClientRegistry registry = new ClientRegistry();
        registry.registerClient("client1", "type1", kwargs -> Map.of());
        registry.registerClient("client2", "type2", kwargs -> Map.of());

        List<String> clients = registry.listClients();
        assertTrue(clients.contains("type1_client1"));
        assertTrue(clients.contains("type2_client2"));
    }

    @Test
    void baseClientCarriesConfigMetadataAndNoopLifecycle() {
        BaseClient client = new BaseClient(Map.of("key", "value"));

        assertEquals("value", client.getConfig().get("key"));
        assertNotNull(client.getMetadata());
        assertEquals(client, client.enter());
        assertTrue(client.close().join());
    }

    private static final class TestDatabaseClient extends BaseClient {
        private static final String __client_name__ = "mysql";
        private static final String __client_type__ = "database";

        private TestDatabaseClient(Map<String, Object> kwargs) {
            super(kwargs);
        }
    }
}
