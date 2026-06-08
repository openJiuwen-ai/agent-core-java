/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Database configuration class.
 * <p>
 * Mirrors Python's {@code DatabaseConfig} in
 * {@code openjiuwen/agent_teams/tools/database/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DatabaseConfig {

    @Builder.Default
    @JsonProperty("db_type")
    private DatabaseType dbType = DatabaseType.SQLITE;

    @Builder.Default
    @JsonProperty("connection_string")
    private String connectionString = "";

    @Builder.Default
    @JsonProperty("db_timeout")
    private int dbTimeout = 30;

    @Builder.Default
    @JsonProperty("db_enable_wal")
    private boolean dbEnableWal = true;
}
