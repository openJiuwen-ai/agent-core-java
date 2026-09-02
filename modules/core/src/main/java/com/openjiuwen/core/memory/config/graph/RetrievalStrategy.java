/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config.graph;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Retrieval strategy during add memory.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RetrievalStrategy extends BaseStrategy {
    private boolean isSameKind = false;
}
