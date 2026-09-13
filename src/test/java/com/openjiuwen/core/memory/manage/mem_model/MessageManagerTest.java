
package com.openjiuwen.core.memory.manage.mem_model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.memory.support.TestDbStore;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

class MessageManagerTest {
    private MessageManager messageManager;

    @BeforeEach
    void setUp() {
        SqlDbStore sqlDbStore = new SqlDbStore(new TestDbStore(createDataSource()));
        DbModel.createTables(sqlDbStore.getDbStore());
        messageManager = new MessageManager(sqlDbStore, new DataIdManager(), new byte[0]);
    }

    @Test
    void addGetGetByIdAndDeleteFollowPythonMessageManagerFlow() {
        String firstId =
            messageManager.add(MessageAddRequest.builder().userId("user-1").scopeId("scope-1").sessionId("session-1")
                    .role("user").content("first").timestamp(OffsetDateTime.parse("2026-05-11T01:00:00Z")).build());
        String secondId = messageManager.add(
                MessageAddRequest.builder().userId("user-1").scopeId("scope-1").sessionId("session-1").role("assistant")
                        .content("second").timestamp(OffsetDateTime.parse("2026-05-11T02:00:00Z")).build());

        List<MessageManager.MessageRecord> records = messageManager.get("user-1", "scope-1", "session-1", 10);

        assertEquals(2, records.size());
        assertEquals("first", records.get(0).message().getContentAsString());
        assertEquals("second", records.get(1).message().getContentAsString());
        assertEquals("assistant", messageManager.getById(secondId).message().getRole());
        assertNull(messageManager.getById("missing"));
        assertTrue(firstId.matches("[0-9a-f]{24}"));

        assertTrue(messageManager.deleteByUserAndScope("user-1", "scope-1"));
        assertEquals(List.of(), messageManager.get("user-1", "scope-1", "session-1", 10));
    }

    @Test
    void addRequiresUserScopeAndContent() {
        assertThrows(BaseError.class,
                () -> messageManager.add(MessageAddRequest.builder().scopeId("scope-1").content("content").build()));
        assertThrows(BaseError.class,
                () -> messageManager.add(MessageAddRequest.builder().userId("user-1").content("content").build()));
        assertThrows(BaseError.class,
                () -> messageManager.add(MessageAddRequest.builder().userId("user-1").scopeId("scope-1").build()));
    }

    @Test
    void getRequiresPositiveMessageLength() {
        assertThrows(BaseError.class, () -> messageManager.get("user-1", "scope-1", "session-1", 0));
    }

    private static DataSource createDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
    }
}
