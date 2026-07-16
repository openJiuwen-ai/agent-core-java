/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import com.openjiuwen.spi.store.BaseDbStore;
import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStorePipeline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

/**
 * JDBC-backed KV store using a simple two-column table.
 * 
 * @since 0.1.7
 */
public class DbBasedKVStore extends BaseKVStore {
    private final BaseDbStore<?> dbStore;
    private final String tableName;

    /**
     * DbBasedKVStore.
     * 
     * @param dbStore dbStore
     * @since 0.1.7
     */
    public DbBasedKVStore(BaseDbStore<?> dbStore) {
        this(dbStore, "kv_store");
    }

    /**
     * DbBasedKVStore.
     * 
     * @param dbStore dbStore
     * @param tableName tableName
     * @since 0.1.7
     */
    public DbBasedKVStore(BaseDbStore<?> dbStore, String tableName) {
        this.dbStore = dbStore;
        this.tableName = tableName;
        initializeTable();
    }

    /**
     * set.
     * 
     * @param key key
     * @param value value
     * @since 0.1.7
     */
    @Override
    public void set(String key, Object value) {
        try (Connection connection = getConnection();
                PreparedStatement statement =
                    connection.prepareStatement("MERGE INTO " + tableName + " (k, v) KEY(k) VALUES (?, ?)")) {
            statement.setString(1, key);
            statement.setString(2, value == null ? null : String.valueOf(value));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to set KV value", e);
        }
    }

    /**
     * exclusiveSet.
     * 
     * @param key key
     * @param value value
     * @param expiry expiry
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean exclusiveSet(String key, Object value, Integer expiry) {
        if (isExists(key)) {
            return false;
        }
        set(key, value);
        return true;
    }

    /**
     * get.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object get(String key) {
        try (Connection connection = getConnection();
                PreparedStatement statement =
                    connection.prepareStatement("SELECT v FROM " + tableName + " WHERE k = ?")) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to get KV value", e);
        }
    }

    /**
     * isExists.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isExists(String key) {
        return get(key) != null;
    }

    /**
     * delete.
     * 
     * @param key key
     * @since 0.1.7
     */
    @Override
    public void delete(String key) {
        try (Connection connection = getConnection();
                PreparedStatement statement =
                    connection.prepareStatement("DELETE FROM " + tableName + " WHERE k = ?")) {
            statement.setString(1, key);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete KV value", e);
        }
    }

    /**
     * getByPrefix.
     * 
     * @param prefix prefix
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> getByPrefix(String prefix) {
        Map<String, Object> result = new LinkedHashMap<>();
        try (Connection connection = getConnection();
                PreparedStatement statement =
                    connection.prepareStatement("SELECT k, v FROM " + tableName + " WHERE k LIKE ?")) {
            statement.setString(1, prefix + "%");
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.put(resultSet.getString(1), resultSet.getString(2));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query KV values by prefix", e);
        }
        return result;
    }

    /**
     * deleteByPrefix.
     * 
     * @param prefix prefix
     * @param batchSize batchSize
     * @since 0.1.7
     */
    @Override
    public void deleteByPrefix(String prefix, Integer batchSize) {
        try (Connection connection = getConnection();
                PreparedStatement statement =
                    connection.prepareStatement("DELETE FROM " + tableName + " WHERE k LIKE ?")) {
            statement.setString(1, prefix + "%");
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete KV values by prefix", e);
        }
    }

    /**
     * mget.
     * 
     * @param keys keys
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Object> mget(List<String> keys) {
        List<Object> result = new ArrayList<>(keys.size());
        for (String key : keys) {
            result.add(get(key));
        }
        return result;
    }

    /**
     * batchDelete.
     * 
     * @param keys keys
     * @param batchSize batchSize
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int batchDelete(List<String> keys, Integer batchSize) {
        int deleted = 0;
        for (String key : keys) {
            if (isExists(key)) {
                delete(key);
                deleted++;
            }
        }
        return deleted;
    }

    /**
     * pipeline.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public KVStorePipeline pipeline() {
        return new KVStorePipeline(operations -> {
            List<Object> results = new ArrayList<>(operations.size());
            for (Object[] operation : operations) {
                String action = String.valueOf(operation[0]);
                String key = operation.length > 1 ? String.valueOf(operation[1]) : "";
                switch (action) {
                    case "set" -> {
                        set(key, operation.length > 2 ? operation[2] : null);
                        results.add(true);
                    }
                    case "get" -> results.add(get(key));
                    case "isExists" -> results.add(isExists(key));
                    default -> results.add(null);
                }
            }
            return results;
        });
    }

    /**
     * getConnection.
     * 
     * @return the result
     * @throws SQLException SQLException
     * @since 0.1.7
     */
    private Connection getConnection() throws SQLException {
        Object engine = dbStore.getEngine();
        if (engine instanceof DataSource dataSource) {
            return dataSource.getConnection();
        }
        if (engine instanceof Connection connection) {
            return connection;
        }
        throw new SQLException(
                "Unsupported DB engine type: " + (engine == null ? "null" : engine.getClass().getName()));
    }

    /**
     * initializeTable.
     * 
     * @since 0.1.7
     */
    private void initializeTable() {
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS " + tableName + " (k VARCHAR(512) PRIMARY KEY, v CLOB)")) {
            statement.execute();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize KV store table", e);
        }
    }
}
