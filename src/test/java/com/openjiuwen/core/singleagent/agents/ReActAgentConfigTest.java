// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link ReActAgentConfig}.
 */
class ReActAgentConfigTest {
    @Test
    void testDefaultValues() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();

        assertThat(config.getMemScopeId()).isEmpty();
        assertThat(config.getModelName()).isEmpty();
        assertThat(config.getModelProvider()).isEqualTo("openai");
        assertThat(config.getApiKey()).isEmpty();
        assertThat(config.getApiBase()).isEmpty();
        assertThat(config.getPromptTemplateName()).isEmpty();
        assertThat(config.getPromptTemplate()).isNotNull().isEmpty();
        assertThat(config.getCustomHeaders()).isNull();
        assertThat(config.getMaxIterations()).isEqualTo(5);
        assertThat(config.getMaxParallelToolCalls()).isEqualTo(3);
        assertThat(config.getModelClientConfig()).isNull();
        assertThat(config.getModelConfigObj()).isNull();
        assertThat(config.getSysOperationId()).isNull();
        assertThat(config.getContextEngineConfig()).isNotNull();
        assertThat(config.getContextProcessors()).isNull();
    }

    @Test
    void testConfigureModel() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();
        config.configureModel("gpt-4");
        assertThat(config.getModelName()).isEqualTo("gpt-4");
    }

    @Test
    void testConfigureModelProvider() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();
        config.configureModelProvider("azure", "key123", "https://api.example.com");

        assertThat(config.getModelProvider()).isEqualTo("azure");
        assertThat(config.getApiKey()).isEqualTo("key123");
        assertThat(config.getApiBase()).isEqualTo("https://api.example.com");
    }

    @Test
    void testConfigurePrompt() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();
        config.configurePrompt("my_prompt");
        assertThat(config.getPromptTemplateName()).isEqualTo("my_prompt");
    }

    @Test
    void testConfigurePromptTemplate() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();
        List<Map<String, String>> template = List.of(Map.of("role", "system", "content", "You are a helper"));
        config.configurePromptTemplate(template);
        assertThat(config.getPromptTemplate()).hasSize(1);
        assertThat(config.getPromptTemplate().get(0).get("role")).isEqualTo("system");
    }

    @Test
    void testConfigureContextEngine() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();
        config.configureContextEngine(100, 5, true);

        ContextEngineConfig ctxConfig = config.getContextEngineConfig();
        assertThat(ctxConfig.getMaxContextMessageNum()).isEqualTo(100);
        assertThat(ctxConfig.getDefaultWindowRoundNum()).isEqualTo(5);
        assertThat(ctxConfig.isEnableReload()).isTrue();
    }

    @Test
    void testConfigureMemScope() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();
        config.configureMemScope("scope-1");
        assertThat(config.getMemScopeId()).isEqualTo("scope-1");
    }

    @Test
    void testConfigureMaxIterations() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();
        config.configureMaxIterations(10);
        assertThat(config.getMaxIterations()).isEqualTo(10);
    }

    @Test
    void testConfigureMaxParallelToolCalls() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();
        config.configureMaxParallelToolCalls(8);
        assertThat(config.getMaxParallelToolCalls()).isEqualTo(8);
    }

    @Test
    void testConfigureModelClient() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();
        config.configureModelClient("openai", "key", "https://api.openai.com", "gpt-4", false);

        assertThat(config.getModelProvider()).isEqualTo("openai");
        assertThat(config.getApiKey()).isEqualTo("key");
        assertThat(config.getApiBase()).isEqualTo("https://api.openai.com");
        assertThat(config.getModelName()).isEqualTo("gpt-4");
        assertThat(config.getModelClientConfig()).isNotNull();
        assertThat(config.getModelConfigObj()).isNotNull();
        assertThat(config.getModelConfigObj().getModelName()).isEqualTo("gpt-4");
    }

    @Test
    void testConfigureModelClientUpdatesExistingModelConfig() {
        ModelRequestConfig existing = ModelRequestConfig.builder().modelName("old-model").build();

        ReActAgentConfig config = ReActAgentConfig.builder().modelConfigObj(existing).build();

        config.configureModelClient("openai", "key", "url", "new-model", true);

        assertThat(config.getModelConfigObj()).isSameAs(existing);
        assertThat(config.getModelConfigObj().getModelName()).isEqualTo("new-model");
    }

    @Test
    void testConfigureContextProcessors() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();
        config.configureContextProcessors(List.of("proc1", "proc2"));
        assertThat(config.getContextProcessors()).hasSize(2);
    }

    @Test
    void testConfigureCustomHeadersBeforeModelClient() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();

        config.configureCustomHeaders(Map.of("token", "token-123", "userId", "user-456")).configureModelClient("openai",
                "key", "https://api.example.com", "gpt-4", false);

        assertThat(config.getCustomHeaders()).containsEntry("token", "token-123");
        assertThat(config.getModelClientConfig().getHeaders()).containsEntry("token", "token-123")
                .containsEntry("userId", "user-456");
    }

    @Test
    void testConfigureCustomHeadersAfterModelClient() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();

        config.configureModelClient("openai", "key", "https://api.example.com", "gpt-4", false);
        config.configureCustomHeaders(Map.of("token", "token-123"));

        assertThat(config.getModelClientConfig().getHeaders()).containsEntry("token", "token-123");
    }

    @Test
    void testBuilderFullConfig() {
        ReActAgentConfig config = ReActAgentConfig.builder().modelName("gpt-4").modelProvider("openai").apiKey("key")
                .apiBase("url").maxIterations(8).memScopeId("scope")
                .promptTemplate(List.of(Map.of("role", "system", "content", "hello"))).build();

        assertThat(config.getModelName()).isEqualTo("gpt-4");
        assertThat(config.getMaxIterations()).isEqualTo(8);
        assertThat(config.getMemScopeId()).isEqualTo("scope");
    }

    @Test
    void testChainingReturnsSelf() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();
        ReActAgentConfig result = config.configureModel("x").configureMemScope("s").configureMaxIterations(3)
                .configureMaxParallelToolCalls(4);
        assertThat(result).isSameAs(config);
    }
}
