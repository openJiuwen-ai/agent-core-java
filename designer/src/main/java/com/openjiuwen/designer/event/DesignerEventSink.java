/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.event;

import com.openjiuwen.designer.schema.DesignerExecutionRun;
import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerNodeActivity;

/**
 * Host sink for designer domain events.
 *
 * <p>The core emits events; the host maps them to its wire protocol.</p>
 *
 * @since 2026-11-06
 */
public interface DesignerEventSink {

    /**
     * Publish a full run snapshot.
     *
     * @param sessionId owning session
     * @param run run snapshot
     */
    default void onRunUpdated(String sessionId, DesignerExecutionRun run) {
    }

    /**
     * Publish a node update with the full run snapshot.
     *
     * @param sessionId owning session
     * @param run run snapshot
     * @param nodeId updated node
     */
    default void onNodeUpdated(String sessionId, DesignerExecutionRun run, String nodeId) {
    }

    /**
     * Publish a full graph snapshot.
     *
     * @param sessionId owning session
     * @param graph graph snapshot
     */
    default void onGraphUpdated(String sessionId, DesignerGraph graph) {
    }

    /**
     * Publish a leader activity line.
     *
     * @param sessionId owning session
     * @param activity activity
     */
    default void onLeaderActivity(String sessionId, DesignerNodeActivity activity) {
    }
}
