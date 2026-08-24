/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.runner.base.Tag;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;
import com.openjiuwen.core.multitenant.TmpFileCleaner;
import com.openjiuwen.core.multitenant.workspace.TieredWorkspaceManager;
import com.openjiuwen.core.multitenant.workspace.WorkspaceResolution;
import com.openjiuwen.core.multitenant.workspace.WorkspaceStore;
import com.openjiuwen.core.multitenant.workspace.WorkspaceType;
import com.openjiuwen.core.multitenant.workspace.WorkspaceStoreFactory;
import com.openjiuwen.core.multitenant.workspace.store.LocalWorkspaceStore;
import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.EventQueue;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.modules.TaskScheduler;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.TaskInteractionEvent;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.tracer.Tracer;
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
import lombok.Setter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Minimal Java baseline for the Python DeepAgent public surface.
 * 
 * @since 0.1.7
 */
@Getter
public class DeepAgent implements AutoCloseable {
    private static final ExecutorService STREAM_EXECUTOR =
            OpenJiuwenExecutors.newBoundedModulePool("deep-agent-stream", true);

    private final AgentCard card;
    private final DeepAgentConfig config;
    private final Workspace workspace;
    private final ReActAgent agent;
    private AgentMode currentMode;

    /**
     * CopyOnWriteArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<Object> registeredRails = new CopyOnWriteArrayList<>();

    /**
     * CopyOnWriteArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<Object> registeredTools = new CopyOnWriteArrayList<>();

    /**
     * CopyOnWriteArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<McpServerConfig> registeredMcps = new CopyOnWriteArrayList<>();
    private SessionToolkit sessionToolkit;
    private TenantWorkspaceResolver workspaceResolver;
    private TieredWorkspaceManager tieredWorkspaceManager;
    private TmpFileCleaner tmpFileCleaner;

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, LoopCoordinator> sessionLoopCoordinators = new ConcurrentHashMap<>();
    private TaskLoopController loopController;
    private TaskManager taskManager;
    private TaskScheduler taskScheduler;
    private EventQueue eventQueue;
    private TaskLoopEventHandler eventHandler;

    /**
     * ConcurrentHashMap.newKeySet.
     * 
     * @since 0.1.7
     */
    private final Set<String> activeTaskLoopSessions = ConcurrentHashMap.newKeySet();
    private Path planFilePath;
    private boolean isInitialized;
    private TaskCompletionRail taskCompletionRail;
    @Setter
    private BaseKVStore kvStore;
    private CompletionPromiseEvaluator completionPromiseEvaluator;
    private boolean isExplicitCompletionPolicy;

    /**
     * DeepAgent.
     * 
     * @param card card
     * @param config config
     * @param workspace workspace
     * @since 0.1.7
     */
    public DeepAgent(AgentCard card, DeepAgentConfig config, Workspace workspace) {
        this.card = card != null ? card : AgentCard.builder().name("deep_agent").description("DeepAgent").build();
        this.config = config != null ? config : DeepAgentConfig.builder().build();
        this.workspace = workspace != null
                ? workspace
                : Workspace.builder().rootPath(this.config.getWorkspacePath()).language(this.config.getLanguage())
                        .build();
        this.agent = new ReActAgent(this.card);
        this.currentMode = this.config.getDefaultMode();
        this.agent.configure(buildReActAgentConfig());
        Model configuredModel = resolveConfiguredModel();
        if (configuredModel != null) {
            this.agent.setLlm(configuredModel);
        }
        if (this.config.isEnableTenantIsolation()) {
            String basePath = this.config.getTenantDataRoot() != null
                    ? this.config.getTenantDataRoot() : this.config.getWorkspacePath();
            this.workspaceResolver = new TenantWorkspaceResolver(basePath);
            this.tmpFileCleaner = new TmpFileCleaner(
                this.config.getTmpTtl(), this.config.getTmpTtlScanInterval(), basePath, this.workspaceResolver);
            this.tmpFileCleaner.start();
        }
    }

    /**
     * Get the TenantWorkspaceResolver, null if tenant isolation is not enabled.
     *
     * @return the workspaceResolver or null
     * @since 0.1.13
     */
    public TenantWorkspaceResolver getWorkspaceResolver() {
        return workspaceResolver;
    }

    /**
     * Check if tenant isolation is enabled.
     *
     * @return true if tenant isolation is enabled
     * @since 0.1.13
     */
    public boolean isTenantIsolationEnabled() {
        return config != null && config.isEnableTenantIsolation();
    }

