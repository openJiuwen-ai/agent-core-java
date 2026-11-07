/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.spi;

import java.util.List;
import java.util.Optional;

/**
 * Host port for designer projects.
 *
 * @since 2026-11-06
 */
public interface DesignerProjectPort {

    /**
     * Ensure the virtual default design project exists.
     *
     * @return default project
     */
    DesignerProjectInfo ensureDefaultDesignProject();

    /**
     * Find one project.
     *
     * @param projectId project id
     * @return project when present
     */
    Optional<DesignerProjectInfo> findProject(String projectId);

    /**
     * List projects visible to the designer.
     *
     * @return projects, never null
     */
    List<DesignerProjectInfo> listProjects();
}
