/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.memory.migration.MigrationPlan;
import com.openjiuwen.spi.store.BaseDbStore;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.sql.DataSource;

/**
 * Database model: table definitions and creation logic.
 * Translates Python's SQLAlchemy declarative models to JDBC DDL.
 * 
 * @since 0.1.7
 */
public final class DbModel {
    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;

    /**
     * USER_MESSAGE_TABLE.
     * 
     * @since 0.1.7
     */
    public static final String USER_MESSAGE_TABLE = "user_message";

    /**
     * SCOPE_USER_MAPPING_TABLE.
     * 
     * @since 0.1.7
     */
    public static final String SCOPE_USER_MAPPING_TABLE = "scope_user_mapping";

    /**
     * MEMORY_META_TABLE.
     * 
     * @since 0.1.7
     */
    public static final String MEMORY_META_TABLE = "memory_meta";

    /**
     * Table configs for migration tracking.
     */
    public static final String[][] MEMORY_TABLES_CONFIG =
        {{USER_MESSAGE_TABLE, "user_messages"}, {SCOPE_USER_MAPPING_TABLE, "scope_user_mapping"}};

    /**
     * DbModel.
     * 
     * @since 0.1.7
     */
    private DbModel() {
    }

    /**
     * Create memory tables if they don't exist.
     * 
     * @param dbStore the database store instance to use for table creation
     * @since 0.1.7
     */
    public static void createTables(BaseDbStore<?> dbStore) {
        Object engine = dbStore.getEngine();
        try (Connection conn = getConnectionFrom(engine)) {
            conn.setAutoCommit(false);
            try {
                Set<String> newlyCreatedTables = new HashSet<>();
                // Check for old version table with 'group_id' column and drop if found
                if (tableExists(conn, USER_MESSAGE_TABLE)) {
                    if (columnExists(conn, USER_MESSAGE_TABLE, "group_id")) {
                        try (Statement stmt = conn.createStatement()) {
                            stmt.executeUpdate("DROP TABLE IF EXISTS " + USER_MESSAGE_TABLE);
                        }
                        MEMORY_LOGGER.debug("Deleted old version sql table");
                    }
                }

                for (String[] tableConfig : MEMORY_TABLES_CONFIG) {
                    String tableName = tableConfig[0];
                    if (!tableExists(conn, tableName)) {
                        newlyCreatedTables.add(tableName);
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
                    if (newlyCreatedTables.contains(tableName)) {
                        int currentVersion = MigrationPlan.getSqlRegistry().getCurrentVersion(entityKey);
                        if (currentVersion > 0) {
                            insertMemoryMeta(conn, tableName, String.valueOf(currentVersion));
                        }
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

    /**
     * createUserMessageTable.
     * 
     * @param conn conn
     * @throws SQLException SQLException
     * @since 0.1.7
     */
    private static void createUserMessageTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + USER_MESSAGE_TABLE + " (" + "message_id VARCHAR(64) PRIMARY KEY,"
                + "user_id VARCHAR(256) NOT NULL," + "scope_id VARCHAR(128) NOT NULL,"
                + "content VARCHAR(4096) NOT NULL," + "session_id VARCHAR(2048)," + "role VARCHAR(32),"
                + "timestamp VARCHAR(64)" + ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * createScopeUserMappingTable.
     * 
     * @param conn conn
     * @throws SQLException SQLException
     * @since 0.1.7
     */
    private static void createScopeUserMappingTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + SCOPE_USER_MAPPING_TABLE + " (" + "user_id VARCHAR(64) NOT NULL,"
                + "scope_id VARCHAR(64) NOT NULL," + "PRIMARY KEY (user_id, scope_id)" + ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * createMemoryMetaTable.
     * 
     * @param conn conn
     * @throws SQLException SQLException
     * @since 0.1.7
     */
    private static void createMemoryMetaTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + MEMORY_META_TABLE + " (" + "table_name VARCHAR(64) PRIMARY KEY,"
                + "schema_version VARCHAR(64) NOT NULL" + ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * insertMemoryMeta.
     * 
     * @param conn conn
     * @param tableName tableName
     * @param schemaVersion schemaVersion
     * @throws SQLException SQLException
     * @since 0.1.7
     */
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

    /**
     * tableExists.
     * 
     * @param conn conn
     * @param tableName tableName
     * @return the result
     * @throws SQLException SQLException
     * @since 0.1.7
     */
    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        for (String candidate : tableNameCandidates(tableName)) {
            try (ResultSet rs = meta.getTables(null, null, candidate, null)) {
                if (rs.next()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * columnExists.
     * 
     * @param conn conn
     * @param tableName tableName
     * @param columnName columnName
     * @return the result
     * @throws SQLException SQLException
     * @since 0.1.7
     */
    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        for (String tableCandidate : tableNameCandidates(tableName)) {
            for (String columnCandidate : tableNameCandidates(columnName)) {
                try (ResultSet rs = meta.getColumns(null, null, tableCandidate, columnCandidate)) {
                    if (rs.next()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * tableNameCandidates.
     * 
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    private static String[] tableNameCandidates(String name) {
        return new String[]{name, name.toUpperCase(Locale.ROOT), name.toLowerCase(Locale.ROOT)};
    }

    /**
     * getConnectionFrom.
     * 
     * @param engine engine
     * @return the result
     * @throws SQLException SQLException
     * @since 0.1.7
     */
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
