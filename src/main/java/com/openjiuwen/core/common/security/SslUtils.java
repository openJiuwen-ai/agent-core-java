package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * SSL/TLS工具类
 * 
 * <p>提供严格的SSL上下文配置、证书加载等功能。
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public class SslUtils {

    private SslUtils() {
        // Utility class
    }

    /**
     * SSL配置类
     */
    public static class SslConfig {
        private final boolean sslVerify;
        private final String sslCert;

        public SslConfig(boolean sslVerify, String sslCert) {
            this.sslVerify = sslVerify;
            this.sslCert = sslCert;
        }

        public boolean isSslVerify() {
            return sslVerify;
        }

        public String getSslCert() {
            return sslCert;
        }
    }

    /**
     * 创建SSL适配器
     * 
     * <p>注意：Java版本不提供此方法，因为HTTP客户端（OkHttp/HttpClient）的SSL配置方式不同。
     * 请直接使用 {@link #createStrictSslContext(String)} 创建SSLContext，
     * 然后配置到您的HTTP客户端中。
     * 
     * @param verifySwitchEnv SSL验证开关环境变量名
     * @param sslCertEnv SSL证书路径环境变量名
     * @param triggerValue 触发关闭验证的值列表
     * @return SSL上下文，如果不需要SSL验证则返回null
     * @deprecated 建议直接使用 {@link #getSslConfig} 和 {@link #createStrictSslContext}
     */
    @Deprecated
    public static SSLContext createSslAdapter(String verifySwitchEnv, String sslCertEnv, List<String> triggerValue) {
        SslConfig config = getSslConfig(verifySwitchEnv, sslCertEnv, triggerValue, true);
        if (config.isSslVerify()) {
            return createStrictSslContext(config.getSslCert());
        }
        return null;
    }

    /**
     * 获取SSL配置
     * 
     * @param verifySwitchEnv SSL验证开关环境变量名
     * @param sslCertEnv SSL证书路径环境变量名
     * @param triggerValue 触发关闭验证的值列表
     * @param urlIsHttps URL是否为HTTPS
     * @return SSL配置
     */
    public static SslConfig getSslConfig(String verifySwitchEnv, String sslCertEnv, 
                                         List<String> triggerValue, boolean urlIsHttps) {
        if (!urlIsHttps) {
            return new SslConfig(false, null);
        }

        boolean isSslVerifyOff = boolEnv(verifySwitchEnv, triggerValue);
        String sslCert = System.getenv(sslCertEnv);

        if (isSslVerifyOff) {
            return new SslConfig(false, null);
        }

        if (sslCert == null) {
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            params.put("error_msg", "when " + verifySwitchEnv + "=true, must provide ssl cert " + sslCertEnv);
            throw new JiuWenBaseException(
                StatusCode.COMMON_SSL_CERT_INVALID.getCode(),
                StatusCode.COMMON_SSL_CERT_INVALID.formatMessage(params)
            );
        }

        return new SslConfig(true, sslCert);
    }

    /**
     * 创建严格的SSL上下文
     * 
     * @param sslCert 证书文件路径
     * @return SSL上下文
     */
    public static SSLContext createStrictSslContext(String sslCert) {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            
            // Initialize with default trust manager if no cert provided
            if (sslCert == null || sslCert.isEmpty()) {
                ctx.init(null, null, null);
                return ctx;
            }

            // Load certificate
            Path certPath = Paths.get(sslCert);
            if (!Files.isRegularFile(certPath)) {
                ExceptionUtils.raiseException(
                    StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                    "certificate file does not exist",
                    null
                );
            }

            String realCertPath = certPath.toRealPath().toString();
            
            // Check if cert is in safe directory
            String safeCertDir = System.getenv("SAFE_CERT_DIR");
            if (safeCertDir == null || safeCertDir.isEmpty()) {
                ExceptionUtils.raiseException(
                    StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                    "SAFE_CERT_DIR is not set",
                    null
                );
            }

            String safePrefix = Paths.get(safeCertDir).toRealPath().toString();
            if (!realCertPath.startsWith(safePrefix)) {
                ExceptionUtils.raiseException(
                    StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                    "certificate path is outside the allowed directory",
                    null
                );
            }

            // Check file attributes
            BasicFileAttributes attrs = Files.readAttributes(certPath, BasicFileAttributes.class);
            if (!attrs.isRegularFile()) {
                ExceptionUtils.raiseException(
                    StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                    "file path is invalid",
                    null
                );
            }

            long size = attrs.size();
            if (size == 0 || size > 1024 * 1024) {
                ExceptionUtils.raiseException(
                    StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                    "file size is invalid",
                    null
                );
            }

            // Read certificate
            byte[] certBytes = Files.readAllBytes(certPath);
            if (certBytes.length == 0) {
                ExceptionUtils.raiseException(
                    StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                    "file content is empty",
                    null
                );
            }

            // Load certificate into trust manager
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(certBytes)
            );

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", certificate);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            );
            tmf.init(keyStore);

            ctx.init(null, tmf.getTrustManagers(), null);

            return ctx;
        } catch (JiuWenBaseException e) {
            throw e;
        } catch (Exception e) {
            ExceptionUtils.raiseException(
                StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED,
                "failed to create SSL context: " + e.getMessage(),
                e
            );
            return null; // unreachable
        }
    }

    /**
     * 解析布尔环境变量
     * 
     * @param name 环境变量名
     * @param triggerValue 触发值列表
     * @return 环境变量值是否在触发值列表中
     */
    private static boolean boolEnv(String name, List<String> triggerValue) {
        String value = System.getenv(name);
        if (value == null) {
            value = System.getProperty(name);
        }
        if (value == null) {
            return false;
        }
        return triggerValue.contains(value.trim().toLowerCase());
    }
}

