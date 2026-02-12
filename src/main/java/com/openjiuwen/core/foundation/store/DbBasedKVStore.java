// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Database-backed implementation of {@link BaseKVStore} using JDBC.
 * <p>
 * Stores key-value pairs in a SQLite database table. Operations are wrapped in
 * CompletableFuture for async-style API, though underlying JDBC operations are synchronous.
 * </p>
 * 
 * <p>
 * Note: Python version uses SQLAlchemy with async support (aiosqlite).
 * This Java version uses synchronous JDBC wrapped in CompletableFuture.
 * For truly async database access, consider migrating to R2DBC in the future.
 * </p>
 * 
 * <p>Converted from Python: agent-core/openjiuwen/core/foundation/store/db_based_kv_store.py</p>
 */
public class DbBasedKVStore extends BaseKVStore {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final Connection connection;
    private boolean tableCreated = false;

    /**
     * Constructs a DbBasedKVStore with the given database connection.
     *
     * @param connection the JDBC connection to use
     */
    public DbBasedKVStore(Connection connection) {
        this.connection = connection;
    }

    @Override
    public CompletableFuture<Void> set(String key, String value) {
        return CompletableFuture.runAsync(() -> {
            try {
                createTableIfNotExist();
                
                // Use REPLACE or INSERT OR REPLACE for SQLite
                String sql = "INSERT OR REPLACE INTO kv_store (key, value) VALUES (?, ?)";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, key);
                    stmt.setString(2, value);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to set key: " + key, e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> exclusiveSet(String key, String value, Integer expiry) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                createTableIfNotExist();
                
                long now = System.currentTimeMillis();
                
                // Check if key exists
                String selectSql = "SELECT value FROM kv_store WHERE key = ?";
                try (PreparedStatement stmt = connection.prepareStatement(selectSql)) {
                    stmt.setString(1, key);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String existingValue = rs.getString("value");
                            try {
                                // Try to parse as JSON to check expiry
                                @SuppressWarnings("unchecked")
                                Map<String, Object> data = OBJECT_MAPPER.readValue(existingValue, Map.class);
                                Object oldExpire = data.get("expiry");
                                if (oldExpire == null || ((Number) oldExpire).longValue() > now) {
                                    // Key exists and not expired
                                    return false;
                                }
                            } catch (JsonProcessingException e) {
                                // Not JSON or invalid format, consider as existing
                                return false;
                            }
                        }
                    }
                }
                
                // Key doesn't exist or is expired, set it
                Long expireAt = expiry != null ? now + (expiry * 1000L) : null;
                Map<String, Object> valueMap = new HashMap<>();
                valueMap.put("value", value);
                valueMap.put("expiry", expireAt);
                
                String jsonValue = OBJECT_MAPPER.writeValueAsString(valueMap);
                
                String insertSql = "INSERT OR REPLACE INTO kv_store (key, value) VALUES (?, ?)";
                try (PreparedStatement stmt = connection.prepareStatement(insertSql)) {
                    stmt.setString(1, key);
                    stmt.setString(2, jsonValue);
                    stmt.executeUpdate();
                }
                
                return true;
            } catch (SQLException | JsonProcessingException e) {
                throw new RuntimeException("Failed to exclusive set key: " + key, e);
            }
        });
    }

    @Override
    public CompletableFuture<String> get(String key) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                createTableIfNotExist();
                
                String sql = "SELECT value FROM kv_store WHERE key = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, key);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getString("value");
                        }
                        return null;
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get key: " + key, e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> exists(String key) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                createTableIfNotExist();
                
                String sql = "SELECT 1 FROM kv_store WHERE key = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, key);
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next();
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to check existence of key: " + key, e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> delete(String key) {
        return CompletableFuture.runAsync(() -> {
            try {
                createTableIfNotExist();
                
                String sql = "DELETE FROM kv_store WHERE key = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, key);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete key: " + key, e);
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, String>> getByPrefix(String prefix) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                createTableIfNotExist();
                
                String sql = "SELECT key, value FROM kv_store WHERE key LIKE ?";
                Map<String, String> result = new HashMap<>();
                
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, prefix + "%");
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            result.put(rs.getString("key"), rs.getString("value"));
                        }
                    }
                }
                
                return result;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get by prefix: " + prefix, e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteByPrefix(String prefix) {
        return CompletableFuture.runAsync(() -> {
            try {
                createTableIfNotExist();
                
                String sql = "DELETE FROM kv_store WHERE key LIKE ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, prefix + "%");
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete by prefix: " + prefix, e);
            }
        });
    }

    @Override
    public CompletableFuture<List<String>> mget(List<String> keys) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                createTableIfNotExist();
                
                if (keys == null || keys.isEmpty()) {
                    return Collections.emptyList();
                }
                
                // Build IN clause
                StringBuilder sql = new StringBuilder("SELECT key, value FROM kv_store WHERE key IN (");
                for (int i = 0; i < keys.size(); i++) {
                    if (i > 0) sql.append(", ");
                    sql.append("?");
                }
                sql.append(")");
                
                Map<String, String> lookup = new HashMap<>();
                try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
                    for (int i = 0; i < keys.size(); i++) {
                        stmt.setString(i + 1, keys.get(i));
                    }
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            lookup.put(rs.getString("key"), rs.getString("value"));
                        }
                    }
                }
                
                // Return values in same order as input keys
                List<String> result = new ArrayList<>();
                for (String key : keys) {
                    result.add(lookup.get(key));
                }
                
                return result;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to mget keys", e);
            }
        });
    }

    /**
     * Create the kv_store table if it doesn't exist yet.
     */
    private synchronized void createTableIfNotExist() throws SQLException {
        if (tableCreated) {
            return;
        }
        
        String createTableSql = 
            "CREATE TABLE IF NOT EXISTS kv_store (" +
            "  key TEXT PRIMARY KEY," +
            "  value TEXT NOT NULL" +
            ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
        }
        
        tableCreated = true;
    }
}

