/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.execution;

import com.openjiuwen.designer.schema.DesignerExecutionRun;
import com.openjiuwen.designer.schema.DesignerNodeState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies node lifecycle transitions to an immutable run snapshot.
 *
 * @since 2026-11-06
 */
public final class DesignerRunStateMapper {

    private DesignerRunStateMapper() {
    }

    /**
     * Mark a node running.
     *
     * @param run current run
     * @param nodeId node id
     * @param now epoch milliseconds
     * @return updated run
     */
    public static DesignerExecutionRun started(DesignerExecutionRun run, String nodeId, long now) {
        Map<String, DesignerNodeState> states = new LinkedHashMap<>(run.nodeStates());
        DesignerNodeState current = states.getOrDefault(nodeId, emptyState());
        states.put(nodeId, new DesignerNodeState("running", now, null, current.outputRef(),
                current.outputRefs(), null, List.of(), current.activity(), current.activityTail(),
                current.activityLog(), current.text(), current.history(), current.version()));
        List<String> currentIds = new ArrayList<>(run.currentNodeIds());
        if (!currentIds.contains(nodeId)) {
            currentIds.add(nodeId);
        }
        return runWith(run, states, currentIds, "running", now);
    }

    /**
     * Apply a node outcome.
     *
     * @param run current run
     * @param nodeId node id
     * @param outcome outcome
     * @param now epoch milliseconds
     * @return updated run
     */
    public static DesignerExecutionRun finished(DesignerExecutionRun run, String nodeId,
            NodeOutcome outcome, long now) {
        Map<String, DesignerNodeState> states = new LinkedHashMap<>(run.nodeStates());
        DesignerNodeState current = states.getOrDefault(nodeId, emptyState());
        String status = outcome.isCompleted() ? "completed" : "failed";
        states.put(nodeId, new DesignerNodeState(status, current.startedAt(), now,
                outcome.outputRef(), outcome.outputRefs(), outcome.error(), outcome.blockedBy(),
                current.activity(), current.activityTail(), current.activityLog(), outcome.text(),
                current.history(), current.version()));
        List<String> currentIds = new ArrayList<>(run.currentNodeIds());
        currentIds.remove(nodeId);
        return runWith(run, states, currentIds, run.status(), now);
    }

    /**
     * Mark a run terminal.
     *
     * @param run current run
     * @param status terminal status
     * @param error optional error
     * @param now epoch milliseconds
     * @return updated run
     */
    public static DesignerExecutionRun terminal(DesignerExecutionRun run, String status,
            String error, long now) {
        return new DesignerExecutionRun(run.schemaVersion(), run.runId(), run.graphId(),
                run.projectId(), status, run.nodeStates(), List.of(), run.createdAt(), now,
                run.metadata(), error, run.warning(), run.warnings());
    }

    private static DesignerExecutionRun runWith(DesignerExecutionRun run,
            Map<String, DesignerNodeState> states, List<String> currentIds, String status, long now) {
        return new DesignerExecutionRun(run.schemaVersion(), run.runId(), run.graphId(),
                run.projectId(), status, states, currentIds, run.createdAt(), now,
                run.metadata(), run.error(), run.warning(), run.warnings());
    }

    private static DesignerNodeState emptyState() {
        return new DesignerNodeState("pending", null, null, null, List.of(), null, List.of(),
                null, List.of(), List.of(), null, List.of(), 1);
    }
}
