/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import java.util.List;

/**
 * Public class RuntimeCleanupResult used by the Java parity implementation.
 *
 * @since 1.0
 */
public class RuntimeCleanupResult {
    private final List<String> deletedTables;
    private final List<String> clearedTables;

    private RuntimeCleanupResult(Builder builder) {
        this.deletedTables = builder.deletedTables != null ? List.copyOf(builder.deletedTables) : List.of();
        this.clearedTables = builder.clearedTables != null ? List.copyOf(builder.clearedTables) : List.of();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getDeletedTables() {
        return deletedTables;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getClearedTables() {
        return clearedTables;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class Builder {
        private List<String> deletedTables;
        private List<String> clearedTables;

        private Builder() {
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder deletedTables(List<String> deletedTables) {
            this.deletedTables = deletedTables;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder clearedTables(List<String> clearedTables) {
            this.clearedTables = clearedTables;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public RuntimeCleanupResult build() {
            return new RuntimeCleanupResult(this);
        }
    }
}
