/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import java.util.Objects;

/**
 * Variable memory unit with fixed memory type and empty memory id.
 *
 * <p>Mirrors Python's {@code VariableUnit} in
 * {@code openjiuwen/core/memory/manage/mem_model/memory_unit.py}.</p>
 */
public class VariableUnit extends BaseMemoryUnit {
    private String variableName;
    private String variableMem;

    public VariableUnit() {
        super(MemoryType.VARIABLE, "");
    }

    public VariableUnit(String variableName, String variableMem) {
        super(MemoryType.VARIABLE, "");
        this.variableName = variableName;
        this.variableMem = variableMem;
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

    @Override
    public void setMemType(MemoryType memType) {
        super.setMemType(MemoryType.VARIABLE);
    }

    @Override
    public void setMemId(String memId) {
        super.setMemId("");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VariableUnit that)) {
            return false;
        }
        return super.equals(that)
                && Objects.equals(variableName, that.variableName)
                && Objects.equals(variableMem, that.variableMem);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), variableName, variableMem);
    }
}
