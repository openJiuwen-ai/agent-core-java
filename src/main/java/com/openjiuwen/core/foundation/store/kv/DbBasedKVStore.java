/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.locks.ReentrantLock;

import javax.sql.DataSource;

/**
 * Mirrors Python's {@code DbBasedKVStore} in
 * {@code openjiuwen/core/foundation/store/kv/db_based_kv_store.py}.
 */
public class DbBasedKVStore extends BaseKVStore {

    private static final String EXCLUSIVE_EXPIRY_KEY = "exclusive_expiry";
    private static final String EXCLUSIVE_VALUE_KEY = "exclusive_value";
    private static final String BYTES_PREFIX = "__BYTES__:";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final DataSource dataSource;
    private final ReentrantLock tableCreateLock = new ReentrantLock();
    private volatile boolean tableCreated;

    public DbBasedKVStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public CompletableFuture<Void> set(String key, Object value) {
        return CompletableFuture.runAsync(() -> {
            ensureTableCreated();
            try (Connection connection = dataSource.getConnection()) {
                withTransaction(connection, conn -> {
                    upsert(conn, key, encodeValue(value));
                    return null;
                });
            } catch (SQLException exception) {
                throw new CompletionException("Failed to set KV value", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> exclusiveSet(String key, Object value, Integer expiry) {
        return CompletableFuture.supplyAsync(() -> {
            ensureTableCreated();
            try (Connection connection = dataSource.getConnection()) {
                return withTransaction(connection, conn -> {
                    StoredValue existing = selectRecord(conn, key);
                    double nowSeconds = Instant.now().toEpochMilli() / 1000.0;
                    if (existing != null) {
                        Map<String, Object> existingPayload = parseJsonObject(existing.value());
                        if (existingPayload == null) {
                            return false;
                        }
                        Object oldExpiry = existingPayload.get(EXCLUSIVE_EXPIRY_KEY);
                        if (oldExpiry == null || asDouble(oldExpiry) > nowSeconds) {
                            return false;
                        }
                    }

                    Double expireAt = expiry == null ? null : nowSeconds + expiry;
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put(EXCLUSIVE_VALUE_KEY, encodeValue(value));
                    payload.put(EXCLUSIVE_EXPIRY_KEY, expireAt);
                    upsert(conn, key, writeJson(payload));
                    return true;
                });
            } catch (SQLException exception) {
                throw new CompletionException("Failed to exclusive-set KV value", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Object> get(String key) {
        return CompletableFuture.supplyAsync(() -> {
            ensureTableCreated();
            try (Connection connection = dataSource.getConnection()) {
                StoredValue record = selectRecord(connection, key);
                if (record == null) {
                    return null;
                }

                Object parsed = parseJson(record.value());
                if (parsed == null) {
                    return decodeValue(record.value());
                }
                if (!(parsed instanceof Map<?, ?> parsedMap)) {
                    return record.value();
                }
                if (parsedMap.containsKey(EXCLUSIVE_EXPIRY_KEY)) {
                    Object exclusiveValue = parsedMap.get(EXCLUSIVE_VALUE_KEY);
                    return exclusiveValue == null ? "" : exclusiveValue;
                }
                return decodeValue(record.value());
            } catch (SQLException exception) {
                throw new CompletionException("Failed to fetch KV value", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> exists(String key) {
        return CompletableFuture.supplyAsync(() -> {
            ensureTableCreated();
            try (Connection connection = dataSource.getConnection()) {
                return selectRecord(connection, key) != null;
            } catch (SQLException exception) {
                throw new CompletionException("Failed to check KV existence", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Void> delete(String key) {
        return CompletableFuture.runAsync(() -> {
            ensureTableCreated();
            try (Connection connection = dataSource.getConnection()) {
                withTransaction(connection, conn -> {
                    try (PreparedStatement statement = conn.prepareStatement(
                            "DELETE FROM kv_store WHERE `key` = ?")) {
                        statement.setString(1, key);
                        statement.executeUpdate();
                    }
                    return null;
                });
            } catch (SQLException exception) {
                throw new CompletionException("Failed to delete KV value", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> getByPrefix(String prefix) {
        return CompletableFuture.supplyAsync(() -> {
            ensureTableCreated();
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT `key`, `value` FROM kv_store WHERE `key` LIKE ? ORDER BY `key`");
                    ) {
                statement.setString(1, prefix + "%");
                try (ResultSet resultSet = statement.executeQuery()) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    while (resultSet.next()) {
                        result.put(resultSet.getString("key"), decodeValue(resultSet.getString("value")));
                    }
                    return result;
                }
            } catch (SQLException exception) {
                throw new CompletionException("Failed to fetch KV values by prefix", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteByPrefix(String prefix, Integer batchSize) {
        return CompletableFuture.runAsync(() -> {
            ensureTableCreated();
            try (Connection connection = dataSource.getConnection()) {
                withTransaction(connection, conn -> {
                    if (batchSize == null || batchSize <= 0) {
                        try (PreparedStatement statement = conn.prepareStatement(
                                "DELETE FROM kv_store WHERE `key` LIKE ?")) {
                            statement.setString(1, prefix + "%");
                            statement.executeUpdate();
                        }
                        return null;
                    }

                    List<String> keysToDelete = selectKeysByPrefix(conn, prefix);
                    for (int index = 0; index < keysToDelete.size(); index += batchSize) {
                        List<String> batch = keysToDelete.subList(index, Math.min(index + batchSize, keysToDelete.size()));
                        deleteKeys(conn, batch);
                    }
                    return null;
                });
            } catch (SQLException exception) {
                throw new CompletionException("Failed to delete KV values by prefix", exception);
            }
        });
    }

    @Override
    public CompletableFuture<List<Object>> mget(List<String> keys) {
        return CompletableFuture.supplyAsync(() -> {
            ensureTableCreated();
            if (keys == null || keys.isEmpty()) {
                return List.of();
            }

            try (Connection connection = dataSource.getConnection()) {
                Map<String, Object> lookup = new HashMap<>();
                String placeholders = "?,".repeat(keys.size());
                String sql = "SELECT `key`, `value` FROM kv_store WHERE `key` IN ("
                        + placeholders.substring(0, placeholders.length() - 1) + ")";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    bindKeys(statement, keys);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            lookup.put(resultSet.getString("key"), decodeValue(resultSet.getString("value")));
                        }
                    }
                }
                List<Object> result = new ArrayList<>(keys.size());
                for (String key : keys) {
                    result.add(lookup.get(key));
                }
                return result;
            } catch (SQLException exception) {
                throw new CompletionException("Failed to mget KV values", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> batchDelete(List<String> keys, Integer batchSize) {
        return CompletableFuture.supplyAsync(() -> {
            ensureTableCreated();
            if (keys == null || keys.isEmpty()) {
                return 0;
            }

            try (Connection connection = dataSource.getConnection()) {
                return withTransaction(connection, conn -> {
                    if (batchSize == null || batchSize <= 0) {
                        return deleteKeys(conn, keys);
                    }

                    int totalDeleted = 0;
                    for (int index = 0; index < keys.size(); index += batchSize) {
                        List<String> batch = keys.subList(index, Math.min(index + batchSize, keys.size()));
                        totalDeleted += deleteKeys(conn, batch);
                    }
                    return totalDeleted;
                });
            } catch (SQLException exception) {
                throw new CompletionException("Failed to batch-delete KV values", exception);
            }
        });
    }

    @Override
    public BasedKVStorePipeline pipeline() {
        return new BasedKVStorePipeline(operations -> CompletableFuture.supplyAsync(() -> {
            ensureTableCreated();
            try (Connection connection = dataSource.getConnection()) {
                return withTransaction(connection, conn -> executePipeline(conn, operations));
            } catch (SQLException exception) {
                throw new CompletionException("Failed to execute KV pipeline", exception);
            }
        }));
    }

    private void ensureTableCreated() {
        if (tableCreated) {
            return;
        }
        tableCreateLock.lock();
        try {
            if (tableCreated) {
                return;
            }
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS kv_store (
                            `key` VARCHAR(255) PRIMARY KEY,
                            `value` VARCHAR(4096) NOT NULL
                        )
                        """);
            } catch (SQLException exception) {
                throw new CompletionException("Failed to create kv_store table", exception);
            }
            tableCreated = true;
        } finally {
            tableCreateLock.unlock();
        }
    }

    private StoredValue selectRecord(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT `key`, `value` FROM kv_store WHERE `key` = ?")) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new StoredValue(resultSet.getString("key"), resultSet.getString("value"));
            }
        }
    }

    private void upsert(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE kv_store SET `value` = ? WHERE `key` = ?")) {
            update.setString(1, value);
            update.setString(2, key);
            if (update.executeUpdate() > 0) {
                return;
            }
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO kv_store (`key`, `value`) VALUES (?, ?)")) {
            insert.setString(1, key);
            insert.setString(2, value);
            insert.executeUpdate();
        } catch (SQLException insertException) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE kv_store SET `value` = ? WHERE `key` = ?")) {
                update.setString(1, value);
                update.setString(2, key);
                if (update.executeUpdate() == 0) {
                    throw insertException;
                }
            }
        }
    }

    private List<String> selectKeysByPrefix(Connection connection, String prefix) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT `key` FROM kv_store WHERE `key` LIKE ? ORDER BY `key`")) {
            statement.setString(1, prefix + "%");
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> keys = new ArrayList<>();
                while (resultSet.next()) {
                    keys.add(resultSet.getString("key"));
                }
                return keys;
            }
        }
    }

    private int deleteKeys(Connection connection, List<String> keys) throws SQLException {
        if (keys.isEmpty()) {
            return 0;
        }
        String placeholders = "?,".repeat(keys.size());
        String sql = "DELETE FROM kv_store WHERE `key` IN ("
                + placeholders.substring(0, placeholders.length() - 1) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindKeys(statement, keys);
            return statement.executeUpdate();
        }
    }

    private List<Object> executePipeline(Connection connection, List<BasedKVStorePipeline.PipelineOperation> operations)
            throws SQLException {
        List<Object> results = new ArrayList<>(operations.size());

        List<BasedKVStorePipeline.PipelineOperation> setOps = new ArrayList<>();
        List<String> getKeys = new ArrayList<>();
        List<String> existsKeys = new ArrayList<>();
        for (BasedKVStorePipeline.PipelineOperation operation : operations) {
            switch (operation.kind()) {
                case "set" -> setOps.add(operation);
                case "get" -> getKeys.add(operation.key());
                case "exists" -> existsKeys.add(operation.key());
                default -> throw new IllegalArgumentException("Unsupported pipeline op: " + operation.kind());
            }
        }

        for (BasedKVStorePipeline.PipelineOperation operation : setOps) {
            upsert(connection, operation.key(), encodeValue(operation.value()));
        }

        Map<String, Object> getResults = fetchValues(connection, getKeys);
        Map<String, Boolean> existsResults = fetchExistence(connection, existsKeys);
        for (BasedKVStorePipeline.PipelineOperation operation : operations) {
            switch (operation.kind()) {
                case "set" -> results.add(null);
                case "get" -> results.add(getResults.get(operation.key()));
                case "exists" -> results.add(existsResults.getOrDefault(operation.key(), false));
                default -> throw new IllegalArgumentException("Unsupported pipeline op: " + operation.kind());
            }
        }
        return results;
    }

    private Map<String, Object> fetchValues(Connection connection, List<String> keys) throws SQLException {
        if (keys.isEmpty()) {
            return Map.of();
        }
        String placeholders = "?,".repeat(keys.size());
        String sql = "SELECT `key`, `value` FROM kv_store WHERE `key` IN ("
                + placeholders.substring(0, placeholders.length() - 1) + ")";
        Map<String, Object> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindKeys(statement, keys);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.put(resultSet.getString("key"), decodeValue(resultSet.getString("value")));
                }
            }
        }
        return result;
    }

    private Map<String, Boolean> fetchExistence(Connection connection, List<String> keys) throws SQLException {
        if (keys.isEmpty()) {
            return Map.of();
        }
        String placeholders = "?,".repeat(keys.size());
        String sql = "SELECT `key` FROM kv_store WHERE `key` IN ("
                + placeholders.substring(0, placeholders.length() - 1) + ")";
        Map<String, Boolean> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindKeys(statement, keys);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.put(resultSet.getString("key"), true);
                }
            }
        }
        return result;
    }

    private void bindKeys(PreparedStatement statement, List<String> keys) throws SQLException {
        for (int index = 0; index < keys.size(); index++) {
            statement.setString(index + 1, keys.get(index));
        }
    }

    private String encodeValue(Object value) {
        if (value instanceof byte[] bytes) {
            return BYTES_PREFIX + Base64.getEncoder().encodeToString(bytes);
        }
        return Objects.toString(value, null);
    }

    private Object decodeValue(String value) {
        if (value != null && value.startsWith(BYTES_PREFIX)) {
            return Base64.getDecoder().decode(value.substring(BYTES_PREFIX.length()));
        }
        return value;
    }

    private Object parseJson(String rawValue) {
        try {
            return OBJECT_MAPPER.readValue(rawValue, Object.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private Map<String, Object> parseJsonObject(String rawValue) {
        try {
            return OBJECT_MAPPER.readValue(rawValue, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new CompletionException("Failed to encode JSON payload", exception);
        }
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private <T> T withTransaction(Connection connection, SqlFunction<Connection, T> action) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T result = action.apply(connection);
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    @FunctionalInterface
    private interface SqlFunction<T, R> {
        R apply(T value) throws SQLException;
    }

    private record StoredValue(String key, String value) {
    }
}
