/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.spawn;

import com.openjiuwen.agent_teams.tools.TeamDatabase;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_shared_resources.py}.
 */
class SharedResourcesTest {

    @AfterEach
    void cleanup() {
        SharedResources.cleanupSharedResources();
    }

    @Test
    void testGetSharedDbReusesSameNonMemoryConfig() {
        SharedResources.cleanupSharedResources();
        DatabaseConfig cfg1 = DatabaseConfig.sqliteFile("team_data/team.db");
        DatabaseConfig cfg2 = DatabaseConfig.sqliteFile("team_data/team.db");

        TeamDatabase db1 = SharedResources.getSharedDb(cfg1);
        TeamDatabase db2 = SharedResources.getSharedDb(cfg2);

        assertSame(db1, db2);
    }

    @Test
    void testGetSharedDbDistinguishesDbTypeWithSameConnection() {
        SharedResources.cleanupSharedResources();
        DatabaseConfig sqliteCfg = DatabaseConfig.sqliteFile("shared-conn");
        DatabaseConfig postgresqlCfg = DatabaseConfig.postgresql("shared-conn");

        TeamDatabase sqliteDb = SharedResources.getSharedDb(sqliteCfg);
        TeamDatabase postgresqlDb = SharedResources.getSharedDb(postgresqlCfg);

        assertNotSame(sqliteDb, postgresqlDb);
    }

    @Test
    void testGetSharedDbMemoryUsesSingleton() {
        SharedResources.cleanupSharedResources();

        TeamDatabase db1 = SharedResources.getSharedDb(DatabaseConfig.inMemory());
        TeamDatabase db2 = SharedResources.getSharedDb(DatabaseConfig.inMemory());

        assertSame(db1, db2);
    }
}
