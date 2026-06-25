/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.schema.ModelHttpVersion;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReActAgentConfigTest {
    @Test
    void configureModelClientDefaultsHttpVersionToNull() {
        ReActAgentConfig config = new ReActAgentConfig();

        config.configureModelClient("openai", "key", "https://example.test/v1", "gpt-test", false);

        assertThat(config.getModelClientConfig().getHttpVersion()).isNull();
    }

    @Test
    void configureModelClientAcceptsHttpVersionAndPreservesCustomHeaders() {
        Map<String, Object> customHeaders = new LinkedHashMap<>();
        customHeaders.put("X-Trace", "trace-1");
        ReActAgentConfig config = new ReActAgentConfig();
        config.configureCustomHeaders(customHeaders);

        config.configureModelClient(
                "openai",
                "key",
                "https://example.test/v1",
                "gpt-test",
                false,
                ModelHttpVersion.HTTP_1_1);

        assertThat(config.getModelClientConfig().getHttpVersion()).isEqualTo(ModelHttpVersion.HTTP_1_1);
        assertThat(config.getModelClientConfig().getCustomHeaders()).containsExactlyEntriesOf(customHeaders);
        assertThat(config.getModelConfigObj().getModelName()).isEqualTo("gpt-test");
    }

    @Test
    void configureModelClientSnakeCaseAcceptsHttpVersionAndReusesModelConfig() {
        ReActAgentConfig config = new ReActAgentConfig();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder().modelName("old-model").build();
        config.setModelConfigObj(requestConfig);

        config.configure_model_client(
                "openai",
                "key",
                "https://example.test/v1",
                "new-model",
                true,
                ModelHttpVersion.HTTP_2);

        assertThat(config.getModelClientConfig().getHttpVersion()).isEqualTo(ModelHttpVersion.HTTP_2);
        assertThat(config.getModelConfigObj()).isSameAs(requestConfig);
        assertThat(config.getModelConfigObj().getModelName()).isEqualTo("new-model");
    }
}
