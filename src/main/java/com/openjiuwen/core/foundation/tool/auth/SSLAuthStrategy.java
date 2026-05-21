/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * SSL authentication strategy.
 * <p>
 * Mirrors Python's {@code SSLAuthStrategy} class from
 * <code>foundation/tool/auth/auth_callback.py</code>.
 *
 * <p>Creates SSL/TLS configuration for HTTP connections based on the
 * authentication configuration parameters.
 */
public class SSLAuthStrategy extends AuthStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(SSLAuthStrategy.class);

    public SSLAuthStrategy() {
        super(AuthType.SSL);
    }

    @Override
    public ToolAuthResult authenticate(ToolAuthConfig authConfig) {
        try {
            Map<String, Object> config = authConfig.getConfig();
            String url = (String) config.getOrDefault("url", "");
            boolean urlIsHttps = !url.isEmpty() && url.toLowerCase().startsWith("https://");

            String verifySwitchEnv = (String) config.getOrDefault("verify_switch_env", "SSL_VERIFY");
            String sslCertEnv = (String) config.getOrDefault("ssl_cert_env", "SSL_CERT");

            // Read SSL verify flag from environment
            boolean sslVerify = getEnvBoolean(verifySwitchEnv, true);
            String sslCert = getEnvString(sslCertEnv, null);

            Map<String, Object> authData = new HashMap<>();
            authData.put("ssl_verify", sslVerify);
            authData.put("ssl_cert", sslCert);
            authData.put("url_is_https", urlIsHttps);

            return new ToolAuthResult(true, authData, "SSL authentication configured");
        } catch (Exception e) {
            LOG.error("Failed to create SSL connector: {}", e.getMessage());
            return new ToolAuthResult(false, new HashMap<>(),
                    "Failed to create SSL connector: " + e.getMessage(), e);
        }
    }

    private boolean getEnvBoolean(String envVar, boolean defaultValue) {
        String value = System.getenv(envVar);
        if (value == null) {
            value = System.getProperty(envVar);
        }
        if (value == null) {
            return defaultValue;
        }
        return !"false".equalsIgnoreCase(value) && !"0".equals(value);
    }

    private String getEnvString(String envVar, String defaultValue) {
        String value = System.getenv(envVar);
        if (value == null) {
            value = System.getProperty(envVar);
        }
        return value != null ? value : defaultValue;
    }
}
