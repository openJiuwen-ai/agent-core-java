/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.dev_tools.agent_builder.builders.AgentBuilderFactory;
import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressManager;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Agent Builder Executor.
 *
 * <p>Mirrors Python's {@code AgentBuilderExecutor} in
 * {@code openjiuwen/dev_tools/agent_builder/executor/executor.py}.</p>
 */
public class AgentBuilderExecutor {
    private static final LoggerProtocol LOGGER = LogManager.getLogger("agent_builder");
    private static final Map<String, String> PROVIDER_MAP = Map.of(
            "openai", "OpenAI",
            "OpenAI", "OpenAI",
            "openrouter", "OpenRouter",
            "OpenRouter", "OpenRouter",
            "siliconflow", "SiliconFlow",
            "SiliconFlow", "SiliconFlow",
            "dashscope", "DashScope",
            "DashScope", "DashScope");

    private final String query;
    private final String sessionId;
    private final String agentType;
    private final Model llm;
    private final HistoryManager historyManager;
    private final ProgressReporter progressReporter;
    private final BaseAgentBuilder agentBuilder;

    public AgentBuilderExecutor(
            String query,
            String sessionId,
            String agentType,
            Map<String, HistoryManager> historyManagerMap,
            Map<String, BaseAgentBuilder> agentBuilderMap,
            ModelInfo modelInfo,
            boolean enableProgress) {
        this.query = Objects.requireNonNull(query, "query");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.agentType = Objects.requireNonNull(agentType, "agentType");
        this.llm = createCoreModel(modelInfo);
        this.historyManager = getHistoryManager(sessionId, historyManagerMap);
        this.progressReporter = enableProgress
                ? ProgressManager.PROGRESS_MANAGER.createReporter(sessionId, agentType)
                : null;
        this.agentBuilder = getAgentBuilder(sessionId, safeBuilderMap(agentBuilderMap));
    }

    public AgentBuilderExecutor(
            String query,
            String sessionId,
            String agentType,
            Map<String, HistoryManager> historyManagerMap,
            Map<String, BaseAgentBuilder> agentBuilderMap,
            Map<String, ?> modelInfo,
            boolean enableProgress) {
        this(query, sessionId, agentType, historyManagerMap, agentBuilderMap, ModelInfo.fromMap(modelInfo),
                enableProgress);
    }

    public AgentBuilderExecutor(
            String query,
            String sessionId,
            String agentType,
            Map<String, HistoryManager> historyManagerMap,
            ModelInfo modelInfo) {
        this(query, sessionId, agentType, historyManagerMap, new LinkedHashMap<>(), modelInfo, true);
    }

    public static Model createCoreModel(Map<String, ?> modelInfo) {
        return createCoreModel(ModelInfo.fromMap(modelInfo));
    }

    public static Model create_core_model(Map<String, ?> modelInfo) {
        return createCoreModel(modelInfo);
    }

    public static Model createCoreModel(ModelInfo modelInfo) {
        ModelInfo info = modelInfo == null ? ModelInfo.empty() : modelInfo;
        if (!pythonTruthy(info.provider()) || !pythonTruthy(info.modelName()) || !pythonTruthy(info.apiKey())) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("required", List.of("model_provider", "model_name", "api_key"));
            details.put("got_keys", info.gotKeys());
            throw new ValidationError(
                    StatusCode.COMPONENT_LLM_CONFIG_INVALID,
                    "model_info missing required fields",
                    details,
                    null,
                    Map.of("error_msg", "model_info missing required fields"));
        }

        String provider = stringValue(info.provider());
        String clientProvider = PROVIDER_MAP.getOrDefault(provider, provider);
        String modelName = stringValue(info.modelName());
        String apiKey = stringValue(info.apiKey());
        String apiBase = pythonTruthy(info.apiBase()) ? stringValue(info.apiBase()) : "";

        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(clientProvider)
                .clientId(modelName)
                .apiKey(apiKey)
                .apiBase(apiBase)
                .verifySsl(pythonTruthy(info.verifySsl()))
                .build();

        ModelRequestConfig.ModelRequestConfigBuilder requestBuilder = ModelRequestConfig.builder()
                .modelName(modelName);
        if (info.temperature() != null) {
            requestBuilder.temperature(info.temperature());
        }
        if (info.maxTokens() != null) {
            requestBuilder.maxTokens(info.maxTokens());
        }
        if (info.topP() != null) {
            requestBuilder.topP(info.topP());
        }

