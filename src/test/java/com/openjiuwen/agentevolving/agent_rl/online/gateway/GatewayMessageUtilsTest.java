package com.openjiuwen.agentevolving.agent_rl.online.gateway;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayMessageUtilsTest {

    @Test
    void flattenMessageContentHandlesStringsListsAndFallbacks() {
        assertEquals("hello", GatewayMessageUtils.flattenMessageContent("hello"));
        assertEquals(
                "alpha beta",
                GatewayMessageUtils.flattenMessageContent(List.of(
                        Map.of("type", "text", "text", "alpha"),
                        Map.of("type", "image", "text", "ignored"),
                        Map.of("type", "text", "text", "beta")
                ))
        );
        assertEquals("", GatewayMessageUtils.flattenMessageContent(null));
        assertEquals("42", GatewayMessageUtils.flattenMessageContent(42));
    }

    @Test
    void extractLastUserInstructionUsesMostRecentNonEmptyUserTurn() {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", "first"),
                Map.of("role", "assistant", "content", "reply"),
                Map.of("role", "user", "content", List.of(Map.of("type", "text", "text", "second turn")))
        );

        assertEquals("second turn", GatewayMessageUtils.extractLastUserInstruction(messages));
        assertEquals("", GatewayMessageUtils.extractLastUserInstruction(List.of()));
    }
}
