/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.session.BaseSession;

import java.util.Iterator;

/**
 * Mirrors Python's {@code Executable} in
 * {@code openjiuwen/core/graph/executable.py}.
 *
 * <p>This dependency is required by Python's {@code ExecutableGraph} in
 * {@code openjiuwen/core/graph/base.py}.</p>
 *
 * @param <I> input type
 * @param <O> output type
 */
public abstract class Executable<I, O> {

    /**
     * Invoke this executable.
     *
     * @param inputs input payload
     * @param session execution session
     * @param kwargs keyword-style arguments
     * @return invocation output
     */
    public O onInvoke(I inputs, BaseSession session, Object... kwargs) {
        String className = getClass().getSimpleName();
        throw new UnsupportedOperationException(
                String.format("Component '%s' does not implement the on_invoke method. "
                        + "Please override this method in the subclass to provide inference logic. "
                        + "Required implementation: async def on_invoke(self, inputs: Input, "
                        + "session: BaseSession, **kwargs) -> Output:", className));
    }

    /**
     * Stream this executable's output.
     *
     * @param inputs input payload
     * @param session execution session
     * @param kwargs keyword-style arguments
     * @return streamed outputs
     */
    public Iterator<O> onStream(I inputs, BaseSession session, Object... kwargs) {
        String className = getClass().getSimpleName();
        throw new UnsupportedOperationException(
                String.format("Component '%s' does not implement the on_stream method. "
                        + "Please override this method in the subclass to provide streaming logic. "
                        + "Required implementation: async def on_stream(self, inputs: Input, "
                        + "session: BaseSession, **kwargs) -> AsyncIterator[Output]:", className));
    }

    /**
     * Collect streaming input into one output.
     *
     * @param inputs input payload
     * @param session execution session
     * @param kwargs keyword-style arguments
     * @return collected output
     */
    public O onCollect(I inputs, BaseSession session, Object... kwargs) {
        String className = getClass().getSimpleName();
        throw new UnsupportedOperationException(
                String.format("Component '%s' does not implement the on_collect method. "
                        + "Please override this method in the subclass to provide collection logic. "
                        + "Required implementation: async def on_collect(self, inputs: Input, "
                        + "session: BaseSession, **kwargs) -> Output:", className));
    }

    /**
     * Transform streaming input into streaming output.
     *
     * @param inputs input payload
     * @param session execution session
     * @param kwargs keyword-style arguments
     * @return transformed outputs
     */
    public Iterator<O> onTransform(I inputs, BaseSession session, Object... kwargs) {
        String className = getClass().getSimpleName();
        throw new UnsupportedOperationException(
                String.format("Component '%s' does not implement the on_transform method. "
                        + "Please override this method in the subclass to provide transformation logic. "
                        + "Required implementation: async def on_transform(self, inputs: Input, "
                        + "session: BaseSession, **kwargs) -> AsyncIterator[Output]:", className));
    }

    /**
     * Whether tracing should be skipped.
     *
     * @return false by default
     */
    public boolean skipTrace() {
        return false;
    }

    /**
     * Whether this executable invokes a graph.
     *
     * @return false by default
     */
    public boolean graphInvoker() {
        return false;
    }

    /**
     * Whether post-commit should run after execution.
     *
     * @return true by default
     */
    public boolean postCommit() {
        return true;
    }

    /**
     * Component type identifier.
     *
     * @return empty string by default
     */
    public String componentType() {
        return "";
    }
}
