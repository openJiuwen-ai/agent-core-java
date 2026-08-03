/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import com.openjiuwen.core.common.security.SslUtils;
import com.openjiuwen.core.runner.callback.AbortError;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for tool authentication.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.foundation.tool.test_auth} in
 * {@code tests/unit_tests/core/foundation/tool/test_auth.py}.</p>
 */
class ToolAuthPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/core/foundation/tool/test_auth.py";

    @TestFactory
    Collection<DynamicTest> pythonToolAuthCases() {
        return List.of(
                caseOf("TestToolAuthConfig::test_tool_auth_config_creation",
                        ToolAuthPythonParityTest::toolAuthConfigCreation),
                caseOf("TestToolAuthConfig::test_tool_auth_config_without_tool_id",
                        ToolAuthPythonParityTest::toolAuthConfigWithoutToolId),
                caseOf("TestToolAuthResult::test_tool_auth_result_creation",
                        ToolAuthPythonParityTest::toolAuthResultCreation),
                caseOf("TestToolAuthResult::test_tool_auth_result_with_error",
                        ToolAuthPythonParityTest::toolAuthResultWithError),
                caseOf("TestAuthHeaderAndQueryProvider::test_auth_provider_with_headers",
                        ToolAuthPythonParityTest::authProviderWithHeaders),
                caseOf("TestAuthHeaderAndQueryProvider::test_auth_provider_with_query_params",
                        ToolAuthPythonParityTest::authProviderWithQueryParams),
                caseOf("TestAuthHeaderAndQueryProvider::test_auth_provider_with_both",
                        ToolAuthPythonParityTest::authProviderWithBoth),
                caseOf("TestAuthHeaderAndQueryProvider::test_auth_provider_without_credentials",
                        ToolAuthPythonParityTest::authProviderWithoutCredentials),
                caseOf("TestAuthCallbacks::test_ssl_auth_handler_verify_true",
                        ToolAuthPythonParityTest::sslAuthHandlerVerifyTrue),
                caseOf("TestAuthCallbacks::test_ssl_auth_handler_verify_false",
                        ToolAuthPythonParityTest::sslAuthHandlerVerifyFalse),
                caseOf("TestAuthCallbacks::test_ssl_auth_handler_exception_handling",
                        ToolAuthPythonParityTest::sslAuthHandlerExceptionHandling),
                caseOf("TestAuthCallbacks::test_ssl_auth_handler_cert_empty",
                        ToolAuthPythonParityTest::sslAuthHandlerCertEmpty),
                caseOf("TestAuthCallbacks::test_auth_header_and_query_params_handler_with_credentials",
                        ToolAuthPythonParityTest::authHeaderAndQueryParamsHandlerWithCredentials),
                caseOf("TestAuthCallbacks::test_auth_header_and_query_params_handler_only_headers",
                        ToolAuthPythonParityTest::authHeaderAndQueryParamsHandlerOnlyHeaders),
                caseOf("TestAuthCallbacks::test_auth_header_and_query_params_handler_only_query_params",
                        ToolAuthPythonParityTest::authHeaderAndQueryParamsHandlerOnlyQueryParams),
                caseOf("TestAuthCallbacks::test_auth_header_and_query_params_handler_empty_credentials",
                        ToolAuthPythonParityTest::authHeaderAndQueryParamsHandlerEmptyCredentials),
                caseOf("TestAuthCallbacks::test_auth_handler_wrong_type",
                        ToolAuthPythonParityTest::authHandlerWrongType)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void toolAuthConfigCreation() {
        ToolAuthConfig config = new ToolAuthConfig(
                AuthType.SSL.getValue(),
                Map.of("verify_switch_env", "RESTFUL_SSL_VERIFY"),
                "restful_api",
                "test-tool-id"
        );

        assertEquals(AuthType.SSL.getValue(), config.getAuthType());
        assertEquals(Map.of("verify_switch_env", "RESTFUL_SSL_VERIFY"), config.getConfig());
        assertEquals("restful_api", config.getToolType());
        assertEquals("test-tool-id", config.getToolId());
    }

    private static void toolAuthConfigWithoutToolId() {
        ToolAuthConfig config = new ToolAuthConfig(
                "api_key",
                Map.of("api_key", "test-key"),
                "database",
                null
        );

        assertEquals("api_key", config.getAuthType());
        assertNull(config.getToolId());
    }

    private static void toolAuthResultCreation() {
        ToolAuthResult result = new ToolAuthResult(
                true,
                Map.of("headers", Map.of("Authorization", "Bearer token")),
                "Authentication successful",
                null
        );

        assertTrue(result.isSuccess());
        assertEquals(Map.of("headers", Map.of("Authorization", "Bearer token")), result.getAuthData());
        assertEquals("Authentication successful", result.getMessage());
        assertNull(result.getError());
    }

    private static void toolAuthResultWithError() {
        RuntimeException error = new RuntimeException("Authentication failed");
        ToolAuthResult result = new ToolAuthResult(false, Map.of(), "Authentication failed", error);

        assertFalse(result.isSuccess());
        assertSame(error, result.getError());
    }

    private static void authProviderWithHeaders() {
        AuthHeaderAndQueryProvider provider = new AuthHeaderAndQueryProvider(
                Map.of("Authorization", "Bearer test-token", "X-Custom", "value"),
                Map.of()
        );

        HttpRequest request = provider.apply(HttpRequest.newBuilder(), URI.create("https://example.com/api"))
                .GET()
                .build();

        assertEquals("Bearer test-token", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("value", request.headers().firstValue("X-Custom").orElseThrow());
    }

    private static void authProviderWithQueryParams() {
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("api_key", "test-key");
        queryParams.put("version", "v1");
        AuthHeaderAndQueryProvider provider = new AuthHeaderAndQueryProvider(
                Map.of(),
                queryParams
        );

        URI signed = provider.mergeQueryParams(URI.create("https://example.com/api?existing=1"));

        assertEquals("https://example.com/api?existing=1&api_key=test-key&version=v1", signed.toString());
    }

    private static void authProviderWithBoth() {
        AuthHeaderAndQueryProvider provider = new AuthHeaderAndQueryProvider(
                Map.of("Authorization", "Bearer test-token"),
                Map.of("api_key", "test-key")
        );

        HttpRequest request = provider.apply(HttpRequest.newBuilder(), URI.create("https://example.com/api"))
                .GET()
                .build();

        assertEquals("Bearer test-token", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("https://example.com/api?api_key=test-key", request.uri().toString());
    }

    private static void authProviderWithoutCredentials() {
        AuthHeaderAndQueryProvider provider = new AuthHeaderAndQueryProvider(Map.of(), Map.of());
        URI requestUri = URI.create("https://example.com/api");

        HttpRequest request = provider.apply(HttpRequest.newBuilder(), requestUri).GET().build();

        assertEquals(requestUri, request.uri());
        assertTrue(provider.getHeaders().isEmpty());
        assertTrue(provider.getQueryParams().isEmpty());
    }

    private static void sslAuthHandlerVerifyTrue() throws Throwable {
        withSslEnv(key -> "RESTFUL_SSL_CERT".equals(key) ? "D:/does-not-exist/test.pem" : null, () -> {
            ToolAuthResult result = new SSLAuthStrategy().authenticate(new ToolAuthConfig(
                    AuthType.SSL.getValue(),
                    Map.of("verify_switch_env", "RESTFUL_SSL_VERIFY", "ssl_cert_env", "RESTFUL_SSL_CERT"),
                    "restful_api",
                    "test-tool"
            ));

            assertTrue(result.isSuccess());
            SslAuthConnector connector = assertInstanceOf(
                    SslAuthConnector.class,
                    result.getAuthData().get("connector")
            );
            assertTrue(connector.isVerifySsl());
            assertEquals("D:/does-not-exist/test.pem", connector.getSslCertPath());
            assertNotNull(connector.getSslContext());
        });
    }

    private static void sslAuthHandlerVerifyFalse() throws Throwable {
        withSslEnv(key -> "RESTFUL_SSL_VERIFY".equals(key) ? "false" : null, () -> {
            ToolAuthResult result = new SSLAuthStrategy().authenticate(new ToolAuthConfig(
                    AuthType.SSL.getValue(),
                    Map.of("verify_switch_env", "RESTFUL_SSL_VERIFY", "ssl_cert_env", "RESTFUL_SSL_CERT"),
                    "restful_api",
                    null
            ));

            SslAuthConnector connector = assertInstanceOf(
                    SslAuthConnector.class,
                    result.getAuthData().get("connector")
            );
            assertFalse(connector.isVerifySsl());
            assertEquals(Boolean.FALSE, connector.sslValue());
        });
    }

    private static void sslAuthHandlerExceptionHandling() throws Throwable {
        RuntimeException originalError = new RuntimeException("SSL config error");
        withSslEnv(key -> {
            throw originalError;
        }, () -> {
            AbortError error = assertThrows(AbortError.class, () -> new SSLAuthStrategy().authenticate(
                    new ToolAuthConfig(
                            AuthType.SSL.getValue(),
                            Map.of("verify_switch_env", "RESTFUL_SSL_VERIFY", "ssl_cert_env", "RESTFUL_SSL_CERT"),
                            "restful_api",
                            null
                    )
            ));

            assertTrue(error.getReason().contains("Failed to create SSL connector"));
            assertTrue(error.getReason().contains("SSL config error"));
            assertSame(originalError, error.getCause());
        });
    }

    private static void sslAuthHandlerCertEmpty() throws Throwable {
        withSslEnv(key -> null, () -> {
            AbortError error = assertThrows(AbortError.class, () -> new SSLAuthStrategy().authenticate(
                    new ToolAuthConfig(AuthType.SSL.getValue(), Map.of(), "restful_api", null)
            ));

            assertTrue(error.getReason().contains("must provide ssl cert"));
        });
    }

    private static void authHeaderAndQueryParamsHandlerWithCredentials() {
        ToolAuthResult result = new HeaderQueryAuthStrategy().authenticate(new ToolAuthConfig(
                AuthType.HEADER_AND_QUERY.getValue(),
                Map.of(
                        "auth_headers", Map.of("Authorization", "Bearer test-token"),
                        "auth_query_params", Map.of("api_key", "test-key")
                ),
                "mcp",
                "test-mcp"
        ));

        AuthHeaderAndQueryProvider provider = assertProvider(result);
        assertEquals(Map.of("Authorization", "Bearer test-token"), provider.getHeaders());
        assertEquals(Map.of("api_key", "test-key"), provider.getQueryParams());
    }

    private static void authHeaderAndQueryParamsHandlerOnlyHeaders() {
        ToolAuthResult result = new HeaderQueryAuthStrategy().authenticate(new ToolAuthConfig(
                AuthType.HEADER_AND_QUERY.getValue(),
                Map.of("auth_headers", Map.of("Authorization", "Bearer test-token")),
                "mcp",
                null
        ));

        AuthHeaderAndQueryProvider provider = assertProvider(result);
        assertEquals(Map.of("Authorization", "Bearer test-token"), provider.getHeaders());
        assertEquals(Map.of(), provider.getQueryParams());
    }

    private static void authHeaderAndQueryParamsHandlerOnlyQueryParams() {
        ToolAuthResult result = new HeaderQueryAuthStrategy().authenticate(new ToolAuthConfig(
                AuthType.HEADER_AND_QUERY.getValue(),
                Map.of("auth_query_params", Map.of("api_key", "test-key")),
                "mcp",
                null
        ));

        AuthHeaderAndQueryProvider provider = assertProvider(result);
        assertEquals(Map.of(), provider.getHeaders());
        assertEquals(Map.of("api_key", "test-key"), provider.getQueryParams());
    }

    private static void authHeaderAndQueryParamsHandlerEmptyCredentials() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("auth_headers", null);
        config.put("auth_query_params", null);

        ToolAuthResult result = new HeaderQueryAuthStrategy().authenticate(new ToolAuthConfig(
                AuthType.HEADER_AND_QUERY.getValue(),
                config,
                "mcp",
                null
        ));

        assertTrue(result.isSuccess());
        assertTrue(result.getAuthData().containsKey("auth_provider"));
        assertNull(result.getAuthData().get("auth_provider"));
    }

    private static void authHandlerWrongType() {
        ToolAuthResult result = AuthStrategyRegistry.executeAuth(new ToolAuthConfig(
                "api_key",
                Map.of(),
                "mcp",
                null
        ));

        assertFalse(result.isSuccess());
        assertEquals(Map.of(), result.getAuthData());
    }

    private static AuthHeaderAndQueryProvider assertProvider(ToolAuthResult result) {
        assertTrue(result.isSuccess());
        return assertInstanceOf(AuthHeaderAndQueryProvider.class, result.getAuthData().get("auth_provider"));
    }

    private static void withSslEnv(Function<String, String> reader, Executable executable) throws Throwable {
        Method setMethod = SslUtils.class.getDeclaredMethod("setEnvReaderForTests", Function.class);
        Method resetMethod = SslUtils.class.getDeclaredMethod("resetEnvReaderForTests");
        setMethod.setAccessible(true);
        resetMethod.setAccessible(true);
        setMethod.invoke(null, reader);
        try {
            executable.execute();
        } finally {
            resetMethod.invoke(null);
        }
    }
}
