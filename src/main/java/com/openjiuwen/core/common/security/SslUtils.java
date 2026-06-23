/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Mirrors Python's {@code SslUtils} in
 * {@code openjiuwen/core/common/security/ssl_utils.py}.
 */
public final class SslUtils {

    private static final long MAX_CERT_SIZE = 1024L * 1024L;
    private static volatile Function<String, String> envReader = System::getenv;

    private SslUtils() {
    }

    public static SSLContext createStrictSslContext(String sslCertPath) {
        try {
            SSLContext context = SSLContext.getInstance("TLSv1.2");
            context.init(null, null, null);

            if (sslCertPath != null && !sslCertPath.isBlank()) {
                Path certPath = Path.of(sslCertPath);
                if (Files.isRegularFile(certPath, LinkOption.NOFOLLOW_LINKS)) {
                    Path realCertPath = certPath.toRealPath();
                    String safeCertDir = envReader.apply("SAFE_CERT_DIR");
                    if (safeCertDir == null || safeCertDir.isBlank()) {
                        throw ErrorHelper.buildError(
                                StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                                "error_msg",
                                "SAFE_CERT_DIR is not set"
                        );
                    }
                    Path safePrefix = Path.of(safeCertDir).toRealPath();
                    if (!realCertPath.startsWith(safePrefix)) {
                        throw ErrorHelper.buildError(
                                StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                                "error_msg",
                                "certificate path is outside the allowed directory"
                        );
                    }
                    secureLoadCert(context, realCertPath);
                }
            }
            return context;
        } catch (BaseError error) {
            throw error;
        } catch (Exception error) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                    "error_msg",
                    "failed to create SSL context"
            );
        }
    }

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
            SSLContext context = SSLContext.getInstance("TLSv1.2");
            context.init(null, trustAllManagers, new SecureRandom());
            return context;
        } catch (Exception error) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                    "error_msg",
                    "failed to create insecure SSL context"
            );
        }
    }

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
        builder.sslContext(createStrictSslContext(sslCertPath));
    }

    public static Object[] getSslConfig(String verifySwitchEnv,
                                        String sslCertEnv,
                                        List<String> triggerValues,
                                        boolean urlIsHttps) {
        if (!urlIsHttps) {
            return new Object[]{false, false};
        }

        if (boolEnv(verifySwitchEnv, triggerValues)) {
            return new Object[]{false, false};
        }

        String sslCert = envReader.apply(sslCertEnv);
        if (sslCert == null) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_SSL_CERT_INVALID,
                    "error_msg",
                    "when " + verifySwitchEnv + "=true, must provide ssl cert " + sslCertEnv
            );
        }
        return new Object[]{true, sslCert};
    }

    private static boolean boolEnv(String name, List<String> triggerValues) {
        String value = envReader.apply(name);
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (String triggerValue : triggerValues) {
            if (normalized.equals(String.valueOf(triggerValue).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    static void setEnvReaderForTests(Function<String, String> reader) {
        envReader = reader != null ? reader : System::getenv;
    }

    static void resetEnvReaderForTests() {
        envReader = System::getenv;
    }

    private static void secureLoadCert(SSLContext context, Path certPath) throws Exception {
        long size = Files.size(certPath);
        if (size == 0 || size > MAX_CERT_SIZE) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                    "error_msg",
                    "file size is invalid"
            );
        }

        byte[] certBytes = Files.readAllBytes(certPath);
        if (certBytes.length == 0) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                    "error_msg",
                    "file content is empty"
            );
        }

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(
                new ByteArrayInputStream(certBytes)
        );

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca", certificate);

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
        );
        trustManagerFactory.init(keyStore);
        context.init(null, trustManagerFactory.getTrustManagers(), null);
    }
}
