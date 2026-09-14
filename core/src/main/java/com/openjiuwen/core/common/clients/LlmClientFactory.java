/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.common.utils.HeaderUtils;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Factory for HTTPX-style clients and OpenAI client configuration handles.
 *
 * <p>Mirrors Python's factory functions in
 * {@code openjiuwen/core/common/clients/llm_client.py}.</p>
 */
public final class LlmClientFactory {

    static {
        ConnectorPoolManager.registerProvider("httpx", config -> CompletableFuture.completedFuture(
                new HttpXConnectorPool(toHttpxConfig(config, true))));
        ClientRegistry.getClientRegistry().registerClient(
                "httpx",
                kwargs -> createHttpxClient(new HttpXConnectorPoolConfig(kwargs), false).join());
        ClientRegistry.getClientRegistry().registerClient(
                "async_openai",
                kwargs -> createAsyncOpenaiClient(toModelClientConfig(kwargs), kwargs).join());
        ClientRegistry.getClientRegistry().registerClient(
                "openai",
                kwargs -> createOpenaiClient(toModelClientConfig(kwargs), kwargs).join());
    }

    private LlmClientFactory() {
    }

    public static CompletableFuture<HttpClient> createHttpxClient(
            HttpXConnectorPoolConfig config,
            boolean needAsync) {
        HttpXConnectorPoolConfig effectiveConfig = config == null ? new HttpXConnectorPoolConfig() : config;
        if (effectiveConfig.isNeedAsync() != needAsync) {
            effectiveConfig = copyWithNeedAsync(effectiveConfig, needAsync);
        }
        return ConnectorPoolManager.getConnectorPoolManager()
                .getConnectorPool("httpx", effectiveConfig)
                .thenApply(pool -> (HttpClient) pool.conn());
    }

    public static CompletableFuture<HttpClient> createHttpxClient(
            Map<String, Object> config,
            boolean needAsync) {
        Map<String, Object> values = config == null ? Map.of() : new LinkedHashMap<>(config);
        values.put("need_async", needAsync);
        return createHttpxClient(new HttpXConnectorPoolConfig(values), needAsync);
    }

    public static CompletableFuture<OpenAiClientHandle> createAsyncOpenaiClient(
            ModelClientConfig config,
            Map<String, Object> kwargs) {
        return createOpenAiHandle(normalizeConfig(config), kwargs, true);
    }

    public static CompletableFuture<OpenAiClientHandle> createAsyncOpenaiClient(
            Map<String, Object> config,
            Map<String, Object> kwargs) {
        return createAsyncOpenaiClient(toModelClientConfig(config), kwargs);
    }

    public static CompletableFuture<OpenAiClientHandle> createOpenaiClient(
            ModelClientConfig config,
            Map<String, Object> kwargs) {
        return createOpenAiHandle(normalizeConfig(config), kwargs, false);
    }

    public static CompletableFuture<OpenAiClientHandle> createOpenaiClient(
            Map<String, Object> config,
            Map<String, Object> kwargs) {
        return createOpenaiClient(toModelClientConfig(config), kwargs);
    }

    public static Map<String, String> sanitizeHeaders(Map<?, ?> headers) {
        return HeaderUtils.sanitizeHeaders(headers);
    }

    static HttpXConnectorPoolConfig toHttpxConfig(ConnectorPoolConfig config, boolean needAsync) {
        if (config instanceof HttpXConnectorPoolConfig httpxConfig) {
            return copyWithNeedAsync(httpxConfig, needAsync);
        }
        HttpXConnectorPoolConfig httpxConfig = new HttpXConnectorPoolConfig();
        httpxConfig.setLimit(config.getLimit());
        httpxConfig.setLimitPerHost(config.getLimitPerHost());
        httpxConfig.setSslVerify(config.isSslVerify());
        httpxConfig.setSslCert(config.getSslCert());
        httpxConfig.setForceClose(config.isForceClose());
        httpxConfig.setKeepaliveTimeout(config.getKeepaliveTimeout());
        httpxConfig.setTtl(config.getTtl());
        httpxConfig.setMaxIdleTime(config.getMaxIdleTime());
        httpxConfig.setExtendParams(config.getExtendParams());
        httpxConfig.setNeedAsync(needAsync);
        return httpxConfig;
    }

