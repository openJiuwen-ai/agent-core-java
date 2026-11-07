/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.spi;

/**
 * Request to create one designer session.
 *
 * @param projectId owning project
 * @param title desired title
 * @param prompt initial user prompt
 * @param modelName selected designer model
 * @param projectDir project directory
 * @since 2026-11-06
 */
public record DesignerSessionCreate(
        String projectId,
        String title,
        String prompt,
        String modelName,
        String projectDir) {
}
