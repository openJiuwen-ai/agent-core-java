// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * SSL工具类
 *
 * <p>提供SSL/TLS相关的配置和工具方法。</p>
 */
public final class SslUtils {

    private static final int MAX_CERT_SIZE = 1024 * 1024; // 1MB

    /**
     * 私有构造函数，防止实例化
     */
    private SslUtils() {
    }

    /**
     * 获取SSL配置
     *
     * @param verifySwitchEnv 验证开关环境变量名
     * @param sslCertEnv SSL证书环境变量名
     * @param triggerValue 触发值列表
     * @param urlIsHttps URL是否使用HTTPS
     * @return SSL配置数组 [是否验证, 证书路径]
     */
    public static Object[] getSslConfig(String verifySwitchEnv, String sslCertEnv,
                                         List<String> triggerValue, boolean urlIsHttps) {
        if (!urlIsHttps) {
            return new Object[]{false, false};
        }

        boolean isSslVerifyOff = isBoolEnv(verifySwitchEnv, triggerValue);
        String sslCert = System.getenv(sslCertEnv);

        if (isSslVerifyOff) {
            return new Object[]{false, false};
        }

        if (sslCert == null) {
            throw new IllegalArgumentException(
                String.format("when %s=true, must provide ssl cert %s", verifySwitchEnv, sslCertEnv)
            );
        }

        return new Object[]{true, sslCert};
    }

    /**
     * 创建严格的SSL上下文
     *
     * <p>Java版本简化实现，实际使用时可以配置SSLContext。</p>
     *
     * @param sslCert SSL证书路径
     * @return SSL配置信息
     */
    public static String createStrictSslContext(String sslCert) {
        if (sslCert != null) {
            Path certPath = Paths.get(sslCert);
            secureLoadCert(certPath);
        }
        return "SSL context configured";
    }

    /**
     * 解析布尔环境变量
     *
     * @param name 环境变量名
     * @param triggerValue 触发值列表
     * @return 是否匹配触发值
     */
    public static boolean isBoolEnv(String name, List<String> triggerValue) {
        String value = System.getenv(name);
        if (value == null) {
            return false;
        }
        return triggerValue.contains(value.trim().toLowerCase());
    }

    /**
     * 安全加载证书
     *
     * @param certPath 证书路径
     */
    private static void secureLoadCert(Path certPath) {
        if (!Files.exists(certPath)) {
            throw new IllegalArgumentException("Certificate file does not exist: " + certPath);
        }

        try {
            // 检查文件大小
            long size = Files.size(certPath);
            if (size == 0 || size > MAX_CERT_SIZE) {
                throw new IllegalArgumentException(
                    String.format("Certificate file size is invalid: %d bytes", size)
                );
            }

            // 读取证书内容
            String content = Files.readString(certPath);
            if (content.isEmpty()) {
                throw new IllegalArgumentException("Certificate file content is empty");
            }

            // 检查是否在允许的目录中（如果设置了SAFE_CERT_DIR）
            String safeCertDir = System.getenv("SAFE_CERT_DIR");
            if (safeCertDir != null) {
                Path safePrefix = Paths.get(safeCertDir).toAbsolutePath().normalize();
                Path realCertPath = certPath.toAbsolutePath().normalize();

                if (!realCertPath.startsWith(safePrefix)) {
                    throw new IllegalArgumentException(
                        "Certificate path is outside the allowed directory"
                    );
                }
            }

        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read certificate file", e);
        }
    }
}