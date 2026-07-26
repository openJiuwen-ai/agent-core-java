/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import okhttp3.OkHttpClient;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * SSL utilities — creates strict SSL contexts for secure HTTPS communication.
 * <p>
 * Enforces TLS 1.2+ with strong cipher suites, mirroring the Python implementation.
 * 
 * @since 0.1.7
 */
public final class SslUtils {
    private static final String[] TLS_12_PLUS_PROTOCOLS = {"TLSv1.3", "TLSv1.2"};

    /**
     * SslUtils.
     * 
     * @since 0.1.7
     */
    private SslUtils() {
    }

    /**
     * Create a strict {@link SSLContext} optionally loading a CA certificate.
     * 
     * @param sslCertPath path to the CA cert file (PEM), or null
     * @return configured SSLContext
     * @since 0.1.7
     */
    public static SSLContext createStrictSslContext(String sslCertPath) {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            if (sslCertPath != null) {
                ctx.init(null, createCustomTrustManagers(sslCertPath), null);
            } else {
                ctx.init(null, null, null);
            }
            return ctx;
        } catch (com.openjiuwen.core.common.exception.BaseError e) {
            throw e;
        } catch (java.security.GeneralSecurityException e) {
            throw ErrorHelper.buildError(StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                    "failed to create SSL context: " + e.getMessage(), null, e, null);
        }
    }

    /**
     * Loads a CA certificate from {@code sslCertPath} (must stay under {@code SAFE_CERT_DIR})
     * and builds trust managers that trust that CA.
     *
     * @param sslCertPath absolute or relative path to a PEM/X.509 certificate file
     * @return trust managers initialized with the custom CA
     * @throws com.openjiuwen.core.common.exception.BaseError when the path is unsafe, the file is invalid,
     *         or trust manager initialization fails
     * @since 0.1.14
     */
    private static TrustManager[] createCustomTrustManagers(String sslCertPath) {
        try {
            String safeCertDir = System.getenv("SAFE_CERT_DIR");
            if (safeCertDir == null || safeCertDir.isBlank()) {
                throw ErrorHelper.buildError(StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED, "SAFE_CERT_DIR is not set",
                        null, null, null);
            }

            Path certPath = Path.of(sslCertPath).toRealPath();
            Path safePrefix = Path.of(safeCertDir).toRealPath();
            if (!certPath.startsWith(safePrefix)) {
                throw ErrorHelper.buildError(StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                        "certificate path is outside the allowed directory", null, null, null);
            }

            long size = Files.size(certPath);
            if (size == 0 || size > 1024 * 1024) {
                throw ErrorHelper.buildError(StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED, "file size is invalid",
                        null, null, null);
            }

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate caCert;
            try (InputStream is = Files.newInputStream(certPath)) {
                Certificate certificate = cf.generateCertificate(is);
                if (!(certificate instanceof X509Certificate x509Certificate)) {
                    throw ErrorHelper.buildError(StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                            "certificate is not X.509", null, null, null);
                }
                caCert = x509Certificate;
            }

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("ca", caCert);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            return tmf.getTrustManagers();
        } catch (com.openjiuwen.core.common.exception.BaseError e) {
            throw e;
        } catch (java.security.GeneralSecurityException | java.io.IOException e) {
            throw ErrorHelper.buildError(StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                    "failed to load custom trust managers: " + e.getMessage(), null, e, null);
        }
    }

    /**
     * Create an insecure SSL context that trusts every certificate.
     * Intended only for explicit verify=false scenarios to mirror Python behaviour.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static SSLContext createInsecureSslContext() {
        try {
            TrustManager[] trustAllManagers = new TrustManager[]{new X509TrustManager() {
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
            }};
            SSLContext ctx = SSLContext.getInstance("TLS");
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
     * @param builder client builder to configure
     * @param targetUrl request target URL
     * @param verifySsl whether to verify the remote certificate chain
     * @param sslCertPath optional CA certificate path
     * @since 0.1.7
     */
    public static void configureHttpClientSsl(HttpClient.Builder builder, String targetUrl, boolean verifySsl,
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
            SSLParameters sslParameters = tls12PlusParameters();
            sslParameters.setEndpointIdentificationAlgorithm("");
            builder.sslParameters(sslParameters);
            return;
        }

        if (sslCertPath != null && !sslCertPath.isBlank()) {
            builder.sslContext(createStrictSslContext(sslCertPath));
            builder.sslParameters(tls12PlusParameters());
        }
    }

    /**
     * Configure SSL behavior for an {@link OkHttpClient.Builder} targeting the given URL.
     *
     * @param builder OkHttp client builder to configure
     * @param targetUrl request target URL
     * @param shouldVerifySsl whether to verify the remote certificate chain
     * @param sslCertPath optional CA certificate path
     * @since 0.1.14
     */
    public static void configureOkHttpClientSsl(OkHttpClient.Builder builder, String targetUrl,
            boolean shouldVerifySsl, String sslCertPath) {
        if (builder == null || targetUrl == null || targetUrl.isBlank()) {
            return;
        }

        URI targetUri = URI.create(targetUrl);
        if (!"https".equalsIgnoreCase(targetUri.getScheme())) {
            return;
        }

        if (!shouldVerifySsl) {
            X509TrustManager trustManager = insecureTrustManager();
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
                builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
            } catch (java.security.GeneralSecurityException e) {
                throw ErrorHelper.buildError(StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                        "failed to configure insecure OkHttp SSL: " + e.getMessage(), null, e, null);
            }
            builder.hostnameVerifier((hostname, session) -> true);
            return;
        }

        if (sslCertPath != null && !sslCertPath.isBlank()) {
            try {
                TrustManager[] trustManagers = createCustomTrustManagers(sslCertPath);
                X509TrustManager trustManager = requireX509TrustManager(trustManagers);
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustManagers, null);
                SSLSocketFactory socketFactory = sslContext.getSocketFactory();
                builder.sslSocketFactory(socketFactory, trustManager);
            } catch (com.openjiuwen.core.common.exception.BaseError e) {
                throw e;
            } catch (java.security.GeneralSecurityException e) {
                throw ErrorHelper.buildError(StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                        "failed to configure OkHttp SSL with custom certificate: " + e.getMessage(), null, e, null);
            }
        }
    }

    /**
     * Returns a trust manager that accepts every certificate chain (verifySsl=false only).
     *
     * @return an X509 trust manager that performs no validation
     * @since 0.1.7
     */
    private static X509TrustManager insecureTrustManager() {
        return new X509TrustManager() {
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
        };
    }

    /**
     * Picks the first {@link X509TrustManager} from the array, or fails if none is present.
     *
     * @param trustManagers trust managers produced by a {@link TrustManagerFactory}
     * @return the first X509 trust manager
     * @throws RuntimeException when no X509 trust manager is available
     * @since 0.1.7
     */
    private static X509TrustManager requireX509TrustManager(TrustManager[] trustManagers) {
        if (trustManagers != null) {
            for (TrustManager tm : trustManagers) {
                if (tm instanceof X509TrustManager x509) {
                    return x509;
                }
            }
        }
        throw ErrorHelper.buildError(StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                "no X509TrustManager available for OkHttp SSL configuration", null, null, null);
    }

    /**
     * tls12PlusParameters.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static SSLParameters tls12PlusParameters() {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(TLS_12_PLUS_PROTOCOLS);
        return sslParameters;
    }

    /**
     * Get SSL config based on environment variables.
     * 
     * @param verifySwitchEnv env var name for verify switch
     * @param sslCertEnv env var name for cert path
     * @param triggerValues values that disable SSL verification
     * @param urlIsHttps whether the target URL uses HTTPS
     * @return three-element array: [sslVerify, sslCertPath, explicitlyEnabled] (Boolean, String, Boolean).
     *         When the verify switch is not explicitly set, returns {true, null, false}
     *         to indicate "use default SSL context" (trust system CAs).
     *         When explicitly set to a trigger value (e.g. "false"), returns {false, null, false}.
     *         When explicitly set to a truthy value, returns {true, sslCertPath, true}.
     * @since 0.1.7
     */
    public static Object[] getSslConfig(String verifySwitchEnv, String sslCertEnv, java.util.List<String> triggerValues,
            boolean urlIsHttps) {
        if (!urlIsHttps) {
            return new Object[]{false, null, false};
        }
        String envValue = readEnvOrProperty(verifySwitchEnv);
        boolean isOff = envValue != null && triggerValues.contains(envValue.trim().toLowerCase(Locale.ROOT));
        if (isOff) {
            return new Object[]{false, null, false};
        }
        // If verify switch is not explicitly set, use default SSL context (trust system CAs)
        if (envValue == null || envValue.isBlank()) {
            return new Object[]{true, null, false};
        }
        // Verify switch is explicitly set to a truthy value — require explicit cert
        String sslCert = readEnvOrProperty(sslCertEnv);
        return new Object[]{true, sslCert, true};
    }

    /**
     * readEnvOrProperty.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    private static String readEnvOrProperty(String key) {
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String propertyValue = System.getProperty(key);
        return propertyValue != null && !propertyValue.isBlank() ? propertyValue : null;
    }
}
