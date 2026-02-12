// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.fixtures.MockLLMModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试模型配置类
 */
class ConfigSchemaTest {

    @Test
    @DisplayName("测试 ModelRequestConfig 别名和默认值")
    void testModelRequestConfigAlias() {
        ModelRequestConfig cfg = new ModelRequestConfig.Builder()
            .model("gpt-4")
            .temperature(0.5)
            .build();
        
        assertEquals("gpt-4", cfg.getModelName());
        assertEquals(0.5, cfg.getTemperature());
    }

    @Test
    @DisplayName("测试 ModelRequestConfig 允许额外参数")
    void testModelRequestConfigAllowsExtra() {
        ModelRequestConfig cfg = new ModelRequestConfig.Builder()
            .model("gpt-4")
            .extraParam("extra_param", 123)
            .build();
        
        assertEquals(123, cfg.getExtraParam("extra_param"));
    }

    @Test
    @DisplayName("测试 ModelClientConfig 默认值")
    void testModelClientConfigDefaults() {
        ModelClientConfig cfg = new ModelClientConfig.Builder()
            .clientProvider("OpenAI")
            .apiKey("k")
            .apiBase("http://x")
            .build();
        
        assertEquals(60.0, cfg.getTimeout());
        assertEquals(3, cfg.getMaxRetries());
        assertTrue(cfg.isVerifySsl());
    }

    @Test
    @DisplayName("测试 ModelClientConfig 必填字段验证")
    void testModelClientConfigRequiredFields() {
        // 配置验证逻辑移至BaseModelClient.validateConfig()
        // 在创建ModelClient时验证，抛出JiuWenBaseException而不是IllegalArgumentException
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
            .modelName("test-model")
            .build();
        ModelClientConfig clientConfig = new ModelClientConfig.Builder()
            .clientProvider("OpenAI")
            .clientId("test-client")
            .apiKey("k")
            // 缺少 apiBase (空字符串)
            .apiBase("")
            .verifySsl(false)
            .build();

        JiuWenBaseException exception = assertThrows(JiuWenBaseException.class, () -> {
            new MockLLMModel(modelConfig, clientConfig);
        });

        assertEquals(StatusCode.MODEL_SERVICE_CONFIG_ERROR.getCode(), exception.getErrorCode());
    }
}


