/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.workflow.HasDrawable;
import com.openjiuwen.core.workflow.component.AdvancedLoopComponent;
import com.openjiuwen.core.workflow.component.WorkflowComponent;
import com.openjiuwen.core.workflow.condition.Condition;
import com.openjiuwen.core.session.BaseSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Advanced loop component exposing Python's {@code body} property.
 *
 * <p>Mirrors Python's {@code AdvancedLoopComponent} in
 * {@code openjiuwen/core/workflow/components/flow/loop/loop_comp.py}.</p>
 */
public class AdvancedLoopComponentImpl extends WorkflowComponent implements AdvancedLoopComponent, LoopController {

    private final HasDrawable body;
    private final Condition condition;
    private final List<LoopCallback> callbacks = new ArrayList<>();
    private boolean broken;

    public AdvancedLoopComponentImpl(HasDrawable body) {
        this(body, alwaysTrueCondition(), List.of(), List.of());
    }

    public AdvancedLoopComponentImpl(
            HasDrawable body,
            Condition condition,
            List<LoopBreakComponent> breakNodes,
            List<LoopCallback> callbacks
    ) {
        if (body == null) {
            throw new IllegalArgumentException("body is None");
        }
        this.body = body;
        this.condition = condition == null ? alwaysTrueCondition() : condition;
        if (breakNodes != null) {
            for (LoopBreakComponent breakNode : breakNodes) {
                if (breakNode != null) {
                    breakNode.setController(this);
                }
            }
        }
        if (callbacks != null) {
            this.callbacks.addAll(callbacks);
        }
    }

    @Override
    public HasDrawable getBody() {
        return body;
    }

    @Override
    public void registerCallback(LoopCallback callback) {
        if (callback != null) {
            callbacks.add(callback);
        }
    }

    public List<LoopCallback> getCallbacks() {
        return List.copyOf(callbacks);
    }

    public boolean evaluateCondition() {
        return evaluateCondition(null);
    }

    public boolean evaluateCondition(BaseSession session) {
        return !broken && condition.evaluate(session);
    }

    @Override
    public void breakLoop() {
        broken = true;
    }

    @Override
    public boolean isBroken() {
        return broken;
    }

    @Override
    public boolean graphInvoker() {
        return true;
    }

    public Executable<?, ?> toExecutable() {
        return new AdvancedLoopExecutable(this);
    }

    /**
     * Python-compatible snake_case bridge for reflected callers.
     *
     * @return executable advanced loop component
     */
    public Executable<?, ?> to_executable() {
        return toExecutable();
    }

    public Object invoke(Object inputs, BaseSession session, ModelContext context) {
        return null;
    }

    private static Condition alwaysTrueCondition() {
        return new Condition() {
            @Override
            public Object doInvoke(Object inputs, BaseSession session) {
                return true;
            }
        };
    }

    private static final class AdvancedLoopExecutable extends Executable<Object, Object> {

        private final AdvancedLoopComponentImpl owner;

        private AdvancedLoopExecutable(AdvancedLoopComponentImpl owner) {
            this.owner = owner;
        }

        @Override
        public Object onInvoke(Object inputs, BaseSession session, Object... kwargs) {
            return owner.invoke(inputs, session, null);
        }

        @Override
        public boolean graphInvoker() {
            return true;
        }
    }
}
