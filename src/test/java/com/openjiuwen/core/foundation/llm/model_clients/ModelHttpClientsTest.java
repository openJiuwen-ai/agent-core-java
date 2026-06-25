/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelHttpVersion;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ModelHttpClientsTest {

    @Test
    void buildsHttp2ClientWhenVersionConfigured() {
        HttpClient client = ModelHttpClients.builder(clientConfig(ModelHttpVersion.HTTP_2), "https://api.example.test/v1")
                .connectTimeout(Duration.ofSeconds(7))
                .build();

        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_2);
        assertThat(client.connectTimeout()).hasValue(Duration.ofSeconds(7));
    }

    @Test
    void buildsHttp11ClientWhenVersionConfigured() {
        HttpClient client = ModelHttpClients.builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
        assertThat(client.connectTimeout()).hasValue(Duration.ofSeconds(5));
    }

    @Test
    void leavesJdkDefaultVersionWhenHttpVersionIsNotConfigured() {
        HttpClient client = ModelHttpClients.builder(clientConfig(null), "https://api.example.test/v1")
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        assertThat(client).isNotNull();
        assertThat(client.connectTimeout()).hasValue(Duration.ofSeconds(3));
    }

    @Test
    void connectTimeoutCanBeOverridden() {
        HttpClient client = ModelHttpClients.builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                .connectTimeout(Duration.ofSeconds(11))
                .build();

        assertThat(client.connectTimeout()).hasValue(Duration.ofSeconds(11));
    }

    @Test
    void buildUsesConfigTimeoutAndVersionWhenTimeoutIsNotExplicitlyOverridden() {
        HttpClient client = ModelHttpClients.builder(clientConfig(ModelHttpVersion.HTTP_2), "https://api.example.test/v1")
                .build();

        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_2);
        assertThat(client.connectTimeout()).hasValue(Duration.ofSeconds(9));
    }

    private static ModelClientConfig clientConfig(ModelHttpVersion httpVersion) {
        return ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiBase("https://api.example.test/v1")
                .httpVersion(httpVersion)
                .timeout(9.0D)
                .build();
    }
}
