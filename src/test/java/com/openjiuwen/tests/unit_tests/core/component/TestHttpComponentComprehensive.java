/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.core.component;

import com.openjiuwen.core.workflow.ComponentExecutable;
import com.openjiuwen.core.workflow.component.tool.http.HTTPRequestComponent;
import com.openjiuwen.core.workflow.component.tool.http.HTTPRequestExecutable;
import com.openjiuwen.core.workflow.component.tool.http.HttpAdvancedOptionsConfig;
import com.openjiuwen.core.workflow.component.tool.http.HttpAuthConfig;
import com.openjiuwen.core.workflow.component.tool.http.HttpAuthType;
import com.openjiuwen.core.workflow.component.tool.http.HttpComponentConfig;
import com.openjiuwen.core.workflow.component.tool.http.HttpContentType;
import com.openjiuwen.core.workflow.component.tool.http.HttpRequestBodyConfig;
import com.openjiuwen.core.workflow.component.tool.http.HttpRequestParamConfig;
import com.openjiuwen.core.workflow.component.tool.http.HttpResponseFormat;
import com.openjiuwen.core.workflow.component.tool.http.HttpResponseHandlingConfig;
import com.openjiuwen.core.workflow.component.tool.http.HttpRetryConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_http_component_comprehensive.py} in
 * {@code tests.unit_tests.core.component}.
 */
@Tag("unit-test")
class TestHttpComponentComprehensive {

    @Test
    @DisplayName("basic GET request configuration")
    void testBasicGetRequest() {
        HTTPRequestComponent component = new HTTPRequestComponent(new HttpComponentConfig(
                HttpRequestParamConfig.builder()
                        .url("https://httpbin.org/get")
                        .method("GET")
                        .headers(Map.of("User-Agent", "openJiuwen HTTP Component"))
                        .build()));

        HttpRequestParamConfig params = component.getConfig().getRequestParams();
        assertEquals("https://httpbin.org/get", params.getUrl());
        assertEquals("GET", params.getMethod());
        assertEquals("openJiuwen HTTP Component", ((Map<?, ?>) params.getHeaders()).get("User-Agent"));
    }

    @Test
    @DisplayName("POST request with JSON body")
    void testPostRequestWithBody() {
        HttpRequestBodyConfig bodyConfig = HttpRequestBodyConfig.builder()
                .contentType(HttpContentType.JSON)
                .jsonData(Map.of("key", "value", "test", true))
                .build();
        HTTPRequestComponent component = new HTTPRequestComponent(new HttpComponentConfig(
                HttpRequestParamConfig.builder()
                        .url("https://httpbin.org/post")
                        .method("POST")
                        .body(bodyConfig)
                        .headers(Map.of("Content-Type", "application/json"))
                        .build()));

        HttpRequestParamConfig params = component.getConfig().getRequestParams();
        assertEquals("POST", params.getMethod());
        assertEquals(HttpContentType.JSON, params.getBody().getContentType());
        assertEquals("value", ((Map<?, ?>) params.getBody().getJsonData()).get("key"));
    }

    @Test
    @DisplayName("authentication configuration")
    void testAuthenticationConfig() {
        HttpAuthConfig authConfig = HttpAuthConfig.builder()
                .type(HttpAuthType.BASIC)
                .username("testuser")
                .password("testpass")
                .build();
        HTTPRequestComponent component = new HTTPRequestComponent(new HttpComponentConfig(
                HttpRequestParamConfig.builder()
                        .url("https://httpbin.org/get")
                        .method("GET")
                        .authentication(authConfig)
                        .build()));

        HttpAuthConfig auth = component.getConfig().getRequestParams().getAuthentication();
        assertEquals(HttpAuthType.BASIC, auth.getType());
        assertEquals("testuser", auth.getUsername());
        assertEquals("testpass", auth.getPassword());
    }

    @Test
    @DisplayName("advanced options configuration")
    void testAdvancedOptions() {
        HTTPRequestComponent component = new HTTPRequestComponent(new HttpComponentConfig(
                HttpRequestParamConfig.builder()
                        .url("https://httpbin.org/get")
                        .method("GET")
                        .advancedOptions(HttpAdvancedOptionsConfig.builder()
                                .followRedirect(true)
                                .timeout(15000)
                                .ignoreSslIssues(false)
                                .build())
                        .retryConfig(HttpRetryConfig.builder()
                                .enabled(true)
                                .maxRetries(3)
                                .retryDelay(1000)
                                .build())
                        .build()));

        HttpRequestParamConfig params = component.getConfig().getRequestParams();
        assertTrue(params.getAdvancedOptions().isFollowRedirect());
        assertEquals(15000, params.getAdvancedOptions().getTimeout());
        assertTrue(params.getRetryConfig().isEnabled());
        assertEquals(3, params.getRetryConfig().getMaxRetries());
    }

    @Test
    @DisplayName("response handling configuration")
    void testResponseHandling() {
        HTTPRequestComponent component = new HTTPRequestComponent(new HttpComponentConfig(
                HttpRequestParamConfig.builder()
                        .url("https://httpbin.org/json")
                        .method("GET")
                        .responseHandling(HttpResponseHandlingConfig.builder()
                                .responseFormat(HttpResponseFormat.JSON)
                                .responseCodeSuccessCodes(List.of(200, 201))
                                .responseMode("full")
                                .build())
                        .build()));

        HttpResponseHandlingConfig responseHandling = component.getConfig().getRequestParams().getResponseHandling();
        assertEquals(HttpResponseFormat.JSON, responseHandling.getResponseFormat());
        assertTrue(responseHandling.getResponseCodeSuccessCodes().contains(200));
        assertEquals("full", responseHandling.getResponseMode());
    }

    @Test
    @DisplayName("executable exposes invoke stream collect and transform methods")
    void testExecutableMethods() throws NoSuchMethodException {
        HTTPRequestComponent component = new HTTPRequestComponent(new HttpComponentConfig(
                HttpRequestParamConfig.builder()
                        .url("https://httpbin.org/get")
                        .method("GET")
                        .build()));

        HTTPRequestExecutable executable = component.getExecutable();

        assertInstanceOf(ComponentExecutable.class, executable);
        assertMethodExists(executable, "invoke");
        assertMethodExists(executable, "stream");
        assertMethodExists(executable, "collect");
        assertMethodExists(executable, "transform");
    }

    private static void assertMethodExists(Object executable, String methodName) throws NoSuchMethodException {
        Method method = executable.getClass().getMethod(
                methodName,
                Object.class,
                com.openjiuwen.core.session.NodeSessionApi.class,
                com.openjiuwen.core.context.ModelContext.class);
        assertEquals(methodName, method.getName());
    }
}
