/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import java.util.List;

/**
 * Public class RuntimeCleanupResult used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class RuntimeCleanupResult {
    private final List<String> deletedTables;
    private final List<String> clearedTables;

    /**
     * RuntimeCleanupResult.
     * 
     * @param builder builder
     * @since 0.1.7
     */
    private RuntimeCleanupResult(Builder builder) {
        this.deletedTables = builder.deletedTables != null ? List.copyOf(builder.deletedTables) : List.of();
        this.clearedTables = builder.clearedTables != null ? List.copyOf(builder.clearedTables) : List.of();
    }

    /**
     * getDeletedTables.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getDeletedTables() {
        return deletedTables;
    }

    /**
     * getClearedTables.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getClearedTables() {
        return clearedTables;
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static final class Builder {
        private List<String> deletedTables;
        private List<String> clearedTables;

        /**
         * Builder.
         * 
         * @since 0.1.7
         */
        private Builder() {
        }

        /**
         * deletedTables.
         * 
         * @param deletedTables deletedTables
         * @return the result
         * @since 0.1.7
         */
        public Builder deletedTables(List<String> deletedTables) {
            this.deletedTables = deletedTables;
            return this;
        }

        /**
         * clearedTables.
         * 
         * @param clearedTables clearedTables
         * @return the result
         * @since 0.1.7
         */
        public Builder clearedTables(List<String> clearedTables) {
            this.clearedTables = clearedTables;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public RuntimeCleanupResult build() {
            return new RuntimeCleanupResult(this);
        }
    }
}
