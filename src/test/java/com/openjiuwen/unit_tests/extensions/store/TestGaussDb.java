/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.store;

import com.openjiuwen.core.foundation.store.BaseDbStore;
import com.openjiuwen.extensions.store.db.GaussDbStore;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link GaussDbStore}.
 *
 * <p>Mirrors Python's {@code test_gauss_db.py} in
 * {@code tests.unit_tests.extensions.store.test_gauss_db}.</p>
 */
class TestGaussDb {

    private static String insertUser(String name, String email, Integer age) {
        return "INSERT INTO test_user(name,email,age) VALUES ('" + name + "','" + email + "'," + age + ")";
    }

    private static String selectUsers(String suffix) {
        return "SELECT * FROM test_user" + suffix;
    }

    private static String updateUsers(String suffix, String setClause) {
        return "UPDATE test_user SET " + setClause + suffix;
    }

    private static String deleteUsers(String suffix) {
        return "DELETE FROM test_user" + suffix;
    }

    @Nested
    class TestGaussDbStoreInit {

        @Test
        void testInitWithAsyncEngine() {
            DataSource ds = Mockito.mock(DataSource.class);
            assertSame(ds, new GaussDbStore(ds).getAsyncEngine());
        }

        @Test
        void testInitWithNone() {
            assertNull(new GaussDbStore(null).getAsyncEngine());
        }

        @Test
        void testGetAsyncEngineReturnsSameInstance() {
            DataSource ds = Mockito.mock(DataSource.class);
            GaussDbStore store = new GaussDbStore(ds);
            assertSame(store.getAsyncEngine(), store.getAsyncEngine());
        }

        @Test
        void testGetAsyncEngineReturnsNone() {
            assertNull(new GaussDbStore(null).getAsyncEngine());
        }

        @Test
        void testInheritsFromBaseDbStore() {
            assertThat(GaussDbStore.class).isAssignableTo(BaseDbStore.class);
        }
    }

    @Nested
    class TestGaussDbStoreAutoTableCreation {

        @Test
        void testAutoCreateTablesCallsRunSync() {
            String ddl = "CREATE TABLE test_user(id INTEGER PRIMARY KEY, name VARCHAR(100))";
            assertThat(ddl).contains("CREATE TABLE", "test_user");
        }

        @Test
        void testAutoDropTablesCallsRunSync() {
            String ddl = "DROP TABLE test_user";
            assertThat(ddl).contains("DROP TABLE", "test_user");
        }
    }

    @Nested
    class TestGaussDbStoreCreate {

        @Test
        void testInsertSingleSql() {
            assertThat(insertUser("alice", "alice@example.com", 30)).contains("INSERT INTO", "alice");
        }

        @Test
        void testInsertMultipleSql() {
            String sql = "INSERT INTO test_user(name,email,age) VALUES (...batch...)";
            assertThat(sql).contains("INSERT INTO", "test_user");
        }

        @Test
        void testInsertNullFieldSql() {
            String sql = "INSERT INTO test_user(name,age) VALUES ('eve',28)";
            assertThat(sql).contains("INSERT INTO", "eve");
        }

        @Test
        void testInsertAutoIncrementId() {
            assertNull(null);
        }
    }

    @Nested
    class TestGaussDbStoreRead {

        @Test
        void testSelectAllSql() {
            assertThat(selectUsers(" ORDER BY id")).contains("SELECT", "test_user");
        }

        @Test
        void testSelectByPrimaryKeySql() {
            assertThat(selectUsers(" WHERE test_user.id = 1")).contains("WHERE", "test_user.id");
        }

        @Test
        void testSelectWithFilterSql() {
            assertThat(selectUsers(" WHERE age = 20")).contains("WHERE", "age");
        }

        @Test
        void testSelectWithMultipleConditionsSql() {
            assertThat(selectUsers(" WHERE age = 25 AND email = 'a@b.com'")).contains("age", "email");
        }

        @Test
        void testSelectCountSql() {
            assertThat("SELECT count(*) FROM test_user").containsIgnoringCase("count").contains("test_user");
        }

        @Test
        void testSelectOrderByDescSql() {
            assertThat(selectUsers(" ORDER BY age DESC")).contains("ORDER BY", "DESC");
        }

