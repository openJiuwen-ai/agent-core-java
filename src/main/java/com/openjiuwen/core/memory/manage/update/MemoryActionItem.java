  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.memory.manage.update;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a memory with its action status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryActionItem {
    private String id;
    private String content;
    private MemoryStatus status;
}
