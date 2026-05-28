/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.builtin_teams.handoff;

import com.openjiuwen.core.multiagent.teams.handoff.HandoffSignal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for handoff signal.
 *
 * <p>Mirrors Python's {@code test_handoff_signal.py} in
 * {@code tests.unit_tests.multi_agent.builtin_teams.handoff}.
 */
class TestHandoffSignal {

    @Nested
    class TestHandoffSignalData {

        @Test
        void testTargetStored() {
            HandoffSignal signal = new HandoffSignal("agent_b");
            assertEquals("agent_b", signal.getTarget());
        }

        @Test
        void testMessageDefaultsToEmptyOptional() {
            assertTrue(new HandoffSignal("b").getMessage().isEmpty());
        }

        @Test
        void testReasonDefaultsToEmptyOptional() {
            assertTrue(new HandoffSignal("b").getReason().isEmpty());
        }

        @Test
        void testCustomMessage() {
            HandoffSignal signal = new HandoffSignal("b", "context", null);
            assertEquals("context", signal.getMessage().orElseThrow());
        }

        @Test
        void testCustomReason() {
            HandoffSignal signal = new HandoffSignal("b", null, "needs billing");
            assertEquals("needs billing", signal.getReason().orElseThrow());
        }

        @Test
        void testEqualityBasedOnValues() {
            HandoffSignal left = new HandoffSignal("b", "m", "r");
            HandoffSignal right = new HandoffSignal("b", "m", "r");
            assertEquals(left, right);
        }

        @Test
        void testInequalityDifferentTarget() {
            assertNotEquals(new HandoffSignal("a"), new HandoffSignal("b"));
        }
    }

    @Nested
    class TestSignalConstants {

        @Test
        void testConstantTargetKeyValue() {
            assertEquals("__handoff_to__", HandoffSignal.HANDOFF_TARGET_KEY);
        }

        @Test
        void testConstantMessageKeyValue() {
            assertEquals("__handoff_message__", HandoffSignal.HANDOFF_MESSAGE_KEY);
        }

        @Test
        void testConstantReasonKeyValue() {
            assertEquals("__handoff_reason__", HandoffSignal.HANDOFF_REASON_KEY);
        }
    }
}
