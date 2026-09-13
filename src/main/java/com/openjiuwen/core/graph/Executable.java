/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.session.BaseSession;

import java.util.Iterator;

/**
 * Generic executable component base class with invoke/stream/collect/transform abilities.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.executable.Executable[Input, Output]}.
 * 
 * @since 0.1.7
 */
public abstract class Executable<I, O> {
    /**
     * onInvoke.
     * 
     * @param inputs inputs
     * @param session session
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    public O onInvoke(I inputs, BaseSession session, Object... kwargs) {
        String className = this.getClass().getSimpleName();
        throw new UnsupportedOperationException(String.format("Component '%s' does not implement the onInvoke method. "
                + "Please override this method in the subclass to provide inference logic.", className));
    }

    /**
     * Stream the component output.
     * 
     * @param inputs input data
     * @param session execution session
     * @param kwargs additional arguments
     * @return an iterator of output chunks
     * @since 0.1.7
     */
    public Iterator<O> onStream(I inputs, BaseSession session, Object... kwargs) {
        String className = this.getClass().getSimpleName();
        throw new UnsupportedOperationException(String.format("Component '%s' does not implement the onStream method. "
                + "Please override this method in the subclass to provide streaming logic.", className));
    }

    /**
     * Collect from streaming inputs and produce a single output.
     * 
     * @param inputs input data
     * @param session execution session
     * @param kwargs additional arguments
     * @return the collected output
     * @since 0.1.7
     */
    public O onCollect(I inputs, BaseSession session, Object... kwargs) {
        String className = this.getClass().getSimpleName();
        throw new UnsupportedOperationException(String.format("Component '%s' does not implement the onCollect method. "
                + "Please override this method in the subclass to provide collection logic.", className));
    }

    /**
     * Transform streaming inputs to streaming outputs.
     * 
     * @param inputs input data
     * @param session execution session
     * @param kwargs additional arguments
     * @return an iterator of transformed output chunks
     * @since 0.1.7
     */
    public Iterator<O> onTransform(I inputs, BaseSession session, Object... kwargs) {
        String className = this.getClass().getSimpleName();
        throw new UnsupportedOperationException(
                String.format(
                        "Component '%s' does not implement the onTransform method. "
                                + "Please override this method in the subclass to provide transformation logic.",
                        className));
    }

    /**
     * Whether tracing should be skipped for this component.
     * 
     * @return true to skip tracing
     * @since 0.1.7
     */
    public boolean skipTrace() {
        return false;
    }

    /**
     * Whether this component is a graph invoker.
     * 
     * @return true if it is a graph invoker
     * @since 0.1.7
     */
    public boolean graphInvoker() {
        return false;
    }

    /**
     * Whether post-commit should be performed after execution.
     * 
     * @return true to perform post-commit
     * @since 0.1.7
     */
    public boolean postCommit() {
        return true;
    }

    /**
     * The component type identifier.
     * 
     * @return component type string
     * @since 0.1.7
     */
    public String componentType() {
        return "";
    }
}
