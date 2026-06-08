package com.openjiuwen.agent_evolving.agent_rl.online.gateway;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayCommonTest {

    @Test
    void fitListPadsAndTruncatesToRequestedLength() {
        assertEquals(List.of(), GatewayCommon.fitList(List.of(1.0d, 2.0d), 0));
        assertEquals(List.of(1.0d, 2.0d), GatewayCommon.fitList(List.of(1.0d, 2.0d, 3.0d), 2));
        assertEquals(List.of(1.0d, 2.0d, 0.0d, 0.0d), GatewayCommon.fitList(List.of(1.0d, 2.0d), 4));
    }

    @Test
    void utcNowIsoUsesUtcOffsetAndKnownGatewayKeysStayExposed() {
        assertTrue(GatewayCommon.utcNowIso().endsWith("+00:00"));
        assertTrue(GatewayCommon.NON_STANDARD_BODY_KEYS.contains("session_id"));
        assertTrue(GatewayCommon.NON_STANDARD_BODY_KEYS.contains("user_id"));
    }
}
