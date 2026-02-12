// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.contextengine.ContextEngine;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReActAgent and ReActAgentConfig.
 * 对应 Python: agent-core/tests/unit_tests/core/single_agent/agents/test_react_agent.py
 */
@ExtendWith(MockitoExtension.class)
class ReActAgentTest {

    @Nested
    @DisplayName("TestReActAgentConfigDefaults")
    class TestReActAgentConfigDefaults {

        @Test
        @DisplayName("test_chain_configuration_all_methods")
        void testChainConfigurationAllMethods() {
            ReActAgentConfig config = new ReActAgentConfig();
            
            ReActAgentConfig result = config
                .configureModel("gpt-4")
                .configureModelProvider("openai", "sk-xxx", "https://api.openai.com")
                .configurePrompt("default_prompt")
                .configurePromptTemplate(List.of(Map.of("role", "system", "content", "You are helpful.")))
                .configureContextLimit(50)
                .configureMemScope("scope_1")
                .configureMaxIterations(10);
            
            assertSame(config, result);
            assertEquals("gpt-4", config.getModelName());
            assertEquals("openai", config.getModelProvider());
            assertEquals("sk-xxx", config.getApiKey());
            assertEquals("https://api.openai.com", config.getApiBase());
            assertEquals("default_prompt", config.getPromptTemplateName());
            assertEquals(1, config.getPromptTemplate().size());
            assertEquals(50, config.getContextWindowLimit());
            assertEquals("scope_1", config.getMemScopeId());
            assertEquals(10, config.getMaxIterations());
        }

        @Test
        @DisplayName("test_configure_model_client_creates_config_objects")
        void testConfigureModelClientCreatesConfigObjects() {
            ReActAgentConfig config = new ReActAgentConfig();
            
            ReActAgentConfig result = config.configureModelClient(
                "OpenAI",
                "sk-test",
                "https://api.openai.com/v1",
                "gpt-4",
                true
            );
            
            assertSame(config, result);
            assertEquals("OpenAI", config.getModelProvider());
            assertEquals("sk-test", config.getApiKey());
            assertEquals("https://api.openai.com/v1", config.getApiBase());
            assertEquals("gpt-4", config.getModelName());
            
            assertNotNull(config.getModelClientConfig());
            assertEquals("OpenAI", config.getModelClientConfig().getClientProvider());
            assertEquals("sk-test", config.getModelClientConfig().getApiKey());
            assertEquals("https://api.openai.com/v1", config.getModelClientConfig().getApiBase());
            assertTrue(config.getModelClientConfig().isVerifySsl());
            
            // ModelRequestConfig should be created with model_name
            assertNotNull(config.getModelConfigObj());
        }
    }

    @Nested
    @DisplayName("TestReActAgentInitialization")
    class TestReActAgentInitialization {

        @Test
        @DisplayName("test_init_creates_default_config_and_components")
        void testInitCreatesDefaultConfigAndComponents() {
            AgentCard card = new AgentCard("a1", "test_agent", "Test", null);
            
            ReActAgent agent = new ReActAgent(card);
            
            assertEquals(card, agent.getCard());
            assertNotNull(agent.getConfig());
            assertInstanceOf(ReActAgentConfig.class, agent.getConfig());
            assertNotNull(agent.getContextEngine());
            assertNull(agent.getLlm()); // Lazy loaded
        }
    }

    @Nested
    @DisplayName("TestReActAgentConfigure")
    class TestReActAgentConfigure {

        @Test
        @DisplayName("test_configure_returns_self_for_chaining")
        void testConfigureReturnsSelfForChaining() {
            AgentCard card = new AgentCard("a1", "agent", "", null);
            ReActAgent agent = new ReActAgent(card);
            
            ReActAgentConfig newConfig = new ReActAgentConfig();
            ReActAgent result = agent.configure(newConfig);
            
            assertSame(agent, result);
            assertSame(newConfig, agent.getConfig());
        }

        @Test
        @DisplayName("test_configure_resets_llm_when_model_provider_changes")
        void testConfigureResetsLlmWhenModelProviderChanges() {
            AgentCard card = new AgentCard("a1", "agent", "", null);
            ReActAgent agent = new ReActAgent(card);
            agent.setLlm(mock(Object.class)); // Simulate existing LLM
            
            ReActAgentConfig newConfig = new ReActAgentConfig();
            newConfig.setModelProvider("different_provider");
            
            agent.configure(newConfig);
            
            assertNull(agent.getLlm());
        }

