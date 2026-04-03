/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import javax.net.ssl.*;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * SSL utilities — creates strict SSL contexts for secure HTTPS communication.
 * <p>
 * Enforces TLS 1.2+ with strong cipher suites, mirroring the Python implementation.
 */
public final class SslUtils {

    private SslUtils() {
    }

    /**
     * Create a strict {@link SSLContext} optionally loading a CA certificate.
     *
     * @param sslCertPath path to the CA cert file (PEM), or null
     * @return configured SSLContext
     */
    public static SSLContext createStrictSslContext(String sslCertPath) {
        try {
            SSLContext ctx = SSLContext.getInstance("TLSv1.2");

            if (sslCertPath != null) {
                Path certPath = Path.of(sslCertPath).toRealPath();

                // Validate cert directory
                String safeCertDir = System.getenv("SAFE_CERT_DIR");
                if (safeCertDir == null || safeCertDir.isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                        "SAFE_CERT_DIR is not set", null, null, null);
                }
                Path safePrefix = Path.of(safeCertDir).toRealPath();
                if (!certPath.startsWith(safePrefix)) {
                    throw ErrorHelper.buildError(StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                        "certificate path is outside the allowed directory", null, null, null);
                }

                // Validate file size
                long size = Files.size(certPath);
                if (size == 0 || size > 1024 * 1024) {
                    throw ErrorHelper.buildError(StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                        "file size is invalid", null, null, null);
                }

                // Load the CA certificate
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate caCert;
                try (InputStream is = Files.newInputStream(certPath)) {
                    caCert = (X509Certificate) cf.generateCertificate(is);
                }

                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                ks.load(null, null);
                ks.setCertificateEntry("ca", caCert);

                TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ks);

                ctx.init(null, tmf.getTrustManagers(), null);
            } else {
                ctx.init(null, null, null);
            }

            return ctx;
        } catch (com.openjiuwen.core.common.exception.BaseError e) {
            throw e;
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                "failed to create SSL context: " + e.getMessage(), null, e, null);
        }
    }

    /**
     * Create an insecure SSL context that trusts every certificate.
     * Intended only for explicit verify=false scenarios to mirror Python behaviour.
     */
    public static SSLContext createInsecureSslContext() {
        try {
            TrustManager[] trustAllManagers = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext ctx = SSLContext.getInstance("TLSv1.2");
            ctx.init(null, trustAllManagers, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                    "failed to create insecure SSL context: " + e.getMessage(), null, e, null);
        }
    }

    /**
     * Configure SSL behavior for an {@link HttpClient.Builder} targeting the given URL.
     *
     * @param builder     client builder to configure
     * @param targetUrl   request target URL
     * @param verifySsl   whether to verify the remote certificate chain
     * @param sslCertPath optional CA certificate path
     */
    public static void configureHttpClientSsl(HttpClient.Builder builder,
                                              String targetUrl,
                                              boolean verifySsl,
                                              String sslCertPath) {
        if (builder == null || targetUrl == null || targetUrl.isBlank()) {
            return;
        }

        URI targetUri = URI.create(targetUrl);
        if (!"https".equalsIgnoreCase(targetUri.getScheme())) {
            return;
        }

        if (!verifySsl) {
            builder.sslContext(createInsecureSslContext());
            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("");
            builder.sslParameters(sslParameters);
            return;
        }

        if (sslCertPath != null && !sslCertPath.isBlank()) {
            builder.sslContext(createStrictSslContext(sslCertPath));
        }
    }

    /**
     * Get SSL config based on environment variables.
     *
     * @param verifySwitchEnv env var name for verify switch
     * @param sslCertEnv      env var name for cert path
     * @param triggerValues   values that disable SSL verification
     * @param urlIsHttps      whether the target URL uses HTTPS
     * @return two-element array: [sslVerify, sslCertPath] (Boolean and String)
     */
    public static Object[] getSslConfig(String verifySwitchEnv, String sslCertEnv,
                                        java.util.List<String> triggerValues, boolean urlIsHttps) {
        if (!urlIsHttps) {
            return new Object[]{false, null};
        }
        String envValue = readEnvOrProperty(verifySwitchEnv);
        boolean isOff = envValue != null && triggerValues.contains(envValue.trim().toLowerCase());
        if (isOff) {
            return new Object[]{false, null};
        }
        String sslCert = readEnvOrProperty(sslCertEnv);
        return new Object[]{true, sslCert};
    }

    private static String readEnvOrProperty(String key) {
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String propertyValue = System.getProperty(key);
        return propertyValue != null && !propertyValue.isBlank() ? propertyValue : null;
    }
}