    /**
     * buildReActAgentConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    private ReActAgentConfig buildReActAgentConfig() {
        ReActAgentConfig runtimeConfig = ReActAgentConfig.builder().promptMode(this.config.getPromptMode()).build()
                .configurePromptTemplate(
                        java.util.List.of(java.util.Map.of("role", "system", "content", this.config.getSystemPrompt())))
                .configureMaxIterations(this.config.getMaxIterations());
        applyModelConfig(runtimeConfig, this.config.getModel());
        applyBackendConfig(runtimeConfig, this.config.getBackend());
        return runtimeConfig;
    }

    /**
     * applyModelConfig.
     * 
     * @param runtimeConfig runtimeConfig
     * @param modelConfig modelConfig
     * @since 0.1.7
     */
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
                    .modelName(string(firstPresent(modelMap, new String[]{"model", "model_name", "modelName"})))
                    .temperature(doubleValue(firstPresent(modelMap, new String[]{"temperature"})))
                    .topP(doubleValue(firstPresent(modelMap, new String[]{"top_p", "topP"})))
                    .maxTokens(integerValue(firstPresent(modelMap, new String[]{"max_tokens", "maxTokens"})))
                    .stop(string(firstPresent(modelMap, new String[]{"stop"})))
                    .user(string(firstPresent(modelMap, new String[]{"user"})))
                    .seed(integerValue(firstPresent(modelMap, new String[]{"seed"})))
                    .extraFields(extraFields(modelMap, "model", "model_name", "modelName", "temperature", "top_p",
                            "topP", "max_tokens", "maxTokens", "stop", "user", "seed"))
                    .build();
            runtimeConfig.setModelConfigObj(requestConfig);
            if (requestConfig.getModelName() != null) {
                runtimeConfig.setModelName(requestConfig.getModelName());
            }
        }
    }

    /**
     * applyBackendConfig.
     * 
     * @param runtimeConfig runtimeConfig
     * @param backendConfig backendConfig
     * @since 0.1.7
     */
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
            String provider = string(firstPresent(backendMap, new String[]{"client_provider", "clientProvider",
                    "model_provider", "modelProvider", "provider", "backend"}));
            String apiKey = string(firstPresent(backendMap, new String[]{"api_key", "apiKey"}));
            String apiBase =
                string(firstPresent(backendMap, new String[]{"api_base", "apiBase", "base_url", "baseUrl"}));
            if (provider == null || apiKey == null || apiBase == null) {
                return;
            }
            ModelClientConfig clientConfig = ModelClientConfig.builder()
                    .clientId(string(firstPresent(backendMap, new String[]{"client_id", "clientId"})))
                    .clientProvider(provider).apiKey(apiKey).apiBase(apiBase)
                    .timeout(doubleOrDefault(firstPresent(backendMap, new String[]{"timeout"}), 60.0))
                    .maxRetries(intOrDefault(firstPresent(backendMap, new String[]{"max_retries", "maxRetries"}), 3))
                    .verifySsl(
                            booleanOrDefault(firstPresent(backendMap, new String[]{"verify_ssl", "verifySsl"}), true))
                    .sslCert(string(firstPresent(backendMap, new String[]{"ssl_cert", "sslCert"})))
                    .headers(headers(firstPresent(backendMap, new String[]{"headers"}))).build();
            runtimeConfig.setModelClientConfig(clientConfig);
            runtimeConfig.setModelProvider(provider);
            runtimeConfig.setApiKey(apiKey);
            runtimeConfig.setApiBase(apiBase);
        }
    }

    /**
     * resolveConfiguredModel.
     * 
     * @return the result
     * @since 0.1.7
     */
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
            } catch (BaseError ignored) {
                // A plain model name is still valid ReActAgentConfig; only resource ids resolve here.
            }
        }
        return nullValue();
    }

    /**
     * firstPresent.
     * 
     * @param source source
     * @param keys keys
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * string.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * doubleValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * doubleOrDefault.
     * 
     * @param value value
     * @param isFallback isFallback
     * @return the result
     * @since 0.1.7
     */
    private static double doubleOrDefault(Object value, double isFallback) {
        Double parsed = doubleValue(value);
        if (parsed != null) {
            return parsed;
        }
        return isFallback;
    }

    /**
     * integerValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * intOrDefault.
     * 
     * @param value value
     * @param isFallback isFallback
     * @return the result
     * @since 0.1.7
     */
    private static int intOrDefault(Object value, int isFallback) {
        Integer parsed = integerValue(value);
        if (parsed != null) {
            return parsed;
        }
        return isFallback;
    }

    /**
     * booleanOrDefault.
     * 
     * @param value value
     * @param isFallback isFallback
     * @return the result
     * @since 0.1.7
     */
    private static boolean booleanOrDefault(Object value, boolean isFallback) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return isFallback;
    }

    /**
     * extraFields.
     * 
     * @param source source
     * @param consumedKeys consumedKeys
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * headers.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
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
     * ensureInitialized.
     * 
     * @since 0.1.7
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
        // Register config.mcps before rails
        registerPendingMcps();
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
        // Sync MCP servers already registered externally (e.g. ResourceMgr.addMcpServer).
        syncMcpServersFromResourceMgr();
        if (config.getPermissions() != null && Boolean.TRUE.equals(config.getPermissions().get("enabled"))) {
            var rail = PermissionFactory.buildPermissionInterruptRail(config.getPermissions(),
                    config.getPermissionHost(), workspace.root());
            agent.registerRail(rail);
            registeredRails.add(rail);
        }
        if (config.isEnableTaskLoop()) {
            ensureTaskLoopRuntime();
        }
        isInitialized = true;
    }

    /**
     * Registers config-declared MCP servers into ResourceMgr and AbilityManager.
     * <p>
     * Aligns with Python {@code _register_pending_mcps}. Existing identical configs are re-tagged;
     * conflicting configs for the same server id fail fast.
     *
     * @since 0.1.14
     */
    private void registerPendingMcps() {
        if (config.getMcps() == null || config.getMcps().isEmpty()) {
            return;
        }
        for (McpServerConfig mcpConfig : config.getMcps()) {
            registerOnePendingMcp(mcpConfig);
        }
    }

    /**
     * Registers a single config-declared MCP server, or re-tags an identical existing one.
     *
     * @param mcpConfig MCP server config from DeepAgent configuration
     * @since 0.1.14
     */
    private void registerOnePendingMcp(McpServerConfig mcpConfig) {
        mcpConfig.normalizeServerId();
        McpServerConfig existing = Runner.resourceMgr().getMcpServerConfig(mcpConfig.getServerId());
        if (existing == null) {
            addNewPendingMcp(mcpConfig);
        } else {
            retagExistingPendingMcp(existing, mcpConfig);
        }
        agent.getAbilityManager().add(mcpConfig);
        if (!registeredMcps.contains(mcpConfig)) {
            registeredMcps.add(mcpConfig);
        }
    }

    /**
     * Adds a new MCP server via ResourceMgr and fails fast on any error result.
     *
     * @param mcpConfig MCP server config to add
     * @since 0.1.14
     */
    private void addNewPendingMcp(McpServerConfig mcpConfig) {
        List<Result<String>> results = Runner.resourceMgr().addMcpServer(mcpConfig, card.getId(), null);
        throwIfAddMcpFailed(results, mcpConfig);
    }

    /**
     * Throws when any ResourceMgr addMcpServer result is an error.
     *
     * @param results addMcpServer results
     * @param mcpConfig config used for error context
     * @since 0.1.14
     */
    private static void throwIfAddMcpFailed(List<Result<String>> results, McpServerConfig mcpConfig) {
        for (Result<String> result : results) {
            if (!result.isError()) {
                continue;
            }
            Exception error = result.getError();
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, "server_config",
                    String.valueOf(mcpConfig), "reason",
                    error != null ? error.getMessage() : "add_mcp_server failed");
        }
    }

    /**
     * Re-tags an already-registered MCP server when configs match; otherwise fails fast.
     *
     * @param existing already-registered config
     * @param mcpConfig candidate config from DeepAgent config
     * @since 0.1.14
     */
    private void retagExistingPendingMcp(McpServerConfig existing, McpServerConfig mcpConfig) {
        if (!sameMcpServerConfig(existing, mcpConfig)) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, "server_config",
                    String.valueOf(mcpConfig), "reason",
                    "server_id '" + mcpConfig.getServerId()
                            + "' is already registered with a different config");
        }
        ensureResourceTagged(mcpConfig.getServerId(), card.getId(), mcpConfig);
        for (String toolId : Runner.resourceMgr().getMcpToolIds(mcpConfig.getServerId())) {
            ensureResourceTagged(toolId, card.getId(), mcpConfig);
        }
    }

    /**
     * Ensures {@code resourceId} carries the agent tag, mapping ResourceMgr errors to MCP add failures.
     *
     * @param resourceId server or tool resource id
     * @param tag agent card id (or equivalent) to attach
     * @param mcpConfig config used only for error context
     * @since 0.1.7
     */
    private void ensureResourceTagged(String resourceId, String tag, McpServerConfig mcpConfig) {
        Result<List<String>> tagResult = Runner.resourceMgr().addResourceTag(resourceId, tag);
        if (tagResult.isError()) {
            Exception error = tagResult.getError();
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, "server_config",
                    String.valueOf(mcpConfig), "reason",
                    error != null ? error.getMessage() : "add_resource_tag failed");
        }
    }

    /**
     * Pulls MCP servers already present in ResourceMgr (agent + global tags) into AbilityManager / registeredMcps.
     * <p>
     * Skips servers whose {@code serverName} was already registered so config-declared MCPs win on name clashes.
     *
     * @since 0.1.7
     */
    private void syncMcpServersFromResourceMgr() {
        Set<String> seenServerNames = new HashSet<>();
        for (McpServerConfig already : registeredMcps) {
            if (already.getServerName() != null) {
                seenServerNames.add(already.getServerName());
            }
        }
        for (Object tag : List.of(card.getId(), Tag.GLOBAL)) {
            List<McpServerConfig> configs = Runner.resourceMgr().listMcpServers(tag);
            for (McpServerConfig mcpConfig : configs) {
                if (mcpConfig == null || mcpConfig.getServerName() == null || mcpConfig.getServerName().isBlank()) {
                    continue;
                }
                if (!seenServerNames.add(mcpConfig.getServerName())) {
                    continue;
                }
                if (agent.getAbilityManager().get(mcpConfig.getServerName()) == null) {
                    agent.getAbilityManager().add(mcpConfig);
                }
                if (!registeredMcps.contains(mcpConfig)) {
                    registeredMcps.add(mcpConfig);
                }
            }
        }
    }

    /**
     * Compares two MCP configs for "same registration" (id/name/path/client type and key transport fields).
     *
     * @param left already-registered config
     * @param right candidate config from DeepAgent config
     * @return {@code true} when they are treated as the same server registration
     * @since 0.1.7
     */
    private static boolean sameMcpServerConfig(McpServerConfig left, McpServerConfig right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        String leftClientType = normalizeClientType(left.getClientType());
        String rightClientType = normalizeClientType(right.getClientType());
        return Objects.equals(left.getServerId(), right.getServerId())
                && Objects.equals(left.getServerName(), right.getServerName())
                && Objects.equals(left.getServerPath(), right.getServerPath())
                && Objects.equals(leftClientType, rightClientType);
    }

    /**
     * Normalizes MCP client type aliases (e.g. {@code streamable-http} → {@code streamable_http}).
     *
     * @param clientType raw client type from config; may be null
     * @return normalized client type, or the original value when no alias applies
     * @since 0.1.7
     */
    private static String normalizeClientType(String clientType) {
        if ("streamable-http".equals(clientType)) {
            return "streamable_http";
        }
        return clientType;
    }

    /**
     * registerDeepRail.
     * 
     * @param rail rail
     * @since 0.1.7
     */
    private void registerDeepRail(Object rail) {
        registeredRails.add(rail);
    }

    /**
     * registerHarnessTool.
     * 
     * @param tool tool
     * @since 0.1.7
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
     * unregisterHarnessTool.
     * 
     * @param tool tool
     * @since 0.1.7
     */
    public void unregisterHarnessTool(Tool tool) {
        if (tool == null) {
            return;
        }
        agent.getAbilityManager().remove(tool.getCard().getName());
        Runner.resourceMgr().removeTool(tool.getCard().getId(), null, TagMatchStrategy.ALL, true);
        registeredTools.remove(tool);
    }

    /**
     * registerConfiguredTool.
     * 
     * @param tool tool
     * @since 0.1.7
     */
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
     * invoke.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> invoke(Map<String, Object> inputs) {
        AgentSessionApi session = null;
        return invoke(inputs, session);
    }

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @param tenantCtx tenantCtx
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> invoke(Map<String, Object> inputs, TenantContext tenantCtx) {
        requireTenantContext(tenantCtx);
        TenantContextHolder.setCurrentTenant(tenantCtx);
        try {
            bindTenantWorkspace(tenantCtx);
            return invoke(inputs);
        } finally {
            TenantContextHolder.clearCurrentTenant();
            unbindTenantWorkspace();
        }
    }

    /**
     * 执行 DeepAgent，传入外部 session 以支持中断恢复。
     * <p>
     * 外部 session 的状态（如中断信息）会被传播到内部 session，
     * 执行完毕后内部 session 的状态也会反向传播回外部 session。
     *
     * @param inputs  输入参数（必须包含 query 和 conversation_id）
     * @param session 外部 session，可为 null（此时自动创建新 session）
     * @return 执行结果
     */
    public Map<String, Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
        TenantContext ctx = (session != null) ? session.getTenantContext() : null;
        requireTenantContext(ctx);
        if (ctx != null && ctx.isTenantAware()) {
            TenantContextHolder.setCurrentTenant(ctx);
            try {
                bindTenantWorkspace(ctx);
                return invokeInternal(inputs, session);
            } finally {
                TenantContextHolder.clearCurrentTenant();
                unbindTenantWorkspace();
            }
        }
        return invokeInternal(inputs, session);
    }

    private Map<String, Object> invokeInternal(Map<String, Object> inputs, AgentSessionApi session) {
        ensureInitialized();
        Map<String, Object> normalized = new LinkedHashMap<>(inputs);
        normalized.putIfAbsent("conversation_id", card.getName() + "_session");
        normalized.putIfAbsent("query", "");
        if (config.isEnableTaskLoop()) {
            String requestLevelSessionId = String.valueOf(normalized.get("conversation_id"));
            AgentSessionApi effectiveSession = session != null
                    ? new AgentSessionApi(requestLevelSessionId, session.getEnvs(), card)
                    : new AgentSessionApi(requestLevelSessionId, null, card);
            // 传播租户上下文到 effective session，确保任务线程能重新绑定（task-loop 跨线程）
            TenantContext effectiveCtx = session != null ? session.getTenantContext() : null;
            if (effectiveCtx == null || !effectiveCtx.isTenantAware()) {
                effectiveCtx = TenantContextHolder.getCurrentTenant();
            }
            if (effectiveCtx != null && effectiveCtx.isTenantAware()) {
                effectiveSession.withTenantContext(effectiveCtx);
            }
            // preRun 前：把 Runner 的 runState（PRE_DONE）拷到 effectiveSession，
            // effectiveSession.preRun 被 CAS 跳过（省 1 次 checkpoint 读）。
            // copySessionState 保持在 preRun 之后，维持原始时序（Runner 最新状态覆盖 recover 结果）。
            // postRun 后：把 effectiveSession 的 runState（PRE_DONE | POST_DONE）拷回 Runner session，
            // Runner.postRun 也被 CAS 跳过（省 1 次 checkpoint 写）。
            if (session != null) {
                effectiveSession.copyRunState(session);
            }
            effectiveSession.preRun(normalized);
            if (session != null) {
                copySessionState(session, effectiveSession);
            }
            Map<String, Object> result = runTaskLoop(normalized, effectiveSession);
            effectiveSession.postRun();
            if (session != null) {
                copySessionState(effectiveSession, session);
                session.copyRunState(effectiveSession);
            }
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agent_name", card.getName());
        result.put("mode", currentMode.name().toLowerCase(Locale.ROOT));
        result.put("workspace", workspace.root().toString());
        result.put("inputs", normalized);
        return result;
    }

    /**
     * stream.
     * 
     * @param inputs inputs
     * @return Iterator<Object>
     * @since 0.1.7
     */
    public java.util.Iterator<Object> stream(Map<String, Object> inputs) {
        return stream(inputs, List.of(StreamMode.OUTPUT));
    }

    /**
     * stream.
     * 
     * @param inputs inputs
     * @param tenantCtx tenantCtx
     * @return Iterator<Object>
     * @since 0.1.7
     */
    public java.util.Iterator<Object> stream(Map<String, Object> inputs, TenantContext tenantCtx) {
        requireTenantContext(tenantCtx);
        TenantContextHolder.setCurrentTenant(tenantCtx);
        try {
            bindTenantWorkspace(tenantCtx);
            return stream(inputs);
        } finally {
            TenantContextHolder.clearCurrentTenant();
            unbindTenantWorkspace();
        }
    }

    /**
     * stream.
     * 
     * @param inputs inputs
     * @param streamModes streamModes
     * @return Iterator<Object>
     * @since 0.1.7
     */
    public java.util.Iterator<Object> stream(Map<String, Object> inputs, List<StreamMode> streamModes) {
        String requestLevelSessionId = String.valueOf(inputs.getOrDefault("conversation_id",
        card.getName() + "_session"));
        AgentSessionApi session = new AgentSessionApi(
                requestLevelSessionId,
                null,
                card,
                streamModes == null || streamModes.isEmpty() ? List.of(StreamMode.OUTPUT) : streamModes
        );
        return stream(inputs, session, streamModes);
    }

    /**
     * stream.
     * 
     * @param inputs inputs
     * @param streamModes streamModes
     * @param tenantCtx tenantCtx
     * @return Iterator<Object>
     * @since 0.1.7
     */
    public java.util.Iterator<Object> stream(Map<String, Object> inputs, List<StreamMode> streamModes,
            TenantContext tenantCtx) {
        requireTenantContext(tenantCtx);
        TenantContextHolder.setCurrentTenant(tenantCtx);
        try {
            bindTenantWorkspace(tenantCtx);
            return stream(inputs, streamModes);
        } finally {
            TenantContextHolder.clearCurrentTenant();
            unbindTenantWorkspace();
        }
    }

    /**
     * stream.
     * 
     * @param inputs inputs
     * @param session session
     * @param streamModes streamModes
     * @return Iterator<Object>
     * @since 0.1.7
     */
    public java.util.Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session,
            List<StreamMode> streamModes) {
        TenantContext ctx = (session != null) ? session.getTenantContext() : null;
        requireTenantContext(ctx);
        if (ctx != null && ctx.isTenantAware()) {
            TenantContextHolder.setCurrentTenant(ctx);
            try {
                bindTenantWorkspace(ctx);
                return streamInternal(inputs, session, streamModes);
            } finally {
                TenantContextHolder.clearCurrentTenant();
                unbindTenantWorkspace();
            }
        }
        return streamInternal(inputs, session, streamModes);
    }

    private java.util.Iterator<Object> streamInternal(Map<String, Object> inputs, AgentSessionApi session,
            List<StreamMode> streamModes) {
        ensureInitialized();
        Map<String, Object> normalized = normalizeStreamInputs(inputs);
        AgentSessionApi effectiveSession = buildEffectiveStreamSession(normalized, session, streamModes);
        // copyRunState 在 preRun 前拷 PRE_DONE，preRun 被 CAS 跳过（省 1 读）。
        // copySessionState 保持在 preRun 之后，维持原始时序。
        // postRun 后（在 streamTaskLoop/streamInvokeOnce 的 finally 里）：拷回 POST_DONE，Runner.postRun 也跳过（省 1 写）。
        if (session != null) {
            effectiveSession.copyRunState(session);
        }
        effectiveSession.preRun(normalized);
        if (session != null) {
            copySessionState(session, effectiveSession);
        }
        if (config.isEnableTaskLoop()) {
            return streamTaskLoop(normalized, effectiveSession, session);
        }
        return streamInvokeOnce(normalized, effectiveSession, session);
    }

    private Map<String, Object> normalizeStreamInputs(Map<String, Object> inputs) {
        Map<String, Object> normalized = new LinkedHashMap<>(inputs);
        normalized.putIfAbsent("conversation_id", card.getName() + "_session");
        normalized.putIfAbsent("query", "");
        if (config.isEnableTaskLoop()) {
            normalized.put("_collect_inner_stream", true);
        }
        return normalized;
    }

    private AgentSessionApi buildEffectiveStreamSession(Map<String, Object> normalized, AgentSessionApi session,
            List<StreamMode> streamModes) {
        String requestLevelSessionId = String.valueOf(normalized.get("conversation_id"));
        if (session != null) {
            AgentSessionApi effective = new AgentSessionApi(requestLevelSessionId, session.getEnvs(), this.card,
                    session.getInner().streamWriterManager().getEnabledModes());
            // 传播租户上下文到流式 effective session
            TenantContext ctx = session.getTenantContext();
            if (ctx != null && ctx.isTenantAware()) {
                effective.withTenantContext(ctx);
            }
            return effective;
        }
        return new AgentSessionApi(requestLevelSessionId, null, this.card,
                streamModes == null || streamModes.isEmpty() ? List.of(StreamMode.OUTPUT) : streamModes);
    }

    private java.util.Iterator<Object> streamTaskLoop(Map<String, Object> normalized,
            AgentSessionApi effectiveSession, AgentSessionApi session) {
        try {
            STREAM_EXECUTOR.execute(() -> {
                try {
                    runTaskLoop(normalized, effectiveSession);
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    writeStreamError(effectiveSession, 0, ex);
                } finally {
                    try {
                        // 关闭流前先传播状态，避免 Runner 收到 EOF 后保存到旧的外层状态。
                        if (session != null) {
                            copySessionState(effectiveSession, session);
                        }
                    } finally {
                        // postRun 执行：关 emitter + checkpoint 落盘（POST_DONE 未拷，CAS 不跳过）。
                        effectiveSession.postRun();
                        // 拷回 runState（PRE_DONE | POST_DONE），让 Runner 的 postRun 也被 CAS 跳过（省 1 写）。
                        if (session != null) {
                            session.copyRunState(effectiveSession);
                        }
                    }
                }
            });
        } catch (RejectedExecutionException ex) {
            // 池满（线程 + 队列全占用）：写入流错误事件而不是挂死客户端，
            // 与 AbortPolicy 语义一致——单请求失败，池子保持健康。
            writeStreamError(effectiveSession, 0, ex);
        }
        return effectiveSession.streamIterator();
    }

    private java.util.Iterator<Object> streamInvokeOnce(Map<String, Object> normalized,
            AgentSessionApi effectiveSession, AgentSessionApi session) {
        List<Object> outputs = new ArrayList<>();
        try {
            Map<String, Object> result = invoke(normalized);
            writeTopLevelStreamResult(effectiveSession, outputs.size(), result);
        } catch (IllegalStateException ex) {
            writeStreamError(effectiveSession, outputs.size(), ex);
        } finally {
            // postRun 执行：关 emitter + checkpoint 落盘。
            effectiveSession.postRun();
            if (session != null) {
                copySessionState(effectiveSession, session);
                // 拷回 runState，让 Runner 的 postRun 也被 CAS 跳过（省 1 写）。
                session.copyRunState(effectiveSession);
            }
        }
        java.util.Iterator<Object> iterator = effectiveSession.streamIterator();
        while (iterator.hasNext()) {
            outputs.add(iterator.next());
        }
        return outputs.iterator();
    }

    private void writeStreamError(AgentSessionApi session, int index, Throwable ex) {
        session.writeStream(new OutputSchema("error", index,
                Map.of("output", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                        "result_type", "error")));
    }

    /**
     * requestAbort.
     * 
     * @since 0.1.7
     */
    public void requestAbort() {
        for (LoopCoordinator coordinator : sessionLoopCoordinators.values()) {
            coordinator.requestAbort();
        }
    }

    /**
     * 请求中止指定会话的任务循环。
     *
     * @param sessionId 会话ID
     */
    public void requestAbort(String sessionId) {
        LoopCoordinator coordinator = sessionLoopCoordinators.get(sessionId);
        if (coordinator != null) {
            coordinator.requestAbort();
        }
    }

    /**
     * steer.
     * 
     * @param message message
     * @since 0.1.7
     */
    public void steer(String message) {
        steer(message, null);
    }

    /**
     * isFollowUp.
     * 
     * @param message message
     * @since 0.1.7
     */
    public void isFollowUp(String message) {
        isFollowUp(message, null);
    }

    /**
     * isFollowUp.
     * 
     * @param message message
     * @param session session
     * @since 0.1.7
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
     * steer.
     * 
     * @param message message
     * @param session session
     * @since 0.1.7
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
        TaskInteractionEvent event = new TaskInteractionEvent(List.of(new DataFrame.TextDataFrame(message)), null);
        eventQueue.publishEvent(card.getId(), session, event);
    }

    /**
     * ensurePlanFile.
     * 
     * @param conversationId conversationId
     * @return the result
     * @since 0.1.7
     */
    public Path ensurePlanFile(String conversationId) {
        String sessionId =
            conversationId != null && !conversationId.isBlank() ? conversationId : card.getName() + "_session";
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
     * getPlanFilePath.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Path getPlanFilePath() {
        return planFilePath;
    }

    /**
     * run.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    public Object run(Map<String, Object> inputs) {
        ensureInitialized();
        return Runner.runAgent(agent, inputs, null, null);
    }

    /**
     * fireAfterTaskIteration.
     * 
     * @param ctx ctx
     * @since 0.1.7
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
     * createSubagent.
     * 
     * @param subagentType subagentType
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
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
     * setMode.
     * 
     * @param mode mode
     * @since 0.1.7
     */
    public void setMode(AgentMode mode) {
        this.currentMode = mode;
    }

    /**
     * setSessionToolkit.
     * 
     * @param sessionToolkit sessionToolkit
     * @since 0.1.7
     */
    public void setSessionToolkit(SessionToolkit sessionToolkit) {
        this.sessionToolkit = sessionToolkit;
    }

    /**
     * findSubagentSpec.
     * 
     * @param subagentType subagentType
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * matchesSubagentName.
     * 
     * @param name name
     * @param requested requested
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * instantiateConfiguredSubagent.
     * 
     * @param spec spec
     * @param normalizedType normalizedType
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    private DeepAgent instantiateConfiguredSubagent(SubAgentConfig spec, String normalizedType, String sessionId) {
        Workspace childWorkspace = resolveChildWorkspace(spec, sessionId);
        DeepAgentConfig childConfig = spec.toDeepAgentConfig();
        applyParentRuntimeFallbacks(childConfig);
        return HarnessFactory.createDeepAgent(spec.getAgentCard(), childConfig, childWorkspace);
    }

    /**
     * applyParentRuntimeFallbacks.
     * 
     * @param childConfig childConfig
     * @since 0.1.7
     */
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

    /**
     * resolveChildWorkspace.
     * 
     * @param spec spec
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    private Workspace resolveChildWorkspace(SubAgentConfig spec, String sessionId) {
        Path basePath;
        if (spec.getWorkspacePath() != null && !spec.getWorkspacePath().isBlank()) {
            basePath = Path.of(spec.getWorkspacePath());
        } else {
            basePath = workspace.root();
        }
        Path childPath = sessionId != null && !sessionId.isBlank() ? basePath.resolve(sessionId) : basePath;
        return Workspace.builder().rootPath(childPath.toString())
                .language(spec.getLanguage() != null && !spec.getLanguage().isBlank()
                        ? spec.getLanguage()
                        : workspace.getLanguage())
                .build();
    }

    /**
     * ensureTaskLoopRuntime.
     * 
     * @since 0.1.7
     */
    private void ensureTaskLoopRuntime() {
        if (loopController == null) {
            loopController = new TaskLoopController();
        }
        if (taskManager == null) {
            ControllerConfig controllerConfig = new ControllerConfig();
            controllerConfig.setMaxConcurrentTasks(32);
            controllerConfig.setScheduleInterval(0.1);
            taskManager = new TaskManager(controllerConfig);
            eventQueue = new EventQueue(controllerConfig);
            eventHandler = new TaskLoopEventHandler(loopController);
            eventHandler.setConfig(controllerConfig);
            eventHandler.setContextEngine(agent.getContextEngine());
            eventHandler.setAbilityManager(agent.getAbilityManager());
            eventHandler.setTaskManager(taskManager);
            taskScheduler = new TaskScheduler(controllerConfig, taskManager, agent.getContextEngine(),
                    agent.getAbilityManager(), eventQueue, card);
            eventHandler.setTaskScheduler(taskScheduler);
            eventQueue.setEventHandler(eventHandler);
            eventQueue.start();
            taskScheduler.start();
        }
        taskScheduler.getTaskExecutorRegistry().addTaskExecutor(TaskLoopEventExecutor.DEEP_TASK_TYPE,
                dependencies -> new CoreTaskLoopEventExecutor(dependencies, this, this::invokeInnerRound));
    }

    /**
     * buildStopEvaluators.
     * 
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * runTaskLoop.
     * 
     * @param normalized normalized
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> runTaskLoop(Map<String, Object> normalized, AgentSessionApi session) {
        ensureTaskLoopRuntime();
        LoopCoordinator coordinator = coordinatorForSession(session);
        coordinator.reset();
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
                Map<String, Object> roundResult = new LinkedHashMap<>(executeCoreLoopRound(roundQuery, isFollowUp,
                        session, Boolean.TRUE.equals(normalized.get("_collect_inner_stream"))));
                roundResult.put("query", currentQuery);
                if (!Objects.equals(roundQuery, currentQuery)) {
                    roundResult.put("task_instruction_query", roundQuery);
                }
                roundResult.put("mode", currentMode.name().toLowerCase(Locale.ROOT));
                rounds.add(roundResult);
                Object roundError = roundResult.get("error");
                if (roundError != null) {
                    session.writeStream(new OutputSchema("error", rounds.size(), Map.of(
                            "output", String.valueOf(roundError),
                            "result_type", "error"
                    )));
                }

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

    /**
     * startTaskLoopRuntime.
     * <p>
     * Registers the session as an active task-loop participant. If a task loop
     * is already running for the same session ID, this method throws
     * {@link IllegalStateException} to prevent the concurrent-use race that
     * would otherwise allow one request's cleanup to destroy another's
     * runtime state.
     *
     * @param session session
     * @throws IllegalStateException if a task loop is already active for the session
     * @since 0.1.7
     */
    private void startTaskLoopRuntime(AgentSessionApi session) {
        if (session == null) {
            return;
        }
        String sessionId = session.getSessionId();
        if (!activeTaskLoopSessions.add(sessionId)) {
            // logger.error("Task loop already active for session: {}", sessionId);
            throw new IllegalStateException(
                    "Task loop already active for session: " + sessionId);
        }
        try {
            taskScheduler.getSessions().put(sessionId, session);
            eventQueue.subscribe(card.getId(), sessionId);
        } catch (BaseError ex) {
            activeTaskLoopSessions.remove(sessionId);
            throw ex;
        }
    }

    /**
     * stopTaskLoopRuntime.
     * 
     * @param session session
     * @since 0.1.7
     */
    private void stopTaskLoopRuntime(AgentSessionApi session) {
        if (session == null) {
            return;
        }
        String sessionId = session.getSessionId();
        activeTaskLoopSessions.remove(sessionId);
        eventQueue.unsubscribe(card.getId(), sessionId);
        taskScheduler.getSessions().remove(sessionId);

        // Clean up per-session state to prevent memory leaks in long-running scenarios.
        // Only non-default sessions are cleaned — the default session is a shared resource.
        if (sessionId != null && !TaskLoopController.DEFAULT_SESSION_ID.equals(sessionId)) {
            sessionLoopCoordinators.remove(sessionId);
            // Clear context engine to break the reference chain:
            // contextPool → SessionModelContext → sessionRef → AgentSession
            //   → Tracer → SpanManager → sessionSpans → spans
            agent.getContextEngine().clearContextBySession(sessionId);
            // Explicitly clear tracer span data to release span references
            // even if other code paths still hold a tracer reference.
            Tracer tracer = session.getInner().tracerTyped();
            if (tracer != null) {
                tracer.clear();
            }
            if (loopController != null) {
                loopController.clearSession(sessionId);
            }
            if (taskManager != null) {
                taskManager.removeTask(TaskFilter.bySessionId(sessionId));
            }
        }
    }

    /**
     * coordinatorForSession.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private LoopCoordinator coordinatorForSession(AgentSessionApi session) {
        String sessionId = session != null && session.getSessionId() != null
                ? session.getSessionId()
                : TaskLoopController.DEFAULT_SESSION_ID;
        return sessionLoopCoordinators.computeIfAbsent(sessionId,
                ignored -> new LoopCoordinator(buildStopEvaluators()));
    }

    /**
     * updateCompletionPromise.
     * 
     * @param coordinator coordinator
     * @param roundResult roundResult
     * @since 0.1.7
     */
    private void updateCompletionPromise(LoopCoordinator coordinator, Map<String, Object> roundResult) {
        CompletionPromiseEvaluator completion =
            coordinator != null ? coordinator.getCompletionPromiseEvaluator() : completionPromiseEvaluator;
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

    /**
     * executeCoreLoopRound.
     * 
     * @param query query
     * @param isFollowUp isFollowUp
     * @param session session
     * @param isCollectInnerStream isCollectInnerStream
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> executeCoreLoopRound(Object query, boolean isFollowUp, AgentSessionApi session,
            boolean isCollectInnerStream) {
        InputEvent event = query instanceof String || query instanceof InputEvent
                ? InputEvent.fromUserInput(query)
                : InputEvent.fromUserInput(Map.of("query", query, "query_payload", query));
        int handlerRound = eventHandler.prepareRound(session.getSessionId(), isFollowUp);
        String taskId = "deep_agent_task_" + session.getSessionId() + "_" + handlerRound;
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

    /**
     * awaitRoundCompletion.
     * 
     * @param taskId taskId
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> awaitRoundCompletion(String taskId, AgentSessionApi session) {
        double timeoutSeconds =
            config.getCompletionTimeout() == null ? 600.0 : Math.max(1.0, config.getCompletionTimeout());
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

    /**
     * invokeInnerRound.
     * 
     * @param inputs inputs
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> invokeInnerRound(Map<String, Object> inputs, AgentSessionApi session) {
        Map<String, Object> effectiveInputs = new LinkedHashMap<>();
        if (inputs != null) {
            effectiveInputs.putAll(inputs);
        }
        effectiveInputs.putIfAbsent("query", "");
        effectiveInputs.putIfAbsent("conversation_id",
                session != null && session.getSessionId() != null
                        ? session.getSessionId()
                        : card.getName() + "_session");

        boolean isCollectInnerStream = Boolean.TRUE.equals(effectiveInputs.get("collect_inner_stream"));
        Map<String, Object> rawResult = isCollectInnerStream
                ? invokeInnerRoundStreaming(effectiveInputs, session)
                : invokeInnerRoundOnce(effectiveInputs, session);
        return normalizeInnerRoundResult(rawResult, effectiveInputs);
    }

    @SuppressWarnings("unchecked")
    /**
     * invokeInnerRoundOnce.
     * 
     * @param effectiveInputs effectiveInputs
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> invokeInnerRoundOnce(Map<String, Object> effectiveInputs, AgentSessionApi session) {
        // task-loop 在独立线程执行，InheritableThreadLocal 不会自动继承调用方线程的租户上下文，
        // 这里从 session 重新绑定，保证 SkillUseRail/工具层能读到正确的租户
        TenantContext ctx = session != null ? session.getTenantContext() : null;
        if (ctx != null && ctx.isTenantAware()) {
            TenantContextHolder.setCurrentTenant(ctx);
            try {
                bindTenantWorkspace(ctx);
                return (Map<String, Object>) agent.invoke(effectiveInputs, session);
            } finally {
                TenantContextHolder.clearCurrentTenant();
                unbindTenantWorkspace();
            }
        }
        return (Map<String, Object>) agent.invoke(effectiveInputs, session);
    }

    /**
     * invokeInnerRoundStreaming.
     * 
     * @param effectiveInputs effectiveInputs
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> invokeInnerRoundStreaming(Map<String, Object> effectiveInputs,
            AgentSessionApi session) {
        AgentSessionApi innerSession = new AgentSessionApi(String.valueOf(effectiveInputs.get("conversation_id")),
                session != null ? session.getEnvs() : null, card, List.of(StreamMode.OUTPUT));
        // 复用上游（CoreTaskLoopEventExecutor.buildEffectiveInputs L236）注入到 effectiveInputs
        // 的 task_id，不在此重新生成。该值最终源自 DeepAgent.executeCoreLoopRound L1643
        // 的 "deep_agent_task_<sessionId>_<handlerRound>"。stream 路径若由用户直接调
        // DeepAgent.stream 测试（effectiveInputs 无 task_id），则 taskId 为 null，
        // AbilityManager 会在 tool_output.payload.task_id 写空字符串。
        Object taskIdRaw = effectiveInputs.get("task_id");
        String taskId = taskIdRaw == null ? null : String.valueOf(taskIdRaw);
        if (taskId != null) {
            innerSession.updateState(java.util.Map.of("task_id", taskId));
        }
        // 传播租户上下文到 inner session
        TenantContext ctx = session != null ? session.getTenantContext() : null;
        if (ctx != null && ctx.isTenantAware()) {
            innerSession.withTenantContext(ctx);
        }
        innerSession.preRun(effectiveInputs);
        copySessionState(session, innerSession);
        // copySessionState 可能覆盖 inner session state，重新注入 task_id 以确保下游可见
        if (taskId != null) {
            innerSession.updateState(java.util.Map.of("task_id", taskId));
        }
        // task-loop 独立线程需重新绑定租户上下文
        if (ctx != null && ctx.isTenantAware()) {
            TenantContextHolder.setCurrentTenant(ctx);
            try {
                bindTenantWorkspace(ctx);
                return collectStreamToResult(effectiveInputs, innerSession, session);
            } finally {
                TenantContextHolder.clearCurrentTenant();
                unbindTenantWorkspace();
            }
        }
        return collectStreamToResult(effectiveInputs, innerSession, session);
    }

    /**
     * collectStreamToResult.
     *
     * @param effectiveInputs effectiveInputs
     * @param innerSession innerSession
     * @param session session
     * @return the result
     */
    private Map<String, Object> collectStreamToResult(Map<String, Object> effectiveInputs,
            AgentSessionApi innerSession, AgentSessionApi session) {
        List<Object> streamItems = new ArrayList<>();
        agent.stream(effectiveInputs, innerSession, List.of(StreamMode.OUTPUT))
                .forEachRemaining(chunk -> {
                    streamItems.add(chunk);
                    if (chunk instanceof OutputSchema outputSchema) {
                        session.writeStream(outputSchema);
                    }
                });
        copySessionState(innerSession, session);
        Map<String, Object> result = extractFinalStreamResult(streamItems);
        List<Object> normalizedChunks = normalizeStreamChunks(streamItems);
        if (!normalizedChunks.isEmpty()) {
            result.put("stream_chunks", normalizedChunks);
        }
        return result;
    }

    /**
     * copySessionState.
     * 
     * @param source source
     * @param target target
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    private void copySessionState(AgentSessionApi source, AgentSessionApi target) {
        if (source == null || target == null) {
            return;
        }
        Map<String, Object> sourceState = source.getInner().state().getState();
        Object global = sourceState.get(State.GLOBAL_STATE_KEY);
        if (global instanceof Map) {
            target.getInner().state().updateGlobal((Map<String, Object>) global);
        }
        Object agentState = sourceState.get(State.AGENT_STATE_KEY);
        if (agentState instanceof Map) {
            target.getInner().state().update((Map<String, Object>) agentState);
        }
    }

    /**
     * writeTopLevelStreamResult.
     * 
     * @param session session
     * @param index index
     * @param result result
     * @since 0.1.7
     */
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
        session.writeStream(new OutputSchema("answer", index, Map.of("output", result, "result_type", "answer")));
    }

    /**
     * normalizeInnerRoundResult.
     * 
     * @param rawResult rawResult
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * resolveOutput.
     * 
     * @param source source
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * extractFinalStreamResult.
     * 
     * @param streamItems streamItems
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * normalizeStreamChunks.
     * 
     * @param streamItems streamItems
     * @return the result
     * @since 0.1.7
     */
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
            } else {
                // no-op
            }
        }
        return normalized;
    }

    /**
     * isTerminalAnswerEnvelope.
     * 
     * @param payload payload
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * resolveTokenUsage.
     * 
     * @param roundResult roundResult
     * @return the result
     * @since 0.1.7
     */
    private int resolveTokenUsage(Map<String, Object> roundResult) {
        UsageMetadata usageMetadata = TaskIterationContext.usageMetadataFrom(roundResult);
        if (usageMetadata != null) {
            return usageMetadata.getTotalTokens();
        }
        return 0;
    }

    /**
     * castMap.
     * 
     * @param source source
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /**
     * copyIfPresent.
     * 
     * @param source source
     * @param target target
     * @param key key
     * @since 0.1.7
     */
    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source != null && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    /**
     * nullValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static <T> T nullValue() {
        return null;
    }

    private void requireTenantContext(TenantContext ctx) {
        if (!config.isEnableTenantIsolation()) {
            return;
        }
        TenantContext current = TenantContextHolder.getCurrentTenant();
        if (current != null && current.isTenantAware()) {
            return;
        }
        if (ctx == null || !ctx.isTenantAware()) {
            throw new IllegalStateException(
                "Tenant isolation is enabled but no tenantId was provided. "
                + "Either pass a valid TenantContext with non-empty tenantId, "
                + "or disable enableTenantIsolation in DeepAgentConfig.");
        }
    }

    private void bindTenantWorkspace(TenantContext ctx) {
        if (!config.isEnableTenantIsolation()) {
            return;
        }
        if (ctx != null && ctx.isTenantAware()) {
            if (tieredWorkspaceManager != null) {
                tieredWorkspaceManager.initializeTenantSpace(ctx);
                WorkspaceResolution workspaceRes = tieredWorkspaceManager.resolve(ctx, WorkspaceType.WORKSPACE);
                Path tenantWorkspace = workspaceRes.getLocalPath();
                CwdContext.setWorkspace(tenantWorkspace.toString());
                CwdContext.setOriginalCwd(tenantWorkspace.toString());
                CwdContext.setTenantRoot(tenantWorkspace.toString());
                workspaceResolver = new TenantWorkspaceResolver(
                    config.getTenantDataRoot() != null ? config.getTenantDataRoot() : config.getWorkspacePath(),
                    tieredWorkspaceManager.getNamespaceFactory());
            } else {
                String baseWorkspace = config.getTenantDataRoot() != null
                        ? config.getTenantDataRoot() : config.getWorkspacePath();
                workspaceResolver = new TenantWorkspaceResolver(baseWorkspace);
                Path tenantWorkspace = workspaceResolver.resolveWorkspaceRoot(ctx);
                workspaceResolver.initializeTenantSpace(ctx);
                CwdContext.setWorkspace(tenantWorkspace.toString());
                CwdContext.setOriginalCwd(tenantWorkspace.toString());
                CwdContext.setTenantRoot(tenantWorkspace.toString());
            }
        }
    }

    private void unbindTenantWorkspace() {
        CwdContext.reset();
    }

    /**
     * destroy.
     * 
     * @since 0.1.7
     */
    public void destroy() {
        if (tmpFileCleaner != null) {
            tmpFileCleaner.stop();
            tmpFileCleaner = null;
        }
        // Release all per-session state eagerly to prevent retention in long-running scenarios.
        sessionLoopCoordinators.clear();
        if (taskManager != null) {
            taskManager.clearState();
        }
    }

    @Override
    public void close() {
        destroy();
    }

    public TieredWorkspaceManager getTieredWorkspaceManager() {
        return tieredWorkspaceManager;
    }

    public void setTieredWorkspaceManager(TieredWorkspaceManager manager) {
        this.tieredWorkspaceManager = manager;
    }

    /**
     * initTieredWorkspaceManager.
     * 
     * @since 0.1.7
     */
    public void initTieredWorkspaceManager() {
        String basePath = config.getTenantDataRoot() != null ? config.getTenantDataRoot() : config.getWorkspacePath();
        WorkspaceStore primaryStore = new LocalWorkspaceStore(basePath);
        java.util.List<WorkspaceStore> secondaryStores = new java.util.ArrayList<>();
        if (config.getWorkspaceSecondaryTiers() != null) {
            for (String tier : config.getWorkspaceSecondaryTiers()) {
                if (WorkspaceStoreFactory.hasProvider(tier)) {
                    secondaryStores.add(WorkspaceStoreFactory.create(tier,
                            config.getWorkspaceTierConfigs().getOrDefault(tier, Map.of())));
                }
            }
        }
        this.tieredWorkspaceManager = new TieredWorkspaceManager(primaryStore, secondaryStores);
    }
}
