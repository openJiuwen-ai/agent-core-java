
package com.openjiuwen.agentteams.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.tools.database.DatabaseConfig;
import com.openjiuwen.agentteams.tools.database.DatabaseType;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;
import com.openjiuwen.core.multiagent.runtime.TeamRuntime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SharedResourcesCompatibilityTest {
    @AfterEach
    void cleanup() {
        SharedResources.cleanupSharedResources();
    }

    @Test
    void sharedDbShouldReuseSameNonMemoryConfig() {
        SharedResources.cleanupSharedResources();

        DatabaseConfig cfg1 =
            DatabaseConfig.builder().dbType(DatabaseType.SQLITE).connectionString("team_data/team.db").build();
        DatabaseConfig cfg2 =
            DatabaseConfig.builder().dbType(DatabaseType.SQLITE).connectionString("team_data/team.db").build();

        TeamDatabase db1 = SharedResources.getSharedDb(cfg1);
        TeamDatabase db2 = SharedResources.getSharedDb(cfg2);

        assertThat(db1).isSameAs(db2);
    }

    @Test
    void sharedDbShouldDistinguishDbTypeWithSameConnection() {
        SharedResources.cleanupSharedResources();

        DatabaseConfig sqliteCfg =
            DatabaseConfig.builder().dbType(DatabaseType.SQLITE).connectionString("shared-conn").build();
        DatabaseConfig postgresqlCfg =
            DatabaseConfig.builder().dbType(DatabaseType.POSTGRESQL).connectionString("shared-conn").build();

        TeamDatabase sqliteDb = SharedResources.getSharedDb(sqliteCfg);
        TeamDatabase postgresqlDb = SharedResources.getSharedDb(postgresqlCfg);

        assertThat(sqliteDb).isNotSameAs(postgresqlDb);
    }

    @Test
    void sharedDbShouldReuseSingleMemorySingleton() {
        SharedResources.cleanupSharedResources();

        TeamDatabase db1 = SharedResources.getSharedDb(DatabaseConfig.builder().dbType(DatabaseType.MEMORY).build());
        TeamDatabase db2 = SharedResources.getSharedDb(DatabaseConfig.builder().dbType(DatabaseType.MEMORY).build());

        assertThat(db1).isSameAs(db2);
    }

    @Test
    void sharedRuntimeShouldReuseSingletonUntilCleanup() {
        SharedResources.cleanupSharedResources();

        TeamRuntime runtime1 = SharedResources.getSharedRuntime();
        TeamRuntime runtime2 = SharedResources.getSharedRuntime();

        assertThat(runtime1).isSameAs(runtime2);

        SharedResources.cleanupSharedResources();
        TeamRuntime runtime3 = SharedResources.getSharedRuntime();
        assertThat(runtime3).isNotSameAs(runtime1);
    }

    @Test
    void sharedFactoriesShouldRespectOverrideAndCleanup() {
        SharedResources.cleanupSharedResources();

        AtomicInteger runtimeCalls = new AtomicInteger();
        AtomicReference<String> dbConfigType = new AtomicReference<>();
        SharedResources.overrideRuntimeFactory(name -> {
            runtimeCalls.incrementAndGet();
            return new TeamRuntime(name);
        });
        SharedResources.overrideDatabaseFactory(config -> {
            dbConfigType.set(config.getDbType() != null ? config.getDbType().name() : null);
            return new _DummyTeamDatabase(config);
        });

        TeamRuntime runtime = SharedResources.getSharedRuntime();
        TeamDatabase db = SharedResources.getSharedDb(DatabaseConfig.builder().dbType(DatabaseType.SQLITE).build());

        assertThat(runtime).isNotNull();
        assertThat(runtimeCalls.get()).isEqualTo(1);
        assertThat(db).isInstanceOf(_DummyTeamDatabase.class);
        assertThat(dbConfigType.get()).isEqualTo("SQLITE");

        SharedResources.cleanupSharedResources();
        TeamRuntime freshRuntime = SharedResources.getSharedRuntime();
        assertThat(freshRuntime).isNotSameAs(runtime);
    }

    private static final class _DummyTeamDatabase extends TeamDatabase {
        private _DummyTeamDatabase(DatabaseConfig config) {
            super(config);
        }
    }
}
