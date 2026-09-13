
package com.openjiuwen.core.memory.manage.mem_model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.memory.support.TestDbStore;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

class ScopeUserMappingManagerTest {
    private ScopeUserMappingManager manager;

    @BeforeEach
    void setUp() {
        SqlDbStore sqlDbStore = new SqlDbStore(new TestDbStore(createDataSource()));
        DbModel.createTables(sqlDbStore.getDbStore());
        manager = new ScopeUserMappingManager(sqlDbStore);
    }

    @Test
    void addIsIdempotentAndQueryDeleteByScopeIdMatchPythonFlow() {
        manager.add("user-1", "scope-1");
        manager.add("user-1", "scope-1");
        manager.add("user-2", "scope-1");

        List<java.util.Map<String, Object>> rows = manager.getByScopeId("scope-1");

        assertEquals(2, rows.size());
        assertTrue(manager.deleteByScopeId("scope-1"));
        assertNull(manager.getByScopeId("scope-1"));
    }

    private static DataSource createDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
    }
}
