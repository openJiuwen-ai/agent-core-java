/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.spi;

import com.openjiuwen.designer.schema.DesignerGraph;

import java.nio.file.Path;

/**
 * Host port for designer filesystem locations.
 *
 * @since 2026-11-06
 */
public interface DesignerWorkspacePort {

    /**
     * Root for graph and run documents.
     *
     * @return graph root
     */
    Path graphRoot();

    /**
     * Directory for generated node outputs.
     *
     * @param graph owning graph
     * @return output directory
     */
    Path outputDir(DesignerGraph graph);

    /**
     * Directory for generated media.
     *
     * @param graph owning graph
     * @return media directory
     */
    Path mediaDir(DesignerGraph graph);
}
