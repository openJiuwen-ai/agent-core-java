/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.spi;

/**
 * Designer session information supplied by the host.
 *
 * @param sessionId session identifier
 * @param title display title
 * @param projectId owning project
 * @param projectDir project directory
 * @param workMode work mode, normally {@code design}
 * @param modelName selected designer model
 * @param graphId paired graph id, when known
 * @since 2026-11-06
 */
public record DesignerSessionInfo(
        String sessionId,
        String title,
        String projectId,
        String projectDir,
        String workMode,
        String modelName,
        String graphId) {
}
