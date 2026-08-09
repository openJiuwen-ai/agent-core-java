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
        return invoke(inputs, adaptSession(session, false), adaptContext(extractContext(kwargs)));
    }

    @Override
    public Iterator<O> onStream(I inputs, BaseSession session, Object... kwargs) {
        ensureSession(session, "on_stream");
        return stream(inputs, adaptSession(session, false), adaptContext(extractContext(kwargs)));
    }

    @Override
    public O onCollect(I inputs, BaseSession session, Object... kwargs) {
        ensureSession(session, "on_collect");
        return collect(inputs, adaptSession(session, true), adaptContext(extractContext(kwargs)));
    }

    @Override
    public Iterator<O> onTransform(I inputs, BaseSession session, Object... kwargs) {
        ensureSession(session, "on_transform");
        return transform(inputs, adaptSession(session, true), adaptContext(extractContext(kwargs)));
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
        throw missingRequiredMethod("invoke",
                "async def invoke(self, inputs: Input, session: Session, context: ModelContext) -> Output");
    }

    /**
     * Execute component synchronously with the pre-0.1.14 public session/context wrappers.
     *
     * @param inputs component input
     * @param session current execution session facade
     * @param context root package model context facade
     * @return component output
     */
    public O invoke(I inputs, NodeSessionApi session, com.openjiuwen.core.context.ModelContext context) {
        return invoke(inputs, session.getInner(), com.openjiuwen.core.context.ModelContext.unwrap(context));
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
     * Execute component with batch input and streaming output through the legacy facade.
     *
     * @param inputs component input
     * @param session current execution session facade
     * @param context root package model context facade
     * @return streamed component output
     */
    public Iterator<O> stream(I inputs, NodeSessionApi session, com.openjiuwen.core.context.ModelContext context) {
        return stream(inputs, session.getInner(), com.openjiuwen.core.context.ModelContext.unwrap(context));
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
     * Execute component with streaming input and batch output through the legacy facade.
     *
     * @param inputs component input
     * @param session current execution session facade
     * @param context root package model context facade
     * @return collected component output
     */
    public O collect(I inputs, NodeSessionApi session, com.openjiuwen.core.context.ModelContext context) {
        return collect(inputs, session.getInner(), com.openjiuwen.core.context.ModelContext.unwrap(context));
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

    /**
     * Execute component with streaming input and output through the legacy facade.
     *
     * @param inputs component input
     * @param session current execution session facade
     * @param context root package model context facade
     * @return transformed component output
     */
    public Iterator<O> transform(I inputs, NodeSessionApi session, com.openjiuwen.core.context.ModelContext context) {
        return transform(inputs, session.getInner(), com.openjiuwen.core.context.ModelContext.unwrap(context));
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
            if (item instanceof com.openjiuwen.core.context.ModelContext modelContext) {
                return modelContext.unwrap();
            }
        }
        return null;
    }

    private NodeSessionApi adaptSession(BaseSession session, boolean streamMode) {
        return new NodeSessionApi(session, streamMode);
    }

    private com.openjiuwen.core.context.ModelContext adaptContext(ModelContext context) {
        return com.openjiuwen.core.context.ModelContext.wrap(context);
    }

    private UnsupportedOperationException missingRequiredMethod(String methodName, String signature) {
        String className = getClass().getSimpleName();
        return new UnsupportedOperationException(
                "Component '" + className + "' is missing required method: " + methodName + "()\n"
                        + "  -> Expected signature: " + signature);
    }
}
