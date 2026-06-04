/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.config.AgentRuntimeConfig;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Factory for creating DeepAgent instances for offline RL rollout generation.
 * <p>
 * Mirrors Python's {@code AgentFactory} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.runtime.agent_factory}.
 */
public class AgentFactory implements Function<RLTask, DeepAgent> {

    private final String systemPrompt;
    private final List<Object> tools;
    private final List<String> toolNames;
    private final double temperature;
    private final int maxNewTokens;
    private final double topP;
    private final double presencePenalty;
    private final double frequencyPenalty;

    private Map<String, Object> config;
    private Object backendProxy;
    private String proxyUrl;

    public AgentFactory(Map<String, Object> config, Object backendProxy) {
        this(
                readString(config, "You are a helpful assistant.", "systemPrompt", "system_prompt"),
                readObjectList(config, "tools"),
                readStringList(config, "toolNames", "tool_names"),
                readDouble(config, 0.7d, "temperature"),
                readInt(config, 512, "maxNewTokens", "max_new_tokens"),
                readDouble(config, 0.9d, "topP", "top_p"),
                readDouble(config, 0.0d, "presencePenalty", "presence_penalty"),
                readDouble(config, 0.0d, "frequencyPenalty", "frequency_penalty")
        );
        this.config = config != null ? new LinkedHashMap<>(config) : new LinkedHashMap<>();
        this.backendProxy = backendProxy;
    }

    public AgentFactory(
            String systemPrompt,
            List<?> tools,
            List<String> toolNames,
            double temperature,
            int maxNewTokens,
            double topP,
            double presencePenalty,
            double frequencyPenalty) {
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
        this.tools = new ArrayList<>(tools != null ? tools : List.of());
        this.toolNames = new ArrayList<>(toolNames != null ? toolNames : List.of());
        this.temperature = temperature;
        this.maxNewTokens = maxNewTokens;
        this.topP = topP;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.config = new LinkedHashMap<>();
    }

    /**
     * Build the default factory from Java runtime config and registered tools.
     *
     * @param runtimeCfg runtime hyper-parameters
     * @param tools registered tools or tool cards
     * @param toolNames tool names selected for this runtime
     * @return configured agent factory
     */
    public static AgentFactory buildAgentFactory(
            AgentRuntimeConfig runtimeCfg,
            List<?> tools,
            List<String> toolNames) {
        AgentRuntimeConfig cfg = runtimeCfg != null ? runtimeCfg : new AgentRuntimeConfig();
        return new AgentFactory(
                promptToString(cfg.getSystemPrompt()),
                tools,
                toolNames,
                cfg.getTemperature(),
                cfg.getMaxNewTokens(),
                cfg.getTopP(),
                cfg.getPresencePenalty(),
                cfg.getFrequencyPenalty()
        );
    }

    /**
     * Reflection-friendly builder for callers that keep runtime config in maps or adapters.
     *
     * @param runtimeCfg runtime config object
     * @param tools registered tools or tool cards
     * @param toolNames tool names selected for this runtime
     * @return configured agent factory
     */
    public static AgentFactory buildAgentFactory(Object runtimeCfg, List<?> tools, List<String> toolNames) {
        if (runtimeCfg instanceof AgentRuntimeConfig cfg) {
            return buildAgentFactory(cfg, tools, toolNames);
        }
        return new AgentFactory(
                promptToString(readProperty(
                        runtimeCfg, "You are a helpful assistant.", "systemPrompt", "system_prompt")),
                tools,
                toolNames,
                readDouble(runtimeCfg, 0.7d, "temperature"),
                readInt(runtimeCfg, 512, "maxNewTokens", "max_new_tokens"),
                readDouble(runtimeCfg, 0.9d, "topP", "top_p"),
                readDouble(runtimeCfg, 0.0d, "presencePenalty", "presence_penalty"),
                readDouble(runtimeCfg, 0.0d, "frequencyPenalty", "frequency_penalty")
        );
    }

    public static AgentFactory build_agent_factory(
            AgentRuntimeConfig runtimeCfg,
            List<?> tools,
            List<String> toolNames) {
        return buildAgentFactory(runtimeCfg, tools, toolNames);
    }

    public static AgentFactory build_agent_factory(Object runtimeCfg, List<?> tools, List<String> toolNames) {
        return buildAgentFactory(runtimeCfg, tools, toolNames);
    }

