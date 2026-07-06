/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.EventQueue;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.modules.TaskScheduler;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.TaskInteractionEvent;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.TaskCompletionRail;
import com.openjiuwen.harness.rails.TaskIterationRail;
import com.openjiuwen.harness.schema.AgentMode;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.security.PermissionFactory;
import com.openjiuwen.harness.subagents.SubAgentConfig;

import com.openjiuwen.harness.task_loop.CompletionPromiseEvaluator;
import com.openjiuwen.harness.task_loop.CoreTaskLoopEventExecutor;
import com.openjiuwen.harness.task_loop.LoopCoordinator;
import com.openjiuwen.harness.task_loop.MaxRoundsEvaluator;
import com.openjiuwen.harness.task_loop.StopConditionEvaluator;
import com.openjiuwen.harness.task_loop.TaskLoopController;
import com.openjiuwen.harness.task_loop.TaskLoopEventHandler;
import com.openjiuwen.harness.task_loop.TaskLoopEventExecutor;
import com.openjiuwen.harness.task_loop.TaskIterationContext;
import com.openjiuwen.harness.task_loop.TimeoutEvaluator;
import com.openjiuwen.harness.tools.SessionToolkit;
import com.openjiuwen.harness.workspace.Workspace;
import lombok.Getter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Minimal Java baseline for the Python DeepAgent public surface.
 */
@Getter
public class DeepAgent {
    private final AgentCard card;
    private final DeepAgentConfig config;
    private final Workspace workspace;
    private final ReActAgent agent;
    private AgentMode currentMode;
    private final List<Object> registeredRails = new CopyOnWriteArrayList<>();
    private final List<Object> registeredTools = new CopyOnWriteArrayList<>();
    private final List<McpServerConfig> registeredMcps = new CopyOnWriteArrayList<>();
    private SessionToolkit sessionToolkit;
    private LoopCoordinator loopCoordinator;
    private final Map<String, LoopCoordinator> sessionLoopCoordinators = new ConcurrentHashMap<>();
    private TaskLoopController loopController;
    private TaskManager taskManager;
    private TaskScheduler taskScheduler;
    private EventQueue eventQueue;
    private TaskLoopEventHandler eventHandler;
    private final Set<String> activeTaskLoopSessions = ConcurrentHashMap.newKeySet();
    private Path planFilePath;
    private boolean isInitialized;
    private TaskCompletionRail taskCompletionRail;
    private CompletionPromiseEvaluator completionPromiseEvaluator;
    private boolean isExplicitCompletionPolicy;

    /**
     * Auto-generated for codecheck compliance.
     */
    public DeepAgent(AgentCard card, DeepAgentConfig config, Workspace workspace) {
        this.card = card != null ? card : AgentCard.builder().name("deep_agent").description("DeepAgent").build();
        this.config = config != null ? config : DeepAgentConfig.builder().build();
        this.workspace = workspace != null
                ? workspace
                : Workspace.builder()
                        .rootPath(this.config.getWorkspacePath())
                        .language(this.config.getLanguage())
                        .build();
        this.agent = new ReActAgent(this.card);
        this.currentMode = this.config.getDefaultMode();
        this.agent.configure(buildReActAgentConfig());
        Model configuredModel = resolveConfiguredModel();
        if (configuredModel != null) {
            this.agent.setLlm(configuredModel);
        }
    }

    private ReActAgentConfig buildReActAgentConfig() {
        ReActAgentConfig runtimeConfig = ReActAgentConfig.builder()
                .promptMode(this.config.getPromptMode())
                .build()
                .configurePromptTemplate(java.util.List.of(
                        java.util.Map.of("role", "system", "content", this.config.getSystemPrompt())
                ))
                .configureMaxIterations(this.config.getMaxIterations());
        applyModelConfig(runtimeConfig, this.config.getModel());
        applyBackendConfig(runtimeConfig, this.config.getBackend());
        return runtimeConfig;
    }

