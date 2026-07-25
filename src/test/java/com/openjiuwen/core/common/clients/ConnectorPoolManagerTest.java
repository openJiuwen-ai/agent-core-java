package com.openjiuwen.core.common.clients;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorPoolManagerTest {

    @AfterEach
    void tearDown() throws Exception {
        ConnectorPoolManager.getInstance().closeAll();
    }

    @Test
    void shouldReusePoolForSameConfig() throws Exception {
        ConnectorPoolManager manager = ConnectorPoolManager.getInstance();
        ConnectorPoolConfig config = new ConnectorPoolConfig();

        ConnectorPool first = manager.getConnectorPool("default", config).join();
        ConnectorPool second = manager.getConnectorPool("default", config).join();

        assertThat(second).isSameAs(first);
        assertThat(first.getRefCount()).isEqualTo(2);

        manager.releaseConnectorPool(config);
        assertThat(first.getRefCount()).isEqualTo(1);
        manager.releaseConnectorPool(config);

        assertThat(first.isClosed()).isTrue();
    }

    @Test
    void shouldUseHttpxPoolForProxyConfig() throws Exception {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("limit", 100);
        values.put("limit_per_host", 30);
        values.put("ssl_verify", false);
        values.put("force_close", false);
        values.put("keepalive_timeout", 60.0);
        values.put("ttl", 3600);
        values.put("max_idle_time", 300);
        values.put("max_keepalive_connections", 20);
        values.put("proxy", "http://127.0.0.1:8080");
        values.put("need_async", true);
        ConnectorPool pool = ConnectorPoolManager.getInstance().getConnectorPool(
                "httpx",
                new HttpXConnectorPoolConfig(values)
        ).join();

        assertThat(pool).isInstanceOf(HttpXConnectorPool.class);
        assertThat(pool.conn()).isInstanceOf(java.net.http.HttpClient.class);
    }
}