    /**
     * Create an agent from a compatibility agent type string.
     *
     * @param agentType task identifier used by older callers
     * @return DeepAgent instance
     */
    public Object createAgent(String agentType) {
        String taskId = agentType != null && !agentType.isBlank() ? agentType : "default";
        return createAgent(new RLTask(taskId, taskId));
    }

    /**
     * Create and configure a DeepAgent instance for the given RL task.
     *
     * @param rlTask task metadata
     * @return configured DeepAgent
     */
    public DeepAgent createAgent(RLTask rlTask) {
        if (proxyUrl == null || proxyUrl.isBlank()) {
            throw new IllegalStateException("proxy_url has not been set on AgentFactory, "
                    + "BackendProxy must be started before creating agents");
        }
        RLTask task = rlTask != null ? rlTask : new RLTask("default", "default");
        String taskId = task.getTaskId() != null && !task.getTaskId().isBlank() ? task.getTaskId() : "default";

        AgentCard agentCard = AgentCard.builder()
                .id("rl_agent_" + taskId)
                .name("RLTrainingAgent")
                .description("RL training agent based on DeepAgent")
                .build();

        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider("OpenAI")
                .apiKey("EMPTY")
                .apiBase(normalizeProxyUrl(proxyUrl) + "/v1")
                .timeout(300)
                .verifySsl(false)
                .build();

        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName("agentrl")
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxNewTokens)
                .build();
        if (Double.compare(presencePenalty, 0.0d) != 0) {
            requestConfig.setExtraField("presencePenalty", presencePenalty);
        }
        if (Double.compare(frequencyPenalty, 0.0d) != 0) {
            requestConfig.setExtraField("frequencyPenalty", frequencyPenalty);
        }

        DeepAgentConfig deepAgentConfig = new DeepAgentConfig();
        deepAgentConfig.setCard(agentCard);
        deepAgentConfig.setModelClientConfig(clientConfig);
        deepAgentConfig.setModelRequestConfig(requestConfig);
        deepAgentConfig.setSystemPrompt(systemPrompt);
        deepAgentConfig.setMaxIterations(10);
        deepAgentConfig.setTools(resolveToolCards());