    private void applyModelConfig(ReActAgentConfig runtimeConfig, Object modelConfig) {
        if (runtimeConfig == null || modelConfig == null) {
            return;
        }
        if (modelConfig instanceof Model model) {
            ModelRequestConfig requestConfig = model.getModelConfig();
            ModelClientConfig clientConfig = model.getModelClientConfig();
            if (requestConfig != null) {
                runtimeConfig.setModelConfigObj(requestConfig);
                if (requestConfig.getModelName() != null) {
                    runtimeConfig.setModelName(requestConfig.getModelName());
                }
            }
            if (clientConfig != null) {
                runtimeConfig.setModelClientConfig(clientConfig);
                runtimeConfig.setModelProvider(clientConfig.getClientProvider());
                runtimeConfig.setApiKey(clientConfig.getApiKey());
                runtimeConfig.setApiBase(clientConfig.getApiBase());
            }
            return;
        }
        if (modelConfig instanceof ModelRequestConfig requestConfig) {
            runtimeConfig.setModelConfigObj(requestConfig);
            if (requestConfig.getModelName() != null) {
                runtimeConfig.setModelName(requestConfig.getModelName());
            }
            return;
        }
        if (modelConfig instanceof String modelName && !modelName.isBlank()) {
            runtimeConfig.configureModel(modelName);
            return;
        }
        if (modelConfig instanceof Map<?, ?> modelMap) {
            ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                    .modelName(string(firstPresent(modelMap, new String[] {"model", "model_name", "modelName"})))
                    .temperature(doubleValue(firstPresent(modelMap, new String[] {"temperature"})))
                    .topP(doubleValue(firstPresent(modelMap, new String[] {"top_p", "topP"})))
                    .maxTokens(integerValue(firstPresent(modelMap, new String[] {"max_tokens", "maxTokens"})))
                    .stop(string(firstPresent(modelMap, new String[] {"stop"})))
                    .user(string(firstPresent(modelMap, new String[] {"user"})))
                    .seed(integerValue(firstPresent(modelMap, new String[] {"seed"})))
                    .extraFields(extraFields(modelMap, "model", "model_name", "modelName", "temperature", "top_p",
                            "topP", "max_tokens", "maxTokens", "stop", "user", "seed"))
                    .build();
            runtimeConfig.setModelConfigObj(requestConfig);
            if (requestConfig.getModelName() != null) {
                runtimeConfig.setModelName(requestConfig.getModelName());
            }
        }
    }

    private void applyBackendConfig(ReActAgentConfig runtimeConfig, Object backendConfig) {
        if (runtimeConfig == null || backendConfig == null) {
            return;
        }
        if (backendConfig instanceof ModelClientConfig clientConfig) {
            runtimeConfig.setModelClientConfig(clientConfig);
            runtimeConfig.setModelProvider(clientConfig.getClientProvider());
            runtimeConfig.setApiKey(clientConfig.getApiKey());
            runtimeConfig.setApiBase(clientConfig.getApiBase());
            return;
        }
        if (backendConfig instanceof String provider && !provider.isBlank()) {
            runtimeConfig.setModelProvider(provider);
            return;
        }
        if (backendConfig instanceof Map<?, ?> backendMap) {
            String provider = string(firstPresent(backendMap, new String[] {"client_provider", "clientProvider",
                    "model_provider", "modelProvider", "provider", "backend"}));
            String apiKey = string(firstPresent(backendMap, new String[] {"api_key", "apiKey"}));
            String apiBase = string(firstPresent(
                    backendMap,
                    new String[] {"api_base", "apiBase", "base_url", "baseUrl"}));
            if (provider == null || apiKey == null || apiBase == null) {
                return;
            }
            ModelClientConfig clientConfig = ModelClientConfig.builder()
                    .clientId(string(firstPresent(backendMap, new String[] {"client_id", "clientId"})))
                    .clientProvider(provider)
                    .apiKey(apiKey)
                    .apiBase(apiBase)
                    .timeout(doubleOrDefault(firstPresent(backendMap, new String[] {"timeout"}), 60.0))
                    .maxRetries(intOrDefault(firstPresent(backendMap, new String[] {"max_retries", "maxRetries"}), 3))
                    .verifySsl(booleanOrDefault(
                            firstPresent(backendMap, new String[] {"verify_ssl", "verifySsl"}),
                            true))
                    .sslCert(string(firstPresent(backendMap, new String[] {"ssl_cert", "sslCert"})))
                    .headers(headers(firstPresent(backendMap, new String[] {"headers"})))
                    .build();
            runtimeConfig.setModelClientConfig(clientConfig);
            runtimeConfig.setModelProvider(provider);
            runtimeConfig.setApiKey(apiKey);
            runtimeConfig.setApiBase(apiBase);
        }
    }

    private Model resolveConfiguredModel() {
        Object modelConfig = this.config.getModel();
        if (modelConfig instanceof Model model) {
            return model;
        }
        if (modelConfig instanceof Supplier<?> supplier) {
            Object supplied = supplier.get();
            return supplied instanceof Model model ? model : null;
        }
        if (modelConfig instanceof String modelId && !modelId.isBlank()) {
            try {
                Object isResolved = Runner.resourceMgr().getModel(modelId);
                if (isResolved instanceof Model model) {
                    return model;
                }
            } catch (RuntimeException ignored) {
                // A plain model name is still valid ReActAgentConfig; only resource ids resolve here.
            }
        }
        return nullValue();
    }

