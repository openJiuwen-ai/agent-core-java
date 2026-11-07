/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.spi;

/**
 * Designer project information supplied by the host.
 *
 * @param projectId project identifier
 * @param name display name
 * @param projectDir project directory
 * @param workMode work mode, normally {@code design}
 * @since 2026-11-06
 */
public record DesignerProjectInfo(
        String projectId,
        String name,
        String projectDir,
        String workMode) {
}
