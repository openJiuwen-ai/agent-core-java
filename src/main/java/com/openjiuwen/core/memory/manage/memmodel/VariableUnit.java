/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import java.util.Objects;

/**
 * Variable memory unit.
 * Corresponds to Python: manage/mem_model/memory_unit.py - VariableUnit
 */
public class VariableUnit extends BaseMemoryUnit {

    private final String variableName;
    private final String variableMem;
    private final String memId;

    private VariableUnit(Builder builder) {
        super(MemoryType.VARIABLE, builder.userId, builder.scopeId);
        this.variableName = builder.variableName;
        this.variableMem = builder.variableMem;
        this.memId = builder.memId != null ? builder.memId : "";
    }

    public String getVariableName() {
        return variableName;
    }

    public String getVariableMem() {
        return variableMem;
    }

    public String getMemId() {
        return memId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String userId;
        private String scopeId;
        private String variableName;
        private String variableMem;
        private String memId = "";

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder scopeId(String scopeId) {
            this.scopeId = scopeId;
            return this;
        }

        public Builder variableName(String variableName) {
            this.variableName = variableName;
            return this;
        }

        public Builder variableMem(String variableMem) {
            this.variableMem = variableMem;
            return this;
        }

        public Builder memId(String memId) {
            this.memId = memId;
            return this;
        }

        public VariableUnit build() {
            Objects.requireNonNull(userId, "userId is required");
            Objects.requireNonNull(scopeId, "scopeId is required");
            Objects.requireNonNull(variableName, "variableName is required");
            Objects.requireNonNull(variableMem, "variableMem is required");
            return new VariableUnit(this);
        }
    }

    @Override
    public String toString() {
        return "VariableUnit{" +
               "variableName='" + variableName + '\'' +
               ", variableMem='" + variableMem + '\'' +
               ", memId='" + memId + '\'' +
               ", userId='" + getUserId() + '\'' +
               ", scopeId='" + getScopeId() + '\'' +
               '}';
    }
}

