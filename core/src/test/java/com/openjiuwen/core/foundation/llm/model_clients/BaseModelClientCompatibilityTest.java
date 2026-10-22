
package com.openjiuwen.core.foundation.llm.model_clients;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class BaseModelClientCompatibilityTest {
    @Test
    void builtHttpClientShouldPreferHttp11LikePythonHttpxDefault() {
        InspectableModelClient client = newInspectableModelClient();

        assertThat(client.buildForTest().version()).isEqualTo(HttpClient.Version.HTTP_1_1);
    }

    @Test
    void streamingRequestShouldAskGatewayToReturnUsageInLastChunk() {
        InspectableModelClient client = newInspectableModelClient();

        Map<String, Object> params = client.buildParamsForTest("hello", true, null);

        assertThat(params.get("stream")).isEqualTo(true);
        assertThat(params.get("stream_options")).isEqualTo(Map.of("include_usage", true));
    }

    @Test
    void nonStreamingRequestShouldNotAddStreamUsageOption() {
        InspectableModelClient client = newInspectableModelClient();

        Map<String, Object> params = client.buildParamsForTest("hello", false, null);

        assertThat(params.get("stream")).isEqualTo(false);
        assertThat(params).doesNotContainKey("stream_options");
    }

    @Test
    void streamingRequestShouldPreserveConfiguredStreamOptions() {
        InspectableModelClient client = newInspectableModelClient();
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("stream_options", Map.of("custom_flag", true));

        Map<String, Object> params = client.buildParamsForTest("hello", true, kwargs);

        assertThat(params.get("stream_options")).isEqualTo(Map.of("custom_flag", true, "include_usage", true));
    }

    private static InspectableModelClient newInspectableModelClient() {
        return new InspectableModelClient(ModelRequestConfig.builder().modelName("demo-model").build(),
                ModelClientConfig.builder().clientProvider("OpenAI").apiKey("test-key")
                        .apiBase("https://example.com/v1").verifySsl(false).build());
    }

    private static final class InspectableModelClient extends BaseModelClient {
        private InspectableModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        private HttpClient buildForTest() {
            return buildHttpClient(1.0);
        }

        private Map<String, Object> buildParamsForTest(Object messages, boolean stream, Map<String, Object> kwargs) {
            return buildRequestParams(messages, null, null, null, null, null, null, stream, kwargs);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                String languageType, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark,
                String negativePrompt, Integer seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }
    }
}
