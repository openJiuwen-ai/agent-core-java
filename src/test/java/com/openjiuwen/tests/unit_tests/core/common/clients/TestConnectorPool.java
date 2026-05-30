/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.common.clients;

import com.openjiuwen.core.common.clients.ConnectorPool;
import com.openjiuwen.core.common.clients.ConnectorPoolConfig;
import com.openjiuwen.core.common.clients.ConnectorPoolManager;
import com.openjiuwen.core.common.clients.TcpConnectorPool;
import com.openjiuwen.core.common.clients.RefCountedResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's {@code tests.unit_tests.core.common.clients.test_connector_pool}.
 */
@Tag("unit-test")
class TestConnectorPool {

    @Test
    @Tag("level0")
    @DisplayName("Test stat")
    void testStat() {
        MockConnectorPool pool = concretePool();
        pool.setCreatedAt(100);

        Map<String, Object> stats = pool.stat();

        assertFalse((Boolean) stats.get("closed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> refDetail = (Map<String, Object>) stats.get("ref_detail");
        assertEquals(Instant.ofEpochSecond(100).toString(), refDetail.get("created_at"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test with connector pool manager")
    void testWithConnectorPoolManager() {
        ConnectorPoolManager manager = new ConnectorPoolManager(5);
        ConnectorPoolConfig config = new ConnectorPoolConfig();
        config.setLimit(50);
        config.setLimitPerHost(10);

        ConnectorPool pool = manager.getConnectorPool("default", config).join();

        assertInstanceOf(TcpConnectorPool.class, pool);
        assertNotNull(pool.conn());
        assertNotNull(pool.getStat());

        manager.releaseConnectorPool(config);
        manager.closeConnectorPool(config, false).join();

        assertEquals(0, manager.getStats().get("total_connector_pools"));
        assertEquals(true, pool.isClosed());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test multiple pools with manager")
    void testMultiplePoolsWithManager() {
        ConnectorPoolManager manager = new ConnectorPoolManager(3);
        List<ConnectorPoolConfig> configs = List.of(configWithLimit(10), configWithLimit(20), configWithLimit(30));
        List<ConnectorPool> pools = new ArrayList<>();

        for (ConnectorPoolConfig config : configs) {
            pools.add(manager.getConnectorPool("default", config).join());
        }

        for (ConnectorPool pool : pools) {
            assertInstanceOf(TcpConnectorPool.class, pool);
        }
        assertEquals(3, manager.getStats().get("total_connector_pools"));

        manager.closeAll().join();
    }

    @Test
    @Tag("level0")
    @DisplayName("Test reuse same config")
    void testReuseSameConfig() {
        ConnectorPoolManager manager = new ConnectorPoolManager();
        ConnectorPoolConfig config = configWithLimit(100);

        ConnectorPool pool1 = manager.getConnectorPool("default", config).join();
        ConnectorPool pool2 = manager.getConnectorPool("default", config).join();

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

    private static class MockConnectorPool extends ConnectorPool {
        private boolean closedFlag;

        MockConnectorPool(ConnectorPoolConfig config) {
            super(config);
        }

        void setCreatedAt(long epochSeconds) {
            try {
                Field field = RefCountedResource.class.getDeclaredField("createdAt");
                field.setAccessible(true);
                field.set(this, Instant.ofEpochSecond(epochSeconds));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Unable to set createdAt for parity test", e);
            }
        }

        @Override
        public Object getConn() {
            return "connector";
        }

        @Override
        protected CompletableFuture<Void> doClose() {
            closedFlag = true;
            return CompletableFuture.completedFuture(null);
        }
    }
}
