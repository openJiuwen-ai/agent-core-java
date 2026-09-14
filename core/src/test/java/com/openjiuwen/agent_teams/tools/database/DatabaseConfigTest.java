package com.openjiuwen.agent_teams.tools.database;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseConfigTest {

    @Test
    void defaultsMatchPythonModel() {
        DatabaseConfig config = new DatabaseConfig();

        assertThat(config.getDbType()).isEqualTo(DatabaseType.SQLITE);
        assertThat(config.getConnectionString()).isEmpty();
        assertThat(config.getDbTimeout()).isEqualTo(30);
        assertThat(config.isDbEnableWal()).isTrue();
    }

    @Test
    void enumFallsBackToSqliteForUnknownValues() {
        assertThat(DatabaseType.fromValue("postgresql")).isEqualTo(DatabaseType.POSTGRESQL);
        assertThat(DatabaseType.fromValue("unknown")).isEqualTo(DatabaseType.SQLITE);
    }
}
