
package com.openjiuwen.core.common.clients;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConnectorPoolManagerTest {
    @AfterEach
    void tearDown() throws Exception {
        ConnectorPoolManager.getInstance().resetForTests();
    }

    @Test
    void shouldReusePoolForSameConfig() throws Exception {
        ConnectorPoolManager manager = ConnectorPoolManager.getInstance();
        ConnectorPoolConfig config = new ConnectorPoolConfig();

        ConnectorPool first = manager.getConnectorPool("default", config);
        ConnectorPool second = manager.getConnectorPool("default", config);

        assertThat(second).isSameAs(first);
        assertThat(first.getRefCount()).isEqualTo(2);

        manager.releaseConnectorPool("default", config);
        assertThat(first.getRefCount()).isEqualTo(1);
        manager.releaseConnectorPool("default", config);

        assertThat(first.isClosed()).isTrue();
    }

    @Test
    void shouldUseHttpxPoolForProxyConfig() throws Exception {
        ConnectorPool pool =
            ConnectorPoolManager.getInstance().getConnectorPool("httpx", new HttpXConnectorPoolConfig(100, 30, false,
                    null, false, 60.0, 3600, 300, java.util.Map.of(), 20, null, "http://127.0.0.1:8080", true));

        assertThat(pool).isInstanceOf(HttpXConnectorPool.class);
        assertThat(pool.conn()).isInstanceOf(java.net.http.HttpClient.class);
    }
}
