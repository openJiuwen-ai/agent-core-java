/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.workflow.condition.Condition;
import com.openjiuwen.core.workflow.internal.WorkflowSessionSupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

final class LoopRuntime {

    static final String BROKEN = "_broken";
    private static final String COMPLETED = "__loop_completed__";
    private static final String COMPLETED_RESULT = "__loop_completed_result__";
    private static final ConcurrentHashMap<String, InvocationGate> INVOCATION_GATES = new ConcurrentHashMap<>();

    private LoopRuntime() {
    }

    static Object invoke(Condition condition,
                         LoopGroup loopGroup,
                         List<com.openjiuwen.core.workflow.component.loop.callback.LoopCallback> callbacks,
                         Object inputs,
                         BaseSession session,
                         ModelContext context) {
        String gateKey = invocationGateKey(session);
        InvocationGate gate = INVOCATION_GATES.compute(gateKey, (key, current) -> {
            InvocationGate selected = current == null ? new InvocationGate() : current;
            selected.users++;
            return selected;
        });
        gate.lock.lock();
        try {
            if (gate.hasCompletedResult) {
                gate.completedResultConsumed = true;
                resetLoopOutputs(session, gate.completedResult);
                return gate.completedResult;
            }
            Object result = invokeSerial(condition, loopGroup, callbacks, inputs, session, context);
            gate.completedResult = result;
            gate.hasCompletedResult = true;
            gate.retainCompletedResult = WorkflowSessionSupport.executionFailed(session);
            return result;
        } finally {
            gate.lock.unlock();
            if (gate.hasCompletedResult && WorkflowSessionSupport.executionFailed(session)) {
                gate.retainCompletedResult = true;
            }
            INVOCATION_GATES.computeIfPresent(gateKey, (key, current) -> {
                if (current != gate) {
                    return current;
                }
                current.users--;
                if (current.users != 0) {
                    return current;
                }
                return current.hasCompletedResult
                        && current.retainCompletedResult
                        && !current.completedResultConsumed
                        ? current
                        : null;
            });
        }
    }

    private static Object invokeSerial(Condition condition,
                                       LoopGroup loopGroup,
                                       List<com.openjiuwen.core.workflow.component.loop.callback.LoopCallback> callbacks,
                                       Object inputs,
                                       BaseSession session,
                                       ModelContext context) {
        WorkflowStateCollection state = WorkflowSessionSupport.stateCollection(session);
        if (state == null) {
            return null;
        }
        if (Boolean.TRUE.equals(state.get(COMPLETED))) {
            return state.get(COMPLETED_RESULT);
        }
        Object storedIndex = state.get(Constant.INDEX);
        boolean firstLoop = storedIndex == null;
        if (firstLoop) {
            state.update(Map.of(BROKEN, false, Constant.INDEX, 0));
            WorkflowSessionSupport.setOutputs(session, Map.of(Constant.INDEX, 0));
            commit(session);
            storedIndex = 0;
        }
        if (firstLoop) {
            for (com.openjiuwen.core.workflow.component.loop.callback.LoopCallback callback : callbacks) {
                callback.call(com.openjiuwen.core.workflow.component.loop.callback.LoopCallback.FIRST_LOOP, session);
            }
        }

        int loopTimes = storedIndex instanceof Number number ? number.intValue() : 0;
        int maxLoopTimes = maxLoopTimes(session);
        while (condition.evaluate(session)) {
            WorkflowSessionSupport.setOutputs(session, Map.of(Constant.INDEX, loopTimes));
            commit(session);
            for (com.openjiuwen.core.workflow.component.loop.callback.LoopCallback callback : callbacks) {
                callback.call(com.openjiuwen.core.workflow.component.loop.callback.LoopCallback.START_ROUND, session);
            }
            if (loopGroup != null) {
                try {
                    loopGroup.invoke(loopBodyInputs(inputs, session, loopTimes), session, context);
                } catch (RuntimeException exception) {
                    GraphInterrupt interrupt = findGraphInterrupt(exception);
                    if (interrupt != null) {
                        return sneakyThrow(interrupt);
                    }
                    throw exception;
                }
            }
            for (com.openjiuwen.core.workflow.component.loop.callback.LoopCallback callback : callbacks) {
                callback.call(com.openjiuwen.core.workflow.component.loop.callback.LoopCallback.END_ROUND,
                        session, loopTimes + 1);
            }
            loopTimes += 1;
            state.update(Map.of(Constant.INDEX, loopTimes));
            WorkflowSessionSupport.setOutputs(session, Map.of(Constant.INDEX, loopTimes));
            commit(session);
            if (isBreakRequested(session)) {
                break;
            }
            if (loopTimes > maxLoopTimes) {
                throw new IllegalStateException("Recursion limit of 10000 reached at step 10001");
            }
        }
        for (com.openjiuwen.core.workflow.component.loop.callback.LoopCallback callback : callbacks) {
            callback.call(com.openjiuwen.core.workflow.component.loop.callback.LoopCallback.OUT_LOOP, session);
        }
        state.update(nullableMap(BROKEN, false, Constant.INDEX, null));
        commit(session);
        Object rawBeforeCleanup = WorkflowSessionSupport.getOutputs(session, WorkflowSessionSupport.componentId(session));
        Object generatedLoopOutputs = normalizeLoopOutputs(rawBeforeCleanup);
        clearLoopBodyOutputs(session, loopGroup);
        Object normalizedOutputs = buildLoopOutputs(generatedLoopOutputs, rawBeforeCleanup, loopGroup);
        resetLoopOutputs(session, normalizedOutputs);
        Map<String, Object> completedState = new LinkedHashMap<>();
        completedState.put(COMPLETED, true);
        completedState.put(COMPLETED_RESULT, normalizedOutputs);
        state.update(completedState);
        commit(session);
        return normalizedOutputs;
    }

