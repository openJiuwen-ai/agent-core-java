package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.memory.support.TestDbStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageManagerTest {

    private MessageManager manager;

    @BeforeEach
    void setUp() {
        SqlDbStore sqlDbStore = new SqlDbStore(new TestDbStore(createDataSource()));
        DbModel.createTables(sqlDbStore.getDbStore());
        manager = new MessageManager(sqlDbStore, new DataIdManager(), new byte[0]);
    }

    @Test
    void getByIdReturnsNullForMissingMessageLikePythonNone() {
        assertNull(manager.getById("missing-message"));
    }

    @Test
    void addGetGetByIdAndDeletePreservePythonVisibleSemantics() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-05-30T10:15:30Z");
        MessageAddRequest request = MessageAddRequest.builder()
                .userId("user-1")
                .scopeId("scope-1")
                .sessionId("session-1")
                .role("user")
                .content("hello")
                .timestamp(timestamp)
                .build();

        String messageId = manager.add(request);

        MessageManager.MessageRecord byId = manager.getById(messageId);
        assertNotNull(byId);
        assertEquals("user", byId.message().getRole());
        assertEquals("hello", byId.message().getContentAsString());
        assertEquals(timestamp, byId.timestamp());

        List<MessageManager.MessageRecord> messages = manager.get("user-1", "scope-1", "session-1", 10);
        assertEquals(1, messages.size());
        assertEquals("hello", messages.getFirst().message().getContentAsString());

        assertTrue(manager.deleteByUserAndScope("user-1", "scope-1"));
        assertNull(manager.getById(messageId));
    }

    private static DataSource createDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
    }
}
