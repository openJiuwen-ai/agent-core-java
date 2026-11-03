/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import com.openjiuwen.core.common.security.SslUtils;
import com.openjiuwen.core.runner.callback.AbortError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SSL authentication strategy.
 *
 * <p>Mirrors Python's {@code SSLAuthStrategy} in
 * {@code openjiuwen/core/foundation/tool/auth/auth_callback.py}.
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
            String url = stringValue(config.getOrDefault("url", ""));
            boolean urlIsHttps = url.isBlank() || url.toLowerCase().startsWith("https://");
            String verifySwitchEnv = stringValue(config.getOrDefault("verify_switch_env", "SSL_VERIFY"));
            String sslCertEnv = stringValue(config.getOrDefault("ssl_cert_env", "SSL_CERT"));

            Object[] sslConfig = SslUtils.getSslConfig(verifySwitchEnv, sslCertEnv, List.of("false"), urlIsHttps);
            boolean sslVerify = Boolean.TRUE.equals(sslConfig[0]);
            String sslCert = sslConfig[1] instanceof String value ? value : null;
            SslAuthConnector connector;
            if (sslVerify) {
                SSLContext sslContext = SslUtils.createStrictSslContext(sslCert);
                connector = SslAuthConnector.strict(sslCert, sslContext);
            } else {
                connector = SslAuthConnector.disabled();
            }

            Map<String, Object> authData = new LinkedHashMap<>();
            authData.put("connector", connector);
            return new ToolAuthResult(true, authData, "SSL authentication configured", null);
        } catch (Exception error) {
            String message = "Failed to create SSL connector: " + error.getMessage();
            LOG.error(message, error);
            throw new AbortError(message, error);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
