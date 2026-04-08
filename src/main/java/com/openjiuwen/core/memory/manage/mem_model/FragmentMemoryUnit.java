/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Fragment memory unit.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FragmentMemoryUnit extends BaseMemoryUnit {
    private String fragmentType;
    private String content;
    private String messageMemId;
    private String timestamp;

    @Override
    public MemoryType getMemType() {
        return MemoryType.FRAGMENT_MEMORY;
    }
}
