/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates and normalizes designer run documents.
 *
 * @since 2026-11-06
 */
public final class DesignerRunNormalizer {

    /**
     * Normalize one run document.
     *
     * @param run raw run
     * @return normalized run
     * @throws DesignerGraphValidationException when the document violates the contract
     */
    public DesignerExecutionRun normalize(DesignerExecutionRun run) {
        if (run == null) {
            throw new DesignerGraphValidationException("run is required");
        }
        if (!DesignerSchema.RUN_SCHEMA_VERSION.equals(run.schemaVersion())) {
            throw new DesignerGraphValidationException("unsupported run schema_version: "
                    + run.schemaVersion());
        }
        String runId = DesignerGraphNormalizer.storageComponent(run.runId(), "run_id");
        String graphId = DesignerGraphNormalizer.storageComponent(run.graphId(), "graph_id");
        String projectId = requireNonBlank(run.projectId(), "project_id");
        String status = normalizeStatus(run.status());
        Map<String, DesignerNodeState> states = normalizeStates(run.nodeStates());
        List<String> current = stringList(run.currentNodeIds(), "current_node_ids");
        long now = System.currentTimeMillis();
        long createdAt = run.createdAt() > 0L ? run.createdAt() : now;
        return new DesignerExecutionRun(
                DesignerSchema.RUN_SCHEMA_VERSION,
                runId,
                graphId,
                projectId,
                status,
                states,
                current,
                createdAt,
                now,
                run.metadata() == null ? Map.of() : run.metadata(),
                truncate(run.error(), DesignerSchema.RUN_ERROR_LIMIT),
                truncate(run.warning(), DesignerSchema.RUN_ERROR_LIMIT),
                normalizeWarnings(run.warnings()));
    }

    private Map<String, DesignerNodeState> normalizeStates(Map<String, DesignerNodeState> rawStates) {
        Map<String, DesignerNodeState> states = new LinkedHashMap<>();
        if (rawStates == null) {
            return states;
        }
        for (Map.Entry<String, DesignerNodeState> entry : rawStates.entrySet()) {
            String nodeId = DesignerGraphNormalizer.storageComponent(entry.getKey(), "node_id");
            states.put(nodeId, normalizeState(entry.getValue()));
        }
        return states;
    }

    private DesignerNodeState normalizeState(DesignerNodeState rawState) {
        if (rawState == null) {
            return new DesignerNodeState("pending", null, null, null, List.of(), null,
                    List.of(), null, List.of(), List.of(), null, List.of(), 1);
        }
        String status = rawState.status() == null || rawState.status().isBlank()
                ? "pending"
                : rawState.status();
        if (!DesignerSchema.NODE_STATUSES.contains(status)) {
            throw new DesignerGraphValidationException("unsupported node status: " + status);
        }
        return new DesignerNodeState(
                status,
                rawState.startedAt(),
                rawState.completedAt(),
                normalizeAssetRef(rawState.outputRef()),
                normalizeAssetRefs(rawState.outputRefs()),
                truncate(rawState.error(), DesignerSchema.NODE_ERROR_LIMIT),
                stringList(rawState.blockedBy(), "blocked_by"),
                normalizeActivity(rawState.activity()),
                tail(rawState.activityTail(), DesignerSchema.ACTIVITY_TAIL_LIMIT),
                log(rawState.activityLog(), DesignerSchema.ACTIVITY_LOG_LIMIT),
                rawState.text(),
                history(rawState.history()),
                rawState.version() > 0 ? rawState.version() : 1);
    }

    private DesignerNodeActivity normalizeActivity(DesignerNodeActivity activity) {
        if (activity == null) {
            return null;
        }
        return new DesignerNodeActivity(
                activity.kind(),
                truncate(activity.text(), DesignerSchema.ACTIVITY_TEXT_LIMIT),
                activity.tool(),
                activity.at());
    }

    private List<String> tail(List<String> values, int limit) {
        List<String> normalized = stringList(values, "activity_tail");
        int from = Math.max(0, normalized.size() - limit);
        return new ArrayList<>(normalized.subList(from, normalized.size()));
    }

    private List<DesignerNodeActivity> log(List<DesignerNodeActivity> values, int limit) {
        List<DesignerNodeActivity> normalized = new ArrayList<>();
        for (DesignerNodeActivity value : values == null ? List.<DesignerNodeActivity>of() : values) {
            DesignerNodeActivity activity = normalizeActivity(value);
            if (activity != null) {
                normalized.add(activity);
            }
        }
        int from = Math.max(0, normalized.size() - limit);
        return new ArrayList<>(normalized.subList(from, normalized.size()));
    }

    private List<DesignerNodeHistory> history(List<DesignerNodeHistory> values) {
        List<DesignerNodeHistory> normalized = new ArrayList<>();
        for (DesignerNodeHistory value : values == null ? List.<DesignerNodeHistory>of() : values) {
            if (value != null) {
                normalized.add(new DesignerNodeHistory(value.ts(), value.text(),
                        normalizeAssetRef(value.outputRef())));
            }
        }
        int from = Math.max(0, normalized.size() - DesignerSchema.NODE_HISTORY_LIMIT);
        return new ArrayList<>(normalized.subList(from, normalized.size()));
    }

    private List<DesignerAssetRef> normalizeAssetRefs(List<DesignerAssetRef> refs) {
        List<DesignerAssetRef> normalized = new ArrayList<>();
        for (DesignerAssetRef ref : refs == null ? List.<DesignerAssetRef>of() : refs) {
            DesignerAssetRef value = normalizeAssetRef(ref);
            if (value != null) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private DesignerAssetRef normalizeAssetRef(DesignerAssetRef ref) {
        if (ref == null) {
            return null;
        }
        String kind = requireNonBlank(ref.kind(), "output_ref.kind");
        if (!DesignerSchema.NODE_TYPES.contains(kind)) {
            throw new DesignerGraphValidationException("unsupported output_ref kind: " + kind);
        }
        return new DesignerAssetRef(kind, requireNonBlank(ref.uri(), "output_ref.uri"),
                ref.mimeType(), ref.label());
    }

    private List<String> normalizeWarnings(List<String> warnings) {
        List<String> normalized = new ArrayList<>();
        for (String warning : warnings == null ? List.<String>of() : warnings) {
            String value = truncate(warning, DesignerSchema.RUN_ERROR_LIMIT);
            if (value != null && !value.isBlank()) {
                normalized.add(value);
            }
        }
        int from = Math.max(0, normalized.size() - DesignerSchema.RUN_WARNINGS_LIMIT);
        return new ArrayList<>(normalized.subList(from, normalized.size()));
    }

    private List<String> stringList(List<String> values, String field) {
        List<String> normalized = new ArrayList<>();
        for (String value : values == null ? List.<String>of() : values) {
            if (value == null || value.isBlank()) {
                throw new DesignerGraphValidationException(field + " must not contain blanks");
            }
            normalized.add(value);
        }
        return normalized;
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "draft";
        }
        if (!DesignerSchema.RUN_STATUSES.contains(status)) {
            throw new DesignerGraphValidationException("unsupported run status: " + status);
        }
        return status;
    }

    private static String requireNonBlank(String value, String field) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            throw new DesignerGraphValidationException(field + " is required");
        }
        return text;
    }

    private static String truncate(String value, int limit) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
