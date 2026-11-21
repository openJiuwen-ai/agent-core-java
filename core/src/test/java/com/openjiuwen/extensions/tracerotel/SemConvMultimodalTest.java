/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * SemConv 多模态摘要常量（openjiuwen.multimodal.*）的语义契约测试。
 *
 * <p>对应 issue agent-solution#851 / agent-core-java#316：8 个常量值必须与
 * 多模态 OTel 核心指标上报 Spec v1.0 §4.1 完全一致，禁止漂移。</p>
 */
class SemConvMultimodalTest {

    @Test
    void multimodalRequestHasMedia_constantMatchesContract() {
        assertThat(SemConv.OJ_MM_REQUEST_HAS_MEDIA)
                .isEqualTo("openjiuwen.multimodal.request.has_media");
    }

    @Test
    void multimodalRequestCount_constantMatchesContract() {
        assertThat(SemConv.OJ_MM_REQUEST_COUNT)
                .isEqualTo("openjiuwen.multimodal.request.count");
    }

    @Test
    void multimodalRequestTypes_constantMatchesContract() {
        assertThat(SemConv.OJ_MM_REQUEST_TYPES)
                .isEqualTo("openjiuwen.multimodal.request.types");
    }

    @Test
    void multimodalRequestBytes_constantMatchesContract() {
        assertThat(SemConv.OJ_MM_REQUEST_BYTES)
                .isEqualTo("openjiuwen.multimodal.request.bytes");
    }

    @Test
    void multimodalOutputCount_constantMatchesContract() {
        assertThat(SemConv.OJ_MM_OUTPUT_COUNT)
                .isEqualTo("openjiuwen.multimodal.output.count");
    }

    @Test
    void multimodalOutputTypes_constantMatchesContract() {
        assertThat(SemConv.OJ_MM_OUTPUT_TYPES)
                .isEqualTo("openjiuwen.multimodal.output.types");
    }

    @Test
    void multimodalOutputBytes_constantMatchesContract() {
        assertThat(SemConv.OJ_MM_OUTPUT_BYTES)
                .isEqualTo("openjiuwen.multimodal.output.bytes");
    }

    @Test
    void multimodalErrorCount_constantMatchesContract() {
        assertThat(SemConv.OJ_MM_ERROR_COUNT)
                .isEqualTo("openjiuwen.multimodal.error.count");
    }
}
