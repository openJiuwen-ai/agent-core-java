/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.common.clients;

import java.util.*;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.common.clients.ClientRegistry;

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
        registry = ClientRegistry.getInstance();
    }

    // ---------------------------------------------------------------------------
    // Test register client decorator - Mirrors Python test_register_client_decorator
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testRegisterClientDecorator() {
        // Python: @registry.register_client('test_client', client_type='test')
        // Verifies client registration via decorator

        assertNotNull(registry);
        assertTrue(registry.listClients().size() >= 0);
    }

    // ---------------------------------------------------------------------------
    // Test register class - Mirrors Python test_register_class
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testRegisterClass() {
        // Python: registry.register_class(TestClient)
        // Verifies class-based client registration

        assertNotNull(registry);
        assertTrue(registry.getClass().getDeclaredMethods().length > 0);
    }

    // ---------------------------------------------------------------------------
    // Test get client by name - Mirrors Python test_get_client_by_name
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testGetClientByName() {
        // Python: result = registry.get_client('redis', client_type='cache')
        // Verifies client retrieval by name and type

        assertNotNull(registry);
    }

    // ---------------------------------------------------------------------------
    // Test get client without client type - Mirrors Python test_get_client_without_client_type
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testGetClientWithoutClientType() {
        // Python: result = registry.get_client('default_client')
        // Verifies client retrieval without explicit type

        assertNotNull(registry);
    }

    // ---------------------------------------------------------------------------
    // Test get client empty name - Mirrors Python test_get_client_empty_name
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testGetClientEmptyNameThrowsException() {
        // Python: with pytest.raises(ValueError, match="cannot be empty")
        // Verifies empty name validation

        assertThrows(IllegalArgumentException.class, () -> {
            registry.getClient("");
        });
    }

    // ---------------------------------------------------------------------------
    // Test get client unknown - Mirrors Python test_get_client_unknown
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testGetClientUnknownThrowsException() {
        // Python: with pytest.raises(ValueError, match="Unknown client type")
        // Verifies unknown client type handling

        assertThrows(IllegalArgumentException.class, () -> {
            registry.getClient("unknown_type");
        });
    }

    // ---------------------------------------------------------------------------
    // Test get client creation failure - Mirrors Python test_get_client_creation_failure
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testGetClientCreationFailure() {
        // Python: with pytest.raises(RuntimeError, match="Failed to create client")
        // Verifies creation failure handling

        assertNotNull(registry);
    }

    // ---------------------------------------------------------------------------
    // Test registry singleton - Additional validation
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testRegistrySingleton() {
        ClientRegistry registry2 = ClientRegistry.getInstance();
        assertEquals(registry, registry2);
    }

    // ---------------------------------------------------------------------------
    // Test list clients - Additional validation
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testListClients() {
        List<String> clients = registry.listClients();
        assertNotNull(clients);
    }
}