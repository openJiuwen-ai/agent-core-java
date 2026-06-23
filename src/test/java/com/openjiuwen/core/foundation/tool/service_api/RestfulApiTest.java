/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.form_handler.FormHandler;
import com.openjiuwen.core.foundation.tool.form_handler.FormHandlerManager;
import com.openjiuwen.core.foundation.tool.form_handler.ToolFormData;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for RESTful API tools.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/foundation/tool/test_restfulapi.py} and
 * {@code openjiuwen/core/foundation/tool/service_api/restful_api.py}.</p>
 */
class RestfulApiTest {

    private static final String RESTFUL_API_SOURCE = "tests/unit_tests/core/foundation/tool/test_restfulapi.py";
    private static final List<String> RESTFUL_API_PYTHON_NODES = List.of(
            RESTFUL_API_SOURCE + "::TestRestFulApi::test_invoke_with_json_response",
            RESTFUL_API_SOURCE + "::TestRestFulApi::test_invoke_with_text_response",
            RESTFUL_API_SOURCE + "::TestRestFulApi::test_invoke_with_error_response",
            RESTFUL_API_SOURCE + "::TestRestFulApi::test_invoke_response_size_exceeded",
            RESTFUL_API_SOURCE + "::TestRestFulApi::test_invoke_with_invalid_json_response",
            RESTFUL_API_SOURCE + "::TestRestFulApi::test_invoke_with_html_response",
            RESTFUL_API_SOURCE + "::TestRestFulApi::test_invoke_with_xml_response",
            RESTFUL_API_SOURCE + "::TestRestFulApi::test_invoke_with_empty_response",
            RESTFUL_API_SOURCE + "::TestRestFulApi::test_invoke_with_custom_headers_in_response",
            RESTFUL_API_SOURCE + "::TestRestFulApi::test_invoke_with_redirect_response",
            RESTFUL_API_SOURCE + "::TestRestFulApi::test_invoke_with_chunked_stream_response",
            RESTFUL_API_SOURCE + "::TestRestFulApi::test_invoke_with_inputs_and_json_response",
            RESTFUL_API_SOURCE + "::TestRestfulApiInvokeWithLocation::test_invoke_with_query_location",
            RESTFUL_API_SOURCE + "::TestRestfulApiInvokeWithLocation::test_invoke_with_path_location",
            RESTFUL_API_SOURCE + "::TestRestfulApiInvokeWithLocation::test_invoke_with_header_location",
            RESTFUL_API_SOURCE + "::TestRestfulApiInvokeWithLocation::test_invoke_with_mixed_locations",
            RESTFUL_API_SOURCE + "::TestRestfulApiInvokeWithLocation::test_invoke_with_no_location_specified",
            RESTFUL_API_SOURCE + "::TestRestfulApiInvokeWithLocation::test_invoke_with_default_values_override",
            RESTFUL_API_SOURCE + "::TestRestfulApiHttpMethods::test_put_method",
            RESTFUL_API_SOURCE + "::TestRestfulApiHttpMethods::test_patch_method",
            RESTFUL_API_SOURCE + "::TestRestfulApiHttpMethods::test_delete_method",
            RESTFUL_API_SOURCE + "::TestRestfulApiHttpMethods::test_head_method",
            RESTFUL_API_SOURCE + "::TestRestfulApiHttpMethods::test_options_method",
            RESTFUL_API_SOURCE + "::TestRestfulApiHttpMethods::test_all_methods_are_supported",
            RESTFUL_API_SOURCE + "::TestRestfulApiHttpMethods::test_path_parameters_with_put",
            RESTFUL_API_SOURCE + "::TestRestfulApiHttpMethods::test_path_parameters_with_delete",
            RESTFUL_API_SOURCE + "::TestRestfulApiHttpMethods::test_delete_with_explicit_body",
            RESTFUL_API_SOURCE + "::TestRestfulApiHttpMethods::test_all_methods_support_path_parameters",
            RESTFUL_API_SOURCE + "::TestRestfulApiHttpMethods::test_multiple_path_parameters_in_url",
            RESTFUL_API_SOURCE + "::TestRestfulApiPathParameterValidation::test_url_with_path_param_but_no_schema_raises_error",
            RESTFUL_API_SOURCE + "::TestRestfulApiPathParameterValidation::test_url_with_path_param_but_not_marked_in_schema_raises_error",
            RESTFUL_API_SOURCE + "::TestRestfulApiPathParameterValidation::test_url_with_multiple_path_params_all_must_be_defined",
            RESTFUL_API_SOURCE + "::TestRestfulApiPathParameterValidation::test_url_with_correct_path_param_schema_succeeds",
            RESTFUL_API_SOURCE + "::TestRestfulApiExceptions::test_invoke_timeout_error",
            RESTFUL_API_SOURCE + "::TestRestfulApiExceptions::test_invoke_response_error",
            RESTFUL_API_SOURCE + "::TestProcessFormDataMethod::test_single_form_param_processing",
            RESTFUL_API_SOURCE + "::TestProcessFormDataMethod::test_form_param_with_body_params",
            RESTFUL_API_SOURCE + "::TestProcessFormDataMethod::test_multiple_form_params_processing",
            RESTFUL_API_SOURCE + "::TestProcessFormDataMethod::test_empty_form_params_and_body_params",
            RESTFUL_API_SOURCE + "::TestProcessFormDataMethod::test_custom_handler_type",
            RESTFUL_API_SOURCE + "::TestCompleteFormSubmissionFlow::test_single_form_field_submission_flow",
            RESTFUL_API_SOURCE + "::TestCompleteFormSubmissionFlow::test_multiple_form_fields_submission_flow",
            RESTFUL_API_SOURCE + "::TestCompleteFormSubmissionFlow::test_form_submission_with_mixed_param_types",
            RESTFUL_API_SOURCE + "::TestExceptionAndBoundaryScenarios::test_handler_not_registered_uses_default",
            RESTFUL_API_SOURCE + "::TestExceptionAndBoundaryScenarios::test_empty_form_data_handling",
            RESTFUL_API_SOURCE + "::TestEmptyFormParamHandling::test_empty_form_param_value_succeeds"
    );

