/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's {@code tests.unit_tests.core.common.clients.test_connector_pool} in
 * {@code tests/unit_tests/core/common/clients/test_connector_pool.py}.
 */
class ConnectorPoolTest {

    @Test
    void statReflectsResourceDetails() {
        MockConnectorPool pool = concretePool();
        setResourceTimes(pool, 100.0d, 100.0d);

        Map<String, Object> stats = pool.stat();

        assertFalse((Boolean) stats.get("closed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> refDetail = (Map<String, Object>) stats.get("ref_detail");
        assertEquals(100.0d, ((Number) refDetail.get("created_at")).doubleValue(), 0.0001d);
    }

    @Test
    void managerCreatesAndClosesDefaultTcpPool() {
        ConnectorPoolManager manager = new ConnectorPoolManager(5);
        ConnectorPoolConfig config = new ConnectorPoolConfig();
        config.setLimit(50);
        config.setLimitPerHost(10);

        ConnectorPool pool = manager.getConnectorPool(config).join();

        assertInstanceOf(TcpConnectorPool.class, pool);
        assertNotNull(pool.conn());

        manager.releaseConnectorPool(config);
        manager.closeConnectorPool(config, false).join();

        assertEquals(0, ((Number) manager.getStats().get("total_connector_pools")).intValue());
    }

    @Test
    void managerSupportsMultipleDistinctPools() {
        ConnectorPoolManager manager = new ConnectorPoolManager(3);
        List<ConnectorPoolConfig> configs = List.of(configWithLimit(10), configWithLimit(20), configWithLimit(30));

        for (ConnectorPoolConfig config : configs) {
            ConnectorPool pool = manager.getConnectorPool(config).join();
            assertInstanceOf(TcpConnectorPool.class, pool);
        }

        assertEquals(3, ((Number) manager.getStats().get("total_connector_pools")).intValue());

        for (ConnectorPoolConfig config : configs) {
            manager.closeConnectorPool(config, false).join();
        }

        manager.closeAll().join();
    }

    @Test
    void managerReusesSameConfigPool() {
        ConnectorPoolManager manager = new ConnectorPoolManager();
        ConnectorPoolConfig config = configWithLimit(100);

        ConnectorPool pool1 = manager.getConnectorPool(config).join();
        ConnectorPool pool2 = manager.getConnectorPool(config).join();

        assertSame(pool1, pool2);
        assertEquals(2, pool1.getRefCount());

        manager.releaseConnectorPool(config);
        manager.releaseConnectorPool(config);

        assertEquals(0, pool1.getRefCount());
        manager.closeAll().join();
    }

    private static MockConnectorPool concretePool() {
        ConnectorPoolConfig config = new ConnectorPoolConfig();
        config.setTtl(10);
        config.setMaxIdleTime(5);
        return new MockConnectorPool(config);
    }

    private static ConnectorPoolConfig configWithLimit(int limit) {
        ConnectorPoolConfig config = new ConnectorPoolConfig();
        config.setLimit(limit);
        return config;
    }

    private static void setResourceTimes(RefCountedResource resource, double createdAt, double lastUsed) {
        setDoubleField(resource, "createdAt", createdAt);
        setDoubleField(resource, "lastUsed", lastUsed);
    }

    private static void setDoubleField(RefCountedResource resource, String fieldName, double value) {
        try {
            Field field = RefCountedResource.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setDouble(resource, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to set " + fieldName + " for parity test", exception);
        }
    }

    private static final class MockConnectorPool extends ConnectorPool {

        private MockConnectorPool(ConnectorPoolConfig config) {
            super(config);
        }

        @Override
        public Object getConn() {
            return "connector";
        }

        @Override
        protected CompletableFuture<Void> doClose(Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
