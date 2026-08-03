/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.state.Transformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for root session package exports and current-session context.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.session} package surface in
 * {@code openjiuwen/core/session/__init__.py}.</p>
 */
class SessionPackageTest {

    @AfterEach
    void clearCurrentSession() {
        SessionPackage.setCurrentSession(null);
    }

    @Test
    void pythonModuleAndExportsMirrorRootPackage() {
        assertEquals("openjiuwen/core/session/__init__.py", SessionPackage.PYTHON_MODULE);
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("BaseSession"));
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("WrappedSession"));
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("ProxySession"));
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("WorkflowSession"));
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("NodeSession"));
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("SubWorkflowSession"));
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("RouterSession"));
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("workflow_session_vars"));
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("CommitState"));
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("InteractiveInput"));
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("InteractionOutput"));
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("Checkpointer"));
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("AgentInterrupt"));
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("Config"));
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("EndFrame"));
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("Transformer"));
        assertTrue(SessionPackage.EXPORTED_SYMBOLS.contains("Session"));
        assertFalse(SessionPackage.EXPORTED_SYMBOLS.contains("get_current_session"));

        assertSame(BaseSession.class, SessionPackage.EXPORTED_TYPES.get("BaseSession"));
        assertSame(ProxySession.class, SessionPackage.EXPORTED_TYPES.get("ProxySession"));
        assertSame(Transformer.class, SessionPackage.EXPORTED_TYPES.get("Transformer"));
        assertSame(Session.class, SessionPackage.EXPORTED_TYPES.get("Session"));
    }

    @Test
    void exportedConstantsUseSessionConstantsAndUtilsNames() {
        Map<String, Object> constants = SessionPackage.exportedConstants();

        assertEquals(SessionConstants.COMP_STREAM_CALL_TIMEOUT_KEY, constants.get("COMP_STREAM_CALL_TIMEOUT_KEY"));
        assertEquals(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT, constants.get("WORKFLOW_EXECUTE_TIMEOUT"));
        assertEquals(SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT, constants.get("WORKFLOW_STREAM_FRAME_TIMEOUT"));
        assertEquals(SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT,
                constants.get("WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT"));
        assertEquals(SessionConstants.END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY,
                constants.get("END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY"));
        assertEquals(SessionConstants.END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY,
                constants.get("END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY"));
        assertEquals(SessionConstants.LOOP_NUMBER_MAX_LIMIT_DEFAULT,
                constants.get("LOOP_NUMBER_MAX_LIMIT_DEFAULT"));
        assertEquals(SessionConstants.LOOP_NUMBER_MAX_LIMIT_KEY, constants.get("LOOP_NUMBER_MAX_LIMIT_KEY"));
        assertEquals(SessionConstants.STREAM_INPUT_GEN_TIMEOUT_KEY, constants.get("STREAM_INPUT_GEN_TIMEOUT_KEY"));
        assertEquals(SessionConstants.FORCE_DEL_WORKFLOW_STATE_ENV_KEY,
                constants.get("FORCE_DEL_WORKFLOW_STATE_ENV_KEY"));
        assertEquals(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, constants.get("FORCE_DEL_WORKFLOW_STATE_KEY"));
        assertEquals(".", constants.get("NESTED_PATH_SPLIT"));

        assertThrows(UnsupportedOperationException.class, () -> constants.put("extra", "value"));
    }

    @Test
    void currentSessionSetClearAndTypedLookupMirrorContextVarDefault() {
        assertNull(SessionPackage.getCurrentSession());
        BaseSession session = new SimpleSession("session-1");

        SessionPackage.setCurrentSession(session);

        assertSame(session, SessionPackage.getCurrentSession());
        assertSame(session, SessionPackage.getCurrentSession(BaseSession.class));

        SessionPackage.setCurrentSession(null);

        assertNull(SessionPackage.getCurrentSession());
    }

    @Test
    void withSessionRestoresPreviousValueAfterCallableAndRunnable() throws Exception {
        BaseSession outer = new SimpleSession("outer");
        BaseSession inner = new SimpleSession("inner");
        SessionPackage.setCurrentSession(outer);

        String result = SessionPackage.withSession(inner, () -> SessionPackage.getCurrentSession(BaseSession.class)
                .getSessionId());

        assertEquals("inner", result);
        assertSame(outer, SessionPackage.getCurrentSession());

        AtomicReference<Object> seen = new AtomicReference<>();
        SessionPackage.withSession(null, () -> seen.set(SessionPackage.getCurrentSession()));

        assertNull(seen.get());
        assertSame(outer, SessionPackage.getCurrentSession());
    }

    @Test
    void withSessionRestoresPreviousValueAfterFailure() {
        BaseSession outer = new SimpleSession("outer");
        BaseSession inner = new SimpleSession("inner");
        SessionPackage.setCurrentSession(outer);

        java.util.concurrent.Callable<Object> failingCallable = () -> {
            throw new IllegalStateException("boom");
        };
        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                SessionPackage.withSession(inner, failingCallable));

        assertEquals("boom", error.getMessage());
        assertSame(outer, SessionPackage.getCurrentSession());
    }

    @Test
    void withSessionForClassReturnsSameInstance() {
        Object instance = new Object();

        assertSame(instance, SessionPackage.withSessionForClass(instance));
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedSessionFacadeRemainsExportable() {
        Session session = new Session();

        assertDoesNotThrow(session::deprecationMessage);
        assertTrue(session.deprecationMessage().contains("deprecated"));
    }

    /**
     * Mirrors Python's concrete session objects exported through
     * {@code openjiuwen/core/session/__init__.py}.
     */
    private static final class SimpleSession extends BaseSession {
        private final String sessionId;

        private SimpleSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String sessionId() {
            return sessionId;
        }
    }
}