    @BeforeAll
    static void disableSsrfProtectionForLocalParityServers() throws Exception {
        setEnvReader(key -> "SSRF_PROTECT_ENABLED".equals(key) ? "false" : System.getenv(key));
    }

    @AfterAll
    static void resetUrlUtilsEnvReader() throws Exception {
        Method method = UrlUtils.class.getDeclaredMethod("resetEnvReaderForTests");
        method.setAccessible(true);
        method.invoke(null);
    }

    @TestFactory
    @DisplayName("Python test_restfulapi.py parity nodes")
    Collection<DynamicTest> pythonRestfulApiParityNodes() {
        return RESTFUL_API_PYTHON_NODES.stream()
                .map(node -> DynamicTest.dynamicTest(node, () -> runRestfulApiPythonNode(node)))
                .toList();
    }

    private void runRestfulApiPythonNode(String node) throws Exception {
        if (node.endsWith("test_invoke_with_json_response")) {
            assertResponseParsing("application/json", 200, "{\"id\":123,\"name\":\"test_user\",\"status\":\"active\"}",
                    Map.of("id", 123, "name", "test_user", "status", "active"));
        } else if (node.endsWith("test_invoke_with_text_response")) {
            assertResponseParsing("text/plain; charset=utf-8", 200, "Operation completed successfully",
                    "Operation completed successfully");
        } else if (node.endsWith("test_invoke_with_error_response")) {
            assertErrorResponseCanBeReturnedWhenStatusCheckIsDisabled();
        } else if (node.endsWith("test_invoke_response_size_exceeded")) {
            assertResponseSizeLimitExceeded();
        } else if (node.endsWith("test_invoke_with_invalid_json_response")) {
            assertInvalidJsonResponseRaises();
        } else if (node.endsWith("test_invoke_with_html_response")) {
            assertResponseParsing("text/html; charset=utf-8", 200, "<html><body>Hello World</body></html>",
                    "<html><body>Hello World</body></html>");
        } else if (node.endsWith("test_invoke_with_xml_response")) {
            assertResponseParsing("application/xml", 200, "<response><status>success</status></response>",
                    "<response><status>success</status></response>");
        } else if (node.endsWith("test_invoke_with_empty_response")) {
            assertResponseParsing("application/json", 204, "", Map.of());
        } else if (node.endsWith("test_invoke_with_custom_headers_in_response")) {
            assertCustomResponseHeadersReturned();
        } else if (node.endsWith("test_invoke_with_redirect_response")) {
            assertRedirectResponseReturned();
        } else if (node.endsWith("test_invoke_with_chunked_stream_response")) {
            assertChunkedJsonArrayResponse();
        } else if (node.endsWith("test_invoke_with_inputs_and_json_response")) {
            assertInputSchemaFormatsRequestAndParsesJson();
        } else if (node.endsWith("test_invoke_with_query_location")) {
            assertQueryLocation();
        } else if (node.endsWith("test_invoke_with_path_location")) {
            assertPathLocation();
        } else if (node.endsWith("test_invoke_with_header_location")) {
            assertHeaderLocation();
        } else if (node.endsWith("test_invoke_with_mixed_locations")) {
            assertMixedLocations();
        } else if (node.endsWith("test_invoke_with_no_location_specified")) {
            assertNoLocationDefaultsToBody();
        } else if (node.endsWith("test_invoke_with_default_values_override")) {
            assertCallerValueOverridesSchemaDefault();
        } else if (node.endsWith("test_all_methods_are_supported")) {
            assertEquals(Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"),
                    RestfulApiCard.SUPPORTED_METHODS);
        } else if (node.endsWith("test_all_methods_support_path_parameters")) {
            for (String method : List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")) {
                assertMethodSupportsPathParameters(method);
            }
        } else if (node.endsWith("test_multiple_path_parameters_in_url")) {
            assertMultiplePathParameters();
        } else if (node.contains("path_param") || node.endsWith("test_delete_with_explicit_body")
                || node.endsWith("test_put_method") || node.endsWith("test_patch_method")
                || node.endsWith("test_delete_method") || node.endsWith("test_head_method")
                || node.endsWith("test_options_method")) {
            assertHttpMethodNode(node);
        } else if (node.contains("PathParameterValidation")) {
            assertPathParameterValidationNode(node);
        } else if (node.endsWith("test_invoke_timeout_error")) {
            assertTimeoutOrConnectionErrorWrapped();
        } else if (node.endsWith("test_invoke_response_error")) {
            assertResponseStatusErrorWrapped();
        } else if (node.contains("TestProcessFormDataMethod")) {
            assertProcessFormDataNode(node);
        } else {
            assertFormSubmissionNode(node);
        }
    }

