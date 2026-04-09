  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.memory.manage.mem_model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Variable memory unit.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VariableUnit extends BaseMemoryUnit {
    private String variableName;
    private String variableMem;

    @Override
    public MemoryType getMemType() {
        return MemoryType.VARIABLE;
    }

    @Override
    public String getMemId() {
        return "";
    }
}
