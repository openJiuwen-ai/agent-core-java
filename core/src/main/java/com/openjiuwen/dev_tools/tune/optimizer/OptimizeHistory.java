/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe trajectory ledger for optimizer traces.
 *
 * <p>Mirrors Python's {@code OptimizeHistory} in
 * {@code openjiuwen/dev_tools/tune/optimizer/base.py}.</p>
 */
public class OptimizeHistory {

    private final ReentrantLock lock = new ReentrantLock();
    private Map<String, List<TraceNode>> trajectory = new LinkedHashMap<>();

    public void addHistory(String caseId, TraceNode node) {
        lock.lock();
        try {
            trajectory.computeIfAbsent(caseId, ignored -> new ArrayList<>()).add(node);
        } finally {
            lock.unlock();
        }
    }

    public void add_history(String caseId, TraceNode node) {
        addHistory(caseId, node);
    }

    public List<TraceNode> getHistory(String caseId) {
        if (!trajectory.containsKey(caseId)) {
            return null;
        }
        return trajectory.get(caseId);
    }

    public List<TraceNode> get_history(String caseId) {
        return getHistory(caseId);
    }

    public List<TraceNode> getLlmCallHistory(String caseId, String llmCallId) {
        List<TraceNode> traceNodeList = getHistory(caseId);
        if (traceNodeList == null || traceNodeList.isEmpty()) {
            return null;
        }
        List<TraceNode> llmCallTraceNodeList = new ArrayList<>();
        for (TraceNode node : traceNodeList) {
            if (llmCallId.equals(node.getLlmCallId())) {
                llmCallTraceNodeList.add(node);
            }
        }
        return llmCallTraceNodeList;
    }

    public List<TraceNode> get_llm_call_history(String caseId, String llmCallId) {
        return getLlmCallHistory(caseId, llmCallId);
    }

    public void clearHistory() {
        trajectory = new LinkedHashMap<>();
    }

    public void clear_history() {
        clearHistory();
    }
}
