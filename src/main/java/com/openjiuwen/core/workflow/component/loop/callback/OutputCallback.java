/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop.callback;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.session.utils.SessionUtils;
import com.openjiuwen.core.workflow.internal.WorkflowSessionSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loop callback that collects round results and generates final output.
 *
 * <p>Mirrors Python's {@code OutputCallback} in
 * {@code openjiuwen/core/workflow/components/flow/loop/callback/output.py}.</p>
 */
public class OutputCallback extends LoopCallback {

    private final Map<String, Object> outputsFormat;
    private final String resultRoot;
    private final String roundResultRoot;

    public OutputCallback(Map<String, Object> outputsFormat, String roundResultRoot, String resultRoot) {
        this.outputsFormat = outputsFormat;
        this.resultRoot = resultRoot;
        this.roundResultRoot = (roundResultRoot != null && !roundResultRoot.isEmpty()) ? roundResultRoot : "round";
    }

    public OutputCallback(Map<String, Object> outputsFormat) {
        this(outputsFormat, null, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object firstInLoop(BaseSession session) {
        WorkflowStateCollection state = WorkflowSessionSupport.stateCollection(session);
        if (state != null) {
            List<Object> results = new ArrayList<>();
            state.update(Map.of(roundResultRoot, results));
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object outLoop(BaseSession session) {
        WorkflowStateCollection state = WorkflowSessionSupport.stateCollection(session);
        if (state == null) {
            return null;
        }
        List<Object> results = (List<Object>) state.get(roundResultRoot);
        return generateOutput(session, results, new ArrayList<>(), outputsFormat);
    }

    @Override
    public Object startRound(BaseSession session) {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object endRound(BaseSession session, Integer loopTimes) {
        WorkflowStateCollection state = WorkflowSessionSupport.stateCollection(session);
        if (state == null) {
            return null;
        }
        Object raw = state.get(roundResultRoot);
        if (!(raw instanceof List)) {
            throw new IllegalStateException("error results in round process");
        }
        List<Object> results = (List<Object>) raw;
        if (results.size() >= loopTimes) {
            return null;
        }
        Object roundInputs = WorkflowSessionSupport.getInputs(session, outputsFormat);
        results.add(roundInputs);
        state.update(Map.of(roundResultRoot, results));
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object generateOutput(BaseSession session, List<Object> results, List<String> root, Object outputFormat) {
        if (outputFormat instanceof Map) {
            Map<String, Object> output = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) outputFormat).entrySet()) {
                List<String> path = new ArrayList<>(root);
                path.add(entry.getKey());
                output.put(entry.getKey(), generateOutput(session, results, path, entry.getValue()));
            }
            return output;
        }

        if (outputFormat instanceof String && SessionUtils.isRefPath((String) outputFormat)) {
            String refStr = SessionUtils.extractOriginKey((String) outputFormat);
            String[] pathParts = refStr.split("\\.");
            if (pathParts.length > 0) {
                String nodeId = WorkflowSessionSupport.componentId(session);
                if (pathParts[0].equals(nodeId)) {
                    return valueFromLatestRound(results, root);
                }
            }
        }

        List<Object> output = new ArrayList<>();
        for (Object result : results) {
            output.add(valueFromPath(result, root));
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    private Object valueFromLatestRound(List<Object> results, List<String> root) {
        if (results.isEmpty()) {
            return null;
        }
        return valueFromPath(results.get(results.size() - 1), root);
    }

    @SuppressWarnings("unchecked")
    private Object valueFromPath(Object data, List<String> root) {
        for (String key : root) {
            data = ((Map<String, Object>) data).get(key);
        }
        return data;
    }
}
