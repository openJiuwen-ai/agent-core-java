/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolsTest {

    @Test
    @DisplayName("protocol literals mirror the Python contract")
    void testProtocolLiterals() {
        assertEquals("approve", Protocols.APPROVE_ACTION);
        assertEquals("append", Protocols.APPEND_MODE);
        assertEquals("conversation_review", Protocols.CONVERSATION_REVIEW_SIGNAL);
        assertEquals("trajectory_issue", Protocols.TRAJECTORY_ISSUE_SIGNAL);
        assertEquals("user_intent", Protocols.USER_INTENT_SIGNAL);
    }

    @Test
    @DisplayName("valid patch actions and sections match Python frozensets")
    void testProtocolSets() {
        assertEquals(4, Protocols.VALID_PATCH_ACTIONS.size());
        assertTrue(Protocols.VALID_PATCH_ACTIONS.contains("replace"));
        assertTrue(Protocols.VALID_PATCH_ACTIONS.contains("skip"));
        assertEquals(8, Protocols.VALID_SECTIONS.size());
        assertTrue(Protocols.VALID_SECTIONS.contains("Workflow"));
        assertTrue(Protocols.VALID_SECTIONS.contains("Constraints"));
    }
}
