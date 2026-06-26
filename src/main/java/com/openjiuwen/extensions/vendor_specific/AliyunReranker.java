/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.vendor_specific;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.base_reranker.RerankerConfig;
import com.openjiuwen.core.retrieval.reranker.DashscopeReranker;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

/**
 * Deprecated alias for DashScope reranker.
 *
 * <p>Mirrors Python's {@code AliyunReranker = DashscopeReranker} in
 * {@code openjiuwen/extensions/vendor_specific/aliyun_reranker.py}.</p>
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class AliyunReranker extends DashscopeReranker {

    public static final String PYTHON_MODULE = "openjiuwen/extensions/vendor_specific/aliyun_reranker.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of("AliyunReranker");
    public static final Class<DashscopeReranker> ALIAS_TARGET = DashscopeReranker.class;
    public static final String DEPRECATION_MESSAGE =
            "AliyunReranker is deprecated, please use openjiuwen.core.retrieval.DashscopeReranker instead.";

    static {
        Loggers.COMMON.warning(DEPRECATION_MESSAGE);
    }

    public AliyunReranker(RerankerConfig config) {
        super(config);
    }

    public AliyunReranker(RerankerConfig config,
                          int maxRetries,
                          double retryWait,
                          Map<String, String> extraHeaders,
                          HttpClient httpClient) {
        super(config, maxRetries, retryWait, extraHeaders, httpClient);
    }
}
