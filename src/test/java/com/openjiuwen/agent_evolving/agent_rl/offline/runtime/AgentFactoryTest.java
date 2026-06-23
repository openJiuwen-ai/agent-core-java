/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.config.AgentRuntimeConfig;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.single_agent.agents.ReActAgent;
import com.openjiuwen.core.single_agent.agents.ReActAgentConfig;
import com.openjiuwen.harness.DeepAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code AgentFactory} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/runtime/agent_factory.py}.
 *
 * <p>Mirrors Python's {@code TestBuildAgentFactory} and
 * {@code TestAgentFactoryCallWithoutProxyUrl} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/offline/runtime/test_agent_factory.py}.</p>
 */
class AgentFactoryTest {

    private static final String TOOL_ID = "agent-factory-tool";
    private static final String OPEN_AI = "OpenAI";

    private Object previousOpenAiFactory;

    @BeforeEach
    void setUp() throws Exception {
        previousOpenAiFactory = modelClientFactories().get(OPEN_AI);
        Model.registerClientFactory(OPEN_AI, (clientConfig, requestConfig) -> new RecordingModelClient());
        removeToolIfPresent(TOOL_ID);
    }

    @AfterEach
    void tearDown() throws Exception {
        Map<String, Object> factories = modelClientFactories();
        if (previousOpenAiFactory == null) {
            factories.remove(OPEN_AI);
        } else {
            factories.put(OPEN_AI, previousOpenAiFactory);
        }
        removeToolIfPresent(TOOL_ID);
    }

    @Test
    void rejectsCallsBeforeProxyUrlIsInitialized() {
        AgentFactory factory = new AgentFactory("sys", List.of(), List.of(), 0.2D, 128,
                0.7D, 0.0D, 0.0D);
        RLTask task = newTask("task-1");

        BaseError error = assertThrows(BaseError.class, () -> factory.apply(task));

        assertThat(error.getStatus()).isEqualTo(StatusCode.AGENT_RL_PROXY_NOT_INITIALIZED);
        assertThat(error.getParams()).containsEntry("error_msg",
                "proxy_url has not been set on AgentFactory, "
                        + "BackendProxy must be started before creating agents");
    }

    @Test
    void createsDeepAgentWithRlModelConfigAndRegistersTools() {
        LocalFunction tool = new LocalFunction(
                new ToolCard(TOOL_ID, "calculator", "Calculator", Map.of("type", "object")),
                inputs -> "ok"
        );
        ToolCard cardOnly = new ToolCard("card-only", "card_only", "Card only", Map.of("type", "object"));
        AgentFactory factory = new AgentFactory("system prompt", List.of(tool, cardOnly), List.of("unused"),
                0.25D, 256, 0.75D, 0.3D, 0.4D);
        factory.setProxyUrl("http://proxy.local");

        DeepAgent agent = factory.apply(newTask("rollout-7"));

        assertThat(agent.getCard().getId()).isEqualTo("rl_agent_rollout-7");
        assertThat(agent.getCard().getName()).isEqualTo("RLTrainingAgent");
        assertThat(agent.deepConfig().getSystemPrompt()).isEqualTo("system prompt");
        assertThat(agent.deepConfig().getMaxIterations()).isEqualTo(10);
        assertThat(agent.deepConfig().isEnableTaskLoop()).isFalse();
        assertThat(agent.deepConfig().getModel()).isInstanceOf(Model.class);

        Model model = (Model) agent.deepConfig().getModel();
        ModelClientConfig clientConfig = model.getModelClientConfig();
        ModelRequestConfig requestConfig = model.getModelConfig();
        assertThat(clientConfig.getClientProvider()).isEqualTo(OPEN_AI);
        assertThat(clientConfig.getApiKey()).isEqualTo("EMPTY");
        assertThat(clientConfig.getApiBase()).isEqualTo("http://proxy.local/v1");
        assertThat(clientConfig.getTimeout()).isEqualTo(300.0D);
        assertThat(clientConfig.isVerifySsl()).isFalse();
        assertThat(requestConfig.getModelName()).isEqualTo("agentrl");
        assertThat(requestConfig.getTemperature()).isEqualTo(0.25D);
        assertThat(requestConfig.getTopP()).isEqualTo(0.75D);
        assertThat(requestConfig.getMaxTokens()).isEqualTo(256);
        assertThat(requestConfig.getExtraFields())
                .containsEntry("presencePenalty", 0.3D)
                .containsEntry("frequencyPenalty", 0.4D);

        assertThat(agent.reactAgent()).isInstanceOf(ReActAgent.class);
        ReActAgentConfig reactConfig = ((ReActAgent) agent.reactAgent()).getConfig();
        assertThat(reactConfig.isLlmReturnTokenIds()).isTrue();
        assertThat(reactConfig.getModelClientConfig()).isSameAs(clientConfig);
        assertThat(reactConfig.getModelConfigObj()).isSameAs(requestConfig);
        assertThat(((ReActAgent) agent.reactAgent()).getAbilityManager().getTools())
                .containsKeys("calculator", "card_only");
        assertThat(agent.getTools()).containsKey("calculator");

        Tool registeredTool = Runner.resourceMgr().getTool(TOOL_ID);
        assertThat(registeredTool).isInstanceOf(LocalFunction.class);
        assertThat(registeredTool).isNotSameAs(tool);
    }

    @Test
    void buildAgentFactoryUsesPromptTemplateContent() {
        AgentRuntimeConfig runtimeConfig = new AgentRuntimeConfig();
        runtimeConfig.setSystemPrompt(new PromptTemplate("rl", "template text", "{{", "}}"));
        runtimeConfig.setTemperature(0.1D);
        runtimeConfig.setMaxNewTokens(64);
        runtimeConfig.setTopP(0.5D);
        runtimeConfig.setPresencePenalty(0.2D);
        runtimeConfig.setFrequencyPenalty(0.6D);

        AgentFactory factory = AgentFactory.build_agent_factory(runtimeConfig, List.of(), List.of("tool-a"));

        assertThat(factory.getSystemPrompt()).isEqualTo("template text");
        assertThat(factory.getTemperature()).isEqualTo(0.1D);
        assertThat(factory.getMaxNewTokens()).isEqualTo(64);
        assertThat(factory.getTopP()).isEqualTo(0.5D);
        assertThat(factory.getPresencePenalty()).isEqualTo(0.2D);
        assertThat(factory.getFrequencyPenalty()).isEqualTo(0.6D);
        assertThat(factory.getToolNames()).containsExactly("tool-a");
    }

    private static RLTask newTask(String taskId) {
        RLTask task = new RLTask();
        task.setTaskId(taskId);
        return task;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> modelClientFactories() throws Exception {
        Field field = Model.class.getDeclaredField("CLIENT_FACTORIES");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(null);
    }

    private static void removeToolIfPresent(String toolId) {
        try {
            Runner.resourceMgr().removeTool(toolId);
        } catch (RuntimeException ignored) {
            // Missing tools are fine; the test only needs a clean registration slot.
        }
    }

    /**
     * No-op model client used to let AgentFactory construct the Python-equivalent Model object.
     */
    private static final class RecordingModelClient implements Model.ModelClient {
        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            return CompletableFuture.completedFuture(new AssistantMessage("ok"));
        }
    }
}
