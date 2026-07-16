/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Locale;

/**
 * Public class DatabaseConfig used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseConfig {
    @Builder.Default
    private DatabaseType dbType = DatabaseType.MEMORY;
    @Builder.Default
    private String connectionString = "";
    @Builder.Default
    private int dbTimeout = 30;
    @Builder.Default
    private boolean isDbEnableWal = true;

    /**
     * fromStorageType.
     * 
     * @param storageType storageType
     * @return the result
     * @since 0.1.7
     */
    public static DatabaseConfig fromStorageType(String storageType) {
        return fromStorageType(storageType, null);
    }

    /**
     * fromStorageType.
     * 
     * @param storageType storageType
     * @param connectionString connectionString
     * @return the result
     * @since 0.1.7
     */
    public static DatabaseConfig fromStorageType(String storageType, String connectionString) {
        if (storageType == null || storageType.isBlank()) {
            return DatabaseConfig.builder().build();
        }
        return switch (storageType.toLowerCase(Locale.ROOT)) {
            case "sqlite" -> DatabaseConfig.builder().dbType(DatabaseType.SQLITE)
                    .connectionString(connectionString != null && !connectionString.isBlank() ? connectionString : "")
                    .isDbEnableWal(true).build();
            case "memory" -> DatabaseConfig.builder().dbType(DatabaseType.MEMORY).connectionString(":memory:")
                    .isDbEnableWal(false).build();
            case "postgresql" -> DatabaseConfig.builder().dbType(DatabaseType.POSTGRESQL)
                    .connectionString(connectionString != null ? connectionString : "").isDbEnableWal(false).build();
            case "mysql" -> DatabaseConfig.builder().dbType(DatabaseType.MYSQL)
                    .connectionString(connectionString != null ? connectionString : "").isDbEnableWal(false).build();
            default -> DatabaseConfig.builder().build();
        };
    }
}
