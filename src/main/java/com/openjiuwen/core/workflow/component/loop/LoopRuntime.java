/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.workflow.condition.Condition;
import com.openjiuwen.core.workflow.internal.WorkflowSessionSupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LoopRuntime {

    static final String BROKEN = "_broken";

    private LoopRuntime() {
    }

    static Object invoke(Condition condition,
                         LoopGroup loopGroup,
                         List<com.openjiuwen.core.workflow.component.loop.callback.LoopCallback> callbacks,
                         Object inputs,
                         BaseSession session,
                         ModelContext context) {
        WorkflowStateCollection state = WorkflowSessionSupport.stateCollection(session);
        if (state == null) {
            return null;
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
        Object normalizedOutputs = buildLoopOutputs(generatedLoopOutputs, rawBeforeCleanup, loopGroup, loopTimes);
        resetLoopOutputs(session, normalizedOutputs);
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
        Object index = null;
        for (Map.Entry<?, ?> entry : outputMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (BROKEN.equals(key) || "round".equals(key) || "start".equals(key)) {
                continue;
            }
            if (Constant.INDEX.equals(key)) {
                index = 0;
                continue;
            }
            normalized.put(key, entry.getValue());
        }
        if (index != null || outputMap.containsKey(Constant.INDEX)) {
            normalized.put(Constant.INDEX, index);
        }
        return normalized;
    }

    private static Object buildLoopOutputs(Object outputs, Object rawOutputs, LoopGroup loopGroup, int loopTimes) {
        if (!(outputs instanceof Map<?, ?> outputMap)) {
            return outputs;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        boolean hasInteractiveBodyOutput = false;
        boolean hasInteractiveLoopNode = hasInteractiveLoopNode(loopGroup);
        for (Map.Entry<?, ?> entry : outputMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (BROKEN.equals(key) || "round".equals(key) || "start".equals(key)) {
                continue;
            }
            if (isLoopBodyNode(key, loopGroup)) {
                if (isInteractiveOutput(entry.getValue())) {
                    normalized.put(key, entry.getValue());
                    hasInteractiveBodyOutput = true;
                }
                continue;
            }
            if (Constant.INDEX.equals(key)) {
                normalized.put(Constant.INDEX, 0);
            } else {
                normalized.put(key, entry.getValue());
            }
        }
        if (outputMap.containsKey(Constant.INDEX) && !normalized.containsKey(Constant.INDEX)) {
            normalized.put(Constant.INDEX, 0);
        }
        if ((hasInteractiveBodyOutput || hasInteractiveLoopNode) && !normalized.containsKey("loop")) {
            normalized.put("loop", Map.of("loop", Map.of(Constant.INDEX, loopEnvelopeIndex(normalized, loopTimes))));
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
            if (value instanceof List<?> list) {
                normalized.put(key, new java.util.ArrayList<>(list));
            }
        }
    }

    private static boolean isLoopBodyNode(String key, LoopGroup loopGroup) {
        return loopGroup != null && loopGroup.getNodeIds().contains(key);
    }

    private static boolean isInteractiveOutput(Object value) {
        return value instanceof Map<?, ?> valueMap && valueMap.containsKey("confirm_result");
    }

    private static boolean hasInteractiveLoopNode(LoopGroup loopGroup) {
        if (loopGroup == null) {
            return false;
        }
        for (String nodeId : loopGroup.getNodeIds()) {
            if (nodeId != null && nodeId.contains("interactive")) {
                return true;
            }
        }
        return false;
    }

    private static int loopEnvelopeIndex(Map<String, Object> normalized, int loopTimes) {
        int index = loopTimes;
        for (Object value : normalized.values()) {
            if (value instanceof List<?> list) {
                index = Math.max(index, list.size());
            }
        }
        return index;
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
        BaseSession current = session instanceof NodeSessionApi nodeSessionApi ? nodeSessionApi.getInner() : session;
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

    @SuppressWarnings("unchecked")
    static <T extends Throwable, R> R sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }
}
