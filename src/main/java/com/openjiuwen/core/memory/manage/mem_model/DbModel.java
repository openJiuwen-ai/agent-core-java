/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.memory.migration.MigrationPlan;
import com.openjiuwen.spi.store.BaseDbStore;

import javax.sql.DataSource;
import java.sql.*;

/**
 * Database model: table definitions and creation logic.
 * Translates Python's SQLAlchemy declarative models to JDBC DDL.
 */
public final class DbModel {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;

    public static final String USER_MESSAGE_TABLE = "user_message";
    public static final String SCOPE_USER_MAPPING_TABLE = "scope_user_mapping";
    public static final String MEMORY_META_TABLE = "memory_meta";

    /**
     * Table configs for migration tracking.
     */
    public static final String[][] MEMORY_TABLES_CONFIG = {
            {USER_MESSAGE_TABLE, "user_messages"},
            {SCOPE_USER_MAPPING_TABLE, "scope_user_mapping"},
    };

    private DbModel() {
    }

    /**
     * Create memory tables if they don't exist.
     */
    public static void createTables(BaseDbStore<?> dbStore) {
        Object engine = dbStore.getEngine();
        try (Connection conn = getConnectionFrom(engine)) {
            conn.setAutoCommit(false);
            try {
                // Check for old version table with 'group_id' column and drop if found
                if (tableExists(conn, USER_MESSAGE_TABLE)) {
                    if (columnExists(conn, USER_MESSAGE_TABLE, "group_id")) {
                        try (Statement stmt = conn.createStatement()) {
                            stmt.executeUpdate("DROP TABLE IF EXISTS " + USER_MESSAGE_TABLE);
                        }
                        MEMORY_LOGGER.debug("Deleted old version sql table");
                    }
                }

                // Create tables
                createMemoryMetaTable(conn);
                createUserMessageTable(conn);
                createScopeUserMappingTable(conn);

                // Update schema versions for newly created tables
                for (String[] tableConfig : MEMORY_TABLES_CONFIG) {
                    String tableName = tableConfig[0];
                    String entityKey = tableConfig[1];
                    int currentVersion = MigrationPlan.getSqlRegistry().getCurrentVersion(entityKey);
                    if (currentVersion > 0) {
                        insertMemoryMeta(conn, tableName, String.valueOf(currentVersion));
                    }
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Failed to create tables: {}", LogEventType.MEMORY_INIT, e.getMessage());
        }
    }

    private static void createUserMessageTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + USER_MESSAGE_TABLE + " ("
                + "message_id VARCHAR(64) PRIMARY KEY,"
                + "user_id VARCHAR(64) NOT NULL,"
                + "scope_id VARCHAR(64) NOT NULL,"
                + "content VARCHAR(4096) NOT NULL,"
                + "session_id VARCHAR(64),"
                + "role VARCHAR(32),"
                + "timestamp VARCHAR(32)"
                + ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private static void createScopeUserMappingTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + SCOPE_USER_MAPPING_TABLE + " ("
                + "user_id VARCHAR(64) NOT NULL,"
                + "scope_id VARCHAR(64) NOT NULL,"
                + "PRIMARY KEY (user_id, scope_id)"
                + ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private static void createMemoryMetaTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + MEMORY_META_TABLE + " ("
                + "table_name VARCHAR(64) PRIMARY KEY,"
                + "schema_version VARCHAR(64) NOT NULL"
                + ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private static void insertMemoryMeta(Connection conn, String tableName, String schemaVersion) throws SQLException {
        // Check if already exists
        String checkSql = "SELECT 1 FROM " + MEMORY_META_TABLE + " WHERE table_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return; // already exists
                }
            }
        }
        String insertSql = "INSERT INTO " + MEMORY_META_TABLE + " (table_name, schema_version) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, tableName);
            ps.setString(2, schemaVersion);
            ps.executeUpdate();
        }
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    private static Connection getConnectionFrom(Object engine) throws SQLException {
        if (engine instanceof DataSource) {
            return ((DataSource) engine).getConnection();
        }
        if (engine instanceof Connection) {
            return (Connection) engine;
        }
        throw new SQLException("Unsupported engine type: " + engine.getClass().getName());
    }
}
