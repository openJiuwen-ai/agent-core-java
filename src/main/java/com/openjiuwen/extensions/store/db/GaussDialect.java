/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.db;

/**
 * GaussDB dialect configuration.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.store.db.gauss_dialect}.
 *
 * Provides GaussDB-specific SQL dialect settings and type mappings.
 */
public final class GaussDialect {

    /** Dialect name for GaussDB. */
    public static final String DIALECT_NAME = "gaussdb";

    /** Default port for GaussDB. */
    public static final int DEFAULT_PORT = 5432;

    /** Driver class name placeholder. */
    public static final String DRIVER_CLASS = "com.huawei.gaussdb.jdbc.Driver";

    /** JDBC URL prefix. */
    public static final String JDBC_URL_PREFIX = "jdbc:gaussdb://";

    private GaussDialect() {
    }

    /**
     * Build JDBC URL for GaussDB connection.
     *
     * @param host Database host.
     * @param port Database port.
     * @param database Database name.
     * @return JDBC URL string.
     */
    public static String buildJdbcUrl(String host, int port, String database) {
        return JDBC_URL_PREFIX + host + ":" + port + "/" + database;
    }

    /**
     * Check if a SQL statement needs modification for GaussDB compatibility.
     *
     * @param sql Original SQL statement.
     * @return Modified SQL statement if needed, or original if compatible.
     */
    public static String modifyForGaussDb(String sql) {
        // GaussDB uses PostgreSQL-compatible syntax
        // Handle specific dialect differences if needed
        if (sql != null && sql.contains("pg_type.typcollation")) {
            // Replace pg_type subquery with NULL for GaussDB compatibility
            return sql.replaceAll("\\(\\s*SELECT\\s+[^)]*?pg_type\\.typcollation[^)]*\\)", "NULL");
        }
        return sql;
    }

    /**
     * Convert data type to GaussDB-compatible string representation.
     *
     * @param value Value to convert.
     * @return String representation for GaussDB.
     */
    public static String convertToString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }
}