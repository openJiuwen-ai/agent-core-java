/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output schema for memory write component.
 * <p>
 * Mirrors Python's {@code MemoryWriteOutput}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryWriteOutput {

    @Builder.Default
    private boolean success = true;
}