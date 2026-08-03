/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients.http;

import com.openjiuwen.core.common.clients.BaseRefResourceMgr;
import com.openjiuwen.core.common.clients.Clients;
import com.openjiuwen.core.common.clients.ConnectorPoolConfig;
import com.openjiuwen.core.common.clients.SessionConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's HTTP client/session behavior in
 * {@code openjiuwen/core/common/clients/http_client.py}.
 */
class HttpClientTest {

    @Test
    void sessionConfigParsesDictAndGeneratesStableKey() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("headers", Map.of("b", "2", "a", "1"));
        raw.put("timeout", 10);
        raw.put("connect_timeout", "2.5");
        raw.put("timeout_args", Map.of("sock_read_timeout", "3", "ceil_threshold_timeout", "5"));
        raw.put("raise_for_status", true);
        raw.put("trust_env", false);
        raw.put("extend_args", Map.of("z", 1, "a", 2));
        raw.put("connector_pool_config", Map.of("limit", 3, "limit_per_host", 2));

        SessionConfig config = new SessionConfig(raw);
        String firstKey = config.generateKey();
        String secondKey = config.generateKey();

        assertThat(config.getHeaders()).containsEntry("a", "1").containsEntry("b", "2");
        assertThat(config.getTimeout()).isEqualTo(10.0d);
        assertThat(config.getConnectTimeout()).isEqualTo(2.5d);
        assertThat(config.isRaiseForStatus()).isTrue();
        assertThat(config.isTrustEnv()).isFalse();
        assertThat(config.getConnectorPoolConfig().getLimit()).isEqualTo(3);
        assertThat(config.getConnectorPoolConfig().getLimitPerHost()).isEqualTo(2);
        assertThat(firstKey).isEqualTo(secondKey);
        assertThat(firstKey).contains("raise_for_status:true");
    }

    @Test
    void httpSessionExposesConfigAndRejectsSessionAfterClose() {
        SessionConfig config = new SessionConfig();
        HttpSession session = new HttpSession(java.net.http.HttpClient.newHttpClient(), config);

        assertThat(session.config()).isSameAs(config);
        assertThat(session.session()).isNotNull();
        session.close().join();

        assertThat(session.isClosed()).isTrue();
        assertThatThrownBy(session::session)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Session is closed");
    }

    @Test
    void sessionManagerReusesSessionsByConfigKey() {
        HttpSessionManager manager = new HttpSessionManager();
        SessionConfig config = new SessionConfig(Map.of("timeout", 1));

        BaseRefResourceMgr.ResourceLease<HttpSession> first = manager.acquire(config).join();
        BaseRefResourceMgr.ResourceLease<HttpSession> second = manager.acquire(config).join();

        assertThat(first.isNew()).isTrue();
        assertThat(second.isNew()).isFalse();
        assertThat(second.resource()).isSameAs(first.resource());
        assertThat(second.resource().getRefCount()).isEqualTo(2);
    }

    @Test
    void httpClientMergesRequestKwargsAndClosesReusableSessionState() {
        SessionConfig config = new SessionConfig();
        config.setHeaders(Map.of("Base", "1"));
        HttpClient client = new HttpClient(config, true);

        Map<String, Object> kwargs = client.buildRequestKwargs(
                Map.of("Request", "2"),
                3.0d,
                null,
                Map.of("allow_redirects", false));

        Map<?, ?> mergedHeaders = (Map<?, ?>) kwargs.get("headers");
        assertThat(mergedHeaders.get("Base")).isEqualTo("1");
        assertThat(mergedHeaders.get("Request")).isEqualTo("2");
        assertThat(kwargs).containsEntry("allow_redirects", false);
        assertThat(((Map<?, ?>) kwargs.get("timeout")).get("total")).isEqualTo(3.0d);
        assertThat(client.close().join()).isTrue();
        assertThat(client.isClosed()).isTrue();
    }

    @Test
    void clientsFacadePromotesHttpSessionExports() {
        assertThat(Clients.typedExportNames()).contains("SessionConfig", "get_http_session_manager");
        assertThat(Clients.sessionConfigClass()).isEqualTo(SessionConfig.class);
        assertThat(Clients.getHttpSessionManager()).isSameAs(HttpSessionManager.getHttpSessionManager());
        assertThat(Clients.futureTypedExportNames()).isEmpty();
    }

    @Test
    void requestOptionsPreservePythonDefaultsAndModifiers() {
        HttpClient.RequestOptions options = HttpClient.RequestOptions.defaults()
                .withParams(Map.of("q", "openjiuwen"))
                .withHeaders(Map.of("Accept", "application/json"))
                .withTimeout(5.0d)
                .withJson(Map.of("name", "agent"));

        assertThat(options.chunked()).isFalse();
        assertThat(options.chunkSize()).isEqualTo(1024);
        assertThat(options.responseBytesSizeLimit()).isEqualTo(10 * 1024 * 1024);
        assertThat(options.params().get("q")).isEqualTo("openjiuwen");
        assertThat(options.headers().get("Accept")).isEqualTo("application/json");
        assertThat(options.timeout()).isEqualTo(5.0d);
        assertThat(((Map<?, ?>) options.json()).get("name")).isEqualTo("agent");
    }
}