        ModelRequestConfig requestConfig = requestBuilder.build();
        if (info.timeout() != null) {
            requestConfig.setExtraField("timeout", info.timeout());
        }
        return new Model(clientConfig, requestConfig);
    }

    public static Model create_core_model(ModelInfo modelInfo) {
        return createCoreModel(modelInfo);
    }

    public static HistoryManager getHistoryManager(
            String sessionId,
            Map<String, HistoryManager> historyManagerMap) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(historyManagerMap, "historyManagerMap");
        HistoryManager existing = historyManagerMap.get(sessionId);
        if (existing == null) {
            HistoryManager historyManager = new HistoryManager();
            historyManagerMap.put(sessionId, historyManager);
            LOGGER.debug("Created new session history manager session_id={}", sessionId);
            return historyManager;
        }

        LOGGER.debug("Reusing existing session history manager session_id={}", sessionId);
        return existing;
    }

    public static HistoryManager get_history_manager(
            String sessionId,
            Map<String, HistoryManager> historyManagerMap) {
        return getHistoryManager(sessionId, historyManagerMap);
    }

    public Object execute() {
        LOGGER.info(
                "Starting build execution session_id={} agent_type={} query_length={}",
                sessionId,
                agentType,
                query.length());
        try {
            historyManager.addUserMessage(query);
            Object result = agentBuilder.execute(query);
            LOGGER.info(
                    "Build execution completed session_id={} agent_type={} result_type={}",
                    sessionId,
                    agentType,
                    result == null ? "null" : result.getClass().getSimpleName());
            return result;
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Build execution failed session_id={} agent_type={} error={} error_type={}",
                    sessionId,
                    agentType,
                    exceptionMessage(exception),
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    public Map<String, Object> getBuildStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session_id", sessionId);
        result.put("agent_type", agentType);
        result.putAll(agentBuilder.getBuildStatus());
        return result;
    }

    public Map<String, Object> get_build_status() {
        return getBuildStatus();
    }

    public String getQuery() {
        return query;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentType() {
        return agentType;
    }

    public Model getLlm() {
        return llm;
    }

    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    public ProgressReporter getProgressReporter() {
        return progressReporter;
    }

    public BaseAgentBuilder getAgentBuilder() {
        return agentBuilder;
    }

    private BaseAgentBuilder getAgentBuilder(String currentSessionId, Map<String, BaseAgentBuilder> agentBuilderMap) {
        BaseAgentBuilder existing = agentBuilderMap.get(currentSessionId);
        if (existing != null) {
            LOGGER.debug("Reusing existing builder session_id={}", currentSessionId);
            return existing;
        }

        AgentBuilderEnums.AgentType agentTypeEnum;
        try {
            agentTypeEnum = AgentBuilderEnums.AgentType.fromValue(agentType);
        } catch (IllegalArgumentException exception) {
            String errorMessage = "Unsupported agent type: " + agentType;
            LOGGER.error("Unsupported agent type agent_type={}", agentType);
            throw new ValidationError(
                    StatusCode.AGENT_BUILDER_AGENT_TYPE_NOT_SUPPORTED,
                    errorMessage,
                    null,
                    exception,
                    Map.of(
                            "error_msg", errorMessage,
                            "agent_type", agentType,
                            "supported_types", supportedAgentTypes()));
        }

        BaseAgentBuilder builder = AgentBuilderFactory.create(agentTypeEnum, llm, historyManager);
        if (progressReporter != null) {
            builder.setProgressReporter(progressReporter);
        }

        agentBuilderMap.put(currentSessionId, builder);
        LOGGER.debug(
                "Created builder instance session_id={} agent_type={}",
                currentSessionId,
                agentType);
        return builder;
    }

    private static List<String> supportedAgentTypes() {
        List<String> values = new ArrayList<>();
        for (AgentBuilderEnums.AgentType type : AgentBuilderEnums.AgentType.values()) {
            values.add(type.getValue());
        }
        return values;
    }

    private static Map<String, BaseAgentBuilder> safeBuilderMap(Map<String, BaseAgentBuilder> agentBuilderMap) {
        return agentBuilderMap == null ? new LinkedHashMap<>() : agentBuilderMap;
    }

    private static boolean pythonTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0;
        }
        if (value instanceof CharSequence text) {
            return text.length() > 0;
        }
        if (value instanceof java.util.Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private static String stringValue(Object value) {
        return String.valueOf(value);
    }

    private static Double numberAsDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Boolean bool) {
            return bool ? 1.0 : 0.0;
        }
        return Double.valueOf(String.valueOf(value));
    }

    private static Integer numberAsInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private static String exceptionMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null ? exception.toString() : message;
    }

    /**
     * Typed Java view over Python's dynamic {@code model_info} dict.
     *
     * <p>Mirrors Python's {@code model_info} in
     * {@code openjiuwen/dev_tools/agent_builder/executor/executor.py}.</p>
     */
    public record ModelInfo(
            Object provider,
            Object modelName,
            Object apiKey,
            Object apiBase,
            Object verifySsl,
            Double temperature,
            Integer maxTokens,
            Double topP,
            Double timeout,
            List<String> gotKeys) {

        public ModelInfo {
            gotKeys = gotKeys == null ? List.of() : List.copyOf(gotKeys);
        }

        public static ModelInfo of(String modelProvider, String modelName, String apiKey) {
            return of(modelProvider, modelName, apiKey, "", false, null, null, null, null);
        }

        public static ModelInfo of(
                String modelProvider,
                String modelName,
                String apiKey,
                String apiBase,
                boolean verifySsl,
                Double temperature,
                Integer maxTokens,
                Double topP,
                Double timeout) {
            return new ModelInfo(
                    modelProvider,
                    modelName,
                    apiKey,
                    apiBase,
                    verifySsl,
                    temperature,
                    maxTokens,
                    topP,
                    timeout,
                    List.of("api_base", "api_key", "model_name", "model_provider", "verify_ssl"));
        }

        public static ModelInfo empty() {
            return new ModelInfo(null, null, null, null, false, null, null, null, null, List.of());
        }

        public static ModelInfo fromMap(Map<String, ?> source) {
            if (source == null) {
                return empty();
            }
            Object provider = firstTruthy(source.get("model_provider"), source.get("client_provider"));
            Object modelName = firstTruthy(source.get("model_name"), source.get("model"));
            List<String> keys = source.keySet().stream()
                    .map(String::valueOf)
                    .sorted(Comparator.naturalOrder())
                    .toList();
            return new ModelInfo(
                    provider,
                    modelName,
                    source.get("api_key"),
                    firstTruthy(source.get("api_base"), ""),
                    source.containsKey("verify_ssl") ? source.get("verify_ssl") : false,
                    numberAsDouble(source.get("temperature")),
                    numberAsInteger(source.get("max_tokens")),
                    numberAsDouble(source.get("top_p")),
                    numberAsDouble(source.get("timeout")),
                    keys);
        }

        private static Object firstTruthy(Object first, Object second) {
            return pythonTruthy(first) ? first : second;
        }
    }
}
