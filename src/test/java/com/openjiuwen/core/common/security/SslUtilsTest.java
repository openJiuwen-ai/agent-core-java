/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.security;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SslUtilsTest {

    @Test
    void configureHttpClientSslDisablesEndpointVerificationWhenRequested() {
        HttpClient.Builder builder = HttpClient.newBuilder();

        SslUtils.configureHttpClientSsl(builder, "https://example.com/v1", false, null);

        HttpClient client = builder.build();
        assertNotNull(client.sslContext());
        assertEquals("", client.sslParameters().getEndpointIdentificationAlgorithm());
    }
}