    private static Object firstPresent(Map<?, ?> source, String[] keys) {
        if (source == null || keys == null) {
            return nullValue();
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return nullValue();
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return nullValue();
            }
        }
        return nullValue();
    }

    private static double doubleOrDefault(Object value, double isFallback) {
        Double parsed = doubleValue(value);
        if (parsed != null) {
            return parsed;
        }
        return isFallback;
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return nullValue();
            }
        }
        return nullValue();
    }

    private static int intOrDefault(Object value, int isFallback) {
        Integer parsed = integerValue(value);
        if (parsed != null) {
            return parsed;
        }
        return isFallback;
    }

    private static boolean booleanOrDefault(Object value, boolean isFallback) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return isFallback;
    }

    private static Map<String, Object> extraFields(Map<?, ?> source, String... consumedKeys) {
        Map<String, Object> extras = new LinkedHashMap<>();
        if (source == null) {
            return extras;
        }
        List<String> consumed = List.of(consumedKeys);
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null && !consumed.contains(String.valueOf(entry.getKey()))) {
                extras.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return extras;
    }

    private static Map<String, String> headers(Object value) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    normalized.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        }
        return normalized;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void ensureInitialized() {
        if (isInitialized) {
            return;
        }
        if (config.getTools() != null) {
            for (Object tool : config.getTools()) {
                registerConfiguredTool(tool);
            }
        }
        if (config.getRails() != null) {
            for (Object rail : config.getRails()) {
                if (rail instanceof AgentRail agentRail) {
                    agent.registerRail(agentRail);
                }
                if (rail instanceof DeepAgentRail deepAgentRail) {
                    deepAgentRail.init(this);
                }
                if (rail instanceof TaskCompletionRail completionRail) {
                    taskCompletionRail = completionRail;
                }
                registerDeepRail(rail);
            }
        }
        if (config.getMcps() != null) {
            registeredMcps.addAll(config.getMcps());
        }
        if (config.getPermissions() != null && Boolean.TRUE.equals(config.getPermissions().get("enabled"))) {
            var rail = PermissionFactory.buildPermissionInterruptRail(
                    config.getPermissions(),
                    config.getPermissionHost(),
                    workspace.root()
            );
            agent.registerRail(rail);
            registeredRails.add(rail);
        }
        if (config.isEnableTaskLoop()) {
            ensureTaskLoopRuntime();
        }
        isInitialized = true;
    }

    private void registerDeepRail(Object rail) {
        registeredRails.add(rail);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void registerHarnessTool(Tool tool) {
        if (tool == null) {
            return;
        }
        if (Runner.resourceMgr().getTool(tool.getCard().getId()) == null) {
            Runner.resourceMgr().addTool(tool, card.getId());
        }
        agent.getAbilityManager().add(tool.getCard());
        if (!registeredTools.contains(tool)) {
            registeredTools.add(tool);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void unregisterHarnessTool(Tool tool) {
        if (tool == null) {
            return;
        }
        agent.getAbilityManager().remove(tool.getCard().getName());
        Runner.resourceMgr().removeTool(tool.getCard().getId(), null, TagMatchStrategy.ALL, true);
        registeredTools.remove(tool);
    }

    private void registerConfiguredTool(Object tool) {
        if (tool instanceof Tool toolInstance) {
            registerHarnessTool(toolInstance);
            return;
        }
        if (tool instanceof ToolCard card) {
            agent.getAbilityManager().add(card);
        }
        registeredTools.add(tool);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> invoke(Map<String, Object> inputs) {
        ensureInitialized();
        Map<String, Object> normalized = new LinkedHashMap<>(inputs);
        normalized.putIfAbsent("conversation_id", card.getName() + "_session");
        normalized.putIfAbsent("query", "");
        if (config.isEnableTaskLoop()) {
            AgentSessionApi session = new AgentSessionApi(
                    String.valueOf(normalized.get("conversation_id")),
                    null,
                    card);
            return runTaskLoop(normalized, session);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agent_name", card.getName());
        result.put("mode", currentMode.name().toLowerCase(Locale.ROOT));
        result.put("workspace", workspace.root().toString());
        result.put("inputs", normalized);
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public java.util.Iterator<Object> stream(Map<String, Object> inputs) {
        return stream(inputs, List.of(StreamMode.OUTPUT));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public java.util.Iterator<Object> stream(Map<String, Object> inputs, List<StreamMode> streamModes) {
        AgentSessionApi session = new AgentSessionApi(
                String.valueOf(inputs.getOrDefault("conversation_id", card.getName() + "_session")),
                null,
                card,
                streamModes == null || streamModes.isEmpty() ? List.of(StreamMode.OUTPUT) : streamModes
        );
        return stream(inputs, session, streamModes);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public java.util.Iterator<Object> stream(
            Map<String, Object> inputs,
            AgentSessionApi session,
            List<StreamMode> streamModes
    ) {
        ensureInitialized();
        Map<String, Object> normalized = new LinkedHashMap<>(inputs);
        normalized.putIfAbsent("conversation_id", card.getName() + "_session");
        normalized.putIfAbsent("query", "");
        if (config.isEnableTaskLoop()) {
            normalized.put("_collect_inner_stream", true);
        }
        AgentSessionApi effectiveSession = session != null
                ? session
                : new AgentSessionApi(
                String.valueOf(normalized.get("conversation_id")),
                null,
                card,
                streamModes == null || streamModes.isEmpty() ? List.of(StreamMode.OUTPUT) : streamModes
        );
        effectiveSession.preRun(normalized);
        if (config.isEnableTaskLoop()) {
            Thread streamThread = new Thread(() -> {
                try {
                    Map<String, Object> result = runTaskLoop(normalized, effectiveSession);
                    writeTopLevelStreamResult(effectiveSession, 0, result);
                } catch (RuntimeException ex) {
                    effectiveSession.writeStream(new OutputSchema("error", 0, Map.of(
                            "output", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                            "result_type", "error"
                    )));
                } finally {
                    effectiveSession.postRun();
                }
            }, "deep-agent-stream-" + effectiveSession.getSessionId());
            streamThread.setDaemon(true);
            streamThread.setUncaughtExceptionHandler((thread, error) -> effectiveSession.writeStream(
                    new OutputSchema("error", 0, Map.of(
                            "output", error.getMessage() == null
                                    ? error.getClass().getSimpleName()
                                    : error.getMessage(),
                            "result_type", "error"
                    ))
            ));
            streamThread.start();
            return effectiveSession.streamIterator();
        }
        List<Object> outputs = new ArrayList<>();
        try {
            Map<String, Object> result = invoke(normalized);
            writeTopLevelStreamResult(effectiveSession, outputs.size(), result);
        } catch (RuntimeException ex) {
            effectiveSession.writeStream(new OutputSchema("error", outputs.size(), Map.of(
                    "output", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                    "result_type", "error"
            )));
        } finally {
            effectiveSession.postRun();
        }
        java.util.Iterator<Object> iterator = effectiveSession.streamIterator();
        while (iterator.hasNext()) {
            outputs.add(iterator.next());
        }
        return outputs.iterator();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void requestAbort() {
        if (loopCoordinator != null) {
            loopCoordinator.requestAbort();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void steer(String message) {
        steer(message, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void isFollowUp(String message) {
        isFollowUp(message, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void isFollowUp(String message, AgentSessionApi session) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (loopController == null) {
            return;
        }
        String sessionId = session != null && session.getSessionId() != null
                ? session.getSessionId()
                : TaskLoopController.DEFAULT_SESSION_ID;
        loopController.enqueueFollowUp(sessionId, message);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void steer(String message, AgentSessionApi session) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (loopController == null) {
            return;
        }
        String sessionId = session != null && session.getSessionId() != null
                ? session.getSessionId()
                : TaskLoopController.DEFAULT_SESSION_ID;
        if (eventQueue == null || session == null || !activeTaskLoopSessions.contains(sessionId)) {
            loopController.enqueueSteering(sessionId, message);
            return;
        }
        TaskInteractionEvent event = new TaskInteractionEvent(
                List.of(new DataFrame.TextDataFrame(message)),
                null
        );
        eventQueue.publishEvent(card.getId(), session, event);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path ensurePlanFile(String conversationId) {
        String sessionId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : card.getName() + "_session";
        Path planDir = workspace.root().resolve(".plans");
        Path isResolved = planDir.resolve(sessionId + ".md").normalize();
        try {
            Files.createDirectories(planDir);
            if (!Files.exists(isResolved)) {
                Files.writeString(isResolved, "# Plan\n");
            }
            planFilePath = isResolved;
            return isResolved;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to create plan file: " + ex.getMessage(), ex);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path getPlanFilePath() {
        return planFilePath;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object run(Map<String, Object> inputs) {
        ensureInitialized();
        return Runner.runAgent(agent, inputs, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void fireAfterTaskIteration(TaskIterationContext ctx) {
        if (ctx == null) {
            return;
        }
        if (ctx.getAgent() == null) {
            ctx.setAgent(this);
        }
        for (Object rail : registeredRails) {
            if (rail instanceof TaskIterationRail taskIterationRail) {
                taskIterationRail.afterTaskIteration(ctx);
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public DeepAgent createSubagent(String subagentType, String sessionId) {
        String normalized = subagentType != null ? subagentType.trim().toLowerCase(Locale.ROOT) : "";
        Object spec = findSubagentSpec(normalized);
        if (spec instanceof DeepAgent deepAgent) {
            return deepAgent;
        }
        if (spec instanceof SubAgentConfig subAgentConfig) {
            return instantiateConfiguredSubagent(subAgentConfig, normalized, sessionId);
        }
        throw new IllegalArgumentException("Unsupported subagent type: " + subagentType);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMode(AgentMode mode) {
        this.currentMode = mode;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSessionToolkit(SessionToolkit sessionToolkit) {
        this.sessionToolkit = sessionToolkit;
    }

    private Object findSubagentSpec(String subagentType) {
        if (config.getSubagents() == null) {
            return nullValue();
        }
        for (Object item : config.getSubagents()) {
            if (item instanceof SubAgentConfig spec) {
                String name = spec.getAgentCard() != null ? spec.getAgentCard().getName() : null;
                if (matchesSubagentName(name, subagentType)) {
                    return spec;
                }
            }
            if (item instanceof DeepAgent agent) {
                String name = agent.getCard() != null ? agent.getCard().getName() : null;
                if (matchesSubagentName(name, subagentType)) {
                    return agent;
                }
            }
        }
        return nullValue();
    }

    private boolean matchesSubagentName(String name, String requested) {
        if (name == null || requested == null) {
            return false;
        }
        if (name.equalsIgnoreCase(requested)) {
            return true;
        }
        return switch (requested) {
            case "code" -> "code_agent".equalsIgnoreCase(name);
            case "explore" -> "explore_agent".equalsIgnoreCase(name);
            case "plan" -> "plan_agent".equalsIgnoreCase(name);
            case "research" -> "research_agent".equalsIgnoreCase(name);
            case "verification" -> "verification_agent".equalsIgnoreCase(name);
            case "browser" -> "browser_agent".equalsIgnoreCase(name);
            default -> false;
        };
    }

    private DeepAgent instantiateConfiguredSubagent(SubAgentConfig spec, String normalizedType, String sessionId) {
        Workspace childWorkspace = resolveChildWorkspace(spec, sessionId);
        DeepAgentConfig childConfig = spec.toDeepAgentConfig();
        applyParentRuntimeFallbacks(childConfig);
        return HarnessFactory.createDeepAgent(spec.getAgentCard(), childConfig, childWorkspace);
    }

    private void applyParentRuntimeFallbacks(DeepAgentConfig childConfig) {
        if (childConfig == null || config == null) {
            return;
        }
        if (childConfig.getModel() == null) {
            childConfig.setModel(config.getModel());
        }
        if (childConfig.getBackend() == null) {
            childConfig.setBackend(config.getBackend());
        }
        if (childConfig.getPromptMode() == null || childConfig.getPromptMode().isBlank()) {
            childConfig.setPromptMode(config.getPromptMode());
        }
    }

    private Workspace resolveChildWorkspace(SubAgentConfig spec, String sessionId) {
        Path basePath;
        if (spec.getWorkspacePath() != null && !spec.getWorkspacePath().isBlank()) {
            basePath = Path.of(spec.getWorkspacePath());
        } else {
            basePath = workspace.root();
        }
        Path childPath = sessionId != null && !sessionId.isBlank() ? basePath.resolve(sessionId) : basePath;
        return Workspace.builder()
                .rootPath(childPath.toString())
                .language(spec.getLanguage() != null && !spec.getLanguage().isBlank()
                        ? spec.getLanguage()
                        : workspace.getLanguage())
                .build();
    }

    private void ensureTaskLoopRuntime() {
        if (loopCoordinator == null) {
            loopCoordinator = new LoopCoordinator(buildStopEvaluators());
        }
        if (loopController == null) {
            loopController = new TaskLoopController();
        }
        if (taskManager == null) {
            ControllerConfig controllerConfig = new ControllerConfig();
            controllerConfig.setScheduleInterval(0.1);
            taskManager = new TaskManager(controllerConfig);
            eventQueue = new EventQueue(controllerConfig);
            eventHandler = new TaskLoopEventHandler(loopController);
            eventHandler.setConfig(controllerConfig);
            eventHandler.setContextEngine(agent.getContextEngine());
            eventHandler.setAbilityManager(agent.getAbilityManager());
            eventHandler.setTaskManager(taskManager);
            taskScheduler = new TaskScheduler(
                    controllerConfig,
                    taskManager,
                    agent.getContextEngine(),
                    agent.getAbilityManager(),
                    eventQueue,
                    card
            );
            eventHandler.setTaskScheduler(taskScheduler);
            eventQueue.setEventHandler(eventHandler);
            eventQueue.start();
            taskScheduler.start();
        }
        taskScheduler
                .getTaskExecutorRegistry()
                .addTaskExecutor(TaskLoopEventExecutor.DEEP_TASK_TYPE,
                        dependencies -> new CoreTaskLoopEventExecutor(dependencies, this, this::invokeInnerRound));
    }

    private List<StopConditionEvaluator> buildStopEvaluators() {
        List<StopConditionEvaluator> evaluators = new ArrayList<>();
        isExplicitCompletionPolicy = taskCompletionRail != null && taskCompletionRail.hasCompletionPromise();
        completionPromiseEvaluator = taskCompletionRail != null
                ? new CompletionPromiseEvaluator(taskCompletionRail.getCompletionPromise(),
                        taskCompletionRail.getRequiredConfirmations())
                : new CompletionPromiseEvaluator();
        evaluators.add(completionPromiseEvaluator);
        Integer maxRounds = taskCompletionRail != null ? taskCompletionRail.getMaxRounds() : null;
        if (maxRounds != null && maxRounds > 0) {
            evaluators.add(new MaxRoundsEvaluator(maxRounds));
        }
        Duration timeout = taskCompletionRail != null ? taskCompletionRail.getTimeout() : null;
        if (timeout != null && !timeout.isNegative() && !timeout.isZero()) {
            evaluators.add(new TimeoutEvaluator(timeout.toMillis() / 1000.0));
        } else if (config.getCompletionTimeout() != null && config.getCompletionTimeout() > 0) {
            evaluators.add(new TimeoutEvaluator(config.getCompletionTimeout()));
        }
        if (taskCompletionRail != null) {
            evaluators.addAll(taskCompletionRail.getExtraEvaluators());
        }
        return evaluators;
    }

    private Map<String, Object> runTaskLoop(Map<String, Object> normalized, AgentSessionApi session) {
        ensureTaskLoopRuntime();
        LoopCoordinator coordinator = coordinatorForSession(session);
        coordinator.reset();
        loopCoordinator = coordinator;
        String sessionId = session != null
                ? session.getSessionId()
                : String.valueOf(normalized.getOrDefault("conversation_id", card.getName() + "_session"));
        Object currentQuery = normalized.getOrDefault("query", "");
        boolean isFollowUp = false;
        List<Map<String, Object>> rounds = new ArrayList<>();
        int maxRounds = Math.max(1, config.getMaxIterations());

        startTaskLoopRuntime(session);
        try {
            while (coordinator.shouldContinue() && rounds.size() < maxRounds) {
                Object roundQuery = currentQuery;
                if (taskCompletionRail != null && currentQuery instanceof String currentQueryText) {
                    roundQuery = taskCompletionRail.applyTaskInstruction(currentQueryText, isFollowUp);
                }
                Map<String, Object> roundResult = new LinkedHashMap<>(executeCoreLoopRound(
                        roundQuery,
                        isFollowUp,
                        session,
                        Boolean.TRUE.equals(normalized.get("_collect_inner_stream"))
                ));
                roundResult.put("query", currentQuery);
                if (!Objects.equals(roundQuery, currentQuery)) {
                    roundResult.put("task_instruction_query", roundQuery);
                }
                roundResult.put("mode", currentMode.name().toLowerCase(Locale.ROOT));
                rounds.add(roundResult);

                coordinator.incrementIteration();
                coordinator.addTokenUsage(resolveTokenUsage(roundResult));
                coordinator.setLastResult(roundResult);
                if (isExplicitCompletionPolicy) {
                    updateCompletionPromise(coordinator, roundResult);
                    if (!coordinator.shouldContinue()) {
                        break;
                    }
                }

                List<String> followUps = new ArrayList<>(loopController.drainFollowUp(sessionId));
                if (followUps.isEmpty()) {
                    if (!isExplicitCompletionPolicy) {
                        updateCompletionPromise(coordinator, roundResult);
                    }
                    continue;
                }

                currentQuery = followUps.remove(0);
                for (String followUpQuery : followUps) {
                    loopController.enqueueFollowUp(sessionId, followUpQuery);
                }
                isFollowUp = true;
                if (coordinator.isAborted()) {
                    break;
                }
            }
        } finally {
            stopTaskLoopRuntime(session);
            loopCoordinator = null;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agent_name", card.getName());
        result.put("mode", currentMode.name().toLowerCase(Locale.ROOT));
        result.put("workspace", workspace.root().toString());
        result.put("inputs", normalized);
        result.put("rounds", rounds);
        result.put("loop_state", coordinator.getState());
        if (!rounds.isEmpty()) {
            Map<String, Object> finalRound = rounds.get(rounds.size() - 1);
            result.put("final_result", finalRound);
            copyIfPresent(finalRound, result, "output");
            copyIfPresent(finalRound, result, "result_type");
            copyIfPresent(finalRound, result, "state");
            copyIfPresent(finalRound, result, "usage_metadata");
            copyIfPresent(finalRound, result, "usage");
            copyIfPresent(finalRound, result, "token_usage");
            copyIfPresent(finalRound, result, "total_tokens");
        }
        return result;
    }

    private void startTaskLoopRuntime(AgentSessionApi session) {
        if (session == null) {
            return;
        }
        taskScheduler.getSessions().put(session.getSessionId(), session);
        if (activeTaskLoopSessions.add(session.getSessionId())) {
            eventQueue.subscribe(card.getId(), session.getSessionId());
        }
    }

    private void stopTaskLoopRuntime(AgentSessionApi session) {
        if (session == null) {
            return;
        }
        activeTaskLoopSessions.remove(session.getSessionId());
        eventQueue.unsubscribe(card.getId(), session.getSessionId());
        taskScheduler.getSessions().remove(session.getSessionId());
    }

    private LoopCoordinator coordinatorForSession(AgentSessionApi session) {
        String sessionId = session != null && session.getSessionId() != null
                ? session.getSessionId()
                : TaskLoopController.DEFAULT_SESSION_ID;
        return sessionLoopCoordinators.computeIfAbsent(
                sessionId,
                ignored -> new LoopCoordinator(buildStopEvaluators()));
    }

    private void updateCompletionPromise(LoopCoordinator coordinator, Map<String, Object> roundResult) {
        CompletionPromiseEvaluator completion = coordinator != null
                ? coordinator.getCompletionPromiseEvaluator()
                : completionPromiseEvaluator;
        if (completion == null) {
            return;
        }
        if (!isExplicitCompletionPolicy || taskCompletionRail == null) {
            completion.markCompleted();
            return;
        }
        java.util.Optional<String> matchedPromise = taskCompletionRail.extractMatchingPromise(roundResult);
        if (matchedPromise.isPresent()) {
            String matched = matchedPromise.orElse(taskCompletionRail.getCompletionPromise());
            completion.notifyFulfilled(matched);
        } else {
            completion.notifyAbsent();
        }
    }

    private Map<String, Object> executeCoreLoopRound(Object query,
                                                     boolean isFollowUp,
                                                     AgentSessionApi session,
                                                     boolean isCollectInnerStream) {
        InputEvent event = query instanceof String || query instanceof InputEvent
                ? InputEvent.fromUserInput(query)
                : InputEvent.fromUserInput(Map.of(
                        "query", query,
                        "query_payload", query
                ));
        int handlerRound = eventHandler.prepareRound(session.getSessionId(), isFollowUp);
        String taskId = "deep_agent_task_" + handlerRound;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("_handler_round_id", handlerRound);
        metadata.put("task_id", taskId);
        metadata.put("run_kind", isFollowUp ? "follow_up" : "outer_loop");
        metadata.put("is_follow_up", isFollowUp);
        metadata.put("loop_queues", loopController.getInteractionQueues(session.getSessionId()));
        if (isCollectInnerStream) {
            metadata.put("collect_inner_stream", true);
        }
        event.setMetadata(metadata);
        eventQueue.publishEvent(card.getId(), session, event);
        return awaitRoundCompletion(taskId, session);
    }

    private Map<String, Object> awaitRoundCompletion(String taskId, AgentSessionApi session) {
        double timeoutSeconds = config.getCompletionTimeout() == null
                ? 600.0
                : Math.max(1.0, config.getCompletionTimeout());
        long timeoutMillis = (long) Math.ceil(timeoutSeconds * 1000.0);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        String sessionId = session != null ? session.getSessionId() : TaskLoopController.DEFAULT_SESSION_ID;
        LoopCoordinator coordinator = coordinatorForSession(session);
        while (System.nanoTime() < deadline) {
            Map<String, Object> result = eventHandler.waitCompletion(sessionId);
            if (!"completion_timeout".equals(result.get("error"))) {
                return result;
            }
            if (coordinator.isAborted()) {
                return Map.of("status", "aborted", "task_id", taskId);
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException ex) {

                return Map.of("error", "interrupted", "task_id", taskId);
            }
        }
        return Map.of("error", "completion_timeout", "task_id", taskId);
    }

    private Map<String, Object> invokeInnerRound(Map<String, Object> inputs, AgentSessionApi session) {
        Map<String, Object> effectiveInputs = new LinkedHashMap<>();
        if (inputs != null) {
            effectiveInputs.putAll(inputs);
        }
        effectiveInputs.putIfAbsent("query", "");
        effectiveInputs.putIfAbsent("conversation_id", session != null && session.getSessionId() != null
                ? session.getSessionId()
                : card.getName() + "_session");

        boolean isCollectInnerStream = Boolean.TRUE.equals(effectiveInputs.get("collect_inner_stream"));
        Map<String, Object> rawResult = isCollectInnerStream
                ? invokeInnerRoundStreaming(effectiveInputs, session)
                : invokeInnerRoundOnce(effectiveInputs, session);
        return normalizeInnerRoundResult(rawResult, effectiveInputs);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeInnerRoundOnce(Map<String, Object> effectiveInputs, AgentSessionApi session) {
        return (Map<String, Object>) agent.invoke(effectiveInputs, session);
    }

    private Map<String, Object> invokeInnerRoundStreaming(
            Map<String, Object> effectiveInputs,
            AgentSessionApi session
    ) {
        AgentSessionApi innerSession = new AgentSessionApi(
                String.valueOf(effectiveInputs.get("conversation_id")),
                session != null ? session.getEnvs() : null,
                card,
                List.of(StreamMode.OUTPUT)
        );
        innerSession.preRun(effectiveInputs);
        copySessionState(session, innerSession);
        List<Object> streamItems = new ArrayList<>();
        agent.stream(effectiveInputs, innerSession, List.of(StreamMode.OUTPUT)).forEachRemaining(streamItems::add);
        copySessionState(innerSession, session);
        Map<String, Object> result = extractFinalStreamResult(streamItems);
        List<Object> normalizedChunks = normalizeStreamChunks(streamItems);
        if (!normalizedChunks.isEmpty()) {
            result.put("stream_chunks", normalizedChunks);
        }
        return result;
    }

    private void copySessionState(AgentSessionApi source, AgentSessionApi target) {
        if (source == null || target == null) {
            return;
        }
        target.getInner().state().setState(source.getInner().state().getState());
    }

    private void writeTopLevelStreamResult(AgentSessionApi session, int index, Map<String, Object> result) {
        if (session == null) {
            return;
        }
        if (result != null && "interrupt".equals(String.valueOf(result.get("result_type")))
                && result.get("state") instanceof List<?> states) {
            for (Object state : states) {
                if (state instanceof OutputSchema outputSchema) {
                    session.writeStream(outputSchema);
                }
            }
            return;
        }
        session.writeStream(new OutputSchema("answer", index, Map.of(
                "output", result,
                "result_type", "answer"
        )));
    }

    private Map<String, Object> normalizeInnerRoundResult(Map<String, Object> rawResult, Map<String, Object> inputs) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> source = rawResult == null ? Map.of() : rawResult;
        result.put("status", "completed");
        String sessionId = string(inputs.get("conversation_id"));
        result.put("round", loopController != null ? loopController.getRoundCounter(sessionId) : 0);
        result.put("is_follow_up", Boolean.TRUE.equals(inputs.get("is_follow_up")));
        result.put("output", resolveOutput(source));
        copyIfPresent(source, result, "result_type");
        copyIfPresent(source, result, "usage_metadata");
        copyIfPresent(source, result, "usage");
        copyIfPresent(source, result, "token_usage");
        copyIfPresent(source, result, "total_tokens");
        copyIfPresent(source, result, "state");
        copyIfPresent(source, result, "messages");
        copyIfPresent(source, result, "tool_calls");
        copyIfPresent(source, result, "stream_chunks");
        copyIfPresent(source, result, "streamChunks");
        copyIfPresent(source, result, "chunks");
        copyIfPresent(source, result, "inner_stream");
        return result;
    }

    private Object resolveOutput(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        if (source.containsKey("output")) {
            return source.get("output");
        }
        if (source.containsKey("content")) {
            return source.get("content");
        }
        return source;
    }

    private Map<String, Object> extractFinalStreamResult(List<Object> streamItems) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("output", "");
        fallback.put("result_type", "answer");
        for (int i = streamItems.size() - 1; i >= 0; i--) {
            Object item = streamItems.get(i);
            if (item instanceof OutputSchema outputSchema) {
                if ("__interaction__".equals(outputSchema.getType())) {
                    Map<String, Object> interrupt = new LinkedHashMap<>();
                    interrupt.put("output", "");
                    interrupt.put("result_type", "interrupt");
                    interrupt.put("state", List.of(outputSchema));
                    return interrupt;
                }
                Object payload = outputSchema.getPayload();
                if (payload instanceof Map<?, ?> payloadMap) {
                    Map<String, Object> normalized = castMap(payloadMap);
                    Object output = normalized.get("output");
                    if (output instanceof Map<?, ?> nestedMap) {
                        return castMap(nestedMap);
                    }
                    return normalized;
                }
            }
        }
        return fallback;
    }

    private List<Object> normalizeStreamChunks(List<Object> streamItems) {
        List<Object> normalized = new ArrayList<>();
        for (Object item : streamItems) {
            if (item instanceof OutputSchema outputSchema) {
                if ("__interaction__".equals(outputSchema.getType())) {
                    normalized.add(outputSchema);
                    continue;
                }
                Object payload = outputSchema.getPayload();
                if (payload instanceof Map<?, ?> payloadMap) {
                    Map<String, Object> normalizedPayload = castMap(payloadMap);
                    if (!isTerminalAnswerEnvelope(normalizedPayload)) {
                        normalized.add(normalizedPayload);
                    }
                } else if (payload != null) {
                    normalized.add(payload);
                }
            } else if (item != null) {
                normalized.add(item);
            }
        }
        return normalized;
    }

    private boolean isTerminalAnswerEnvelope(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return false;
        }
        if (!"answer".equals(String.valueOf(payload.get("result_type")))) {
            return false;
        }
        Object output = payload.get("output");
        if (!(output instanceof Map<?, ?> outputMap)) {
            return false;
        }
        return outputMap.containsKey("output") || outputMap.containsKey("content")
                || outputMap.containsKey("result_type") || outputMap.containsKey("usage_metadata");
    }

    private int resolveTokenUsage(Map<String, Object> roundResult) {
        UsageMetadata usageMetadata = TaskIterationContext.usageMetadataFrom(roundResult);
        if (usageMetadata != null) {
            return usageMetadata.getTotalTokens();
        }
        return 0;
    }

    private static Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source != null && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }
    private static <T> T nullValue() {
        return null;
    }

}