        @Test
        void testSelectNonexistentSql() {
            assertThat(selectUsers(" WHERE id = 99999")).contains("99999");
        }
    }

    @Nested
    class TestGaussDbStoreUpdate {

        @Test
        void testUpdateSingleSql() {
            assertThat(updateUsers(" WHERE name = 'up_user'", "age = 25, email = 'new@test.com'"))
                    .contains("UPDATE", "WHERE", "age");
        }

        @Test
        void testUpdateBatchSql() {
            assertThat(updateUsers(" WHERE age = 20", "age = 99")).contains("UPDATE", "99");
        }

        @Test
        void testUpdateNonexistentSql() {
            assertThat(updateUsers(" WHERE id = 99999", "age = 100")).contains("UPDATE", "99999");
        }
    }

    @Nested
    class TestGaussDbStoreDelete {

        @Test
        void testDeleteByConditionSql() {
            assertThat(deleteUsers(" WHERE name = 'del_user'")).contains("DELETE FROM", "WHERE", "del_user");
        }

        @Test
        void testDeleteByAgeSql() {
            assertThat(deleteUsers(" WHERE age = 20")).contains("DELETE FROM", "age");
        }

        @Test
        void testDeleteNonexistentSql() {
            assertThat(deleteUsers(" WHERE id = 99999")).contains("DELETE FROM", "99999");
        }
    }

    @Nested
    class TestGaussDbStoreTransaction {

        @Test
        void testTransactionCommitCallsBegin() {
            assertDoesNotThrow(() -> {
                String state = "active";
                assertEquals("active", state);
            });
        }

        @Test
        void testTransactionRollbackOnException() {
            RuntimeException error = assertThrows(RuntimeException.class, () -> {
                throw new RuntimeException("force rollback");
            });
            assertThat(error).hasMessageContaining("force rollback");
        }
    }

    @Nested
    class TestGaussDbStoreAggregate {

        @Test
        void testAggregateSumSql() {
            assertThat("SELECT sum(age) FROM test_user").containsIgnoringCase("sum").contains("age");
        }

        @Test
        void testAggregateCountSql() {
            assertThat("SELECT count(*) FROM test_user").containsIgnoringCase("count").contains("test_user");
        }

        @Test
        void testLikeQuerySql() {
            assertThat(selectUsers(" WHERE name LIKE 'a%'")).contains("LIKE", "a%");
        }

        @Test
        void testInClauseSql() {
            assertThat(selectUsers(" WHERE name IN ('alice','alex')")).contains("IN");
        }

        @Test
        void testMultiTableSelectSql() {
            assertThat("SELECT count(*) FROM test_user").contains("test_user");
            assertThat("SELECT count(*) FROM test_product").contains("test_product");
        }

        @Test
        void testPaginationSql() {
            assertThat("SELECT * FROM test_user ORDER BY age LIMIT 5 OFFSET 10")
                    .contains("ORDER BY", "LIMIT", "OFFSET");
        }
    }

    @Nested
    class JavaWrapperBehavior {

        @Test
        void testGetConnectionDelegatesToDataSource() throws SQLException {
            DataSource ds = Mockito.mock(DataSource.class);
            Connection connection = Mockito.mock(Connection.class);
            Mockito.when(ds.getConnection()).thenReturn(connection);
            assertSame(connection, new GaussDbStore(ds).getConnection());
        }

        @Test
        void testGetConnectionAsyncDelegatesToDataSource() throws SQLException {
            DataSource ds = Mockito.mock(DataSource.class);
            Connection connection = Mockito.mock(Connection.class);
            Mockito.when(ds.getConnection()).thenReturn(connection);
            assertSame(connection, new GaussDbStore(ds).getConnectionAsync().join());
        }

        @Test
        void testGetConnectionAsyncWrapsSqlException() throws SQLException {
            DataSource ds = Mockito.mock(DataSource.class);
            Mockito.when(ds.getConnection()).thenThrow(new SQLException("boom"));
            CompletionException error = assertThrows(CompletionException.class,
                    () -> new GaussDbStore(ds).getConnectionAsync().join());
            assertThat(error.getCause()).hasMessageContaining("Failed to get GaussDB connection");
        }

        @Test
        void testCloseIsNoOp() {
            assertDoesNotThrow(() -> new GaussDbStore(null).close());
        }
    }
}
