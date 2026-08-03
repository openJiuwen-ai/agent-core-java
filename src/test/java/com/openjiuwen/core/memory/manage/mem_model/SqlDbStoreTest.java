/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.foundation.store.db.DefaultDbStore;

import org.h2.jdbcx.JdbcDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * Focused validation for {@link SqlDbStore}.
 *
 * <p>Mirrors Python's {@code SqlDbStore} in
 * {@code openjiuwen/core/memory/manage/mem_model/sql_db_store.py}.</p>
 */
public final class SqlDbStoreTest {

    private SqlDbStoreTest() {
    }

    public static void main(String[] args) throws Exception {
        JdbcDataSource dataSource = newDataSource();
        SqlDbStore store = new SqlDbStore(new DefaultDbStore<>(dataSource));
        createTable(dataSource);

        require(store.write("memory_items", row("1", "user-a", "scope-a", "2026-06-14T00:00:00Z", "first")).join(),
                "write first");
        require(store.write("memory_items", row("2", "user-b", "scope-a", "2026-06-14T00:01:00Z", "second")).join(),
                "write second");
        require(store.get("memory_items", "1").join().get("content").equals("first"), "get all columns");
        require(store.get("memory_items", "1", List.of("content")).join().equals(Map.of("content", "first")),
                "get selected columns");
        require(store.exist("memory_items", Map.of("scope_id", "scope-a")).join(), "exists");
        require(store.batchGet("memory_items", List.of(Map.of("id", "missing", "user_id", "user-b"))).join().size() == 1,
                "batch_get uses OR conditions like Python");
        require(store.conditionGet("memory_items", Map.of("scope_id", List.of("scope-a")), null).join().size() == 2,
                "condition_get");
        require(store.getWithSort("memory_items", Map.of("scope_id", "scope-a"), "timestamp", "DESC", 1)
                .join().get(0).get("id").equals("2"), "get_with_sort");
        require(store.update("memory_items", Map.of("id", "2"), Map.of("content", "updated")).join(),
                "update");
        require(store.get("memory_items", "2").join().get("content").equals("updated"), "updated content");
        require(store.conditionGet("memory_items", Map.of("scope_id", "scope-a"), null).join() == null,
                "condition_get invalid condition returns null");
        require(store.delete("memory_items", Map.of("id", List.of("1", "2"))).join(), "delete");
        require(!store.exist("memory_items", Map.of("scope_id", "scope-a")).join(), "deleted rows");
        require(store.deleteTable("memory_items").join(), "delete table");
        store.invalidateTableCache("memory_items");

        System.out.println("PASS SqlDbStoreTest");
    }

    private static JdbcDataSource newDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:sql_db_store_test;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static void createTable(JdbcDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE memory_items (
                        id VARCHAR(64) PRIMARY KEY,
                        user_id VARCHAR(64),
                        scope_id VARCHAR(64),
                        timestamp VARCHAR(64),
                        content VARCHAR(256)
                    )
                    """);
        }
    }

    private static Map<String, Object> row(String id, String userId, String scopeId, String timestamp, String content) {
        return Map.of(
                "id", id,
                "user_id", userId,
                "scope_id", scopeId,
                "timestamp", timestamp,
                "content", content
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
