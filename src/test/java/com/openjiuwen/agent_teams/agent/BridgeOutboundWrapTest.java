/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.BridgeOutboundWrap.BridgeMailboxInjectMode;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link BridgeOutboundWrap}.
 *
 * <p>Mirrors Python's wrap tests in
 * {@code openjiuwen/agent_teams/agent/bridge_outbound_wrap.py}.</p>
 */
class BridgeOutboundWrapTest {

    @Test
    void passthroughMinimalHeaderCn() {
        String text = BridgeOutboundWrap.wrapOutboundToRemote(
                "alice",
                "Alice",
                TeamRole.TEAMMATE,
                "dev",
                "please review pr 123",
                false,
                null,
                BridgeMailboxInjectMode.PASSTHROUGH,
                "cn"
        );

        assertEquals("[来自 Alice] please review pr 123", text);
    }

    @Test
    void passthroughMinimalHeaderEn() {
        String text = BridgeOutboundWrap.wrapOutboundToRemote(
                "alice",
                "Alice",
                TeamRole.TEAMMATE,
                "dev",
                "please review pr 123",
                false,
                null,
                BridgeMailboxInjectMode.PASSTHROUGH,
                "en"
        );

        assertEquals("[from Alice] please review pr 123", text);
    }

    @Test
    void passthroughBroadcastMarkerUsesSenderFallback() {
        String text = BridgeOutboundWrap.wrapOutboundToRemote(
                "leader",
                null,
                null,
                null,
                "standup in 5",
                true,
                null,
                BridgeMailboxInjectMode.PASSTHROUGH,
                "en"
        );

        assertTrue(text.startsWith("[from leader (broadcast)]"));
    }

    @Test
    void rephraseIncludesRolePersonaAndTaskHintCn() {
        String text = BridgeOutboundWrap.wrapOutboundToRemote(
                "alice",
                "Alice",
                TeamRole.TEAMMATE,
                "senior dev",
                "please refactor metrics module",
                false,
                "任务 #42 重构监控",
                BridgeMailboxInjectMode.REPHRASE,
                "cn"
        );

        assertTrue(text.contains("Alice"));
        assertTrue(text.contains("teammate"));
        assertTrue(text.contains("senior dev"));
        assertTrue(text.contains("please refactor metrics module"));
        assertTrue(text.contains("相关任务：任务 #42 重构监控"));
    }

    @Test
    void rephraseWithoutTaskHintOmitsReLine() {
        String text = BridgeOutboundWrap.wrapOutboundToRemote(
                "alice",
                "Alice",
                TeamRole.TEAMMATE,
                "dev",
                "hi",
                false,
                null,
                BridgeMailboxInjectMode.REPHRASE,
                "en"
        );

        assertFalse(text.contains("Re:"));
    }

    @Test
    void rephraseBroadcastKindLabel() {
        String text = BridgeOutboundWrap.wrapOutboundToRemote(
                "leader",
                "Leader",
                TeamRole.LEADER,
                "planner",
                "standup",
                true,
                null,
                BridgeMailboxInjectMode.REPHRASE,
                "en"
        );

        assertTrue(text.contains("broadcast"));
    }
}
