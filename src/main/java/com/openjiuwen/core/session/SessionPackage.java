/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.AgentInterrupt;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.RouterSession;
import com.openjiuwen.core.session.internal.SubWorkflowSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.internal.WrappedSession;
import com.openjiuwen.core.session.state.Transformer;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.utils.SessionUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Package bridge for root session exports and current-session context.
 *
 * <p>Mirrors Python's {@code openjiuwen/core/session/__init__.py}.</p>
 */
public final class SessionPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/session/__init__.py";

    private static final ThreadLocal<Object> CURRENT_SESSION = new ThreadLocal<>();

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "BaseSession",
            "WrappedSession",
            "ProxySession",
            "WorkflowSession",
            "NodeSession",
            "SubWorkflowSession",
            "RouterSession",
            "workflow_session_vars",
            "CommitState",
            "InteractiveInput",
            "InteractionOutput",
            "Checkpointer",
            "AgentInterrupt",
            "Config",
            "COMP_STREAM_CALL_TIMEOUT_KEY",
            "WORKFLOW_EXECUTE_TIMEOUT",
            "WORKFLOW_STREAM_FRAME_TIMEOUT",
            "WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT",
            "END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY",
            "END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY",
            "LOOP_NUMBER_MAX_LIMIT_DEFAULT",
            "LOOP_NUMBER_MAX_LIMIT_KEY",
            "STREAM_INPUT_GEN_TIMEOUT_KEY",
            "FORCE_DEL_WORKFLOW_STATE_ENV_KEY",
            "FORCE_DEL_WORKFLOW_STATE_KEY",
            "NESTED_PATH_SPLIT",
            "EndFrame",
            "get_by_schema",
            "get_value_by_nested_path",
            "extract_origin_key",
            "is_ref_path",
            "Transformer",
            "Session"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private SessionPackage() {
    }

    public static Object getCurrentSession() {
        return CURRENT_SESSION.get();
    }

    public static <T> T getCurrentSession(Class<T> sessionType) {
        Object session = CURRENT_SESSION.get();
        if (session == null) {
            return null;
        }
        return sessionType.cast(session);
    }

    public static void setCurrentSession(Object session) {
        if (session == null) {
            CURRENT_SESSION.remove();
        } else {
            CURRENT_SESSION.set(session);
        }
    }

    public static <T> T withSession(Object session, Callable<T> callable) throws Exception {
        Object previous = CURRENT_SESSION.get();
        setCurrentSession(session);
        try {
            return callable.call();
        } finally {
            restorePrevious(previous);
        }
    }

    public static void withSession(Object session, Runnable runnable) {
        Object previous = CURRENT_SESSION.get();
        setCurrentSession(session);
        try {
            runnable.run();
        } finally {
            restorePrevious(previous);
        }
    }

    public static <T> T withSessionForClass(T instance) {
        return instance;
    }

    public static Map<String, Object> exportedConstants() {
        Map<String, Object> constants = new LinkedHashMap<>();
        constants.put("COMP_STREAM_CALL_TIMEOUT_KEY", SessionConstants.COMP_STREAM_CALL_TIMEOUT_KEY);
        constants.put("WORKFLOW_EXECUTE_TIMEOUT", SessionConstants.WORKFLOW_EXECUTE_TIMEOUT);
        constants.put("WORKFLOW_STREAM_FRAME_TIMEOUT", SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT);
        constants.put("WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT", SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT);
        constants.put("END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY",
                SessionConstants.END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY);
        constants.put("END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY",
                SessionConstants.END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY);
        constants.put("LOOP_NUMBER_MAX_LIMIT_DEFAULT", SessionConstants.LOOP_NUMBER_MAX_LIMIT_DEFAULT);
        constants.put("LOOP_NUMBER_MAX_LIMIT_KEY", SessionConstants.LOOP_NUMBER_MAX_LIMIT_KEY);
        constants.put("STREAM_INPUT_GEN_TIMEOUT_KEY", SessionConstants.STREAM_INPUT_GEN_TIMEOUT_KEY);
        constants.put("FORCE_DEL_WORKFLOW_STATE_ENV_KEY", SessionConstants.FORCE_DEL_WORKFLOW_STATE_ENV_KEY);
        constants.put("FORCE_DEL_WORKFLOW_STATE_KEY", SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY);
        constants.put("NESTED_PATH_SPLIT", SessionUtils.NESTED_PATH_SPLIT);
        return Map.copyOf(constants);
    }

    private static void restorePrevious(Object previous) {
        if (previous == null) {
            CURRENT_SESSION.remove();
        } else {
            CURRENT_SESSION.set(previous);
        }
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("BaseSession", BaseSession.class);
        exports.put("WrappedSession", WrappedSession.class);
        exports.put("ProxySession", ProxySession.class);
        exports.put("WorkflowSession", WorkflowSession.class);
        exports.put("NodeSession", NodeSession.class);
        exports.put("SubWorkflowSession", SubWorkflowSession.class);
        exports.put("RouterSession", RouterSession.class);
        exports.put("CommitState", WorkflowCommitState.class);
        exports.put("InteractiveInput", InteractiveInput.class);
        exports.put("InteractionOutput", InteractionOutput.class);
        exports.put("Checkpointer", Checkpointer.class);
        exports.put("AgentInterrupt", AgentInterrupt.class);
        exports.put("Config", Config.class);
        exports.put("EndFrame", SessionUtils.EndFrame.class);
        exports.put("Transformer", Transformer.class);
        exports.put("Session", Session.class);
        return Map.copyOf(exports);
    }
}