        DeepAgent agent = new DeepAgent(agentCard);
        agent.configure(deepAgentConfig);
        enableRlTokenCapture(agent);
        registerTools(agent);
        return agent;
    }

    @Override
    public DeepAgent apply(RLTask rlTask) {
        return createAgent(rlTask);
    }

    public DeepAgent call(RLTask rlTask) {
        return createAgent(rlTask);
    }

    private List<ToolCard> resolveToolCards() {
        List<ToolCard> cards = new ArrayList<>();
        for (Object tool : tools) {
            ToolCard card = extractToolCard(tool);
            if (card != null && shouldIncludeTool(card)) {
                cards.add(card);
            }
        }
        return cards;
    }

    private void registerTools(DeepAgent agent) {
        for (Object tool : tools) {
            ToolCard card = extractToolCard(tool);
            if (card == null || !shouldIncludeTool(card)) {
                continue;
            }
            agent.getAbilityManager().add(card);
            if (tool instanceof Tool foundationTool
                    && Runner.resourceMgr().getTool(card.getId()) == null) {
                Runner.resourceMgr().addTool(foundationTool, null);
            }
        }
    }

    private boolean shouldIncludeTool(ToolCard card) {
        return toolNames.isEmpty() || toolNames.contains(card.getName()) || toolNames.contains(card.getId());
    }

    private static ToolCard extractToolCard(Object tool) {
        if (tool instanceof ToolCard card) {
            return card;
        }
        if (tool instanceof Tool foundationTool) {
            return foundationTool.getCard();
        }
        Object card = readProperty(tool, "card", "card", "getCard");
        if (card instanceof ToolCard toolCard) {
            return toolCard;
        }
        return nullValue();
    }

    private static void enableRlTokenCapture(DeepAgent agent) {
        Object delegate = agent.getDelegate();
        Object delegateConfig = delegate != null ? readProperty(delegate, nullValue(), "config") : nullValue();
        if (delegateConfig == null) {
            return;
        }
        writeField(delegateConfig, "llm_return_token_ids", true);
        writeField(delegateConfig, "llm_logprobs", true);
        writeField(delegateConfig, "llm_top_logprobs", 1);
    }

    private static void writeField(Object target, String fieldName, Object value) {
        if (target == null) {
            return;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException ignored) {
                return;
            }
        }
    }

    private static String normalizeProxyUrl(String value) {
        String url = value != null ? value.trim() : "";
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String promptToString(Object prompt) {
        Object content = prompt;
        if (prompt instanceof PromptTemplate template) {
            content = template.getContent();
        }
        return content != null ? String.valueOf(content) : "";
    }

    private static List<Object> readObjectList(Map<String, Object> source, String key) {
        if (source == null) {
            return new ArrayList<>();
        }
        Object value = source.get(key);
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof Object[] array) {
            List<Object> result = new ArrayList<>();
            Collections.addAll(result, array);
            return result;
        }
        return value == null ? new ArrayList<>() : new ArrayList<>(List.of(value));
    }

    private static List<String> readStringList(Map<String, Object> source, String... keys) {
        Object value = readProperty(source, nullValue(), keys);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        if (value instanceof Object[] array) {
            List<String> result = new ArrayList<>();
            for (Object item : array) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return value == null ? new ArrayList<>() : new ArrayList<>(List.of(String.valueOf(value)));
    }

    private static String readString(Map<String, Object> source, String defaultValue, String... keys) {
        Object value = readProperty(source, defaultValue, keys);
        return value != null ? promptToString(value) : defaultValue;
    }

    private static int readInt(Object source, int defaultValue, String... keys) {
        Object value = readProperty(source, defaultValue, keys);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static double readDouble(Object source, double defaultValue, String... keys) {
        Object value = readProperty(source, defaultValue, keys);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static Object readProperty(Object source, Object defaultValue, String... keys) {
        if (source == null) {
            return defaultValue;
        }
        if (source instanceof Map<?, ?> map) {
            for (String key : keys) {
                if (map.containsKey(key)) {
                    return map.get(key);
                }
            }
            return defaultValue;
        }
        for (String key : keys) {
            Object value = invokeGetter(source, key);
            if (value != Marker.ABSENT) {
                return value;
            }
            value = readField(source, key);
            if (value != Marker.ABSENT) {
                return value;
            }
        }
        return defaultValue;
    }

    private static Object invokeGetter(Object target, String name) {
        List<String> methodNames = List.of(name, "get" + capitalize(toCamel(name)), "is" + capitalize(toCamel(name)));
        for (String methodName : methodNames) {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Method method = type.getDeclaredMethod(methodName);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (NoSuchMethodException ignored) {
                    type = type.getSuperclass();
                } catch (ReflectiveOperationException ignored) {
                    return Marker.ABSENT;
                }
            }
        }
        return Marker.ABSENT;
    }

    private static Object readField(Object target, String name) {
        String camel = toCamel(name);
        for (String fieldName : List.of(name, camel)) {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                } catch (IllegalAccessException ignored) {
                    return Marker.ABSENT;
                }
            }
        }
        return Marker.ABSENT;
    }

    private static String toCamel(String value) {
        if (value == null || !value.contains("_")) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char c : value.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                builder.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    @SuppressWarnings("unchecked")
    private static <T> T nullValue() {
        return (T) null;
    }

    public String getSystemPrompt() { return systemPrompt; }
    public List<Object> getTools() { return Collections.unmodifiableList(tools); }
    public List<String> getToolNames() { return Collections.unmodifiableList(toolNames); }
    public double getTemperature() { return temperature; }
    public int getMaxNewTokens() { return maxNewTokens; }
    public double getTopP() { return topP; }
    public double getPresencePenalty() { return presencePenalty; }
    public double getFrequencyPenalty() { return frequencyPenalty; }
    public String getProxyUrl() { return proxyUrl; }
    public String getProxy_url() { return proxyUrl; }
    public void setProxyUrl(String proxyUrl) { this.proxyUrl = proxyUrl; }
    public void setProxy_url(String proxyUrl) { this.proxyUrl = proxyUrl; }
    public Map<String, Object> getConfig() { return Collections.unmodifiableMap(config); }
    public void setConfig(Map<String, Object> config) {
        this.config = config != null ? new LinkedHashMap<>(config) : new LinkedHashMap<>();
    }
    public Object getBackendProxy() { return backendProxy; }
    public void setBackendProxy(Object backendProxy) { this.backendProxy = backendProxy; }

    private enum Marker {
        ABSENT
    }
}