        @Test
        @DisplayName("test_configure_resets_llm_when_api_key_changes")
        void testConfigureResetsLlmWhenApiKeyChanges() {
            AgentCard card = new AgentCard("a1", "agent", "", null);
            ReActAgent agent = new ReActAgent(card);
            agent.setLlm(mock(Object.class));
            
            ReActAgentConfig newConfig = new ReActAgentConfig();
            newConfig.setApiKey("new_api_key");
            
            agent.configure(newConfig);
            
            assertNull(agent.getLlm());
        }

        @Test
        @DisplayName("test_configure_rebuilds_context_engine_when_window_limit_changes")
        void testConfigureRebuildsContextEngineWhenWindowLimitChanges() {
            AgentCard card = new AgentCard("a1", "agent", "", null);
            ReActAgent agent = new ReActAgent(card);
            
            ContextEngine oldContextEngine = agent.getContextEngine();
            
            ReActAgentConfig newConfig = new ReActAgentConfig();
            newConfig.setContextWindowLimit(100); // Different from default 20
            
            agent.configure(newConfig);
            
            assertNotSame(oldContextEngine, agent.getContextEngine());
        }
    }

    @Nested
    @DisplayName("TestReActAgentGetLLM")
    class TestReActAgentGetLLM {

        @Test
        @DisplayName("test_get_llm_raises_when_model_client_config_is_none")
        void testGetLlmRaisesWhenModelClientConfigIsNone() {
            AgentCard card = new AgentCard("a1", "agent", "", null);
            ReActAgent agent = new ReActAgent(card);
            
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                agent.getOrCreateLlm();
            });
            
            assertTrue(ex.getMessage().contains("model_client_config") || 
                       ex.getMessage().contains("ModelClientConfig"));
        }

        @Test
        @DisplayName("test_get_llm_returns_cached_instance_on_subsequent_calls")
        void testGetLlmReturnsCachedInstanceOnSubsequentCalls() {
            AgentCard card = new AgentCard("a1", "agent", "", null);
            ReActAgent agent = new ReActAgent(card);
            
            ReActAgentConfig config = new ReActAgentConfig();
            config.configureModelClient("OpenAI", "sk-test", "https://api.test.com", "gpt-4", true);
            agent.configure(config);
            
            // Set a mock LLM directly
            Object mockLlm = new Object();
            agent.setLlm(mockLlm);
            
            Object result1 = agent.getLlm();
            Object result2 = agent.getLlm();
            
            assertSame(result1, result2);
        }
    }

    @Nested
    @DisplayName("TestReActAgentInvoke")
    class TestReActAgentInvoke {

        @Mock
        private Session mockSession;

        @Test
        @DisplayName("test_invoke_raises_when_dict_missing_query")
        void testInvokeRaisesWhenDictMissingQuery() throws Exception {
            AgentCard card = new AgentCard("a1", "agent", "", null);
            ReActAgent agent = new ReActAgent(card);
            
            Map<String, Object> input = new HashMap<>();
            input.put("not_query", "value");
            
            CompletableFuture<Object> future = agent.invoke(input, mockSession);
            Object result = future.get();
            
            // Should return error result with message containing "query"
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertEquals("error", resultMap.get("result_type"));
            String output = String.valueOf(resultMap.get("output"));
            assertTrue(output.contains("query"), "Error should mention 'query'");
        }

        @Test
        @DisplayName("test_invoke_raises_for_invalid_input_type")
        void testInvokeRaisesForInvalidInputType() throws Exception {
            AgentCard card = new AgentCard("a1", "agent", "", null);
            ReActAgent agent = new ReActAgent(card);
            
            // Pass invalid type (Integer)
            Object invalidInput = 12345;
            
            CompletableFuture<Object> future = agent.invoke(invalidInput, mockSession);
            Object result = future.get();
            
            // Should return error result with message about invalid type
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertEquals("error", resultMap.get("result_type"));
            String output = String.valueOf(resultMap.get("output"));
            assertTrue(output.contains("Map") || output.contains("String") || output.contains("dict"),
                "Error should mention valid input types");
        }
    }

    @Nested
    @DisplayName("TestReActAgentStream")
    class TestReActAgentStream {

        @Mock
        private Session mockSession;

        @Test
        @DisplayName("test_stream_returns_flux")
        void testStreamReturnsIterable() {
            AgentCard card = new AgentCard("a1", "agent", "", null);
            ReActAgent agent = new ReActAgent(card);
            
            // stream() should return something iterable
            // Even if actual execution fails, the method should return an iterator
            var result = agent.stream("test", mockSession, List.of(StreamMode.OUTPUT));
            
            assertNotNull(result);
        }
    }
}

