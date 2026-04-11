/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Base class for a single memory data item.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BaseMemoryUnit {
    private MemoryType memType;
    private String memId;
}
