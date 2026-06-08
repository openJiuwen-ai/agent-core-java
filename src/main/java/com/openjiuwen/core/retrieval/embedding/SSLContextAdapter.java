/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;

/**
 * Mirrors Python's {@code SSLContextAdapter} in
 * {@code openjiuwen/core/retrieval/embedding/utils.py}.
 */
public class SSLContextAdapter {

    private final SSLContext sslContext;

    public SSLContextAdapter(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public HttpClient.Builder apply(HttpClient.Builder builder) {
        return builder.sslContext(sslContext);
    }
}
