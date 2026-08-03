/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import javax.net.ssl.SSLContext;

/**
 * Mirrors Python's aiohttp {@code TCPConnector} auth result in
 * {@code openjiuwen/core/foundation/tool/auth/auth_callback.py}.
 */
public final class SslAuthConnector {

    private final boolean verifySsl;
    private final String sslCertPath;
    private final SSLContext sslContext;

    private SslAuthConnector(boolean verifySsl, String sslCertPath, SSLContext sslContext) {
        this.verifySsl = verifySsl;
        this.sslCertPath = sslCertPath;
        this.sslContext = sslContext;
    }

    public static SslAuthConnector strict(String sslCertPath, SSLContext sslContext) {
        return new SslAuthConnector(true, sslCertPath, sslContext);
    }

    public static SslAuthConnector disabled() {
        return new SslAuthConnector(false, null, null);
    }

    public boolean isVerifySsl() {
        return verifySsl;
    }

    public String getSslCertPath() {
        return sslCertPath;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public Object sslValue() {
        return verifySsl ? sslContext : Boolean.FALSE;
    }
}
