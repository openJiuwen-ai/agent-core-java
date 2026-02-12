// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试模型信息类
 */
class ModeInfoTest {

    @Test
    @DisplayName("测试 BaseModelInfo 别名和验证器")
    void testBaseModelInfoAliasAndValidator() {
        // alias "model" 应该通过验证器填充 model_name
        BaseModelInfo info = new BaseModelInfo.Builder()
            .apiBase("http://x")
            .model("gpt-4")
            .build();
        assertEquals("gpt-4", info.getModelName());

        // 直接使用 model_name 不会被填充，除非使用别名
        // 这是为了复制Python Pydantic的行为：当Field有alias时，直接使用字段名会导致值被重置为默认值
        BaseModelInfo info2 = new BaseModelInfo.Builder()
            .apiBase("http://x")
            .modelName("gpt-3.5")  // 直接设置modelName而不是通过alias
            .build();
        // Python行为：返回空字符串
        assertEquals("", info2.getModelName());
    }

    @Test
    @DisplayName("测试 timeout 验证")
    void testTimeoutValidation() {
        // timeout 必须大于0
        assertThrows(IllegalArgumentException.class, () -> {
            new BaseModelInfo.Builder()
                .apiBase("http://x")
                .timeout(0)
                .build();
        });
    }

    @Test
    @DisplayName("测试 ModelConfig record")
    void testModelConfigRecord() {
        BaseModelInfo modelInfo = new BaseModelInfo.Builder()
            .apiBase("http://x")
            .model("gpt-4")
            .build();
        
        ModelConfig cfg = new ModelConfig("OpenAI", modelInfo);
        
        assertEquals("OpenAI", cfg.modelProvider());
        assertNotNull(cfg.modelInfo());
        assertTrue(cfg.modelInfo() instanceof BaseModelInfo);
    }
}


