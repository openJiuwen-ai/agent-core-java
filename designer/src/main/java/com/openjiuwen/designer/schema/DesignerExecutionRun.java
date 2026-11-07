/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Designer execution run document.
 *
 * @param schemaVersion wire schema version
 * @param runId run identifier, stable for the graph lifetime
 * @param graphId owning graph identifier
 * @param projectId owning project identifier
 * @param status draft, running, paused, completed, failed, or cancelled
 * @param nodeStates per-node execution state
 * @param currentNodeIds nodes currently running
 * @param createdAt creation time in epoch milliseconds
 * @param updatedAt last update time in epoch milliseconds
 * @param metadata extensible metadata such as trigger_source
 * @param error run-level failure reason
 * @param warning single run-level warning
 * @param warnings additional run-level warnings
 * @since 2026-11-06
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DesignerExecutionRun(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("run_id") String runId,
        @JsonProperty("graph_id") String graphId,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("status") String status,
        @JsonProperty("node_states") Map<String, DesignerNodeState> nodeStates,
        @JsonProperty("current_node_ids") List<String> currentNodeIds,
        @JsonProperty("created_at") long createdAt,
        @JsonProperty("updated_at") long updatedAt,
        @JsonProperty("metadata") Map<String, Object> metadata,
        @JsonProperty("error") String error,
        @JsonProperty("warning") String warning,
        @JsonProperty("warnings") List<String> warnings) {

    public DesignerExecutionRun {
        nodeStates = nodeStates == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(nodeStates));
        currentNodeIds = currentNodeIds == null ? List.of() : List.copyOf(currentNodeIds);
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
