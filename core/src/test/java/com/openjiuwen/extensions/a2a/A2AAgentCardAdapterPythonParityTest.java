/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code TestA2AAgentCardAdapter} in
 * {@code tests/unit_tests/extensions/a2a/test_a2a_agentcard_adapter.py}.</p>
 */
class A2AAgentCardAdapterPythonParityTest {
    @Test
    void toA2aAgentCardShouldMapBasicFieldsAndDefaultModes() {
        AgentCard card = new AgentCard("1234567890abcdef1234567890abcdef", "示例智能体", "这是一个示例智能体");
        card.setInputParams(Map.of("age", 25));
        card.setOutputParams(Map.of("greeting", "hello"));

        A2AAgentCardAdapter.A2aAgentCard result = A2AAgentCardAdapter.toA2aAgentCard(card);

        assertEquals("示例智能体", result.getName());
        assertTrue(result.getDescription().startsWith("这是一个示例智能体"));
        assertTrue(result.getDescription().contains("[input_params]"));
        assertTrue(result.getDescription().contains("[output_params]"));
        assertEquals(List.of("text/plain", "application/json"), result.getDefaultInputModes());
        assertEquals(List.of("text/plain", "application/json"), result.getDefaultOutputModes());
        assertFalse(result.getDescription().contains("1234567890abcdef1234567890abcdef"));
    }

    @Test
    void toA2aAgentCardShouldFillSupportedInterfacesFromConfig() {
        AgentCard card = new AgentCard();
        card.setName("demo");
        card.setDescription("desc");

        A2AAgentCardAdapter.A2aAgentCard result = A2AAgentCardAdapter.toA2aAgentCard(
                card,
                null,
                "HTTP+JSON",
                "1.0",
                null,
                List.of(Map.of(
                        "url", "https://rest.example.com/v1",
                        "protocol_binding", "HTTP+JSON",
                        "protocol_version", "1.0",
                        "tenant", "testtenant")));

        assertEquals(1, result.getSupportedInterfaces().size());
        A2AAgentCardAdapter.AgentInterface agentInterface = result.getSupportedInterfaces().get(0);
        assertEquals("https://rest.example.com/v1", agentInterface.getUrl());
        assertEquals("HTTP+JSON", agentInterface.getProtocolBinding());
        assertEquals("1.0", agentInterface.getProtocolVersion());
        assertEquals("testtenant", agentInterface.getTenant());
    }

    @Test
    void toA2aAgentCardShouldFallbackToSingleInterfaceArgs() {
        AgentCard card = new AgentCard();
        card.setName("demo");
        card.setDescription("desc");

        A2AAgentCardAdapter.A2aAgentCard result = A2AAgentCardAdapter.toA2aAgentCard(
                card, "https://grpc.example.com/a2a", "GRPC", "1.0", "tenant-a", null);

        assertEquals(1, result.getSupportedInterfaces().size());
        A2AAgentCardAdapter.AgentInterface agentInterface = result.getSupportedInterfaces().get(0);
        assertEquals("https://grpc.example.com/a2a", agentInterface.getUrl());
        assertEquals("GRPC", agentInterface.getProtocolBinding());
        assertEquals("1.0", agentInterface.getProtocolVersion());
        assertEquals("tenant-a", agentInterface.getTenant());
    }

    @Test
    void fromA2aAgentCardShouldMapNameAndDescription() {
        A2AAgentCardAdapter.A2aAgentCard a2aCard = new A2AAgentCardAdapter.A2aAgentCard(
                "Recipe Agent",
                "Agent that helps users with recipes and cooking.",
                new A2AAgentCardAdapter.AgentCapabilities(true, false),
                A2AAgentCardAdapter.DEFAULT_INPUT_MODES,
                A2AAgentCardAdapter.DEFAULT_OUTPUT_MODES);

        AgentCard result = A2AAgentCardAdapter.fromA2aAgentCard(a2aCard);

        assertNotNull(result.getId());
        assertFalse(result.getId().isBlank());
        assertEquals("Recipe Agent", result.getName());
        assertEquals("Agent that helps users with recipes and cooking.", result.getDescription());
    }
}
