/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import com.openjiuwen.core.runner.callback.AbortError;
import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.EventFilter;
import com.openjiuwen.core.runner.callback.ToolCallEvents;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthCallbackTest {

    @Test
    void authTypeResolvesPythonEnumValues() {
        assertEquals(AuthType.SSL, AuthType.fromValue("ssl"));
        assertEquals(AuthType.HEADER_AND_QUERY, AuthType.fromValue("header_and_query"));
        assertNull(AuthType.fromValue("api_key"));
    }

    @Test
    void providerAddsHeadersToRequestBuilder() {
        AuthHeaderAndQueryProvider provider = new AuthHeaderAndQueryProvider(
                Map.of("Authorization", "Bearer test-token", "X-Custom", "value"),
                Map.of()
        );

        HttpRequest request = provider.apply(
                HttpRequest.newBuilder(),
                URI.create("https://example.com/api")
        ).GET().build();

        assertEquals("Bearer test-token", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("value", request.headers().firstValue("X-Custom").orElseThrow());
        assertEquals(URI.create("https://example.com/api"), request.uri());
    }

    @Test
    void providerMergesQueryParamsWithExistingQuery() {
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("api_key", "test-key");
        queryParams.put("version", "v1");
        AuthHeaderAndQueryProvider provider = new AuthHeaderAndQueryProvider(
                Map.of(),
                queryParams
        );

        URI merged = provider.mergeQueryParams(URI.create("https://example.com/api?existing=1"));

        assertEquals("https://example.com/api?existing=1&api_key=test-key&version=v1", merged.toString());
    }

    @Test
    void providerAppliesHeadersAndQueryParamsTogether() {
        AuthHeaderAndQueryProvider provider = new AuthHeaderAndQueryProvider(
                Map.of("Authorization", "Bearer test-token"),
                Map.of("api_key", "test-key")
        );

        HttpRequest request = provider.apply(
                HttpRequest.newBuilder(),
                URI.create("https://example.com/api")
        ).GET().build();

        assertEquals("Bearer test-token", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("https://example.com/api?api_key=test-key", request.uri().toString());
    }

    @Test
    void providerWithoutCredentialsLeavesUriUnchanged() {
        AuthHeaderAndQueryProvider provider = new AuthHeaderAndQueryProvider(Map.of(), Map.of());
        URI uri = URI.create("https://example.com/api");

        assertEquals(uri, provider.mergeQueryParams(uri));
        assertTrue(provider.getHeaders().isEmpty());
        assertTrue(provider.getQueryParams().isEmpty());
    }

    @Test
    void headerQueryStrategyCreatesProviderWithCredentials() {
        ToolAuthResult result = new HeaderQueryAuthStrategy().authenticate(new ToolAuthConfig(
                "header_and_query",
                Map.of(
                        "auth_headers", Map.of("Authorization", "Bearer test-token"),
                        "auth_query_params", Map.of("api_key", "test-key")
                ),
                "mcp",
                "test-mcp"
        ));

        assertTrue(result.isSuccess());
        AuthHeaderAndQueryProvider provider = assertInstanceOf(
                AuthHeaderAndQueryProvider.class,
                result.getAuthData().get("auth_provider")
        );
        assertEquals(Map.of("Authorization", "Bearer test-token"), provider.getHeaders());
        assertEquals(Map.of("api_key", "test-key"), provider.getQueryParams());
    }

    @Test
    void headerQueryStrategyCreatesProviderWithOnlyHeaders() {
        ToolAuthResult result = new HeaderQueryAuthStrategy().authenticate(new ToolAuthConfig(
                "header_and_query",
                Map.of("auth_headers", Map.of("Authorization", "Bearer test-token")),
                "mcp",
                null
        ));

        AuthHeaderAndQueryProvider provider = assertProvider(result);
        assertEquals(Map.of("Authorization", "Bearer test-token"), provider.getHeaders());
        assertTrue(provider.getQueryParams().isEmpty());
    }

    @Test
    void headerQueryStrategyCreatesProviderWithOnlyQueryParams() {
        ToolAuthResult result = new HeaderQueryAuthStrategy().authenticate(new ToolAuthConfig(
                "header_and_query",
                Map.of("auth_query_params", Map.of("api_key", "test-key")),
                "mcp",
                null
        ));

        AuthHeaderAndQueryProvider provider = assertProvider(result);
        assertTrue(provider.getHeaders().isEmpty());
        assertEquals(Map.of("api_key", "test-key"), provider.getQueryParams());
    }

    @Test
    void headerQueryStrategyReturnsNullProviderForExplicitNullCredentials() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("auth_headers", null);
        config.put("auth_query_params", null);

        ToolAuthResult result = new HeaderQueryAuthStrategy().authenticate(new ToolAuthConfig(
                "header_and_query",
                config,
                "mcp",
                null
        ));

        assertTrue(result.isSuccess());
        assertTrue(result.getAuthData().containsKey("auth_provider"));
        assertNull(result.getAuthData().get("auth_provider"));
    }

    @Test
    void toolAuthConfigPreservesNullConfigValues() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("auth_headers", null);

        ToolAuthConfig authConfig = new ToolAuthConfig("header_and_query", config, "mcp", null);

        assertTrue(authConfig.getConfig().containsKey("auth_headers"));
        assertNull(authConfig.getConfig().get("auth_headers"));
    }

    @Test
    void toolAuthResultPreservesNullAuthDataValues() {
        Map<String, Object> authData = new LinkedHashMap<>();
        authData.put("auth_provider", null);

        ToolAuthResult result = new ToolAuthResult(true, authData, "", null);

        assertTrue(result.getAuthData().containsKey("auth_provider"));
        assertNull(result.getAuthData().get("auth_provider"));
    }

    @Test
    void registryReturnsFailureForUnsupportedType() {
        ToolAuthResult result = AuthStrategyRegistry.executeAuth(new ToolAuthConfig(
                "api_key",
                Map.of(),
                "mcp",
                null
        ));

        assertFalse(result.isSuccess());
        assertEquals(Map.of(), result.getAuthData());
        assertTrue(result.getMessage().contains("Unsupported auth type: api_key"));
    }

    @Test
    void unifiedHandlerDelegatesToRegistry() {
        ToolAuthResult result = AuthCallback.unifiedAuthHandler(new ToolAuthConfig(
                "header_and_query",
                Map.of("auth_query_params", Map.of("api_key", "test-key")),
                "mcp",
                null
        ));

        AuthHeaderAndQueryProvider provider = assertProvider(result);
        assertEquals(Map.of("api_key", "test-key"), provider.getQueryParams());
    }

    @Test
    void registerWithFrameworkRegistersToolAuthCallback() {
        CapturingFramework framework = new CapturingFramework();

        AuthCallback.registerWith(framework);

        assertEquals(ToolCallEvents.TOOL_AUTH, framework.event);
        assertEquals("unified_auth_handler", framework.callbackType);
        ToolAuthResult result = assertInstanceOf(ToolAuthResult.class, framework.callback.apply(Map.of(
                "auth_config",
                new ToolAuthConfig("header_and_query", Map.of("auth_headers", Map.of("Authorization", "Bearer x")),
                        "mcp", null)
        )));
        assertTrue(result.isSuccess());
    }

    @Test
    void sslStrategyCreatesDisabledConnectorForHttpUrl() {
        ToolAuthResult result = new SSLAuthStrategy().authenticate(new ToolAuthConfig(
                "ssl",
                Map.of("url", "http://example.com"),
                "restful_api",
                "test-tool"
        ));

        SslAuthConnector connector = assertInstanceOf(SslAuthConnector.class, result.getAuthData().get("connector"));
        assertFalse(connector.isVerifySsl());
        assertEquals(Boolean.FALSE, connector.sslValue());
    }

    @Test
    void sslStrategyRaisesAbortErrorWhenDefaultHttpsLacksCert() {
        AbortError error = assertThrows(AbortError.class, () -> new SSLAuthStrategy().authenticate(new ToolAuthConfig(
                "ssl",
                Map.of(),
                "restful_api",
                "test-tool"
        )));

        assertTrue(error.getReason().contains("Failed to create SSL connector"));
        assertTrue(error.getReason().contains("must provide ssl cert"));
    }

    @Test
    void sslStrategyKeepsConfiguredEnvNamesInFailurePath() {
        AbortError error = assertThrows(AbortError.class, () -> new SSLAuthStrategy().authenticate(new ToolAuthConfig(
                "ssl",
                Map.of("verify_switch_env", "RESTFUL_SSL_VERIFY", "ssl_cert_env", "RESTFUL_SSL_CERT"),
                "restful_api",
                null
        )));

        assertTrue(error.getReason().contains("RESTFUL_SSL_VERIFY"));
        assertTrue(error.getReason().contains("RESTFUL_SSL_CERT"));
    }

    @Test
    void callbackRejectsMissingAuthConfig() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> AuthCallback.unifiedAuthHandler(Map.of())
        );

        assertEquals("auth_config is required for tool authentication", error.getMessage());
    }

    @Test
    void providerMapsAreImmutableCopies() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer before");
        AuthHeaderAndQueryProvider provider = new AuthHeaderAndQueryProvider(headers, Map.of());
        headers.put("Authorization", "Bearer after");

        assertEquals("Bearer before", provider.getHeaders().get("Authorization"));
        assertDoesNotThrow(() -> assertNotNull(provider.getHeaders()));
    }

    private static AuthHeaderAndQueryProvider assertProvider(ToolAuthResult result) {
        assertTrue(result.isSuccess());
        return assertInstanceOf(AuthHeaderAndQueryProvider.class, result.getAuthData().get("auth_provider"));
    }

    private static final class CapturingFramework implements DecoratorFramework {
        private String event;
        private Function<Map<String, Object>, Object> callback;
        private String callbackType;

        @Override
        public CallbackInfo registerSync(String event, Function<Map<String, Object>, Object> callback, int priority,
                                         boolean once, String namespace, Set<String> tags, List<EventFilter> filters,
                                         Function<Map<String, Object>, Object> rollbackHandler,
                                         Function<Map<String, Object>, Object> errorHandler, int maxRetries,
                                         double retryDelay, Double timeout, String callbackType) {
            this.event = event;
            this.callback = callback;
            this.callbackType = callbackType;
            return CallbackInfo.builder().callback(callback).callbackType(callbackType).build();
        }

        @Override
        public void trigger(String event, Object[] args, Map<String, Object> kwargs) {
        }

        @Override
        public Object triggerTransform(String event, Object[] args, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public Map<String, List<CallbackInfo>> getCallbacks() {
            return Map.of(event, new ArrayList<>());
        }
    }
}
