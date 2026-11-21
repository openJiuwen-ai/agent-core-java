/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's behavior around
 * {@code openjiuwen/core/common/security/ssl_utils.py}.
 */
class SslUtilsTest {

    @Test
    void configureHttpClientSslDisablesEndpointVerificationWhenRequested() {
        HttpClient.Builder builder = HttpClient.newBuilder();

        SslUtils.configureHttpClientSsl(builder, "https://example.com/v1", false, null);

        HttpClient client = builder.build();
        assertNotNull(client.sslContext());
        assertEquals("", client.sslParameters().getEndpointIdentificationAlgorithm());
    }

    @Test
    void getSslConfigReturnsFalsePairWhenUrlIsNotHttps() {
        Object[] config = SslUtils.getSslConfig("SSL_VERIFY", "SSL_CERT", List.of("true"), false);

        assertEquals(false, config[0]);
        assertEquals(false, config[1]);
    }

    @Test
    void getSslConfigRaisesWhenVerifyIsOnAndCertMissing() {
        BaseError error = assertThrows(
                BaseError.class,
                () -> SslUtils.getSslConfig("SSL_VERIFY", "SSL_CERT", List.of(), true)
        );

        assertEquals(StatusCode.COMMON_SSL_CERT_INVALID, error.getStatus());
    }

    @Test
    void createStrictSslContextIgnoresMissingCertFile() {
        assertSame(
                SSLContext.class,
                SslUtils.createStrictSslContext("target/missing-cert.pem").getClass()
        );
    }

    @Test
    void strictSslContextKeepsTls13() {
        List<String> protocols = List.of(
                SslUtils.createStrictSslContext(null).getDefaultSSLParameters().getProtocols()
        );

        assertTrue(protocols.contains("TLSv1.3"));
        assertFalse(protocols.contains("TLSv1"));
        assertFalse(protocols.contains("TLSv1.1"));
    }

    @Test
    void insecureSslContextKeepsTls13() {
        List<String> protocols = List.of(
                SslUtils.createInsecureSslContext().getDefaultSSLParameters().getProtocols()
        );

        assertTrue(protocols.contains("TLSv1.3"));
        assertFalse(protocols.contains("TLSv1"));
        assertFalse(protocols.contains("TLSv1.1"));
    }
}
