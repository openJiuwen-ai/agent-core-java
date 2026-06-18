/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.session.BaseSession;

import java.util.Iterator;

/**
 * Mirrors Python's {@code ComponentExecutable} in
 * {@code openjiuwen/core/workflow/components/component.py}.
 *
 * @param <I> component input type
 * @param <O> component output type
 */
public abstract class ComponentExecutable<I, O> extends Executable<I, O> {

    @Override
    public O onInvoke(I inputs, BaseSession session, Object... kwargs) {
        ensureSession(session, "on_invoke");
        return invoke(inputs, session, extractContext(kwargs));
    }

    @Override
    public Iterator<O> onStream(I inputs, BaseSession session, Object... kwargs) {
        ensureSession(session, "on_stream");
        return stream(inputs, session, extractContext(kwargs));
    }

    @Override
    public O onCollect(I inputs, BaseSession session, Object... kwargs) {
        ensureSession(session, "on_collect");
        return collect(inputs, session, extractContext(kwargs));
    }

    @Override
    public Iterator<O> onTransform(I inputs, BaseSession session, Object... kwargs) {
        ensureSession(session, "on_transform");
        return transform(inputs, session, extractContext(kwargs));
    }

    /**
     * Execute component synchronously with batch input and output.
     *
     * @param inputs component input
     * @param session current execution session
     * @param context context engine
     * @return component output
     */
    public O invoke(I inputs, BaseSession session, ModelContext context) {
        throw missingRequiredMethod("stream",
                "async def stream(self, inputs: Input, session: Session, "
                        + "context: ModelContext) -> AsyncIterator[Output]");
    }

    /**
     * Execute component with batch input but streaming output.
     *
     * @param inputs component input
     * @param session current execution session
     * @param context context engine
     * @return streamed component output
     */
    public Iterator<O> stream(I inputs, BaseSession session, ModelContext context) {
        throw missingRequiredMethod("stream",
                "async def stream(self, inputs: Input, session: Session, "
                        + "context: ModelContext) -> AsyncIterator[Output]");
    }

    /**
     * Execute component with streaming input but batch output.
     *
     * @param inputs component input
     * @param session current execution session
     * @param context context engine
     * @return collected component output
     */
    public O collect(I inputs, BaseSession session, ModelContext context) {
        throw missingRequiredMethod("collect",
                "async def collect(self, inputs: Input, session: Session, context: ModelContext) -> Output");
    }

    /**
     * Execute component with streaming input and streaming output.
     *
     * @param inputs component input
     * @param session current execution session
     * @param context context engine
     * @return transformed component output
     */
    public Iterator<O> transform(I inputs, BaseSession session, ModelContext context) {
        throw missingRequiredMethod("transform",
                "async def transform(self, inputs: Input, session: Session, "
                        + "context: ModelContext) -> AsyncIterator[Output]");
    }

    private void ensureSession(BaseSession session, String methodName) {
        if (session == null) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_INNER_ORCHESTRATION_ERROR,
                    "reason", "session type must be BaseSession when " + methodName);
        }
    }

    private ModelContext extractContext(Object... kwargs) {
        if (kwargs == null) {
            return null;
        }
        for (Object item : kwargs) {
            if (item instanceof ModelContext modelContext) {
                return modelContext;
            }
        }
        return null;
    }

    private UnsupportedOperationException missingRequiredMethod(String methodName, String signature) {
        String className = getClass().getSimpleName();
        return new UnsupportedOperationException(
                "Component '" + className + "' is missing required method: " + methodName + "()\n"
                        + "  -> Expected signature: " + signature);
    }
}
