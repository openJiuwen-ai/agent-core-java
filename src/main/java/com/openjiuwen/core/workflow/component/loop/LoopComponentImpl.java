/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
                String inputType = inputs == null ? "null" : inputs.getClass().getSimpleName();
                throw new IllegalArgumentException("inputs must be a map, but got " + inputType);
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

                Integer loopNumber = resolveLoopNumber(rawInputs instanceof Map
                        ? (Map<String, Object>) rawInputs
                        : null, loopInput.getLoopNumber());
                if (loopNumber == null) {
                    throw new IllegalArgumentException("loop_number variable not found or is None");
                }
                if (loopNumber > maxLoopLimit) {
                    throw new IllegalArgumentException("loop_number exceeds maximum limit " + maxLoopLimit);
                }
                condition = new NumberConditionInSession(loopNumber);

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

            BaseSession innerSession = session.getInner();
            Map<String, Object> invokeInputs = new LinkedHashMap<>();
            invokeInputs.put(Constant.INPUTS_KEY, Map.of());
            invokeInputs.put(Constant.CONFIG_KEY, inputsMap.get(Constant.CONFIG_KEY));
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

    private static Integer resolveLoopNumber(Map<String, Object> rawInputs, Integer parsedLoopNumber) {
        if (rawInputs == null || !rawInputs.containsKey("loop_number")) {
            return parsedLoopNumber;
        }

        Object rawLoopNumber = rawInputs.get("loop_number");
        if (rawLoopNumber == null) {
            return null;
        }
        if (rawLoopNumber instanceof Number number) {
            return parseIntegralNumber(number);
        }
        if (rawLoopNumber instanceof String text) {
            return parseIntegralString(text);
        }
        throw new IllegalArgumentException("loop_number must be an integer");
    }

    private static Integer parseIntegralNumber(Number number) {
        if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
            return number.intValue();
        }
        if (number instanceof java.math.BigInteger bigInteger) {
            return bigInteger.intValueExact();
        }

        BigDecimal decimal = new BigDecimal(number.toString());
        try {
            return decimal.intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("loop_number must be an integer", e);
        }
    }

    private static Integer parseIntegralString(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("loop_number must be an integer");
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("loop_number must be an integer", e);
        }
    }
}
