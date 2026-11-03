/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.component;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.BaseSession;
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests/unit_tests/core/component/test_http_component_comprehensive.py}.</p>
 */
class HttpComponentComprehensiveMissingTest {

    @Test
    void basicGetRequestConfiguration() {
        HttpRequestParamConfig requestParams = HttpRequestParamConfig.builder()
                .url("https://httpbin.org/get")
                .method("GET")
                .headers(Map.of("User-Agent", "openJiuwen HTTP Component"))
                .build();

        HTTPRequestComponent component = component(requestParams);

        assertThat(component.getConfig().getRequestParams().getUrl()).isEqualTo("https://httpbin.org/get");
        assertThat(component.getConfig().getRequestParams().getMethod()).isEqualTo("GET");
        assertThat(component.getConfig().getRequestParams().getHeaders())
                .isEqualTo(Map.of("User-Agent", "openJiuwen HTTP Component"));
    }

    @Test
    void postRequestWithJsonBody() {
        HttpRequestParamConfig requestParams = HttpRequestParamConfig.builder()
                .url("https://httpbin.org/post")
                .method("POST")
                .body(HttpRequestBodyConfig.builder()
                        .contentType(HttpContentType.JSON)
                        .jsonData(Map.of("key", "value", "test", true))
                        .build())
                .headers(Map.of("Content-Type", "application/json"))
                .build();

        HTTPRequestComponent component = component(requestParams);

        assertThat(component.getConfig().getRequestParams().getMethod()).isEqualTo("POST");
        assertThat(component.getConfig().getRequestParams().getBody().getContentType()).isEqualTo(HttpContentType.JSON);
        assertThat(component.getConfig().getRequestParams().getBody().getJsonData())
                .isEqualTo(Map.of("key", "value", "test", true));
    }

    @Test
    void authenticationConfiguration() {
        HttpRequestParamConfig requestParams = HttpRequestParamConfig.builder()
                .url("https://httpbin.org/get")
                .method("GET")
                .authentication(HttpAuthConfig.builder()
                        .type(HttpAuthType.BASIC)
                        .username("testuser")
                        .password("testpass")
                        .build())
                .build();

        HTTPRequestComponent component = component(requestParams);
        HttpAuthConfig auth = component.getConfig().getRequestParams().getAuthentication();

        assertThat(auth.getType()).isEqualTo(HttpAuthType.BASIC);
        assertThat(auth.getUsername()).isEqualTo("testuser");
        assertThat(auth.getPassword()).isEqualTo("testpass");
    }

    @Test
    void advancedOptionsConfiguration() {
        HttpRequestParamConfig requestParams = HttpRequestParamConfig.builder()
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
                .build();

        HTTPRequestComponent component = component(requestParams);
        HttpAdvancedOptionsConfig advancedOptions = component.getConfig().getRequestParams().getAdvancedOptions();
        HttpRetryConfig retryConfig = component.getConfig().getRequestParams().getRetryConfig();

        assertThat(advancedOptions.isFollowRedirect()).isTrue();
        assertThat(advancedOptions.getTimeout()).isEqualTo(15000);
        assertThat(advancedOptions.isIgnoreSslIssues()).isFalse();
        assertThat(retryConfig.isEnabled()).isTrue();
        assertThat(retryConfig.getMaxRetries()).isEqualTo(3);
    }

    @Test
    void responseHandlingConfiguration() {
        HttpRequestParamConfig requestParams = HttpRequestParamConfig.builder()
                .url("https://httpbin.org/json")
                .method("GET")
                .responseHandling(HttpResponseHandlingConfig.builder()
                        .responseFormat(HttpResponseFormat.JSON)
                        .responseCodeSuccessCodes(List.of(200, 201))
                        .responseMode("full")
                        .build())
                .build();

        HTTPRequestComponent component = component(requestParams);
        HttpResponseHandlingConfig responseHandling = component.getConfig().getRequestParams().getResponseHandling();

        assertThat(responseHandling.getResponseFormat()).isEqualTo(HttpResponseFormat.JSON);
        assertThat(responseHandling.getResponseCodeSuccessCodes()).contains(200);
        assertThat(responseHandling.getResponseMode()).isEqualTo("full");
    }

    @Test
    void executableMethodsExist() throws Exception {
        HttpRequestParamConfig requestParams = HttpRequestParamConfig.builder()
                .url("https://httpbin.org/get")
                .method("GET")
                .build();

        HTTPRequestExecutable executable = component(requestParams).getExecutable();

        assertThat(executable).isNotNull();
        assertThat(HTTPRequestExecutable.class.getMethod("invoke", Object.class, BaseSession.class, ModelContext.class))
                .isNotNull();
        assertThat(HTTPRequestExecutable.class.getMethod("stream", Object.class, BaseSession.class, ModelContext.class))
                .isNotNull();
        assertThat(HTTPRequestExecutable.class.getMethod("collect", Object.class, BaseSession.class, ModelContext.class))
                .isNotNull();
        assertThat(HTTPRequestExecutable.class.getMethod("transform", Object.class, BaseSession.class, ModelContext.class))
                .isNotNull();
    }

    private static HTTPRequestComponent component(HttpRequestParamConfig requestParams) {
        HttpComponentConfig config = new HttpComponentConfig();
        config.setRequestParams(requestParams);
        return new HTTPRequestComponent(config);
    }
}