    private static Object loopBodyInputs(Object inputs, BaseSession session, int loopTimes) {
        if (!(inputs instanceof Map<?, ?> inputMap)) {
            return inputs;
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        inputMap.forEach((key, value) -> envelope.put(String.valueOf(key), value));
        Object rawInputs = envelope.get(Constant.INPUTS_KEY);
        Map<String, Object> bodyInputs = new LinkedHashMap<>();
        if (rawInputs instanceof Map<?, ?> rawInputMap) {
            rawInputMap.forEach((key, value) -> bodyInputs.put(String.valueOf(key), value));
        }
        bodyInputs.put(WorkflowSessionSupport.componentId(session), Map.of(Constant.INDEX, loopTimes));
        bodyInputs.put(Constant.INDEX, loopTimes);
        envelope.put(Constant.INPUTS_KEY, bodyInputs);
        return envelope;
    }

    private static void clearLoopBodyOutputs(BaseSession session, LoopGroup loopGroup) {
        if (loopGroup == null || loopGroup.getNodeIds().isEmpty()) {
            return;
        }
        Object outputs = WorkflowSessionSupport.getOutputs(session, WorkflowSessionSupport.componentId(session));
        if (!(outputs instanceof Map<?, ?> outputMap)) {
            return;
        }
        Map<String, Object> cleanup = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : outputMap.entrySet()) {
            cleanup.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        boolean changed = false;
        for (String nodeId : loopGroup.getNodeIds()) {
            if (cleanup.containsKey(nodeId)) {
                cleanup.put(nodeId, null);
                changed = true;
            }
        }
        if (changed) {
            WorkflowSessionSupport.setOutputs(session, cleanup);
            commit(session);
        }
    }

    private static Map<String, Object> nullableMap(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static Object normalizeLoopOutputs(Object outputs) {
        if (!(outputs instanceof Map<?, ?> outputMap)) {
            return outputs;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : outputMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (BROKEN.equals(key) || "round".equals(key) || "start".equals(key)) {
                continue;
            }
            // Align with Python out-of-loop: set_outputs({INDEX: None}) — omit INDEX from final outputs.
            if (Constant.INDEX.equals(key)) {
                continue;
            }
            if (isInternalLoopState(key, entry.getValue())) {
                continue;
            }
            normalized.put(key, entry.getValue());
        }
        return normalized;
    }

    private static Object buildLoopOutputs(Object outputs, Object rawOutputs, LoopGroup loopGroup) {
        if (!(outputs instanceof Map<?, ?> outputMap)) {
            return outputs;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : outputMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (BROKEN.equals(key) || "round".equals(key) || "start".equals(key)) {
                continue;
            }
            if (isLoopBodyNode(key, loopGroup)) {
                continue;
            }
            if (isInternalLoopState(key, entry.getValue())) {
                continue;
            }
            if (Constant.INDEX.equals(key)) {
                continue;
            }
            normalized.put(key, entry.getValue());
        }
        mergeGeneratedOutputLists(normalized, rawOutputs, loopGroup);
        return normalized;
    }

    private static void mergeGeneratedOutputLists(Map<String, Object> normalized, Object rawOutputs, LoopGroup loopGroup) {
        if (!(rawOutputs instanceof Map<?, ?> rawOutputMap)) {
            return;
        }
        for (Map.Entry<?, ?> entry : rawOutputMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (normalized.containsKey(key) || isLoopBodyNode(key, loopGroup)
                    || BROKEN.equals(key) || "round".equals(key) || "start".equals(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (isInternalLoopState(key, value)) {
                continue;
            }
            if (value instanceof List<?> list) {
                normalized.put(key, new java.util.ArrayList<>(list));
            }
        }
    }

    private static boolean isInternalLoopState(String key, Object value) {
        return "loop".equals(key) && isOnlyLoopIndexEnvelope(value);
    }

    private static boolean isOnlyLoopIndexEnvelope(Object value) {
        if (!(value instanceof Map<?, ?> valueMap)) {
            return false;
        }
        if (valueMap.isEmpty()) {
            return true;
        }
        if (valueMap.size() != 1) {
            return false;
        }
        if (valueMap.containsKey(Constant.INDEX)) {
            return true;
        }
        return valueMap.containsKey("loop") && isOnlyLoopIndexEnvelope(valueMap.get("loop"));
    }

    private static boolean isLoopBodyNode(String key, LoopGroup loopGroup) {
        return loopGroup != null && loopGroup.getNodeIds().contains(key);
    }

    private static void resetLoopOutputs(BaseSession session, Object normalizedOutputs) {
        if (!(normalizedOutputs instanceof Map<?, ?> normalizedMap)) {
            return;
        }
        Object currentOutputs = WorkflowSessionSupport.getOutputs(session, WorkflowSessionSupport.componentId(session));
        if (currentOutputs instanceof Map<?, ?> currentMap) {
            Map<String, Object> cleanup = new LinkedHashMap<>();
            for (Object key : currentMap.keySet()) {
                cleanup.put(String.valueOf(key), null);
            }
            WorkflowSessionSupport.setOutputs(session, cleanup);
            commit(session);
        }
        Map<String, Object> replacement = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : normalizedMap.entrySet()) {
            replacement.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        WorkflowSessionSupport.setOutputs(session, replacement);
        commit(session);
    }

    private static Map<String, Object> nullableMap(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(firstKey, firstValue);
        map.put(secondKey, secondValue);
        return map;
    }

    private static int maxLoopTimes(BaseSession session) {
        Object configured = WorkflowSessionSupport.getEnv(session, SessionConstants.LOOP_NUMBER_MAX_LIMIT_KEY);
        if (configured instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        return SessionConstants.LOOP_NUMBER_MAX_LIMIT_DEFAULT;
    }

    static boolean requestBreak(BaseSession session) {
        BaseSession target = loopOwnerSession(session);
        WorkflowStateCollection state = WorkflowSessionSupport.stateCollection(target);
        if (state == null) {
            return false;
        }
        state.update(Map.of(BROKEN, true));
        commit(target);
        return true;
    }

    private static boolean isBreakRequested(BaseSession session) {
        WorkflowStateCollection state = WorkflowSessionSupport.stateCollection(session);
        return state != null && Boolean.TRUE.equals(state.get(BROKEN));
    }

    private static BaseSession loopOwnerSession(BaseSession session) {
        BaseSession current = session;
        return WorkflowSessionSupport.parentOrSelf(current);
    }

    private static void commit(BaseSession session) {
        WorkflowCommitState commitState = WorkflowSessionSupport.workflowState(session);
        if (commitState != null) {
            commitState.commit();
        }
    }

    static GraphInterrupt findGraphInterrupt(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof GraphInterrupt interrupt) {
                return interrupt;
            }
            current = current.getCause();
        }
        return null;
    }

    static BaseError findBaseError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BaseError baseError) {
                return baseError;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String invocationGateKey(BaseSession session) {
        if (session == null) {
            return "null-session";
        }
        String sessionId = session.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "session@" + System.identityHashCode(session);
        }
        return sessionId + '\u0000' + WorkflowSessionSupport.workflowId(session) + '\u0000'
                + WorkflowSessionSupport.componentId(session);
    }

    private static final class InvocationGate {
        private final ReentrantLock lock = new ReentrantLock();
        private int users;
        private boolean hasCompletedResult;
        private boolean retainCompletedResult;
        private boolean completedResultConsumed;
        private Object completedResult;
    }

    @SuppressWarnings("unchecked")
    static <T extends Throwable, R> R sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }
}
