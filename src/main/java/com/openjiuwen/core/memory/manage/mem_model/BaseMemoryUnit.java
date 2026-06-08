/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import java.util.Objects;

/**
 * Base model for a single memory data item.
 *
 * <p>Mirrors Python's {@code BaseMemoryUnit} in
 * {@code openjiuwen/core/memory/manage/mem_model/memory_unit.py}.</p>
 */
public class BaseMemoryUnit {
    private MemoryType memType;
    private String memId;

    public BaseMemoryUnit() {
    }

    public BaseMemoryUnit(MemoryType memType, String memId) {
        this.memType = memType;
        this.memId = memId;
    }

    public MemoryType getMemType() {
        return memType;
    }

    public void setMemType(MemoryType memType) {
        this.memType = memType;
    }

    public String getMemId() {
        return memId;
    }

    public void setMemId(String memId) {
        this.memId = memId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseMemoryUnit that)) {
            return false;
        }
        return memType == that.memType && Objects.equals(memId, that.memId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memType, memId);
    }
}
