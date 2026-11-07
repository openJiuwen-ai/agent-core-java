/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.execution;

import com.openjiuwen.designer.schema.DesignerAssetRef;

import java.util.List;

/**
 * Result of executing one designer node.
 *
 * @param status completed, failed, or skipped
 * @param text textual output
 * @param outputRef primary asset
 * @param outputRefs all assets, primary first
 * @param error failure reason
 * @param blockedBy upstream nodes that blocked execution
 * @since 2026-11-06
 */
public record NodeOutcome(
        String status,
        String text,
        DesignerAssetRef outputRef,
        List<DesignerAssetRef> outputRefs,
        String error,
        List<String> blockedBy) {

    public NodeOutcome {
        outputRefs = outputRefs == null ? List.of() : List.copyOf(outputRefs);
        blockedBy = blockedBy == null ? List.of() : List.copyOf(blockedBy);
    }

    /**
     * Create a completed text outcome.
     *
     * @param text textual output
     * @return completed outcome
     */
    public static NodeOutcome completedText(String text) {
        return new NodeOutcome("completed", text, null, List.of(), null, List.of());
    }

    /**
     * Create a completed media outcome.
     *
     * @param text optional message
     * @param outputRef primary asset
     * @param outputRefs all assets
     * @return completed outcome
     */
    public static NodeOutcome completedMedia(String text, DesignerAssetRef outputRef,
            List<DesignerAssetRef> outputRefs) {
        List<DesignerAssetRef> refs = outputRefs == null || outputRefs.isEmpty()
                ? List.of(outputRef)
                : outputRefs;
        return new NodeOutcome("completed", text, outputRef, refs, null, List.of());
    }

    /**
     * Create a failed outcome.
     *
     * @param error failure reason
     * @return failed outcome
     */
    public static NodeOutcome failed(String error) {
        return new NodeOutcome("failed", null, null, List.of(), error, List.of());
    }

    /**
     * Create a skipped outcome.
     *
     * @param blockedBy upstream nodes that blocked execution
     * @param error reason
     * @return skipped outcome
     */
    public static NodeOutcome skipped(List<String> blockedBy, String error) {
        return new NodeOutcome("skipped", null, null, List.of(), error, blockedBy);
    }

    /**
     * Whether this outcome is completed.
     *
     * @return true when completed
     */
    public boolean isCompleted() {
        return "completed".equals(status);
    }
}
