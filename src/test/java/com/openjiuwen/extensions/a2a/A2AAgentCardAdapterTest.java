/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.single_agent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code A2AAgentCardAdapter} in
 * {@code openjiuwen/extensions/a2a/a2a_agentcard_adapter.py}.
 */
class A2AAgentCardAdapterTest {
    @Test
    void toA2aAgentCardBuildsCapabilitiesModesDescriptionAndFallbackInterface() {
        AgentCard card = new AgentCard();
        card.setName("agent");
        card.setDescription(" desc ");
        card.setInputParams(Map.of("b", 2, "a", "值"));
        card.setOutputParams(String.class);

        A2AAgentCardAdapter.A2aAgentCard result = A2AAgentCardAdapter.toA2aAgentCard(
                card, "https://example.test/a2a", "HTTP+JSON", "1.0", "tenant-a", null);

        assertNotNull(result);
        assertEquals("agent", result.getName());
        assertTrue(result.getDescription().startsWith("desc\n[input_params] {\"a\": \"值\", \"b\": 2}"));
        assertTrue(result.getDescription().contains("[output_params] {\"type\": \"String\"}"));
        assertTrue(result.getCapabilities().isStreaming());
        assertFalse(result.getCapabilities().isPushNotifications());
        assertEquals(List.of("text/plain", "application/json"), result.getDefaultInputModes());
        assertEquals(1, result.getSupportedInterfaces().size());
        assertEquals("tenant-a", result.getSupportedInterfaces().get(0).getTenant());
    }

    @Test
    void toA2aAgentCardReturnsNullForNonAgentCard() {
        assertNull(A2AAgentCardAdapter.toA2aAgentCard("not-a-card"));
    }

    @Test
    void supportedInterfacesFilterInvalidItemsAndOverrideFallbackWhenValid() {
        AgentCard card = new AgentCard();
        card.setName("agent");
        List<Object> supported = List.of(
                Map.of("url", "", "protocol_binding", "x", "protocol_version", "1"),
                "ignored",
                Map.of("url", "https://remote", "protocol_binding", "grpc", "protocol_version", "2", "tenant", 7)
        );

        A2AAgentCardAdapter.A2aAgentCard result = A2AAgentCardAdapter.toA2aAgentCard(
                card, "https://fallback", "HTTP+JSON", "1.0", "tenant-a", supported);

        assertEquals(1, result.getSupportedInterfaces().size());
        assertEquals("https://remote", result.getSupportedInterfaces().get(0).getUrl());
        assertEquals("grpc", result.getSupportedInterfaces().get(0).getProtocolBinding());
        assertEquals("2", result.getSupportedInterfaces().get(0).getProtocolVersion());
        assertEquals("7", result.getSupportedInterfaces().get(0).getTenant());
    }

    @Test
    void fromA2aAgentCardCreatesOpenjiuwenAgentCard() {
        A2AAgentCardAdapter.A2aAgentCard source = new A2AAgentCardAdapter.A2aAgentCard(
                "agent", "description", new A2AAgentCardAdapter.AgentCapabilities(true, false),
                List.of("text/plain"), List.of("application/json"));

        AgentCard result = A2AAgentCardAdapter.fromA2aAgentCard(source);

        assertEquals("agent", result.getName());
        assertEquals("description", result.getDescription());
    }
}
