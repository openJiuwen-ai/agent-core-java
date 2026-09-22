/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.harness.security.PermissionConfirmResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A turn that resumes an interrupted tool call with a confirmation answer has to be persisted like
 * any other turn: the answer travels in the session state, so it must survive the state write.
 */
class ConfirmResumeCheckpointTest {
    private static final String INTERRUPTION_KEY = "__react_agent_interruption__";

    private static final String SESSION_ID = "confirm-resume-session";

    @Test
    @DisplayName("a resume turn carrying a confirmation answer is saved, so the interrupt is gone")
    void resumeTurnWithAConfirmationAnswerIsSaved() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        AgentSession session = new AgentSession(SESSION_ID, new Config(), checkpointer);

        // The interrupted turn leaves its interruption state behind and is checkpointed.
        session.state().updateGlobal(Map.of(INTERRUPTION_KEY, "pending-tool-call"));
        checkpointer.interruptAgentExecute(session);

        // The resuming turn answers it, clears the interruption state and finishes.
        checkpointer.preAgentExecute(session, answer());
        Map<String, Object> cleared = new HashMap<>();
        cleared.put(INTERRUPTION_KEY, null);
        session.state().updateGlobal(cleared);
        assertDoesNotThrow(() -> checkpointer.postAgentExecute(session));

        // The next turn of the same session must not find the answered interrupt again.
        AgentSession next = new AgentSession(SESSION_ID, new Config(), checkpointer);
        checkpointer.preAgentExecute(next, null);
        assertNull(next.state().getGlobal(INTERRUPTION_KEY));
    }

    @Test
    @DisplayName("the confirmation answer itself survives the state round trip")
    void confirmationAnswerSurvivesTheRoundTrip() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        AgentSession session = new AgentSession(SESSION_ID, new Config(), checkpointer);

        checkpointer.preAgentExecute(session, answer());
        checkpointer.postAgentExecute(session);

        AgentSession next = new AgentSession(SESSION_ID, new Config(), checkpointer);
        checkpointer.preAgentExecute(next, null);

        Object restored = next.state().get(Constant.INTERACTIVE_INPUT);
        assertInstanceOf(List.class, restored);
        Object first = ((List<?>) restored).get(0);
        assertInstanceOf(PermissionConfirmResponse.class, first);
        PermissionConfirmResponse response = (PermissionConfirmResponse) first;
        assertTrue(response.isApproved());
        assertEquals("looks fine", response.getFeedback());
    }

    private static PermissionConfirmResponse answer() {
        return PermissionConfirmResponse.builder().approved(true).feedback("looks fine").build();
    }
}
