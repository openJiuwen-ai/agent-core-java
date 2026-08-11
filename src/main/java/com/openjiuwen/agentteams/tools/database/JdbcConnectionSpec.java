/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/**
 * Parsed JDBC connection data with credentials separated from the URL.
 *
 * @since 0.1.15
 */
final class JdbcConnectionSpec {
    private static final String JDBC_PREFIX = "jdbc:";

    private final String jdbcUrl;
    private final Properties connectionProperties;

    private JdbcConnectionSpec(String jdbcUrl, Properties connectionProperties) {
        this.jdbcUrl = jdbcUrl;
        this.connectionProperties = copyProperties(connectionProperties);
    }

    static JdbcConnectionSpec from(DatabaseConfig config) {
        Objects.requireNonNull(config, "config");
        DatabaseType databaseType = config.getDbType();
        if (databaseType != DatabaseType.POSTGRESQL && databaseType != DatabaseType.MYSQL) {
            throw new IllegalArgumentException("JDBC connection spec requires PostgreSQL or MySQL");
        }
        String connectionString = normalizedInput(databaseType, config.getConnectionString());
        URI connectionUri = parseUri(connectionString.substring(JDBC_PREFIX.length()), databaseType);
        Properties properties = new Properties();
        addUserInfo(properties, connectionUri.getUserInfo());
        String filteredQuery = filterCredentialQuery(connectionUri.getRawQuery(), properties);
        addConnectTimeout(properties, databaseType, config.getDbTimeout());
        return new JdbcConnectionSpec(buildJdbcUrl(connectionUri, filteredQuery), properties);
    }

    String jdbcUrl() {
        return jdbcUrl;
    }

    Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, copyProperties(connectionProperties));
    }

    private static String normalizedInput(DatabaseType databaseType, String configuredValue) {
        String value = configuredValue == null ? "" : configuredValue.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(displayName(databaseType) + " requires a non-empty connectionString");
        }
        if (databaseType == DatabaseType.POSTGRESQL) {
            return normalizePostgresqlInput(value);
        }
        return normalizeMysqlInput(value);
    }

    private static String normalizePostgresqlInput(String value) {
        if (value.startsWith("jdbc:postgresql://")) {
            return value;
        }
        if (value.startsWith("postgresql://")) {
            return JDBC_PREFIX + value;
        }
        if (value.startsWith("postgres://")) {
            return "jdbc:postgresql://" + value.substring("postgres://".length());
        }
        throw new IllegalArgumentException(
                "PostgreSQL connectionString must use postgresql://, postgres://, or jdbc:postgresql:// scheme");
    }

    private static String normalizeMysqlInput(String value) {
        if (value.startsWith("jdbc:mysql://")) {
            return value;
        }
        if (value.startsWith("mysql://")) {
            return JDBC_PREFIX + value;
        }
        throw new IllegalArgumentException("MySQL connectionString must use mysql:// or jdbc:mysql:// scheme");
    }

    private static URI parseUri(String value, DatabaseType databaseType) {
        try {
            URI uri = new URI(value);
            String expectedScheme = databaseType == DatabaseType.POSTGRESQL ? "postgresql" : "mysql";
            if (!expectedScheme.equals(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
                throw invalidConnectionString(databaseType);
            }
            if (uri.getFragment() != null) {
                throw invalidConnectionString(databaseType);
            }
            return uri;
        } catch (URISyntaxException ignored) {
            throw invalidConnectionString(databaseType);
        }
    }

    private static IllegalArgumentException invalidConnectionString(DatabaseType databaseType) {
        return new IllegalArgumentException(displayName(databaseType) + " connectionString is not a valid URI");
    }

    private static String displayName(DatabaseType databaseType) {
        return databaseType == DatabaseType.POSTGRESQL ? "PostgreSQL" : "MySQL";
    }

    private static void addUserInfo(Properties properties, String userInfo) {
        if (userInfo == null || userInfo.isBlank()) {
            return;
        }
        String[] parts = userInfo.split(":", 2);
        properties.setProperty("user", parts[0]);
        if (parts.length == 2) {
            properties.setProperty("password", parts[1]);
        }
    }

    private static String filterCredentialQuery(String rawQuery, Properties properties) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        List<String> retainedParameters = new ArrayList<>();
        for (String parameter : rawQuery.split("&")) {
            if (!extractCredentialParameter(parameter, properties)) {
                retainedParameters.add(parameter);
            }
        }
        return String.join("&", retainedParameters);
    }

    private static boolean extractCredentialParameter(String parameter, Properties properties) {
        String[] parts = parameter.split("=", 2);
        String key = decodeQueryValue(parts[0]).toLowerCase(Locale.ROOT);
        if (!"user".equals(key) && !"username".equals(key) && !"password".equals(key)) {
            return false;
        }
        if (parts.length == 2) {
            String propertyName = "username".equals(key) ? "user" : key;
            properties.setProperty(propertyName, decodeQueryValue(parts[1]));
        }
        return true;
    }

    private static String decodeQueryValue(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void addConnectTimeout(Properties properties, DatabaseType databaseType, int timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            return;
        }
        if (databaseType == DatabaseType.POSTGRESQL) {
            properties.putIfAbsent("connectTimeout", Integer.toString(timeoutSeconds));
            return;
        }
        long timeoutMillis = Math.min((long) timeoutSeconds * 1000L, (long) Integer.MAX_VALUE);
        properties.putIfAbsent("connectTimeout", Long.toString(timeoutMillis));
    }

    private static String buildJdbcUrl(URI uri, String filteredQuery) {
        StringBuilder builder = new StringBuilder(JDBC_PREFIX).append(uri.getScheme()).append("://");
        String host = uri.getHost();
        if (host.indexOf(':') >= 0) {
            builder.append('[').append(host).append(']');
        } else {
            builder.append(host);
        }
        if (uri.getPort() >= 0) {
            builder.append(':').append(uri.getPort());
        }
        if (uri.getRawPath() != null) {
            builder.append(uri.getRawPath());
        }
        if (filteredQuery != null && !filteredQuery.isBlank()) {
            builder.append('?').append(filteredQuery);
        }
        return builder.toString();
    }

    private static Properties copyProperties(Properties source) {
        Properties copy = new Properties();
        copy.putAll(source);
        return copy;
    }
}
