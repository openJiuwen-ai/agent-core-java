/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.internal.NodeSession;

import java.util.Iterator;

/**
 * Base executable for workflow components, providing the four fundamental execution patterns:
 * invoke, stream, collect, transform.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.component.ComponentExecutable}.
 * 
 * @since 0.1.7
 */
public abstract class ComponentExecutable extends Executable<Object, Object>
        implements com.openjiuwen.core.graph.Vertex.MixModeAware {
    /**
     * onInvoke.
     * 
     * @param inputs inputs
     * @param session session
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object onInvoke(Object inputs, BaseSession session, Object... kwargs) {
        if (!(session instanceof NodeSession)) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_INNER_ORCHESTRATION_ERROR, "reason",
                    "session type must be NodeSession when on_invoke");
        }
        ModelContext context = extractContext(kwargs);
        return invoke(inputs, new NodeSessionApi((NodeSession) session, false), context);
    }

    /**
     * onStream.
     * 
     * @param inputs inputs
     * @param session session
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<Object> onStream(Object inputs, BaseSession session, Object... kwargs) {
        if (!(session instanceof NodeSession)) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_INNER_ORCHESTRATION_ERROR, "reason",
                    "session type must be NodeSession when on_stream");
        }
        ModelContext context = extractContext(kwargs);
        return stream(inputs, new NodeSessionApi((NodeSession) session, false), context);
    }

    /**
     * onCollect.
     * 
     * @param inputs inputs
     * @param session session
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object onCollect(Object inputs, BaseSession session, Object... kwargs) {
        if (!(session instanceof NodeSession)) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_INNER_ORCHESTRATION_ERROR, "reason",
                    "session type must be NodeSession when on_collect");
        }
        ModelContext context = extractContext(kwargs);
        return collect(inputs, new NodeSessionApi((NodeSession) session, true), context);
    }

    /**
     * onTransform.
     * 
     * @param inputs inputs
     * @param session session
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<Object> onTransform(Object inputs, BaseSession session, Object... kwargs) {
        if (!(session instanceof NodeSession)) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_INNER_ORCHESTRATION_ERROR, "reason",
                    "session is not NodeSession when on_transform");
        }
        ModelContext context = extractContext(kwargs);
        return transform(inputs, new NodeSessionApi((NodeSession) session, true), context);
    }

    /**
     * setMix.
     * 
     * @since 0.1.7
     */
    @Override
    public void setMix() {
        // Default no-op; components that need mixed batch/stream behavior override this.
    }

    /**
     * Execute component synchronously with batch input and output.
     * 
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        throw new UnsupportedOperationException(
                "Component '" + getClass().getSimpleName() + "' is missing required method: invoke()");
    }

    /**
     * Execute component with batch input but streaming output.
     * 
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
        throw new UnsupportedOperationException(
                "Component '" + getClass().getSimpleName() + "' is missing required method: stream()");
    }

    /**
     * Execute component with streaming input but batch output.
     * 
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    public Object collect(Object inputs, NodeSessionApi session, ModelContext context) {
        throw new UnsupportedOperationException(
                "Component '" + getClass().getSimpleName() + "' is missing required method: collect()");
    }

    /**
     * Execute component with streaming input and streaming output.
     * 
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
        throw new UnsupportedOperationException(
                "Component '" + getClass().getSimpleName() + "' is missing required method: transform()");
    }

    /**
     * extractContext.
     * 
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    private static ModelContext extractContext(Object... kwargs) {
        if (kwargs != null) {
            for (Object arg : kwargs) {
                if (arg instanceof ModelContext) {
                    return (ModelContext) arg;
                }
            }
        }
        return null;
    }
}
