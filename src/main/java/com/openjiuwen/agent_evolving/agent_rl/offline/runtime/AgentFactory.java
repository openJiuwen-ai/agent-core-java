/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.config.AgentRuntimeConfig;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.single_agent.agents.ReActAgent;
import com.openjiuwen.core.single_agent.agents.ReActAgentConfig;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Callable factory that creates DeepAgent instances for each RL task.
 *
 * <p>Mirrors Python's {@code AgentFactory} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/runtime/agent_factory.py}.</p>
 */
public class AgentFactory implements Function<RLTask, DeepAgent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentFactory.class);
    private static final String MODEL_NAME = "agentrl";
    private static final String CLIENT_PROVIDER = "OpenAI";
    private static final String API_KEY = "EMPTY";

    private final String systemPrompt;
    private final List<Object> tools;
    private final List<String> toolNames;
    private final double temperature;
    private final int maxNewTokens;
    private final double topP;
    private final double presencePenalty;
    private final double frequencyPenalty;
    private String proxyUrl;

    public AgentFactory(
            String systemPrompt,
            List<?> tools,
            List<String> toolNames,
            double temperature,
            int maxNewTokens,
            double topP,
            double presencePenalty,
            double frequencyPenalty) {
        this.systemPrompt = systemPrompt;
        this.tools = new ArrayList<>(tools == null ? List.of() : tools);
        this.toolNames = new ArrayList<>(toolNames == null ? List.of() : toolNames);
        this.temperature = temperature;
        this.maxNewTokens = maxNewTokens;
        this.topP = topP;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
    }

    /**
     * Create and configure a DeepAgent instance for the given RL task.
     *
     * @param rlTask RL task metadata
     * @return configured DeepAgent
     */
    public DeepAgent createAgent(RLTask rlTask) {
        if (proxyUrl == null || proxyUrl.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_RL_PROXY_NOT_INITIALIZED,
                    "error_msg",
                    "proxy_url has not been set on AgentFactory, "
                            + "BackendProxy must be started before creating agents"
            );
        }
        Objects.requireNonNull(rlTask, "rlTask");

        AgentCard agentCard = new AgentCard(
                "rl_agent_" + String.valueOf(rlTask.getTaskId()),
                "RLTrainingAgent",
                "RL training agent based on DeepAgent"
        );
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(CLIENT_PROVIDER)
                .apiKey(API_KEY)
                .apiBase(proxyUrl + "/v1")
                .timeout(300)
                .verifySsl(false)
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(MODEL_NAME)
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxNewTokens)
                .build();
        if (Double.compare(presencePenalty, 0.0D) != 0) {
            requestConfig.setExtraField("presencePenalty", presencePenalty);
        }
        if (Double.compare(frequencyPenalty, 0.0D) != 0) {
            requestConfig.setExtraField("frequencyPenalty", frequencyPenalty);
        }

        Model model = new Model(clientConfig, requestConfig);
        DeepAgentConfig config = new DeepAgentConfig();
        config.setModel(model);
        config.setCard(agentCard);
        config.setSystemPrompt(systemPrompt);
        config.setMaxIterations(10);
        config.setEnableTaskLoop(false);

        DeepAgent agent = new DeepAgent(agentCard);
        agent.configure(config);
        ReActAgent reactAgent = buildReactAgent(agentCard, model, clientConfig, requestConfig);
        agent.setReactAgent(reactAgent, true);
        if (!tools.isEmpty()) {
            registerTools(agent, reactAgent);
        }
        return agent;
    }

    @Override
    public DeepAgent apply(RLTask rlTask) {
        return createAgent(rlTask);
    }

    public DeepAgent call(RLTask rlTask) {
        return createAgent(rlTask);
    }

    private ReActAgent buildReactAgent(AgentCard agentCard, Model model, ModelClientConfig clientConfig,
                                       ModelRequestConfig requestConfig) {
        ReActAgentConfig reactConfig = new ReActAgentConfig();
        reactConfig.setModelProvider(CLIENT_PROVIDER);
        reactConfig.setApiKey(API_KEY);
        reactConfig.setApiBase(clientConfig.getApiBase());
        reactConfig.setModelName(MODEL_NAME);
        reactConfig.setModelClientConfig(clientConfig);
        reactConfig.setModelConfigObj(requestConfig);
        reactConfig.setMaxIterations(10);
        reactConfig.setLlmReturnTokenIds(true);

        ReActAgent reactAgent = new ReActAgent(agentCard);
        reactAgent.configure(reactConfig);
        reactAgent.setLlm(model);
        return reactAgent;
    }

    private void registerTools(DeepAgent agent, ReActAgent reactAgent) {
        for (Object item : tools) {
            if (item instanceof Tool foundationTool && foundationTool.getCard() != null) {
                ToolCard card = foundationTool.getCard();
                reactAgent.getAbilityManager().add(card);
                agent.registerTool(foundationTool);
                if (Runner.resourceMgr().getTool(card.getId()) == null) {
                    Runner.resourceMgr().addTool(rebuildLocalFunctionIfNeeded(foundationTool));
                }
                continue;
            }
            if (item instanceof ToolCard card) {
                reactAgent.getAbilityManager().add(card);
                continue;
            }
            LOGGER.warn("AgentFactory: unrecognized tool type {}, skipping.",
                    item == null ? null : item.getClass());
        }
    }

    private static Tool rebuildLocalFunctionIfNeeded(Tool tool) {
        if (tool instanceof LocalFunction localFunction) {
            return new LocalFunction(localFunction.getCard(), localFunction.getFunc());
        }
        return tool;
    }

    /**
     * Build a default AgentFactory from runtime config + tools.
     *
     * @param runtimeCfg runtime hyper-parameters
     * @param tools registered tools or tool cards
     * @param toolNames tool names selected for the runtime
     * @return configured agent factory
     */
    public static AgentFactory buildAgentFactory(
            AgentRuntimeConfig runtimeCfg,
            List<?> tools,
            List<String> toolNames) {
        AgentRuntimeConfig cfg = runtimeCfg == null ? new AgentRuntimeConfig() : runtimeCfg;
        return new AgentFactory(
                promptToString(cfg.getSystemPrompt()),
                listCopy(tools),
                toolNames == null ? List.of() : List.copyOf(toolNames),
                cfg.getTemperature(),
                cfg.getMaxNewTokens(),
                cfg.getTopP(),
                cfg.getPresencePenalty(),
                cfg.getFrequencyPenalty()
        );
    }

    public static AgentFactory build_agent_factory(
            AgentRuntimeConfig runtimeCfg,
            List<?> tools,
            List<String> toolNames) {
        return buildAgentFactory(runtimeCfg, tools, toolNames);
    }

    private static List<?> listCopy(List<?> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String promptToString(Object prompt) {
        Object content = prompt instanceof PromptTemplate template ? template.getContent() : prompt;
        return content == null ? null : String.valueOf(content);
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public List<Object> getTools() {
        return Collections.unmodifiableList(tools);
    }

    public List<String> getToolNames() {
        return Collections.unmodifiableList(toolNames);
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMaxNewTokens() {
        return maxNewTokens;
    }

    public double getTopP() {
        return topP;
    }

    public double getPresencePenalty() {
        return presencePenalty;
    }

    public double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public String getProxyUrl() {
        return proxyUrl;
    }

    public void setProxyUrl(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }

    public String getProxy_url() {
        return proxyUrl;
    }

    public void setProxy_url(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }
}
