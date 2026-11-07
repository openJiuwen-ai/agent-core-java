/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.workspace;

import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.spi.DesignerWorkspacePort;

import java.nio.file.Path;

/**
 * Default filesystem layout for standalone designer use.
 *
 * @since 2026-11-06
 */
public final class DesignerDefaultWorkspacePort implements DesignerWorkspacePort {

    private final Path root;

    /**
     * Create a workspace rooted at the supplied directory.
     *
     * @param root designer data root
     */
    public DesignerDefaultWorkspacePort(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Path graphRoot() {
        return root;
    }

    @Override
    public Path outputDir(DesignerGraph graph) {
        Object sessionId = graph.metadata().get("session_id");
        String owner = sessionId == null || String.valueOf(sessionId).isBlank()
                ? graph.graphId()
                : String.valueOf(sessionId);
        return root.resolve("projects").resolve(owner).resolve("outputs");
    }

    @Override
    public Path mediaDir(DesignerGraph graph) {
        return outputDir(graph).resolve("media");
    }
}
