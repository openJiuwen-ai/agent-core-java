/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Per-node execution state inside a designer run.
 *
 * @param status pending, running, completed, failed, or cancelled
 * @param startedAt start time in epoch milliseconds
 * @param completedAt completion time in epoch milliseconds
 * @param outputRef primary output
 * @param outputRefs all outputs, primary first
 * @param error failure reason
 * @param blockedBy upstream node ids that prevented execution
 * @param activity latest activity
 * @param activityTail recent activity lines
 * @param activityLog structured recent activity entries
 * @param text textual output
 * @param history archived output versions, oldest first
 * @param version execution version
 * @since 2026-11-06
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DesignerNodeState(
        @JsonProperty("status") String status,
        @JsonProperty("started_at") Long startedAt,
        @JsonProperty("completed_at") Long completedAt,
        @JsonProperty("output_ref") DesignerAssetRef outputRef,
        @JsonProperty("output_refs") List<DesignerAssetRef> outputRefs,
        @JsonProperty("error") String error,
        @JsonProperty("blocked_by") List<String> blockedBy,
        @JsonProperty("activity") DesignerNodeActivity activity,
        @JsonProperty("activity_tail") List<String> activityTail,
        @JsonProperty("activity_log") List<DesignerNodeActivity> activityLog,
        @JsonProperty("text") String text,
        @JsonProperty("history") List<DesignerNodeHistory> history,
        @JsonProperty("version") int version) {

    public DesignerNodeState {
        outputRefs = outputRefs == null ? List.of() : List.copyOf(outputRefs);
        blockedBy = blockedBy == null ? List.of() : List.copyOf(blockedBy);
        activityTail = activityTail == null ? List.of() : List.copyOf(activityTail);
        activityLog = activityLog == null ? List.of() : List.copyOf(activityLog);
        history = history == null ? List.of() : List.copyOf(history);
    }
}
