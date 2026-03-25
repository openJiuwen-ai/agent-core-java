/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.WorkflowInteraction;
import com.openjiuwen.core.workflow.HasDrawable;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.LoopComponent;
import com.openjiuwen.core.workflow.component.loop.callback.IntermediateLoopVarCallback;
import com.openjiuwen.core.workflow.component.loop.callback.OutputCallback;
import com.openjiuwen.core.workflow.condition.AlwaysTrue;
import com.openjiuwen.core.workflow.condition.ArrayConditionInSession;
import com.openjiuwen.core.workflow.condition.Condition;
import com.openjiuwen.core.workflow.condition.ExpressionCondition;
import com.openjiuwen.core.workflow.condition.FuncCondition;
import com.openjiuwen.core.workflow.condition.NumberConditionInSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Full loop component implementation that creates an AdvancedLoopComponentImpl
 * based on runtime inputs (loop type, condition, etc.).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.LoopComponent}.
 * Implements the {@link LoopComponent} interface for Drawable compatibility.
 */
public class LoopComponentImpl extends WorkflowComponent implements LoopComponent {

    private final LoopGroup loopGroup;
    private final Map<String, Object> outputSchema;

    public LoopComponentImpl(LoopGroup loopGroup, Map<String, Object> outputSchema) {
        this.loopGroup = loopGroup;
        this.outputSchema = outputSchema;
        loopGroup.checkValidate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        try {
            if (!(inputs instanceof Map)) {
                throw new IllegalArgumentException("inputs must be a map, but got " + inputs.getClass().getSimpleName());
            }

            Map<String, Object> inputsMap = (Map<String, Object>) inputs;
            Object rawInputs = inputsMap.get(Constant.INPUTS_KEY);
            if (rawInputs == null) {
                throw new IllegalArgumentException("missing required key " + Constant.INPUTS_KEY);
            }

            LoopInput loopInput = LoopInput.fromMap(rawInputs instanceof Map ? (Map<String, Object>) rawInputs : null);

            Condition condition;
            String loopType = loopInput.getLoopType();

            if (LoopType.ARRAY.getValue().equals(loopType)) {
                condition = new ArrayConditionInSession(loopInput.getLoopArray());

            } else if (LoopType.NUMBER.getValue().equals(loopType)) {
                int maxLoopLimit = SessionConstants.LOOP_NUMBER_MAX_LIMIT_DEFAULT;
                Object envLimit = session.getEnv(SessionConstants.LOOP_NUMBER_MAX_LIMIT_KEY);
                if (envLimit instanceof Number) {
                    maxLoopLimit = ((Number) envLimit).intValue();
                }

                if (loopInput.getLoopNumber() == null) {
                    throw new IllegalArgumentException("loop_number variable not found or is None");
                }
                if (loopInput.getLoopNumber() > maxLoopLimit) {
                    throw new IllegalArgumentException("loop_number exceeds maximum limit " + maxLoopLimit);
                }
                condition = new NumberConditionInSession(loopInput.getLoopNumber());

            } else if (LoopType.ALWAYS_TRUE.getValue().equals(loopType)) {
                condition = new AlwaysTrue();

            } else if (LoopType.EXPRESSION.getValue().equals(loopType)) {
                Object expr = loopInput.getBoolExpression();
                if (expr instanceof Boolean) {
                    boolean val = (Boolean) expr;
                    condition = new FuncCondition(() -> val);
                } else {
                    condition = new ExpressionCondition(String.valueOf(expr));
                }

            } else {
                throw new IllegalArgumentException("invalid loop type '" + loopType + "' for LoopComponent");
            }

            OutputCallback outputCallback = new OutputCallback(outputSchema);
            List<com.openjiuwen.core.workflow.component.loop.callback.LoopCallback> callbacks = new ArrayList<>();
            callbacks.add(outputCallback);

            if (loopInput.getIntermediateVar() != null && !loopInput.getIntermediateVar().isEmpty()) {
                callbacks.add(new IntermediateLoopVarCallback(loopInput.getIntermediateVar()));
            }

            AdvancedLoopComponentImpl loopComponent = new AdvancedLoopComponentImpl(
                    loopGroup, condition, loopGroup.getBreakComponents(), callbacks);

            BaseSession innerSession = extractInnerSession(session);
            Map<String, Object> invokeInputs = Map.of(
                    Constant.INPUTS_KEY, Map.of(),
                    Constant.CONFIG_KEY, inputsMap.getOrDefault(Constant.CONFIG_KEY, Map.of()));
            return loopComponent.onInvoke(invokeInputs, innerSession);

        } catch (RuntimeException e) {
            if (e instanceof com.openjiuwen.core.common.exception.BaseError) {
                throw e;
            }
            if (containsGraphInterrupt(e)) {
                throw e;
            }
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_EXECUTION_ERROR,
                    "comp", session.getComponentId(),
                    "reason", e.getMessage());
        }
    }

    @Override
    public boolean graphInvoker() {
        return true;
    }

    public LoopGroup getLoop() {
        return loopGroup;
    }

    @Override
    public HasDrawable getLoopGroup() {
        return (HasDrawable) loopGroup;
    }

    private BaseSession extractInnerSession(NodeSessionApi sessionApi) {
        try {
            java.lang.reflect.Field inner = sessionApi.getClass().getDeclaredField("inner");
            inner.setAccessible(true);
            return (BaseSession) inner.get(sessionApi);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot extract inner session from NodeSessionApi", e);
        }
    }

    private static boolean containsGraphInterrupt(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof GraphInterrupt || current instanceof WorkflowInteraction.GraphInterruptRuntimeWrapper) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
