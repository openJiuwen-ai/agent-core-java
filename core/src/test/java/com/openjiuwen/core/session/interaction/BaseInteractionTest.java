/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.state.SessionStateAccess;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests BaseInteraction bootstrap and consumption behavior.
 *
 * <p>Mirrors Python's {@code BaseInteraction} and {@code AgentInterrupt} in
 * {@code openjiuwen/core/session/interaction/base.py}.</p>
 */
class BaseInteractionTest {

    @Test
    void defaultInputIsStoredAndConsumed() {
        TestSession session = new TestSession();
        TestInteraction interaction = new TestInteraction(session, "default-input");

        assertEquals(List.of("default-input"), session.state().get(Constant.INTERACTIVE_INPUT));
        assertEquals("default-input", interaction.latest());
        assertEquals("default-input", interaction.next());
        assertNull(interaction.next());
    }

    @Test
    void stateInputsAreConsumedBeforeDefaultInput() {
        TestSession session = new TestSession();
        session.state().update(Map.of(Constant.INTERACTIVE_INPUT, List.of("stored-1", "stored-2")));

        TestInteraction interaction = new TestInteraction(session, "default-input");

        assertEquals(List.of("stored-1", "stored-2", "default-input"),
                session.state().get(Constant.INTERACTIVE_INPUT));
        assertEquals("default-input", interaction.latest());
        assertEquals("stored-1", interaction.next());
        assertEquals("stored-2", interaction.next());
        assertEquals("default-input", interaction.next());
        assertNull(interaction.next());
    }

    @Test
    void nonListStateInputDoesNotSeedInteractiveInputs() {
        TestSession session = new TestSession();
        session.state().update(Map.of(Constant.INTERACTIVE_INPUT, "raw-input"));

        TestInteraction interaction = new TestInteraction(session);

        assertEquals("raw-input", session.state().get(Constant.INTERACTIVE_INPUT));
        assertNull(interaction.latest());
        assertNull(interaction.next());
    }

    @Test
    void userLatestInputDefaultsToNoneEquivalent() {
        TestInteraction interaction = new TestInteraction(new TestSession());

        assertNull(interaction.userLatestInput("question"));
    }

    @Test
    void agentInterruptKeepsPythonMessageAttribute() {
        AgentInterrupt interrupt = new AgentInterrupt("stop-here");

        assertEquals("stop-here", interrupt.message);
        assertEquals("stop-here", interrupt.getMessage());
    }

    private static final class TestInteraction extends BaseInteraction {

        private TestInteraction(BaseSession session) {
            super(session);
        }

        private TestInteraction(BaseSession session, Object defaultInput) {
            super(session, defaultInput);
        }

        private Object next() {
            return getNextInteractiveInput();
        }

        private Object latest() {
            return latestInteractiveInputs;
        }

        @Override
        public Object waitUserInputs(Object value) {
            return value;
        }
    }

    private static final class TestSession extends BaseSession {

        private final AgentStateCollection state = new AgentStateCollection();

        @Override
        public SessionStateAccess state() {
            return state;
        }
    }
}
