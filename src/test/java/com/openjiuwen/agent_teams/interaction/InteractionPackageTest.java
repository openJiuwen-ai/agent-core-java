/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the interaction package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.interaction} in
 * {@code openjiuwen/agent_teams/interaction/__init__.py}.</p>
 */
class InteractionPackageTest {

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertEquals("openjiuwen/agent_teams/interaction/__init__.py", InteractionPackage.PYTHON_MODULE);
        assertEquals("External interaction layer for agent teams.", InteractionPackage.DESCRIPTION);
        assertEquals(List.of(
                "BridgeAgentNotEnabledError",
                "BridgeProtocolAdapter",
                "DeliverResult",
                "GodViewMessage",
                "HumanAgentInbox",
                "HumanAgentInboundEvent",
                "HumanAgentMessage",
                "HumanAgentNotEnabledError",
                "InteractPayload",
                "OperatorMessage",
                "REMOTE_UNAVAILABLE_SENTINEL",
                "UnknownBridgeAgentError",
                "UnknownHumanAgentError",
                "UserInbox",
                "is_reserved_name",
                "parse_interact_str",
                "parse_mention"
        ), InteractionPackage.EXPORTED_SYMBOLS);
    }

    @Test
    void implementedExportsReferenceTranslatedTypes() {
        assertSame(BridgeAgentNotEnabledError.class, InteractionPackage.BRIDGE_AGENT_NOT_ENABLED_ERROR);
        assertSame(BridgeProtocolAdapter.class, InteractionPackage.BRIDGE_PROTOCOL_ADAPTER);
        assertSame(DeliverResult.class, InteractionPackage.DELIVER_RESULT);
        assertSame(GodViewMessage.class, InteractionPackage.GOD_VIEW_MESSAGE);
        assertSame(HumanAgentInboundEvent.class, InteractionPackage.HUMAN_AGENT_INBOUND_EVENT);
        assertSame(HumanAgentMessage.class, InteractionPackage.HUMAN_AGENT_MESSAGE);
        assertSame(InteractPayload.class, InteractionPackage.INTERACT_PAYLOAD);
        assertSame(OperatorMessage.class, InteractionPackage.OPERATOR_MESSAGE);
        assertSame(UnknownBridgeAgentError.class, InteractionPackage.UNKNOWN_BRIDGE_AGENT_ERROR);
        assertEquals(BridgeProtocol.REMOTE_UNAVAILABLE_SENTINEL, InteractionPackage.REMOTE_UNAVAILABLE_SENTINEL);
        assertTrue(InteractionPackage.REMOTE_UNAVAILABLE_SENTINEL.contains("remote"));
    }
}
