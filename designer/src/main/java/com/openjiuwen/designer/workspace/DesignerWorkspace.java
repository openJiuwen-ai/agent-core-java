/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.workspace;

import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.spi.DesignerProjectInfo;
import com.openjiuwen.designer.spi.DesignerSessionInfo;

import java.util.List;

/**
 * Workspace payload returned by the designer workspace RPCs.
 *
 * @param project owning project
 * @param session selected session
 * @param graph selected graph
 * @param sessions sessions in the project
 * @since 2026-11-06
 */
public record DesignerWorkspace(
        DesignerProjectInfo project,
        DesignerSessionInfo session,
        DesignerGraph graph,
        List<DesignerSessionInfo> sessions) {

    public DesignerWorkspace {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
    }
}
