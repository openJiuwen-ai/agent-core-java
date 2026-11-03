/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
