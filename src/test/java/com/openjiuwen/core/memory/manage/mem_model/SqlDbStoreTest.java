package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.memory.support.TestDbStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlDbStoreTest {

    private SqlDbStore sqlDbStore;

    @BeforeEach
    void setUp() {
        sqlDbStore = new SqlDbStore(new TestDbStore(createDataSource()));
        DbModel.createTables(sqlDbStore.getDbStore());
        seedMessages();
    }

    @Test
    void conditionGetReturnsMatchingRow() {
        Map<String, List<Object>> filters = new LinkedHashMap<>();
        filters.put("message_id", new ArrayList<>(List.of("m1")));

        List<Map<String, Object>> rows = sqlDbStore.conditionGet("user_message", filters, null);

        assertNotNull(rows);
        assertEquals(1, rows.size());
        assertEquals("u1", rows.get(0).get("user_id"));
        assertEquals("Hello", rows.get(0).get("content"));
    }

    @Test
    void getWithSortReturnsAscendingResults() {
        List<Map<String, Object>> rows = sqlDbStore.getWithSort(
                "user_message",
                Map.of("user_id", "u1"),
                "timestamp",
                "ASC",
                10
        );

        assertEquals(2, rows.size());
        assertEquals("m1", rows.get(0).get("message_id"));
        assertEquals("m2", rows.get(1).get("message_id"));
    }

    @Test
    void existUpdateAndDeleteFollowPythonSemantics() {
        assertTrue(sqlDbStore.exist("user_message", Map.of("message_id", "m1")));
        assertFalse(sqlDbStore.exist("user_message", Map.of("message_id", "not_exist")));

        assertTrue(sqlDbStore.update(
                "user_message",
                Map.of("message_id", "m1"),
                Map.of("content", "hi")
        ));
        Map<String, List<Object>> singleFilter = new LinkedHashMap<>();
        singleFilter.put("message_id", new ArrayList<>(List.of("m1")));
        assertEquals("hi", sqlDbStore.conditionGet("user_message", singleFilter, null).get(0).get("content"));

        Map<String, Object> batchConditions = new LinkedHashMap<>();
        batchConditions.put("message_id", new ArrayList<>(List.of("m2", "m3")));
        assertTrue(sqlDbStore.update(
                "user_message",
                batchConditions,
                Map.of("content", "batch")
        ));

        Map<String, List<Object>> m2Filter = new LinkedHashMap<>();
        m2Filter.put("message_id", new ArrayList<>(List.of("m2")));
        Map<String, List<Object>> m3Filter = new LinkedHashMap<>();
        m3Filter.put("message_id", new ArrayList<>(List.of("m3")));
        assertEquals("batch", sqlDbStore.conditionGet("user_message", m2Filter, null).get(0).get("content"));
        assertEquals("batch", sqlDbStore.conditionGet("user_message", m3Filter, null).get(0).get("content"));

        assertTrue(sqlDbStore.delete("user_message", Map.of("message_id", "m1")));
        assertTrue(sqlDbStore.conditionGet("user_message", singleFilter, null).isEmpty());
    }

    private void seedMessages() {
        assertTrue(sqlDbStore.write("user_message", row("u1", "group1", "s1", "m1", "user", "Hello", "2025-11-19 09:00:00")));
        assertTrue(sqlDbStore.write("user_message", row("u1", "group1", "s1", "m2", "user", "World", "2025-11-19 10:00:00")));
        assertTrue(sqlDbStore.write("user_message", row("u2", "group2", "s2", "m3", "assistant", "Hi there", "2025-11-19 11:00:00")));
    }

    private static Map<String, Object> row(
            String userId,
            String scopeId,
            String sessionId,
            String messageId,
            String role,
            String content,
            String timestamp) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user_id", userId);
        row.put("scope_id", scopeId);
        row.put("session_id", sessionId);
        row.put("message_id", messageId);
        row.put("role", role);
        row.put("content", content);
        row.put("timestamp", timestamp);
        return row;
    }

    private static DataSource createDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
    }
}
