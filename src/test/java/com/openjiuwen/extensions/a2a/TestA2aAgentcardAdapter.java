/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test A2A AgentCard adapter functionality.
 * <p>
 * Mirrors Python's {@code test_a2a_agentcard_adapter.py} in
 * {@code tests/unit_tests/extensions/a2a/test_a2a_agentcard_adapter.py}.
 *
 */
class TestA2aAgentcardAdapter {

    /**
     * Test A2AAgentCardAdapter.toA2aAgentCard method.
     * <p>
     * Mirrors Python's {@code TestA2AAgentCardAdapter} class.
     */
    @Nested
    class TestToA2aAgentCard {

        @Test
        void testToA2aAgentCardShouldMapBasicFieldsAndDefaultModes() {
            AgentCard card = AgentCard.builder()
                    .id("1234567890abcdef1234567890abcdef")
                    .name("demo agent")
                    .description("demo description")
                    .inputParams(Map.of("age", 25))
                    .outputParams(Map.of("greeting", "hello"))
                    .build();

            A2AAgentCardAdapter.A2aAgentCard result = A2AAgentCardAdapter.toA2aAgentCard(card);

            assertEquals("demo agent", result.getName());
            assertTrue(result.getDescription().startsWith("demo description"));
            assertTrue(result.getDescription().contains("[input_params]"));
            assertTrue(result.getDescription().contains("[output_params]"));
            assertEquals(List.of("text/plain", "application/json"), result.getDefaultInputModes());
            assertEquals(List.of("text/plain", "application/json"), result.getDefaultOutputModes());
            assertFalse(result.getDescription().contains("1234567890abcdef1234567890abcdef"));
        }

        @Test
        void testToA2aAgentCardShouldFillSupportedInterfacesFromConfig() {
            AgentCard card = AgentCard.builder().name("demo").description("desc").build();

            A2AAgentCardAdapter.A2aAgentCard result = A2AAgentCardAdapter.toA2aAgentCard(
                    card,
                    List.of(new A2AAgentCardAdapter.SupportedInterface(
                            "https://rest.example.com/v1", "HTTP+JSON", "1.0", "testtenant")),
                    null,
                    null,
                    null,
                    null);

            assertEquals(1, result.getSupportedInterfaces().size());
            A2AAgentCardAdapter.SupportedInterface item = result.getSupportedInterfaces().get(0);
            assertEquals("https://rest.example.com/v1", item.url());
            assertEquals("HTTP+JSON", item.protocolBinding());
            assertEquals("1.0", item.protocolVersion());
            assertEquals("testtenant", item.tenant());
        }

        @Test
        void testToA2aAgentCardShouldFallbackToSingleInterfaceArgs() {
            AgentCard card = AgentCard.builder().name("demo").description("desc").build();

            A2AAgentCardAdapter.A2aAgentCard result = A2AAgentCardAdapter.toA2aAgentCard(
                    card,
                    List.of(),
                    "https://grpc.example.com/a2a",
                    "GRPC",
                    "1.0",
                    "tenant-a");

            assertEquals(1, result.getSupportedInterfaces().size());
            A2AAgentCardAdapter.SupportedInterface item = result.getSupportedInterfaces().get(0);
            assertEquals("https://grpc.example.com/a2a", item.url());
            assertEquals("GRPC", item.protocolBinding());
            assertEquals("1.0", item.protocolVersion());
            assertEquals("tenant-a", item.tenant());
        }

        @Test
        void testFromA2aAgentCardShouldMapNameAndDescription() {
            A2AAgentCardAdapter.A2aAgentCard a2aCard = new A2AAgentCardAdapter.A2aAgentCard(
                    "Recipe Agent",
                    "Agent that helps users with recipes and cooking.",
                    List.of(),
                    List.of(),
                    List.of());

            AgentCard result = A2AAgentCardAdapter.fromA2aAgentCard(a2aCard);

            assertNotNull(result.getId());
            assertEquals("Recipe Agent", result.getName());
            assertEquals("Agent that helps users with recipes and cooking.", result.getDescription());
        }
    }
}