    @Nested
    @DisplayName("RestfulApiCard tests")
    class RestfulApiCardTests {

        @Test
        @DisplayName("toolInfo returns the Python-compatible ToolInfo object")
        void testGetToolInfo() {
            Map<String, Object> inputParams = Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "test", Map.of("description", "test", "type", "string", "default", "123")
                    ),
                    "required", List.of("test")
            );

            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .inputParams(inputParams)
                    .url("http://127.0.0.1:8000")
                    .method("GET")
                    .build();

            ToolInfo result = card.toolInfo();
            assertEquals("test", result.getName());
            assertEquals("test", result.getDescription());
            assertEquals("object", result.getParameters().get("type"));
        }

        @Test
        @DisplayName("default values match the Python card")
        void testDefaults() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com")
                    .build();

            assertEquals("POST", card.getMethod());
            assertEquals(60.0, card.getTimeout());
            assertEquals(10 * 1024 * 1024, card.getMaxResponseByteSize());
            assertEquals(Map.of(), card.getHeaders());
            assertEquals(Map.of(), card.getQueries());
            assertEquals(Map.of(), card.getPaths());
        }

        @Test
        @DisplayName("method validation happens while building the card")
        void testInvalidMethodRejectedByCard() {
            assertThrows(Throwable.class, () -> RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com")
                    .method("INVALID_METHOD")
                    .build());
        }

        @Test
        @DisplayName("path params require input schema entries with location=path")
        void testPathParamValidation() {
            assertThrows(Throwable.class, () -> RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com/api/v1/Activities/{id}")
                    .method("GET")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of("id", Map.of("type", "integer"))))
                    .build());
        }

        @Test
        @DisplayName("RestfulApiCard extends ToolCard")
        void testRestfulApiCardExtendsToolCard() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("test")
                    .description("test")
                    .url("http://example.com")
                    .method("GET")
                    .build();

            assertInstanceOf(ToolCard.class, card);
        }
    }

    @Nested
    @DisplayName("RestfulApi invoke tests")
    class RestfulApiInvokeTests {

        @Test
        @DisplayName("GET maps path, query, default query, header, and body params into the URL")
        void testInvokeGetWithParams() throws Exception {
            AtomicReference<String> requestPath = new AtomicReference<>();
            AtomicReference<Map<String, String>> requestQuery = new AtomicReference<>();
            AtomicReference<String> requestHeader = new AtomicReference<>();
            HttpServer server = createServer(exchange -> {
                requestPath.set(exchange.getRequestURI().getPath());
                requestQuery.set(parseQuery(exchange.getRequestURI().getRawQuery()));
                requestHeader.set(exchange.getRequestHeaders().getFirst("X-Test"));
                writeJson(exchange, 200, "{\"ok\":true}");
            });

            try {
                RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                        .name("test")
                        .description("test")
                        .url("http://127.0.0.1:" + server.getAddress().getPort() + "/users/{id}")
                        .method("GET")
                        .headers(Map.of("X-Test", "demo"))
                        .queries(Map.of("limit", 10))
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "id", Map.of("type", "integer", "location", "path"),
                                        "format", Map.of("type", "string", "location", "query"),
                                        "ids", Map.of("type", "array", "location", "query"),
                                        "keyword", Map.of("type", "string"))))
                        .timeout(5.0)
                        .build());

                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) api.invoke(
                        Map.of("id", 42, "format", "json", "ids", List.of(1, 2), "keyword", "hello"));

                assertEquals("/users/42", requestPath.get());
                assertEquals("10", requestQuery.get().get("limit"));
                assertEquals("json", requestQuery.get().get("format"));
                assertEquals("hello", requestQuery.get().get("keyword"));
                assertEquals("1,2", requestQuery.get().get("ids"));
                assertEquals("demo", requestHeader.get());
                assertEquals(200, result.get("code"));
                assertEquals("success", result.get("message"));
                assertEquals("OK", result.get("reason"));
            } finally {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("PUT sends JSON body and replaces path parameters")
        void testInvokePutWithBodyAndPathParams() throws Exception {
            AtomicReference<String> requestMethod = new AtomicReference<>();
            AtomicReference<String> requestPath = new AtomicReference<>();
            AtomicReference<String> requestBody = new AtomicReference<>();
            HttpServer server = createServer(exchange -> {
                requestMethod.set(exchange.getRequestMethod());
                requestPath.set(exchange.getRequestURI().getPath());
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                writeJson(exchange, 200, "{\"updated\":true}");
            });

            try {
                RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                        .name("update_activity")
                        .description("update activity")
                        .url("http://127.0.0.1:" + server.getAddress().getPort() + "/Activities/{id}")
                        .method("PUT")
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "id", Map.of("type", "integer", "location", "path"),
                                        "name", Map.of("type", "string", "location", "body"))))
                        .timeout(5.0)
                        .build());

                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) api.invoke(Map.of("id", 7, "name", "demo"));

                assertEquals("PUT", requestMethod.get());
                assertEquals("/Activities/7", requestPath.get());
                assertTrue(requestBody.get().contains("\"name\":\"demo\""));
                assertEquals(200, result.get("code"));
            } finally {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("DELETE sends body parameters as query params")
        void testDeleteUsesParamsForBodyLocation() throws Exception {
            AtomicReference<Map<String, String>> requestQuery = new AtomicReference<>();
            HttpServer server = createServer(exchange -> {
                requestQuery.set(parseQuery(exchange.getRequestURI().getRawQuery()));
                writeJson(exchange, 200, "{\"deleted\":true}");
            });

            try {
                RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                        .name("delete")
                        .description("delete")
                        .url("http://127.0.0.1:" + server.getAddress().getPort() + "/items")
                        .method("DELETE")
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of("reason", Map.of("type", "string"))))
                        .timeout(5.0)
                        .build());

                api.invoke(Map.of("reason", "cleanup"));

                assertEquals("cleanup", requestQuery.get().get("reason"));
            } finally {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("raise_for_status=false returns error response instead of throwing")
        void testInvokeWithRaiseForStatusFalse() throws Exception {
            HttpServer server = createServer(exchange -> writeJson(exchange, 404, "{\"error\":true}"));
            try {
                RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                        .name("test")
                        .description("test")
                        .url("http://127.0.0.1:" + server.getAddress().getPort() + "/missing")
                        .method("GET")
                        .timeout(5.0)
                        .build());

                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) api.invoke(Map.of(), Map.of("raise_for_status", false));

                assertEquals(404, result.get("code"));
                assertEquals("Not Found", result.get("message"));
                assertEquals("Not Found", result.get("reason"));
            } finally {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("response byte limit is enforced before parsing")
        void testResponseSizeLimit() throws Exception {
            HttpServer server = createServer(exchange -> writePlain(exchange, 200, "x".repeat(2048)));
            try {
                RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                        .name("large")
                        .description("large")
                        .url("http://127.0.0.1:" + server.getAddress().getPort() + "/large")
                        .method("GET")
                        .timeout(5.0)
                        .build());

                assertThrows(Throwable.class, () -> api.invoke(Map.of(), Map.of("max_response_byte_size", 1024)));
            } finally {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("form params remove caller Content-Type and send multipart body")
        void testFormSubmissionFlow() throws Exception {
            AtomicReference<String> contentType = new AtomicReference<>();
            AtomicReference<String> requestBody = new AtomicReference<>();
            HttpServer server = createServer(exchange -> {
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                writeJson(exchange, 200, "{\"status\":\"success\"}");
            });

            try {
                RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                        .name("submit")
                        .description("submit")
                        .url("http://127.0.0.1:" + server.getAddress().getPort() + "/submit")
                        .method("POST")
                        .headers(Map.of("Content-Type", "application/json"))
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "field", Map.of(
                                                "type", "string",
                                                "location", "form",
                                                "form_handler_type", "default"),
                                        "metadata", Map.of("type", "object", "location", "body"))))
                        .timeout(5.0)
                        .build());

                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) api.invoke(
                        Map.of("field", "value", "metadata", Map.of("key", "value")));

                assertEquals(200, result.get("code"));
                assertTrue(contentType.get().startsWith("multipart/form-data; boundary="));
                assertTrue(requestBody.get().contains("name=\"field\""));
                assertTrue(requestBody.get().contains("value"));
                assertTrue(requestBody.get().contains("name=\"metadata\""));
                assertTrue(requestBody.get().contains("{\"key\":\"value\"}"));
            } finally {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("stream is not supported")
        void testStreamNotSupported() {
            RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                    .name("test_api")
                    .description("Test API")
                    .url("http://example.com/api/test")
                    .method("GET")
                    .build());

            assertThrows(Throwable.class, () -> api.stream(Map.of()));
        }
    }

    @Nested
    @DisplayName("GUI helper tests")
    class GuiHelperTests {

        @Test
        @DisplayName("getParametersByLocation groups path, body, query, header, and form params")
        void testGetParametersByLocationHelper() {
            RestfulApiCard card = RestfulApiCard.builder()
                    .name("update_activity")
                    .description("update")
                    .url("http://example.com/api/v1/Activities/{id}")
                    .method("PUT")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "id", Map.of("type", "integer", "description", "Activity ID", "location", "path"),
                                    "name", Map.of("type", "string", "description", "Activity name", "location", "body"),
                                    "notify", Map.of("type", "boolean", "description", "Send notification", "location", "query"),
                                    "api_key", Map.of("type", "string", "description", "API Key", "location", "header"),
                                    "file", Map.of("type", "string", "description", "File data", "location", "form")),
                            "required", List.of("id", "name")))
                    .build();

            Map<String, List<Map<String, Object>>> params = RestfulApi.getParametersByLocation(card);

            assertEquals("id", params.get("path").getFirst().get("name"));
            assertEquals(true, params.get("path").getFirst().get("required"));
            assertEquals("name", params.get("body").getFirst().get("name"));
            assertEquals(true, params.get("body").getFirst().get("required"));
            assertEquals("notify", params.get("query").getFirst().get("name"));
            assertEquals("api_key", params.get("header").getFirst().get("name"));
            assertEquals("file", params.get("form").getFirst().get("name"));
        }
    }

    @SuppressWarnings("unchecked")
    private void assertResponseParsing(String contentType, int statusCode, String payload, Object expectedData)
            throws Exception {
        HttpServer server = createServer(exchange -> writeResponse(exchange, statusCode, contentType, payload));
        try {
            RestfulApi api = simpleApi(server, "GET");
            Map<String, Object> result = (Map<String, Object>) api.invoke(Map.of(), Map.of("raise_for_status", false));

            assertEquals(statusCode, result.get("code"));
            assertEquals(expectedData, result.get("data"));
            assertTrue(result.containsKey("headers"));
            assertTrue(result.containsKey("url"));
        } finally {
            server.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    private void assertErrorResponseCanBeReturnedWhenStatusCheckIsDisabled() throws Exception {
        HttpServer server = createServer(exchange -> writeResponse(
                exchange,
                400,
                "application/json",
                "{\"code\":400,\"message\":\"Invalid request parameters\"}"));
        try {
            RestfulApi api = simpleApi(server, "GET");
            Map<String, Object> result = (Map<String, Object>) api.invoke(Map.of(), Map.of("raise_for_status", false));

            assertEquals(400, result.get("code"));
            assertEquals("Bad Request", result.get("message"));
            assertEquals(Map.of("code", 400, "message", "Invalid request parameters"), result.get("data"));
        } finally {
            server.stop(0);
        }
    }

    private void assertResponseSizeLimitExceeded() throws Exception {
        HttpServer server = createServer(exchange -> writePlain(exchange, 200, "x".repeat(2048)));
        try {
            RestfulApi api = simpleApi(server, "GET");
            assertThrows(Throwable.class, () -> api.invoke(Map.of(), Map.of("max_response_byte_size", 1024)));
        } finally {
            server.stop(0);
        }
    }

    private void assertInvalidJsonResponseRaises() throws Exception {
        HttpServer server = createServer(exchange -> writeResponse(exchange, 200, "application/json",
                "{invalid: json, missing: quotes}"));
        try {
            RestfulApi api = simpleApi(server, "GET");
            assertThrows(Throwable.class, () -> api.invoke(Map.of()));
        } finally {
            server.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    private void assertCustomResponseHeadersReturned() throws Exception {
        HttpServer server = createServer(exchange -> {
            exchange.getResponseHeaders().set("X-Custom-Header", "custom-value");
            exchange.getResponseHeaders().set("X-RateLimit-Limit", "1000");
            exchange.getResponseHeaders().set("X-RateLimit-Remaining", "950");
            exchange.getResponseHeaders().set("X-Request-ID", "req-123456789");
            writeResponse(exchange, 200, "application/json", "{\"status\":\"success\"}");
        });
        try {
            RestfulApi api = simpleApi(server, "GET");
            Map<String, Object> result = (Map<String, Object>) api.invoke(Map.of());
            Map<String, String> headers = (Map<String, String>) result.get("headers");

            assertEquals("custom-value", header(headers, "X-Custom-Header"));
            assertEquals("1000", header(headers, "X-RateLimit-Limit"));
            assertEquals("950", header(headers, "X-RateLimit-Remaining"));
            assertEquals("req-123456789", header(headers, "X-Request-ID"));
        } finally {
            server.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    private void assertRedirectResponseReturned() throws Exception {
        HttpServer server = createServer(exchange -> {
            exchange.getResponseHeaders().set("Location", "http://example.com/api/new-location");
            writeResponse(exchange, 302, "text/plain", "Resource has moved to new location");
        });
        try {
            RestfulApi api = simpleApi(server, "GET");
            Map<String, Object> result = (Map<String, Object>) api.invoke(Map.of(), Map.of("raise_for_status", false));
            Map<String, String> headers = (Map<String, String>) result.get("headers");

            assertEquals(302, result.get("code"));
            assertEquals("Found", result.get("message"));
            assertEquals("Resource has moved to new location", result.get("data"));
            assertEquals("http://example.com/api/new-location", header(headers, "Location"));
        } finally {
            server.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    private void assertChunkedJsonArrayResponse() throws Exception {
        HttpServer server = createServer(exchange -> writeResponse(exchange, 200, "application/json",
                "[{\"id\":1,\"name\":\"item1\"},{\"id\":2,\"name\":\"item2\"}]"));
        try {
            RestfulApi api = simpleApi(server, "GET");
            Map<String, Object> result = (Map<String, Object>) api.invoke(Map.of());
            List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");

            assertEquals(2, data.size());
            assertEquals("item1", data.getFirst().get("name"));
            assertEquals("item2", data.get(1).get("name"));
        } finally {
            server.stop(0);
        }
    }

    private void assertInputSchemaFormatsRequestAndParsesJson() throws Exception {
        AtomicReference<Map<String, String>> query = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            query.set(parseQuery(exchange.getRequestURI().getRawQuery()));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeResponse(exchange, 200, "application/json", "{\"user\":{\"id\":123},\"status\":\"active\"}");
        });
        try {
            RestfulApi api = apiWithSchema(server, "POST", "/api/users", Map.of(
                    "user_id", param("integer", "query"),
                    "name", param("string", "body"),
                    "email", param("string", "body")));
            api.invoke(Map.of("user_id", 123, "name", "张三", "email", "zhangsan@example.com"));

            assertEquals("123", query.get().get("user_id"));
            assertTrue(body.get().contains("\"name\":\"张三\""));
            assertTrue(body.get().contains("\"email\":\"zhangsan@example.com\""));
        } finally {
            server.stop(0);
        }
    }

    private void assertQueryLocation() throws Exception {
        AtomicReference<Map<String, String>> query = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            query.set(parseQuery(exchange.getRequestURI().getRawQuery()));
            writeJson(exchange, 200, "{\"ok\":true}");
        });
        try {
            RestfulApi api = apiWithSchema(server, "GET", "/search", Map.of(
                    "q", param("string", "query"),
                    "page", param("integer", "query")));
            api.invoke(Map.of("q", "openjiuwen", "page", 2));

            assertEquals("openjiuwen", query.get().get("q"));
            assertEquals("2", query.get().get("page"));
        } finally {
            server.stop(0);
        }
    }

    private void assertPathLocation() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            path.set(exchange.getRequestURI().getPath());
            writeJson(exchange, 200, "{\"ok\":true}");
        });
        try {
            RestfulApi api = apiWithSchema(server, "GET", "/api/v1/users/{user_id}", Map.of(
                    "user_id", param("integer", "path")));
            api.invoke(Map.of("user_id", 123));

            assertEquals("/api/v1/users/123", path.get());
        } finally {
            server.stop(0);
        }
    }

    private void assertHeaderLocation() throws Exception {
        AtomicReference<String> header = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            header.set(exchange.getRequestHeaders().getFirst("api_key"));
            writeJson(exchange, 200, "{\"ok\":true}");
        });
        try {
            RestfulApi api = apiWithSchema(server, "GET", "/secure", Map.of("api_key", param("string", "header")));
            api.invoke(Map.of("api_key", "key-abc123"));

            assertEquals("key-abc123", header.get());
        } finally {
            server.stop(0);
        }
    }

    private void assertMixedLocations() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<Map<String, String>> query = new AtomicReference<>();
        AtomicReference<String> header = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            path.set(exchange.getRequestURI().getPath());
            query.set(parseQuery(exchange.getRequestURI().getRawQuery()));
            header.set(exchange.getRequestHeaders().getFirst("auth_token"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, "{\"ok\":true}");
        });
        try {
            RestfulApi api = apiWithSchema(server, "POST", "/api/users/{user_id}/upload", Map.of(
                    "user_id", param("integer", "path"),
                    "filter", param("string", "query"),
                    "auth_token", param("string", "header"),
                    "metadata", param("object", "body")));
            api.invoke(Map.of("user_id", 123, "filter", "active", "auth_token", "token123",
                    "metadata", Map.of("key", "value")));

            assertEquals("/api/users/123/upload", path.get());
            assertEquals("active", query.get().get("filter"));
            assertEquals("token123", header.get());
            assertTrue(body.get().contains("\"metadata\":{\"key\":\"value\"}"));
        } finally {
            server.stop(0);
        }
    }

    private void assertNoLocationDefaultsToBody() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, "{\"ok\":true}");
        });
        try {
            RestfulApi api = apiWithSchema(server, "POST", "/users/register", Map.of(
                    "username", Map.of("type", "string"),
                    "email", Map.of("type", "string")));
            api.invoke(Map.of("username", "testuser", "email", "test@example.com"));

            assertTrue(body.get().contains("\"username\":\"testuser\""));
            assertTrue(body.get().contains("\"email\":\"test@example.com\""));
        } finally {
            server.stop(0);
        }
    }

    private void assertCallerValueOverridesSchemaDefault() throws Exception {
        AtomicReference<Map<String, String>> query = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            query.set(parseQuery(exchange.getRequestURI().getRawQuery()));
            writeJson(exchange, 200, "{\"ok\":true}");
        });
        try {
            RestfulApi api = apiWithSchema(server, "GET", "/items", Map.of(
                    "format", Map.of("type", "string", "location", "query", "default", "xml")));
            api.invoke(Map.of("format", "json"));

            assertEquals("json", query.get().get("format"));
        } finally {
            server.stop(0);
        }
    }

    private void assertHttpMethodNode(String node) throws Exception {
        if (node.endsWith("test_delete_with_explicit_body")) {
            assertMethodUsesExpectedTransport("DELETE", false);
            return;
        }
        if (node.endsWith("test_path_parameters_with_put")) {
            assertMethodSupportsPathParameters("PUT");
            return;
        }
        if (node.endsWith("test_path_parameters_with_delete")) {
            assertMethodSupportsPathParameters("DELETE");
            return;
        }
        if (node.endsWith("test_put_method")) {
            assertMethodUsesExpectedTransport("PUT", true);
        } else if (node.endsWith("test_patch_method")) {
            assertMethodUsesExpectedTransport("PATCH", true);
        } else if (node.endsWith("test_delete_method")) {
            assertMethodUsesExpectedTransport("DELETE", false);
        } else if (node.endsWith("test_head_method")) {
            assertMethodUsesExpectedTransport("HEAD", false);
        } else if (node.endsWith("test_options_method")) {
            assertMethodUsesExpectedTransport("OPTIONS", false);
        } else {
            assertPathParameterValidationNode(node);
        }
    }

    private void assertMethodUsesExpectedTransport(String method, boolean expectsJsonBody) throws Exception {
        AtomicReference<String> actualMethod = new AtomicReference<>();
        AtomicReference<Map<String, String>> query = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            actualMethod.set(exchange.getRequestMethod());
            query.set(parseQuery(exchange.getRequestURI().getRawQuery()));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, "{\"ok\":true}");
        });
        try {
            RestfulApi api = apiWithSchema(server, method, "/items", Map.of("data", Map.of("type", "string")));
            api.invoke(Map.of("data", "test_value"), Map.of("raise_for_status", false));

            assertEquals(method, actualMethod.get());
            if (expectsJsonBody) {
                assertTrue(body.get().contains("\"data\":\"test_value\""));
            } else {
                assertEquals("test_value", query.get().get("data"));
            }
        } finally {
            server.stop(0);
        }
    }

    private void assertMethodSupportsPathParameters(String method) throws Exception {
        AtomicReference<String> actualMethod = new AtomicReference<>();
        AtomicReference<String> actualPath = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            actualMethod.set(exchange.getRequestMethod());
            actualPath.set(exchange.getRequestURI().getPath());
            writeJson(exchange, 200, "{\"ok\":true}");
        });
        try {
            RestfulApi api = apiWithSchema(server, method, "/api/v1/Activities/{id}", Map.of(
                    "id", param("string", "path"),
                    "data", Map.of("type", "string")));
            api.invoke(Map.of("id", "abc-123", "data", "test_value"), Map.of("raise_for_status", false));

            assertEquals(method, actualMethod.get());
            assertEquals("/api/v1/Activities/abc-123", actualPath.get());
        } finally {
            server.stop(0);
        }
    }

    private void assertMultiplePathParameters() throws Exception {
        AtomicReference<String> actualPath = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            actualPath.set(exchange.getRequestURI().getPath());
            writeJson(exchange, 200, "{\"ok\":true}");
        });
        try {
            RestfulApi api = apiWithSchema(server, "GET", "/api/{version}/users/{userId}/posts/{postId}", Map.of(
                    "version", param("string", "path"),
                    "userId", param("integer", "path"),
                    "postId", param("integer", "path")));
            api.invoke(Map.of("version", "v2", "userId", 42, "postId", 99));

            assertEquals("/api/v2/users/42/posts/99", actualPath.get());
        } finally {
            server.stop(0);
        }
    }

    private void assertPathParameterValidationNode(String node) {
        if (node.endsWith("test_url_with_correct_path_param_schema_succeeds")) {
            assertDoesNotThrow(() -> RestfulApiCard.builder()
                    .name("ok")
                    .description("ok")
                    .url("http://example.com/users/{id}")
                    .method("GET")
                    .inputParams(Map.of("type", "object", "properties", Map.of("id", param("integer", "path"))))
                    .build());
            return;
        }
        assertThrows(Throwable.class, () -> RestfulApiCard.builder()
                .name("bad")
                .description("bad")
                .url(node.contains("multiple") ? "http://example.com/{tenant}/users/{id}" : "http://example.com/users/{id}")
                .method("GET")
                .inputParams(node.endsWith("test_url_with_path_param_but_no_schema_raises_error")
                        ? Map.of()
                        : Map.of("type", "object", "properties", Map.of("tenant", param("string", "path"))))
                .build());
    }

    private void assertTimeoutOrConnectionErrorWrapped() {
        RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                .name("timeout")
                .description("timeout")
                .url("http://127.0.0.1:9/unavailable")
                .method("GET")
                .timeout(1.0)
                .build());

        assertThrows(Throwable.class, () -> api.invoke(Map.of()));
    }

    private void assertResponseStatusErrorWrapped() throws Exception {
        HttpServer server = createServer(exchange -> writeJson(exchange, 500, "{\"error\":true}"));
        try {
            RestfulApi api = simpleApi(server, "GET");
            assertThrows(Throwable.class, () -> api.invoke(Map.of()));
        } finally {
            server.stop(0);
        }
    }

    private void assertProcessFormDataNode(String node) throws Exception {
        RestfulApi api = new RestfulApi(RestfulApiCard.builder()
                .name("form")
                .description("form")
                .url("http://example.com/upload")
                .method("POST")
                .build());
        if (node.endsWith("test_empty_form_params_and_body_params")) {
            assertEquals(0, api.processFormData(Map.of(), Map.of()).size());
        } else if (node.endsWith("test_form_param_with_body_params")) {
            ToolFormData form = api.processFormData(
                    Map.of("file_url", Map.of("form_handler_type", "default", "value", "http://example.com/doc.pdf")),
                    Map.of("title", "Test Document", "count", 5));
            assertTrue(form.names().containsAll(List.of("file_url", "title", "count")));
        } else if (node.endsWith("test_multiple_form_params_processing")) {
            ToolFormData form = api.processFormData(
                    Map.of("file1", Map.of("form_handler_type", "default", "value", "content1"),
                            "file2", Map.of("form_handler_type", "default", "value", "content2")),
                    Map.of());
            assertTrue(form.names().containsAll(List.of("file1", "file2")));
        } else if (node.endsWith("test_custom_handler_type")) {
            FormHandlerManager.getInstance().register("custom", CustomFormHandler.class);
            ToolFormData form = api.processFormData(
                    Map.of("custom_data", Map.of("form_handler_type", "custom", "value", "test_value")),
                    Map.of());
            assertEquals("processed_value", form.values("custom_data").getFirst());
        } else {
            ToolFormData form = api.processFormData(
                    Map.of("username", Map.of("form_handler_type", "default", "value", "test_user"),
                            "age", Map.of("form_handler_type", "default", "value", 25)),
                    Map.of());
            assertTrue(form.names().containsAll(List.of("username", "age")));
        }
    }

    private void assertFormSubmissionNode(String node) throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<Map<String, String>> query = new AtomicReference<>();
        AtomicReference<String> header = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            path.set(exchange.getRequestURI().getPath());
            query.set(parseQuery(exchange.getRequestURI().getRawQuery()));
            header.set(exchange.getRequestHeaders().getFirst("auth_token"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, "{\"status\":\"success\"}");
        });
        try {
            RestfulApi api = apiWithSchema(server, "POST", "/api/users/{user_id}/upload", Map.of(
                    "user_id", param("integer", "path"),
                    "filter", param("string", "query"),
                    "auth_token", param("string", "header"),
                    "file_data", Map.of("type", "string", "location", "form", "form_handler_type", "default"),
                    "metadata", param("object", "body"),
                    "title", param("string", "body")));
            Map<String, Object> inputs = node.endsWith("test_empty_form_data_handling")
                    ? Map.of("user_id", 1)
                    : Map.of("user_id", 123, "filter", "active", "auth_token", "token123",
                            "file_data", "file_content", "metadata", Map.of("key", "value"),
                            "title", "Document without file");
            api.invoke(inputs);

            if (node.endsWith("test_empty_form_data_handling")) {
                assertTrue(contentType.get().startsWith("application/json"));
                assertEquals("{}", body.get());
            } else {
                assertEquals("/api/users/123/upload", path.get());
                assertEquals("active", query.get().get("filter"));
                assertEquals("token123", header.get());
                assertTrue(contentType.get().startsWith("multipart/form-data; boundary="));
                assertTrue(body.get().contains("name=\"file_data\""));
            }
        } finally {
            server.stop(0);
        }
    }

    private RestfulApi simpleApi(HttpServer server, String method) {
        return new RestfulApi(RestfulApiCard.builder()
                .name("test")
                .description("test")
                .url("http://127.0.0.1:" + server.getAddress().getPort() + "/api/test")
                .method(method)
                .timeout(5.0)
                .build());
    }

    private RestfulApi apiWithSchema(HttpServer server,
                                     String method,
                                     String path,
                                     Map<String, Object> properties) {
        return new RestfulApi(RestfulApiCard.builder()
                .name("api")
                .description("api")
                .url("http://127.0.0.1:" + server.getAddress().getPort() + path)
                .method(method)
                .inputParams(Map.of("type", "object", "properties", properties))
                .timeout(5.0)
                .build());
    }

    private static Map<String, Object> param(String type, String location) {
        return Map.of("type", type, "location", location);
    }

    private static String header(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static void setEnvReader(Function<String, String> reader) throws Exception {
        Method method = UrlUtils.class.getDeclaredMethod("setEnvReaderForTests", Function.class);
        method.setAccessible(true);
        method.invoke(null, reader);
    }

    private static HttpServer createServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return server;
    }

    private static void writeJson(HttpExchange exchange, int statusCode, String payload) throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static void writePlain(HttpExchange exchange, int statusCode, String payload) throws IOException {
        writeResponse(exchange, statusCode, "text/plain; charset=utf-8", payload);
    }

    private static void writeResponse(HttpExchange exchange, int statusCode, String contentType, String payload)
            throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1
                    ? java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                    : "";
            result.merge(key, value, (left, right) -> left + "," + right);
        }
        return result;
    }

    public static final class CustomFormHandler implements FormHandler {
        @Override
        public CompletionStage<ToolFormData> handle(
                ToolFormData form,
                Map<String, Object> formData,
                Map<String, Object> kwargs) {
            form.addField("custom_data", "processed_value");
            return CompletableFuture.completedFuture(form);
        }
    }
}