    private static CompletableFuture<OpenAiClientHandle> createOpenAiHandle(
            ModelClientConfig config,
            Map<String, Object> kwargs,
            boolean asyncClient) {
        Map<String, Object> httpConfig = kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
        httpConfig.put("proxy", UrlUtils.getGlobalProxyUrl(config.getApiBase()));
        httpConfig.put("ssl_verify", config.isVerifySsl());
        httpConfig.put("ssl_cert", config.getSslCert());
        return createHttpxClient(httpConfig, asyncClient).thenApply(httpClient -> new OpenAiClientHandle(
                asyncClient,
                config.getApiKey(),
                config.getApiBase(),
                httpClient,
                config.getTimeout(),
                config.getMaxRetries(),
                sanitizeHeaders(config.getCustomHeaders())));
    }

    private static HttpXConnectorPoolConfig copyWithNeedAsync(HttpXConnectorPoolConfig source, boolean needAsync) {
        HttpXConnectorPoolConfig copy = new HttpXConnectorPoolConfig();
        copy.setLimit(source.getLimit());
        copy.setLimitPerHost(source.getLimitPerHost());
        copy.setSslVerify(source.isSslVerify());
        copy.setSslCert(source.getSslCert());
        copy.setForceClose(source.isForceClose());
        copy.setKeepaliveTimeout(source.getKeepaliveTimeout());
        copy.setTtl(source.getTtl());
        copy.setMaxIdleTime(source.getMaxIdleTime());
        copy.setExtendParams(source.getExtendParams());
        copy.setMaxKeepaliveConnections(source.getMaxKeepaliveConnections());
        copy.setLocalAddress(source.getLocalAddress());
        copy.setProxy(source.getProxy());
        copy.setNeedAsync(needAsync);
        return copy;
    }

    private static ModelClientConfig normalizeConfig(ModelClientConfig config) {
        return config == null ? new ModelClientConfig() : config;
    }

    private static ModelClientConfig toModelClientConfig(Map<String, Object> values) {
        ModelClientConfig config = new ModelClientConfig();
        if (values == null) {
            return config;
        }
        config.setApiKey(stringValue(first(values, "api_key", "apiKey")));
        config.setApiBase(stringValue(first(values, "api_base", "apiBase", "base_url", "baseUrl")));
        config.setTimeout(doubleValue(first(values, "timeout"), config.getTimeout()));
        config.setMaxRetries(intValue(first(values, "max_retries", "maxRetries"), config.getMaxRetries()));
        config.setVerifySsl(booleanValue(first(values, "verify_ssl", "verifySsl"), config.isVerifySsl()));
        config.setSslCert(stringValue(first(values, "ssl_cert", "sslCert")));
        Object headers = first(values, "custom_headers", "customHeaders", "default_headers", "defaultHeaders");
        if (headers instanceof Map<?, ?> map) {
            Map<String, Object> customHeaders = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    customHeaders.put(String.valueOf(key), value);
                }
            });
            config.setCustomHeaders(customHeaders);
        }
        return config;
    }

    private static Object first(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static double doubleValue(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static boolean booleanValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * Java handle containing the OpenAI client constructor data.
     *
     * <p>Mirrors Python's {@code AsyncOpenAI(**openai_kwargs)} and
     * {@code OpenAI(**openai_kwargs)} arguments in
     * {@code openjiuwen/core/common/clients/llm_client.py}.</p>
     */
    public record OpenAiClientHandle(
            boolean asyncClient,
            String apiKey,
            String baseUrl,
            HttpClient httpClient,
            double timeout,
            int maxRetries,
            Map<String, String> defaultHeaders) {
    }
}
