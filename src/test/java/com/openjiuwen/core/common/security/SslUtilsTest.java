/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
