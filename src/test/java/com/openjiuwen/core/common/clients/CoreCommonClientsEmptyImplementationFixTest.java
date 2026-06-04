package com.openjiuwen.core.common.clients;

import com.openjiuwen.core.common.clients.http.HttpClient;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for core common client empty-implementation fixes.
 */
class CoreCommonClientsEmptyImplementationFixTest {

    @Test
    void httpClientInitializeAppliesRegistryConfig() {
        HttpClient client = new HttpClient();

        client.initialize(Map.of(
                "timeout", 30.0,
                "connect_timeout", 10.0,
                "headers", Map.of("User-Agent", "Test"),
                "proxy", "http://proxy.example:8080",
                "raise_for_status", true,
                "trust_env", false
        ));

        assertEquals(30.0, client.getConfig().getTimeout());
        assertEquals(10.0, client.getConfig().getConnectTimeout());
        assertEquals("Test", client.getConfig().getHeaders().get("User-Agent"));
        assertEquals("http://proxy.example:8080", client.getConfig().getProxy());
        assertTrue(client.getConfig().isRaiseForStatus());
        assertFalse(client.getConfig().isTrustEnv());
        assertTrue(client.isHealthy());
    }

    @Test
    void clientRegistryCanConstructConfiguredHttpClient() {
        ClientRegistry registry = new ClientRegistry();
        registry.registerClass(HttpClient.class);

        Object client = registry.getClient("http", "common", Map.of("timeout", 12.5));

        HttpClient httpClient = assertInstanceOf(HttpClient.class, client);
        assertEquals(12.5, httpClient.getConfig().getTimeout());
    }
}
