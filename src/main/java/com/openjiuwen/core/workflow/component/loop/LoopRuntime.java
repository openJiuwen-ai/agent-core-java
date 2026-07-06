/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.context_engine.ModelContext;
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

final class LoopRuntime {

    private static final String BROKEN = "_broken";

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
            for (com.openjiuwen.core.workflow.component.loop.callback.LoopCallback callback : callbacks) {
                callback.call(com.openjiuwen.core.workflow.component.loop.callback.LoopCallback.START_ROUND, session);
            }
            if (loopGroup != null) {
                try {
                    loopGroup.invoke(inputs, session, context);
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
            if (loopTimes >= maxLoopTimes) {
                throw new IllegalStateException("Recursion limit of 10000 reached at step 10001");
            }
        }
        for (com.openjiuwen.core.workflow.component.loop.callback.LoopCallback callback : callbacks) {
            callback.call(com.openjiuwen.core.workflow.component.loop.callback.LoopCallback.OUT_LOOP, session);
        }
        state.update(nullableMap(BROKEN, false, Constant.INDEX, null));
        commit(session);
        clearLoopBodyOutputs(session, loopGroup);
        Object normalizedOutputs = normalizeLoopOutputs(
                WorkflowSessionSupport.getOutputs(session, WorkflowSessionSupport.componentId(session)));
        resetLoopOutputs(session, normalizedOutputs);
        return normalizedOutputs;
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
