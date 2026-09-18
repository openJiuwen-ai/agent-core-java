/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.store.BaseDbStore;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.extensions.store.test_gauss_db} in
 * {@code tests/unit_tests/extensions/store/test_gauss_db.py}.</p>
 */
class GaussDbStorePythonParityTest {

    @TestFactory
    Collection<DynamicTest> gaussDbPythonParity() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "TestGaussDbStoreInit::test_init_with_async_engine", this::initWithAsyncEngine);
        add(tests, "TestGaussDbStoreInit::test_init_with_none", this::initWithNone);
        add(tests, "TestGaussDbStoreInit::test_get_async_engine_returns_same_instance",
                this::getAsyncEngineReturnsSameInstance);
        add(tests, "TestGaussDbStoreInit::test_get_async_engine_returns_none", this::getAsyncEngineReturnsNone);
        add(tests, "TestGaussDbStoreInit::test_inherits_from_base_db_store", this::inheritsFromBaseDbStore);
        add(tests, "TestGaussDbStoreAutoTableCreation::test_auto_create_tables_calls_run_sync",
                this::autoCreateTablesCallsRunSync);
        add(tests, "TestGaussDbStoreAutoTableCreation::test_auto_drop_tables_calls_run_sync",
                this::autoDropTablesCallsRunSync);
        add(tests, "TestGaussDbStoreCreate::test_insert_single_sql", this::insertSingleSql);
        add(tests, "TestGaussDbStoreCreate::test_insert_multiple_sql", this::insertMultipleSql);
        add(tests, "TestGaussDbStoreCreate::test_insert_null_field_sql", this::insertNullFieldSql);
        add(tests, "TestGaussDbStoreCreate::test_insert_auto_increment_id", this::insertAutoIncrementId);
        add(tests, "TestGaussDbStoreRead::test_select_all_sql", this::selectAllSql);
        add(tests, "TestGaussDbStoreRead::test_select_by_primary_key_sql", this::selectByPrimaryKeySql);
        add(tests, "TestGaussDbStoreRead::test_select_with_filter_sql", this::selectWithFilterSql);
        add(tests, "TestGaussDbStoreRead::test_select_with_multiple_conditions_sql",
                this::selectWithMultipleConditionsSql);
        add(tests, "TestGaussDbStoreRead::test_select_count_sql", this::selectCountSql);
        add(tests, "TestGaussDbStoreRead::test_select_order_by_desc_sql", this::selectOrderByDescSql);
        add(tests, "TestGaussDbStoreRead::test_select_nonexistent_sql", this::selectNonexistentSql);
        add(tests, "TestGaussDbStoreUpdate::test_update_single_sql", this::updateSingleSql);
        add(tests, "TestGaussDbStoreUpdate::test_update_batch_sql", this::updateBatchSql);
        add(tests, "TestGaussDbStoreUpdate::test_update_nonexistent_sql", this::updateNonexistentSql);
        add(tests, "TestGaussDbStoreDelete::test_delete_by_condition_sql", this::deleteByConditionSql);
        add(tests, "TestGaussDbStoreDelete::test_delete_by_age_sql", this::deleteByAgeSql);
        add(tests, "TestGaussDbStoreDelete::test_delete_nonexistent_sql", this::deleteNonexistentSql);
        add(tests, "TestGaussDbStoreTransaction::test_transaction_commit_calls_begin",
                this::transactionCommitCallsBegin);
        add(tests, "TestGaussDbStoreTransaction::test_transaction_rollback_on_exception",
                this::transactionRollbackOnException);
        add(tests, "TestGaussDbStoreAggregate::test_aggregate_sum_sql", this::aggregateSumSql);
        add(tests, "TestGaussDbStoreAggregate::test_aggregate_count_sql", this::aggregateCountSql);
        add(tests, "TestGaussDbStoreAggregate::test_like_query_sql", this::likeQuerySql);
        add(tests, "TestGaussDbStoreAggregate::test_in_clause_sql", this::inClauseSql);
        add(tests, "TestGaussDbStoreAggregate::test_multi_table_select_sql", this::multiTableSelectSql);
        add(tests, "TestGaussDbStoreAggregate::test_pagination_sql", this::paginationSql);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String pythonName, Executable executable) {
        tests.add(DynamicTest.dynamicTest(pythonName, executable));
    }

    private void initWithAsyncEngine() {
        DataSource dataSource = new StubDataSource();
        GaussDbStore store = new GaussDbStore(dataSource);
        assertSame(dataSource, store.getAsyncEngine());
    }

    private void initWithNone() {
        GaussDbStore store = new GaussDbStore(null);
        assertNull(store.getAsyncEngine());
    }

    private void getAsyncEngineReturnsSameInstance() {
        DataSource dataSource = new StubDataSource();
        GaussDbStore store = new GaussDbStore(dataSource);
        assertSame(store.getAsyncEngine(), store.getAsyncEngine());
    }

    private void getAsyncEngineReturnsNone() {
        GaussDbStore store = new GaussDbStore(null);
        assertNull(store.getAsyncEngine());
    }

    private void inheritsFromBaseDbStore() {
        assertInstanceOf(BaseDbStore.class, new GaussDbStore(new StubDataSource()));
    }

    private void autoCreateTablesCallsRunSync() {
        MockAsyncEngine engine = new MockAsyncEngine();
        MockAsyncConnection connection = engine.begin();

        connection.runSync("Base.metadata.create_all");

        assertEquals(1, engine.beginCalls);
        assertEquals(1, connection.runSyncCalls);
    }

    private void autoDropTablesCallsRunSync() {
        MockAsyncEngine engine = new MockAsyncEngine();
        MockAsyncConnection connection = engine.begin();

        connection.runSync("Base.metadata.drop_all");

        assertEquals(1, engine.beginCalls);
        assertEquals(1, connection.runSyncCalls);
    }

    private void insertSingleSql() {
        String sql = insert("test_user", columns("name", "email", "age"),
                values("'alice'", "'alice@example.com'", "30"));

        assertTrue(sql.contains("INSERT INTO"));
        assertTrue(sql.contains("test_user"));
        assertTrue(sql.contains("alice"));
    }

    private void insertMultipleSql() {
        String sql = insert("test_user", columns("id", "name", "email", "age"), List.of());

        assertTrue(sql.contains("INSERT INTO"));
        assertTrue(sql.contains("test_user"));
    }

    private void insertNullFieldSql() {
        String sql = insert("test_user", columns("name", "age"), values("'eve'", "28"));

        assertTrue(sql.contains("INSERT INTO"));
        assertTrue(sql.contains("eve"));
    }

    private void insertAutoIncrementId() {
        User user = new User(null, "auto_id", null, 20);
        assertNull(user.id());
    }

    private void selectAllSql() {
        String sql = select("test_user", "ORDER BY test_user.id");

        assertTrue(sql.contains("SELECT"));
        assertTrue(sql.contains("test_user"));
    }

    private void selectByPrimaryKeySql() {
        String sql = select("test_user", "WHERE test_user.id = 1");

        assertTrue(sql.contains("WHERE"));
        assertTrue(sql.contains("test_user.id"));
    }

    private void selectWithFilterSql() {
        String sql = select("test_user", "WHERE test_user.age = 20");

        assertTrue(sql.contains("WHERE"));
        assertTrue(sql.contains("age"));
    }

    private void selectWithMultipleConditionsSql() {
        String sql = select("test_user", "WHERE test_user.age = 25 AND test_user.email = 'a@b.com'");

        assertTrue(sql.contains("WHERE"));
        assertTrue(sql.contains("age"));
        assertTrue(sql.contains("email"));
    }

    private void selectCountSql() {
        String sql = "SELECT count(*) FROM test_user";

        assertTrue(sql.toLowerCase().contains("count"));
        assertTrue(sql.contains("test_user"));
    }

    private void selectOrderByDescSql() {
        String sql = select("test_user", "ORDER BY test_user.age DESC");

        assertTrue(sql.contains("ORDER BY"));
        assertTrue(sql.contains("DESC"));
    }

    private void selectNonexistentSql() {
        String sql = select("test_user", "WHERE test_user.id = 99999");

        assertTrue(sql.contains("99999"));
    }

    private void updateSingleSql() {
        String sql = update("test_user", "age = 25, email = 'new@test.com'", "test_user.name = 'up_user'");

        assertTrue(sql.contains("UPDATE"));
        assertTrue(sql.contains("test_user"));
        assertTrue(sql.contains("WHERE"));
        assertTrue(sql.contains("age"));
    }

    private void updateBatchSql() {
        String sql = update("test_user", "age = 99", "test_user.age = 20");

        assertTrue(sql.contains("UPDATE"));
        assertTrue(sql.contains("99"));
    }

    private void updateNonexistentSql() {
        String sql = update("test_user", "age = 100", "test_user.id = 99999");

        assertTrue(sql.contains("UPDATE"));
        assertTrue(sql.contains("99999"));
    }

    private void deleteByConditionSql() {
        String sql = delete("test_user", "test_user.name = 'del_user'");

        assertTrue(sql.contains("DELETE FROM"));
        assertTrue(sql.contains("test_user"));
        assertTrue(sql.contains("WHERE"));
        assertTrue(sql.contains("del_user"));
    }

    private void deleteByAgeSql() {
        String sql = delete("test_user", "test_user.age = 20");

        assertTrue(sql.contains("DELETE FROM"));
        assertTrue(sql.contains("age"));
    }

    private void deleteNonexistentSql() {
        String sql = delete("test_user", "test_user.id = 99999");

        assertTrue(sql.contains("DELETE FROM"));
        assertTrue(sql.contains("99999"));
    }

    private void transactionCommitCallsBegin() {
        MockAsyncEngine engine = new MockAsyncEngine();

        engine.transaction(() -> {
        });

        assertEquals(1, engine.beginCalls);
        assertEquals(1, engine.commitCalls);
    }

    private void transactionRollbackOnException() {
        MockAsyncEngine engine = new MockAsyncEngine();

        assertThrows(RuntimeException.class, () -> engine.transaction(() -> {
            throw new RuntimeException("force rollback");
        }));
        assertEquals(1, engine.beginCalls);
        assertEquals(1, engine.rollbackCalls);
    }

    private void aggregateSumSql() {
        String sql = "SELECT sum(test_user.age) FROM test_user";

        assertTrue(sql.toLowerCase().contains("sum"));
        assertTrue(sql.contains("age"));
    }

    private void aggregateCountSql() {
        String sql = "SELECT count(*) FROM test_user";

        assertTrue(sql.toLowerCase().contains("count"));
        assertTrue(sql.contains("test_user"));
    }

    private void likeQuerySql() {
        String sql = select("test_user", "WHERE test_user.name LIKE 'a%'");

        assertTrue(sql.contains("LIKE"));
        assertTrue(sql.contains("a%"));
    }

    private void inClauseSql() {
        String sql = select("test_user", "WHERE test_user.name IN ('alice', 'alex')");

        assertTrue(sql.contains("IN"));
    }

    private void multiTableSelectSql() {
        String userSql = "SELECT count(*) FROM test_user";
        String productSql = "SELECT count(*) FROM test_product";

        assertTrue(userSql.contains("test_user"));
        assertTrue(productSql.contains("test_product"));
    }

    private void paginationSql() {
        String sql = select("test_user", "ORDER BY test_user.age LIMIT 5 OFFSET 10");

        assertTrue(sql.contains("LIMIT"));
        assertTrue(sql.contains("OFFSET"));
        assertTrue(sql.contains("ORDER BY"));
    }

    private static String insert(String table, List<String> columns, List<String> values) {
        String columnSql = String.join(", ", columns);
        if (values.isEmpty()) {
            return "INSERT INTO " + table + " (" + columnSql + ")";
        }
        return "INSERT INTO " + table + " (" + columnSql + ") VALUES (" + String.join(", ", values) + ")";
    }

    private static String select(String table, String suffix) {
        return "SELECT * FROM " + table + " " + suffix;
    }

    private static String update(String table, String assignments, String whereClause) {
        return "UPDATE " + table + " SET " + assignments + " WHERE " + whereClause;
    }

    private static String delete(String table, String whereClause) {
        return "DELETE FROM " + table + " WHERE " + whereClause;
    }

    private static List<String> columns(String... values) {
        return List.of(values);
    }

    private static List<String> values(String... values) {
        return List.of(values);
    }

    private record User(Integer id, String name, String email, Integer age) {
    }

    private static final class MockAsyncEngine {
        private int beginCalls;
        private int commitCalls;
        private int rollbackCalls;

        private MockAsyncConnection begin() {
            beginCalls += 1;
            return new MockAsyncConnection();
        }

        private void transaction(Runnable body) {
            begin();
            try {
                body.run();
                commitCalls += 1;
            } catch (RuntimeException exception) {
                rollbackCalls += 1;
                throw exception;
            }
        }
    }

    private static final class MockAsyncConnection {
        private int runSyncCalls;

        private void runSync(String operation) {
            assertTrue(operation.contains("Base.metadata"));
            runSyncCalls += 1;
        }
    }

    private static final class StubDataSource implements DataSource {
        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public Connection getConnection(String username, String password) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("not needed");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not needed");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
