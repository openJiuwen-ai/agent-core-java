package com.openjiuwen.agent_evolving.agent_rl.online.gateway.app;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayRequestContextTest {

    @Test
    void resolveTraceIdUsesHeaderOrSynthesizesOne() {
        assertEquals(
                "trace-123",
                GatewayRequestContext.resolveTraceId(Map.of("X-Request-Id", "trace-123"))
        );

        String traceId = GatewayRequestContext.resolveTraceId(Map.of());
        assertEquals(8, traceId.length());
        assertTrue(traceId.chars().allMatch(character -> Character.digit(character, 16) >= 0));
    }

    @Test
    void requireMessagesRejectsMissingOrEmptyList() {
        GatewayHttpException missing = assertThrows(
                GatewayHttpException.class,
                () -> GatewayRequestContext.requireMessages(Map.of())
        );
        assertEquals(400, missing.getStatusCode());

        GatewayHttpException empty = assertThrows(
                GatewayHttpException.class,
                () -> GatewayRequestContext.requireMessages(Map.of("messages", List.of()))
        );
        assertEquals(400, empty.getStatusCode());

        assertEquals(
                List.of(Map.of("role", "user", "content", "hello")),
                GatewayRequestContext.requireMessages(Map.of("messages", List.of(Map.of("role", "user", "content", "hello"))))
        );
    }

    @Test
    void requireUserIdUsesHeaderOrSingleUserDefault() {
        assertEquals(
                "user-1",
                GatewayRequestContext.requireUserId(Map.of("x-user-id", " user-1 "), Map.of("single_user_default", false))
        );
        assertEquals(
                "jiuwenclaw-web",
                GatewayRequestContext.requireUserId(Map.of(), new GatewayConfigView(true))
        );
    }

    @Test
    void requireUserIdRejectsMissingIdentityWhenDefaultDisabled() {
        GatewayHttpException exception = assertThrows(
                GatewayHttpException.class,
                () -> GatewayRequestContext.requireUserId(Map.of(), new GatewayConfigView(false))
        );
        assertEquals(400, exception.getStatusCode());
        assertFalse(exception.getDetail().isEmpty());
    }

    private record GatewayConfigView(boolean singleUserDefault) {
    }
}
