/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStorePipeline;

import org.sqlite.SQLiteConfig;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SQLite-backed key-value store for persistence checkpointers.
 *
 * <p>The store accepts string and byte-array values. Each instance owns one JDBC connection and serializes access
 * to that connection. A pipeline is executed in one database transaction so checkpoint metadata and payloads are
 * committed atomically.
 *
 * @since 0.1.14
 */
public final class SqliteKVStore extends BaseKVStore {
    private static final int VALUE_KIND_STRING = 0;
    private static final int VALUE_KIND_BYTES = 1;
    private static final int MILLIS_PER_SECOND = 1000;
    private static final int MAX_TIMEOUT_SECONDS = 3600;
    private static final String MEMORY_DATABASE_PATH = ":memory:";
    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS checkpointer_kv_store (
                k TEXT PRIMARY KEY NOT NULL,
                v BLOB NOT NULL,
                value_kind INTEGER NOT NULL,
                expires_at INTEGER
            )
            """;
    private static final String SET_SQL = """
            INSERT INTO checkpointer_kv_store (k, v, value_kind, expires_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(k) DO UPDATE SET
                v = excluded.v,
                value_kind = excluded.value_kind,
                expires_at = excluded.expires_at
            """;
    private static final String EXCLUSIVE_SET_SQL = """
            INSERT INTO checkpointer_kv_store (k, v, value_kind, expires_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(k) DO UPDATE SET
                v = excluded.v,
                value_kind = excluded.value_kind,
                expires_at = excluded.expires_at
            WHERE checkpointer_kv_store.expires_at IS NOT NULL
                AND checkpointer_kv_store.expires_at <= ?
            """;
    private static final String GET_SQL = """
            SELECT v, value_kind
            FROM checkpointer_kv_store
            WHERE k = ? AND (expires_at IS NULL OR expires_at > ?)
            """;
    private static final String EXISTS_SQL = """
            SELECT 1
            FROM checkpointer_kv_store
            WHERE k = ? AND (expires_at IS NULL OR expires_at > ?)
            """;
    private static final String DELETE_SQL = "DELETE FROM checkpointer_kv_store WHERE k = ?";
    private static final String GET_BY_PREFIX_SQL = """
            SELECT k, v, value_kind
            FROM checkpointer_kv_store
            WHERE substr(k, 1, length(?)) = ?
                AND (expires_at IS NULL OR expires_at > ?)
            ORDER BY k
            """;
    private static final String DELETE_BY_PREFIX_SQL = """
            DELETE FROM checkpointer_kv_store
            WHERE substr(k, 1, length(?)) = ?
            """;

    private final ReentrantLock connectionLock = new ReentrantLock();
    private final Connection connection;
    private boolean isClosed;
    private boolean isUnusable;

    /**
     * Creates a SQLite key-value store.
     *
     * @param databasePath SQLite database path, or {@code :memory:}
     * @param timeoutSeconds maximum number of seconds to wait for a database lock
     * @param isWalEnabled whether WAL journal mode is enabled for file databases
     * @throws IllegalArgumentException if the path or timeout is invalid
     * @throws IllegalStateException if the database cannot be opened or initialized
     * @since 0.1.14
     */
    public SqliteKVStore(String databasePath, int timeoutSeconds, boolean isWalEnabled) {
        validateTimeout(timeoutSeconds);
        this.connection = openConnection(databasePath, timeoutSeconds, isWalEnabled);
    }

    /** {@inheritDoc} */
    @Override
    public void set(String key, Object value) {
        executeLocked("set value", () -> set(connection, key, value, null));
    }

    /** {@inheritDoc} */
    @Override
    public boolean exclusiveSet(String key, Object value, Integer expiry) {
        return queryLocked("set value exclusively", () -> exclusiveSet(connection, key, value, expiry));
    }

    /** {@inheritDoc} */
    @Override
    public Object get(String key) {
        return queryLocked("get value", () -> get(connection, key));
    }

    /** {@inheritDoc} */
    @Override
    public boolean isExists(String key) {
        return queryLocked("check value existence", () -> isExists(connection, key));
    }

    /** {@inheritDoc} */
    @Override
    public void delete(String key) {
        executeLocked("delete value", () -> delete(connection, key));
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getByPrefix(String prefix) {
        return queryLocked("get values by prefix", () -> getByPrefix(connection, prefix));
    }

    /** {@inheritDoc} */
    @Override
    public void deleteByPrefix(String prefix, Integer batchSize) {
        executeLocked("delete values by prefix", () -> deleteByPrefix(connection, prefix));
    }

    /** {@inheritDoc} */
    @Override
    public List<Object> mget(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return queryLocked("get multiple values", () -> mget(connection, keys));
    }

    /** {@inheritDoc} */
    @Override
    public int batchDelete(List<String> keys, Integer batchSize) {
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        return queryLocked("delete multiple values", () -> batchDelete(connection, keys));
    }

    /** {@inheritDoc} */
    @Override
    public KVStorePipeline pipeline() {
        return new KVStorePipeline(this::executePipeline);
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        connectionLock.lock();
        try {
            if (isClosed) {
                return;
            }
            connection.close();
            isClosed = true;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to close SQLite KV store", exception);
        } finally {
            connectionLock.unlock();
        }
    }

    private static Connection openConnection(String databasePath, int timeoutSeconds, boolean isWalEnabled) {
        DatabaseLocation location = resolveDatabaseLocation(databasePath);
        Connection openedConnection = null;
        try {
            SQLiteConfig sqliteConfig = createConfiguration(timeoutSeconds,
                    isWalEnabled && !location.isMemory());
            openedConnection = DriverManager.getConnection(location.jdbcUrl(), sqliteConfig.toProperties());
            initializeTable(openedConnection);
            return openedConnection;
        } catch (SQLException exception) {
            closeAfterInitializationFailure(openedConnection, exception);
            throw new IllegalStateException("Failed to initialize SQLite KV store", exception);
        }
    }

    private static DatabaseLocation resolveDatabaseLocation(String databasePath) {
        if (databasePath == null || databasePath.isBlank()) {
            throw new IllegalArgumentException("SQLite database path cannot be blank");
        }
        if (MEMORY_DATABASE_PATH.equals(databasePath)) {
            return new DatabaseLocation(JDBC_PREFIX + MEMORY_DATABASE_PATH, true);
        }
        if (databasePath.startsWith("jdbc:")) {
            throw new IllegalArgumentException("SQLite database path must not be a JDBC URL");
        }

        String filePath = databasePath.endsWith(".db") ? databasePath : databasePath + ".db";
        try {
            File canonicalFile = Path.of(filePath).toFile().getCanonicalFile();
            Path canonicalPath = canonicalFile.toPath();
            Path parentPath = canonicalPath.getParent();
            if (parentPath == null) {
                throw new IllegalArgumentException("SQLite database path must have a parent directory");
            }
            Files.createDirectories(parentPath);
            if (Files.exists(canonicalPath) && !Files.isRegularFile(canonicalPath)) {
                throw new IllegalArgumentException("SQLite database path must reference a regular file");
            }
            return new DatabaseLocation(JDBC_PREFIX + canonicalPath, false);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to normalize SQLite database path", exception);
        }
    }

    private static void validateTimeout(int timeoutSeconds) {
        if (timeoutSeconds <= 0 || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("SQLite timeout must be between 1 and 3600 seconds");
        }
    }

    private static SQLiteConfig createConfiguration(int timeoutSeconds, boolean isWalEnabled) {
        int timeoutMillis = timeoutSeconds * MILLIS_PER_SECOND;
        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.setBusyTimeout(timeoutMillis);
        if (isWalEnabled) {
            sqliteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);
        }
        return sqliteConfig;
    }

    private static void initializeTable(Connection targetConnection) throws SQLException {
        try (Statement statement = targetConnection.createStatement()) {
            statement.execute(CREATE_TABLE_SQL);
        }
    }

    private static void closeAfterInitializationFailure(Connection targetConnection, SQLException originalException) {
        if (targetConnection == null) {
            return;
        }
        try {
            targetConnection.close();
        } catch (SQLException closeException) {
            originalException.addSuppressed(closeException);
        }
    }

    private static void set(Connection targetConnection, String key, Object value, Integer expiry)
            throws SQLException {
        validateKey(key);
        StoredValue storedValue = encodeValue(value);
        try (PreparedStatement statement = targetConnection.prepareStatement(SET_SQL)) {
            bindValue(statement, key, storedValue, expiry, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private static boolean exclusiveSet(Connection targetConnection, String key, Object value, Integer expiry)
            throws SQLException {
        validateKey(key);
        StoredValue storedValue = encodeValue(value);
        long currentTime = System.currentTimeMillis();
        try (PreparedStatement statement = targetConnection.prepareStatement(EXCLUSIVE_SET_SQL)) {
            bindValue(statement, key, storedValue, expiry, currentTime);
            statement.setLong(5, currentTime);
            return statement.executeUpdate() == 1;
        }
    }

    private static Object get(Connection targetConnection, String key) throws SQLException {
        validateKey(key);
        try (PreparedStatement statement = targetConnection.prepareStatement(GET_SQL)) {
            statement.setString(1, key);
            statement.setLong(2, System.currentTimeMillis());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? decodeValue(resultSet, 1, 2) : null;
            }
        }
    }

    private static boolean isExists(Connection targetConnection, String key) throws SQLException {
        validateKey(key);
        try (PreparedStatement statement = targetConnection.prepareStatement(EXISTS_SQL)) {
            statement.setString(1, key);
            statement.setLong(2, System.currentTimeMillis());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static void delete(Connection targetConnection, String key) throws SQLException {
        validateKey(key);
        try (PreparedStatement statement = targetConnection.prepareStatement(DELETE_SQL)) {
            statement.setString(1, key);
            statement.executeUpdate();
        }
    }

    private static Map<String, Object> getByPrefix(Connection targetConnection, String prefix) throws SQLException {
        validatePrefix(prefix);
        Map<String, Object> result = new LinkedHashMap<>();
        try (PreparedStatement statement = targetConnection.prepareStatement(GET_BY_PREFIX_SQL)) {
            statement.setString(1, prefix);
            statement.setString(2, prefix);
            statement.setLong(3, System.currentTimeMillis());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.put(resultSet.getString(1), decodeValue(resultSet, 2, 3));
                }
            }
        }
        return result;
    }

    private static void deleteByPrefix(Connection targetConnection, String prefix) throws SQLException {
        validatePrefix(prefix);
        try (PreparedStatement statement = targetConnection.prepareStatement(DELETE_BY_PREFIX_SQL)) {
            statement.setString(1, prefix);
            statement.setString(2, prefix);
            statement.executeUpdate();
        }
    }

    private static List<Object> mget(Connection targetConnection, List<String> keys) throws SQLException {
        List<Object> result = new ArrayList<>(keys.size());
        try (PreparedStatement statement = targetConnection.prepareStatement(GET_SQL)) {
            for (String key : keys) {
                validateKey(key);
                statement.setString(1, key);
                statement.setLong(2, System.currentTimeMillis());
                try (ResultSet resultSet = statement.executeQuery()) {
                    result.add(resultSet.next() ? decodeValue(resultSet, 1, 2) : null);
                }
            }
        }
        return result;
    }

    private static int batchDelete(Connection targetConnection, List<String> keys) throws SQLException {
        int deletedCount = 0;
        try (PreparedStatement statement = targetConnection.prepareStatement(DELETE_SQL)) {
            for (String key : keys) {
                validateKey(key);
                statement.setString(1, key);
                deletedCount += statement.executeUpdate();
            }
        }
        return deletedCount;
    }

    private List<Object> executePipeline(List<Object[]> operations) {
        connectionLock.lock();
        try {
            ensureOpen();
            return executeTransaction(operations);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to execute SQLite KV pipeline", exception);
        } finally {
            connectionLock.unlock();
        }
    }

    private List<Object> executeTransaction(List<Object[]> operations) throws SQLException {
        boolean wasAutoCommitEnabled = connection.getAutoCommit();
        connection.setAutoCommit(false);
        List<Object> results;
        try {
            results = new ArrayList<>(operations.size());
            for (Object[] operation : operations) {
                results.add(executeOperation(operation));
            }
            connection.commit();
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            if (rollback(exception)) {
                restoreAutoCommitAfterFailure(wasAutoCommitEnabled, exception);
            } else {
                invalidateConnection(exception);
            }
            throw exception;
        }
        restoreAutoCommitAfterSuccess(wasAutoCommitEnabled);
        return results;
    }

    private Object executeOperation(Object[] operation) throws SQLException {
        if (operation == null || operation.length < 2) {
            throw new IllegalArgumentException("SQLite pipeline operation is incomplete");
        }
        if (!(operation[0] instanceof String action) || !(operation[1] instanceof String key)) {
            throw new IllegalArgumentException("SQLite pipeline action and key must be strings");
        }
        return switch (action) {
            case "set" -> executeSetOperation(operation, key);
            case "get" -> get(connection, key);
            case "isExists" -> isExists(connection, key);
            default -> throw new IllegalArgumentException("Unsupported SQLite pipeline action: " + action);
        };
    }

    private Object executeSetOperation(Object[] operation, String key) throws SQLException {
        if (operation.length < 3) {
            throw new IllegalArgumentException("SQLite pipeline set operation has no value");
        }
        Integer expiry = readExpiry(operation);
        set(connection, key, operation[2], expiry);
        return Boolean.TRUE;
    }

    private static Integer readExpiry(Object[] operation) {
        if (operation.length < 4 || operation[3] == null) {
            return null;
        }
        if (operation[3] instanceof Integer expiry) {
            return expiry;
        }
        throw new IllegalArgumentException("SQLite pipeline expiry must be an integer");
    }

    private boolean rollback(Exception originalException) {
        try {
            connection.rollback();
            return true;
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
            return false;
        }
    }

    private void restoreAutoCommitAfterFailure(boolean wasAutoCommitEnabled, Exception originalException) {
        try {
            connection.setAutoCommit(wasAutoCommitEnabled);
        } catch (SQLException restoreException) {
            originalException.addSuppressed(restoreException);
            invalidateConnection(originalException);
        }
    }

    private void restoreAutoCommitAfterSuccess(boolean wasAutoCommitEnabled) throws SQLException {
        try {
            connection.setAutoCommit(wasAutoCommitEnabled);
        } catch (SQLException restoreException) {
            invalidateConnection(restoreException);
            throw restoreException;
        }
    }

    private void invalidateConnection(Exception originalException) {
        isUnusable = true;
        try {
            connection.close();
            isClosed = true;
        } catch (SQLException closeException) {
            originalException.addSuppressed(closeException);
        }
    }

    private static void bindValue(PreparedStatement statement, String key, StoredValue storedValue, Integer expiry,
            long currentTime) throws SQLException {
        statement.setString(1, key);
        statement.setBytes(2, storedValue.value());
        statement.setInt(3, storedValue.kind());
        if (expiry != null && expiry > 0) {
            statement.setLong(4, currentTime + expiry.longValue() * MILLIS_PER_SECOND);
        } else {
            statement.setNull(4, Types.BIGINT);
        }
    }

    private static StoredValue encodeValue(Object value) {
        if (value instanceof String stringValue) {
            return new StoredValue(VALUE_KIND_STRING, stringValue.getBytes(StandardCharsets.UTF_8));
        }
        if (value instanceof byte[] byteValue) {
            return new StoredValue(VALUE_KIND_BYTES, byteValue);
        }
        throw new IllegalArgumentException("SQLite KV values must be strings or byte arrays");
    }

    private static Object decodeValue(ResultSet resultSet, int valueIndex, int kindIndex) throws SQLException {
        byte[] value = resultSet.getBytes(valueIndex);
        int valueKind = resultSet.getInt(kindIndex);
        if (valueKind == VALUE_KIND_STRING) {
            return new String(value, StandardCharsets.UTF_8);
        }
        if (valueKind == VALUE_KIND_BYTES) {
            return value;
        }
        throw new SQLException("SQLite KV value has an unsupported kind: " + valueKind);
    }

    private static void validateKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("SQLite KV key cannot be null");
        }
    }

    private static void validatePrefix(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("SQLite KV prefix cannot be null");
        }
    }

    private void executeLocked(String operation, SqlAction action) {
        connectionLock.lock();
        try {
            ensureOpen();
            action.execute();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to " + operation + " in SQLite KV store", exception);
        } finally {
            connectionLock.unlock();
        }
    }

    private <T> T queryLocked(String operation, SqlSupplier<T> supplier) {
        connectionLock.lock();
        try {
            ensureOpen();
            return supplier.get();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to " + operation + " in SQLite KV store", exception);
        } finally {
            connectionLock.unlock();
        }
    }

    private void ensureOpen() {
        if (isClosed || isUnusable) {
            throw new IllegalStateException("SQLite KV store is closed or unusable");
        }
    }

    @FunctionalInterface
    private interface SqlAction {
        void execute() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }

    private record DatabaseLocation(String jdbcUrl, boolean isMemory) {
    }

    private record StoredValue(int kind, byte[] value) {
    }
}
