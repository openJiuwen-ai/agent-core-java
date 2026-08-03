/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Integration-style check: repeated connect refusals open the model circuit breaker.
 */
class OpenAiCompatibleModelClientCircuitBreakerTest {

    @BeforeEach
    void setProps() {
        System.setProperty("openjiuwen.llm.circuit.failure-threshold", "3");
        System.setProperty("openjiuwen.llm.circuit.open-duration-millis", "60000");
        System.setProperty("openjiuwen.llm.http.connect-timeout-seconds", "1");
    }

    @AfterEach
    void clearProps() {
        System.clearProperty("openjiuwen.llm.circuit.failure-threshold");
        System.clearProperty("openjiuwen.llm.circuit.open-duration-millis");
        System.clearProperty("openjiuwen.llm.http.connect-timeout-seconds");
    }

    @Test
    void repeatedConnectRefusalsOpenCircuitAndFailFast() {
        // 127.0.0.1 with a closed port → ConnectException / connection refused
        ModelClientConfig clientConfig = ModelClientConfig.builder().clientProvider("OpenAI").apiKey("sk-test")
                .apiBase("http://127.0.0.1:1/v1").timeout(5).build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder().modelName("test-model").build();
        Model model = new Model(clientConfig, requestConfig);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> model.invoke(List.of(new UserMessage("hello")), null, null, null, null, null,
                    null, null, null, null)).isInstanceOf(Exception.class);
        }

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> model.invoke(List.of(new UserMessage("hello")), null, null, null, null, null, null,
                null, null, null)).isInstanceOf(Exception.class).hasMessageContaining("circuit breaker open");
        // Fail-fast should not wait for connect timeout (~1s+)
        assertThat(System.currentTimeMillis() - start).isLessThan(500L);
    }
}
