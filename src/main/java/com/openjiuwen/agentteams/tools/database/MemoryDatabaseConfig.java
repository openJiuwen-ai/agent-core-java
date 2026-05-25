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
@AllArgsConstructor
public class MemoryDatabaseConfig {
    @Builder.Default
    private DatabaseType dbType = DatabaseType.MEMORY;
    @Builder.Default
    private int dbTimeout = 30;

    public DatabaseConfig toDatabaseConfig() {
        return DatabaseConfig.builder()
                .dbType(DatabaseType.MEMORY)
                .connectionString(":memory:")
                .dbTimeout(dbTimeout)
                .isDbEnableWal(false)
                .build();
    }
}
