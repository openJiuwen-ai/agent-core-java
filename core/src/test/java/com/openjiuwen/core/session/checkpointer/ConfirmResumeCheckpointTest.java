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
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.singleagent.interrupt.InterruptConstants;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptEntry;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.harness.rails.interrupt.AskUserRail.AskUserPayload;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail.ConfirmPayload;
import com.openjiuwen.harness.security.PermissionConfirmResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A turn that resumes an interrupted tool call with a confirmation answer has to be persisted like
 * any other turn: both the interruption state and the answer travel in the session state, so both
 * must survive the state write a persisting checkpointer performs.
 */
class ConfirmResumeCheckpointTest {
    private static final String INTERRUPTION_KEY = InterruptConstants.INTERRUPTION_KEY;

    private static final String SESSION_ID = "confirm-resume-session";

    private static final String TOOL_CALL_ID = "call-1";

    @Test
    @DisplayName("an interrupted turn carrying tool interruption state is checkpointed")
    void interruptedTurnWithToolInterruptionStateIsSaved() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        AgentSession session = newSession(checkpointer);

        session.state().updateGlobal(Map.of(INTERRUPTION_KEY, toolInterruptionState()));
        assertDoesNotThrow(() -> checkpointer.interruptAgentExecute(session));

        // The next turn has to find the pending tool call in order to resume it.
        AgentSession next = newSession(checkpointer);
        checkpointer.preAgentExecute(next, null);
        ToolInterruptionState restored =
                assertInstanceOf(ToolInterruptionState.class, next.state().getGlobal(INTERRUPTION_KEY));
        assertEquals("what is the weather", restored.getOriginalQuery());
        ToolInterruptEntry entry = restored.getInterruptedTools().get(TOOL_CALL_ID);
        assertEquals("bash", entry.getToolCall().getName());
        assertEquals("Please approve", entry.getInterruptRequests().get(TOOL_CALL_ID).getMessage());
    }

    @Test
    @DisplayName("a resume turn carrying a confirmation answer is saved, so the interrupt is gone")
    void resumeTurnWithAConfirmationAnswerIsSaved() {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        AgentSession session = newSession(checkpointer);

        // The interrupted turn leaves its interruption state behind and is checkpointed.
        session.state().updateGlobal(Map.of(INTERRUPTION_KEY, toolInterruptionState()));
        checkpointer.interruptAgentExecute(session);

        // The resuming turn answers it, clears the interruption state and finishes.
        checkpointer.preAgentExecute(session, permissionAnswer());
        Map<String, Object> cleared = new HashMap<>();
        cleared.put(INTERRUPTION_KEY, null);
        session.state().updateGlobal(cleared);
        assertDoesNotThrow(() -> checkpointer.postAgentExecute(session));

        // The next turn of the same session must not find the answered interrupt again.
        AgentSession next = newSession(checkpointer);
        checkpointer.preAgentExecute(next, null);
        assertNull(next.state().getGlobal(INTERRUPTION_KEY));
    }

    @Test
    @DisplayName("the permission confirmation answer itself survives the state round trip")
    void permissionAnswerSurvivesTheRoundTrip() {
        PermissionConfirmResponse response =
                assertInstanceOf(PermissionConfirmResponse.class, roundTrip(permissionAnswer()));
        assertTrue(response.isApproved());
        assertEquals("looks fine", response.getFeedback());
        assertTrue(response.isAutoConfirm());
        assertTrue(response.isPersistAllow());
    }

    @Test
    @DisplayName("a confirm rail answer survives the state round trip")
    void confirmPayloadSurvivesTheRoundTrip() {
        ConfirmPayload payload = new ConfirmPayload(true, "go ahead", false);
        assertEquals(payload, roundTrip(payload));
    }

    @Test
    @DisplayName("an ask-user rail answer survives the state round trip")
    void askUserPayloadSurvivesTheRoundTrip() {
        AskUserPayload payload = new AskUserPayload(Map.of("which city", "Shenzhen"));
        assertEquals(payload, roundTrip(payload));
    }

    private static Object roundTrip(Object resumeInput) {
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(new InMemoryKVStore());
        AgentSession session = newSession(checkpointer);
        checkpointer.preAgentExecute(session, resumeInput);
        checkpointer.postAgentExecute(session);

        AgentSession next = newSession(checkpointer);
        checkpointer.preAgentExecute(next, null);
        List<?> restored = assertInstanceOf(List.class, next.state().get(Constant.INTERACTIVE_INPUT));
        return restored.get(0);
    }

    private static AgentSession newSession(PersistenceCheckpointer checkpointer) {
        return new AgentSession(SESSION_ID, new Config(), checkpointer, null, null);
    }

    private static PermissionConfirmResponse permissionAnswer() {
        return new PermissionConfirmResponse(true, "looks fine", true, true);
    }

    private static ToolInterruptionState toolInterruptionState() {
        ToolCall toolCall = ToolCall.builder().id(TOOL_CALL_ID).name("bash").build();

        ToolInterruptEntry entry = new ToolInterruptEntry();
        entry.setToolCall(toolCall);
        entry.setInterruptRequests(Map.of(
                TOOL_CALL_ID, new InterruptRequest("Please approve", Map.of(), TOOL_CALL_ID)));

        ToolInterruptionState state = new ToolInterruptionState();
        state.setOriginalQuery("what is the weather");
        state.setIteration(1);
        state.setInterruptedTools(Map.of(TOOL_CALL_ID, entry));
        return state;
    }
}
