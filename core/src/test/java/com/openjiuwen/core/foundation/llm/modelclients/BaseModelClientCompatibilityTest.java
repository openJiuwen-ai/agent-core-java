package com.openjiuwen.core.foundation.llm.modelclients;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

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
