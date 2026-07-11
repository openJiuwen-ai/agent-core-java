/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.HasDrawable;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.component.LoopComponent;
import com.openjiuwen.core.workflow.component.WorkflowComponent;
import com.openjiuwen.core.workflow.component.loop.callback.IntermediateLoopVarCallback;
import com.openjiuwen.core.workflow.component.loop.callback.OutputCallback;
import com.openjiuwen.core.workflow.condition.AlwaysTrue;
import com.openjiuwen.core.workflow.condition.ArrayConditionInSession;
import com.openjiuwen.core.workflow.condition.Condition;
import com.openjiuwen.core.workflow.condition.ExpressionCondition;
import com.openjiuwen.core.workflow.condition.FuncCondition;
import com.openjiuwen.core.workflow.condition.NumberConditionInSession;
import com.openjiuwen.core.workflow.internal.WorkflowSessionSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loop component implementation exposing Python's {@code loop_group} property.
 *
 * <p>Mirrors Python's {@code LoopComponent} in
 * {@code openjiuwen/core/workflow/components/flow/loop/loop_comp.py}.</p>
 */
public class LoopComponentImpl extends WorkflowComponent implements LoopComponent {

    private final LoopGroup loopGroup;
    private final Map<String, ?> outputSchema;

    public LoopComponentImpl(LoopGroup loopGroup, Map<String, ?> outputSchema) {
        if (loopGroup == null) {
            throw new IllegalArgumentException("loop_group is None");
        }
        this.loopGroup = loopGroup;
        this.outputSchema = outputSchema == null ? Map.of() : new LinkedHashMap<>(outputSchema);
        this.loopGroup.checkValidate();
    }

    @Override
    public HasDrawable getLoopGroup() {
        return loopGroup;
    }

    public Map<String, ?> getOutputSchema() {
        return outputSchema;
    }

    @Override
    public boolean graphInvoker() {
        return true;
    }

    public Executable<?, ?> toExecutable() {
        return new LoopExecutable(this);
    }

    /**
     * Python-compatible snake_case bridge for reflected callers.
     *
     * @return executable loop component
     */
    public Executable<?, ?> to_executable() {
        return toExecutable();
    }

    @SuppressWarnings("unchecked")
    public Object invoke(Object inputs, BaseSession session, ModelContext context) {
        return invokeInternal(inputs, session, context);
    }

    @SuppressWarnings("unchecked")
    private Object invokeInternal(Object inputs, BaseSession session, ModelContext context) {
        try {
            if (!(inputs instanceof Map<?, ?> inputsMap)) {
                throw new IllegalArgumentException("inputs must be a dict, but got "
                        + (inputs == null ? "NoneType" : inputs.getClass().getSimpleName()));
            }
            if (!inputsMap.containsKey(Constant.INPUTS_KEY)) {
                throw new IllegalArgumentException("missing required key " + Constant.INPUTS_KEY);
            }
            Object loopInputValue = inputsMap.get(Constant.INPUTS_KEY);
            if (!(loopInputValue instanceof Map<?, ?> loopInputMap)) {
                throw new IllegalArgumentException(Constant.INPUTS_KEY + " must be a dict, but got "
                        + (loopInputValue == null ? "NoneType" : loopInputValue.getClass().getSimpleName()));
            }

            LoopInput loopInput = LoopInput.fromMap(toStringObjectMap(loopInputMap));
            List<com.openjiuwen.core.workflow.component.loop.callback.LoopCallback> callbacks = new ArrayList<>();
            callbacks.add(new OutputCallback(toStringObjectMap(outputSchema)));
            if (loopInput.getIntermediateVar() != null && !loopInput.getIntermediateVar().isEmpty()) {
                callbacks.add(new IntermediateLoopVarCallback(loopInput.getIntermediateVar()));
            }

            Object config = inputsMap.containsKey(Constant.CONFIG_KEY) ? inputsMap.get(Constant.CONFIG_KEY) : Map.of();
            return LoopRuntime.invoke(
                    conditionFor(loopInput),
                    loopGroup,
                    callbacks,
                    Map.of(Constant.INPUTS_KEY, Map.of(), Constant.CONFIG_KEY, config),
                    session,
                    context);
        } catch (BaseError exception) {
            throw exception;
        } catch (Exception exception) {
            com.openjiuwen.core.graph.pregel.GraphInterrupt interrupt = LoopRuntime.findGraphInterrupt(exception);
            if (interrupt != null) {
                return LoopRuntime.sneakyThrow(interrupt);
            }
            BaseError nestedBaseError = LoopRuntime.findBaseError(exception);
            if (nestedBaseError != null) {
                throw nestedBaseError;
            }
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_EXECUTION_ERROR,
                    "reason", normalizeExceptionMessage(exception),
                    "comp", WorkflowSessionSupport.componentId(session));
        }
    }

    private static String normalizeExceptionMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return null;
        }
        return message.replace("Expected list/tuple for", "Expected list for");
    }

    private static Condition conditionFor(LoopInput input) {
        LoopType loopType = LoopType.fromValue(input.getLoopType());
        if (loopType == LoopType.ARRAY) {
            return new ArrayConditionInSession(input.getLoopArray());
        }
        if (loopType == LoopType.NUMBER) {
            return new NumberConditionInSession(input.getLoopNumber());
        }
        if (loopType == LoopType.EXPRESSION) {
            Object boolExpression = input.getBoolExpression();
            if (boolExpression instanceof Boolean boolValue) {
                return new FuncCondition(() -> boolValue);
            }
            String expression = boolExpression == null ? "" : String.valueOf(boolExpression);
            if (expression.matches(".*}\\s*\\[.*")) {
                throw new IllegalArgumentException("expression syntax error");
            }
            return new ExpressionCondition(expression);
        }
        return new AlwaysTrue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static final class LoopExecutable extends Executable<Object, Object> {

        private final LoopComponentImpl owner;

        private LoopExecutable(LoopComponentImpl owner) {
            this.owner = owner;
        }

        @Override
        public Object onInvoke(Object inputs, BaseSession session, Object... kwargs) {
            return owner.invokeInternal(inputs, session, extractContext(kwargs));
        }

        @Override
        public java.util.Iterator<Object> onStream(Object inputs, BaseSession session, Object... kwargs) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR,
                    "ability", ComponentAbility.STREAM.name(),
                    "comp", WorkflowSessionSupport.componentId(session),
                    "reason", "Component 'LoopExecutable' does not implement the on_stream method.",
                    "workflow", session == null ? "" : session.workflowId());
        }

        @Override
        public boolean graphInvoker() {
            return true;
        }

        private static ModelContext extractContext(Object... kwargs) {
            if (kwargs == null) {
                return null;
            }
            for (Object kwarg : kwargs) {
                if (kwarg instanceof ModelContext modelContext) {
                    return modelContext;
                }
                if (kwarg instanceof Map<?, ?> map && map.get("context") instanceof ModelContext modelContext) {
                    return modelContext;
                }
            }
            return null;
        }
    }
}
