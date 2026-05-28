/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Holds state for memory_search / memory_get / read-write tools (node "memory").
 * <p>
 * Mirrors Python's {@code MemoryToolContext} dataclass from
 * {@code core/memory/lite/memory_tool_context.py}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class MemoryToolContext extends LiteMemoryToolContextBase {
    // Inherits all fields from LiteMemoryToolContextBase
    // No additional fields — this is a marker subclass.
}
