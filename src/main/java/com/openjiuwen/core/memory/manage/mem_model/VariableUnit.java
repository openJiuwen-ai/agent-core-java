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

    public static VariableUnitBuilder builder() {
        return new VariableUnitBuilder();
    }

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public String getVariableMem() {
        return variableMem;
    }

    public void setVariableMem(String variableMem) {
        this.variableMem = variableMem;
    }

    public MemoryType getMemType() {
        return MemoryType.VARIABLE;
    }

    public String getMemId() {
        return "";
    }

    public static final class VariableUnitBuilder {
        private MemoryType memType;
        private String memId;
        private String variableName;
        private String variableMem;

        public VariableUnitBuilder memType(MemoryType memType) {
            this.memType = memType;
            return this;
        }

        public VariableUnitBuilder memId(String memId) {
            this.memId = memId;
            return this;
        }

        public VariableUnitBuilder variableName(String variableName) {
            this.variableName = variableName;
            return this;
        }

        public VariableUnitBuilder variableMem(String variableMem) {
            this.variableMem = variableMem;
            return this;
        }

        public VariableUnit build() {
            VariableUnit unit = new VariableUnit();
            unit.setMemType(memType);
            unit.setMemId(memId);
            unit.variableName = variableName;
            unit.variableMem = variableMem;
            return unit;
        }
    }
}
