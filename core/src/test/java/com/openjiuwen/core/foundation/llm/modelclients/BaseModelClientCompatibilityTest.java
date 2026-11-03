/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

class BaseModelClientCompatibilityTest {

    @Test
    void builtHttpClientDefaultsToHttp11WhenVersionIsNotConfigured() {
        ModelClientConfig config = ModelClientConfig.builder()
                .clientProvider("OpenAI")
                .apiKey("test-key")
                .apiBase("https://example.com/v1")
                .verifySsl(false)
                .build();

        HttpClient client = ModelHttpClients.builder(config, config.getApiBase())
                .withSsl()
                .withProxy()
                .build();

        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
    }
}
