/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.vendor_specific;

import com.openjiuwen.core.foundation.store.base_reranker.RerankerConfig;
import com.openjiuwen.core.retrieval.reranker.DashscopeReranker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's deprecated reranker alias in
 * {@code openjiuwen/extensions/vendor_specific/aliyun_reranker.py}.
 */
class AliyunRerankerTest {

    @Test
    @SuppressWarnings("deprecation")
    void exposesDeprecatedAliasForDashscopeReranker() {
        AliyunReranker reranker = new AliyunReranker(baseConfig());

        assertThat(AliyunReranker.PYTHON_MODULE)
                .isEqualTo("openjiuwen/extensions/vendor_specific/aliyun_reranker.py");
        assertThat(AliyunReranker.EXPORTED_SYMBOLS).isEqualTo(List.of("AliyunReranker"));
        assertThat(AliyunReranker.ALIAS_TARGET).isEqualTo(DashscopeReranker.class);
        assertThat(reranker).isInstanceOf(DashscopeReranker.class);
        assertThat(reranker.getApiUrl()).isEqualTo("https://dashscope.aliyuncs.com/api/v1");
        assertThat(AliyunReranker.DEPRECATION_MESSAGE)
                .contains("deprecated")
                .contains("DashscopeReranker");
    }

    private static RerankerConfig baseConfig() {
        return RerankerConfig.builder()
                .modelName("qwen3-rerank")
                .apiKey("test-api-key")
                .apiBase("https://dashscope.aliyuncs.com/api/v1")
                .build();
    }
}
