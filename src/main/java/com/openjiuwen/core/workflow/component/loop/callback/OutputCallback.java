/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop.callback;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.session.utils.SessionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loop callback that collects round results and generates final output.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.callback.output.OutputCallback}.
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
        if (session.state() instanceof WorkflowStateCollection) {
            List<Object> results = new ArrayList<>();
            ((WorkflowStateCollection) session.state()).update(Map.of(roundResultRoot, results));
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object outLoop(BaseSession session) {
        if (!(session.state() instanceof WorkflowStateCollection)) {
            return null;
        }
        WorkflowStateCollection state = (WorkflowStateCollection) session.state();
        Object raw = state.get(roundResultRoot);
        List<Object> results = (raw instanceof List) ? (List<Object>) raw : new ArrayList<>();
        return generateOutput(session, results, new ArrayList<>(), outputsFormat);
    }

    @Override
    public Object startRound(BaseSession session) {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object endRound(BaseSession session, int loopTimes) {
        if (!(session.state() instanceof WorkflowStateCollection)) {
            return null;
        }
        WorkflowStateCollection state = (WorkflowStateCollection) session.state();
        Object raw = state.get(roundResultRoot);
        if (!(raw instanceof List)) {
            throw new IllegalStateException("error results in round process");
        }
        List<Object> results = (List<Object>) raw;
        if (results.size() >= loopTimes) {
            return null;
        }
        Object roundInputs = state.getInputs(outputsFormat);
        results.add(roundInputs);
        state.update(Map.of(roundResultRoot, results));
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object generateOutput(BaseSession session, List<Object> results, List<String> root, Object outputFormat) {
        if (outputFormat instanceof Map) {
            Map<String, Object> output = new HashMap<>();
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
            // Compare first path segment with the session's node_id
            if (pathParts.length > 0 && session instanceof NodeSession) {
                String nodeId = ((NodeSession) session).nodeId();
                if (pathParts[0].equals(nodeId)) {
                    if (results.isEmpty()) {
                        return null;
                    }
                    Object data = results.get(results.size() - 1);
                    for (String key : root) {
                        if (data instanceof Map) {
                            data = ((Map<String, Object>) data).get(key);
                        } else {
                            return null;
                        }
                    }
                    return data;
                }
            }
        }

        List<Object> output = new ArrayList<>();
        for (Object result : results) {
            Object data = result;
            for (String key : root) {
                if (data instanceof Map) {
                    data = ((Map<String, Object>) data).get(key);
                } else {
                    data = null;
                    break;
                }
            }
            output.add(data);
        }
        return output;
    }
}
