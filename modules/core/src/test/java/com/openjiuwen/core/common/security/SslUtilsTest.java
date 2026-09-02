/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import okhttp3.OkHttpClient;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.Arrays;

class SslUtilsTest {
    @Test
    void configureHttpClientSslDisablesEndpointVerificationWhenRequested() {
        HttpClient.Builder builder = HttpClient.newBuilder();

        SslUtils.configureHttpClientSsl(builder, "https://example.com/v1", false, null);

        HttpClient client = builder.build();
        assertNotNull(client.sslContext());
        assertEquals("", client.sslParameters().getEndpointIdentificationAlgorithm());
        assertTrue(Arrays.asList(client.sslParameters().getProtocols()).contains("TLSv1.2"));
        assertTrue(Arrays.asList(client.sslParameters().getProtocols()).contains("TLSv1.3"));
    }

    @Test
    void configureOkHttpClientSslDisablesVerificationWhenRequested() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        SslUtils.configureOkHttpClientSsl(builder, "https://example.com/v1", false, null);

        OkHttpClient client = builder.build();
        assertNotNull(client.sslSocketFactory());
        assertNotNull(client.x509TrustManager());
        assertTrue(client.hostnameVerifier().verify("example.com", null));
    }

    @Test
    void configureOkHttpClientSslSkipsNonHttpsTargets() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        OkHttpClient before = builder.build();

        SslUtils.configureOkHttpClientSsl(builder, "http://example.com/v1", false, null);

        OkHttpClient after = builder.build();
        assertEquals(before.sslSocketFactory().getClass(), after.sslSocketFactory().getClass());
    }
}
