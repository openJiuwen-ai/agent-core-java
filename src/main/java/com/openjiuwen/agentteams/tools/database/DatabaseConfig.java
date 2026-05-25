/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class DatabaseConfig used by the Java parity implementation.
 *
 * @since 1.0
 */
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

    public static DatabaseConfig fromStorageType(String storageType) {
        return fromStorageType(storageType, null);
    }

    public static DatabaseConfig fromStorageType(String storageType, String connectionString) {
        if (storageType == null || storageType.isBlank()) {
            return DatabaseConfig.builder().build();
        }
        return switch (storageType.toLowerCase()) {
            case "sqlite" -> DatabaseConfig.builder()
                    .dbType(DatabaseType.SQLITE)
                    .connectionString(connectionString != null && !connectionString.isBlank()
                            ? connectionString : "")
                    .isDbEnableWal(true)
                    .build();
            case "memory" -> DatabaseConfig.builder()
                    .dbType(DatabaseType.MEMORY)
                    .connectionString(":memory:")
                    .isDbEnableWal(false)
                    .build();
            case "postgresql" -> DatabaseConfig.builder()
                    .dbType(DatabaseType.POSTGRESQL)
                    .connectionString(connectionString != null ? connectionString : "")
                    .isDbEnableWal(false)
                    .build();
            case "mysql" -> DatabaseConfig.builder()
                    .dbType(DatabaseType.MYSQL)
                    .connectionString(connectionString != null ? connectionString : "")
                    .isDbEnableWal(false)
                    .build();
            default -> DatabaseConfig.builder().build();
        };
    }
}
