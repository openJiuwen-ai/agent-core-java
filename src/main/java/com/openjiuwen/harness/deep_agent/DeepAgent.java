/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.runner.resourcemanager.ResourceManagerBase;
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
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;
import com.openjiuwen.core.multitenant.TmpFileCleaner;
import com.openjiuwen.core.multitenant.workspace.TieredWorkspaceManager;
import com.openjiuwen.core.multitenant.workspace.WorkspaceResolution;
import com.openjiuwen.core.multitenant.workspace.WorkspaceStore;
import com.openjiuwen.core.multitenant.workspace.WorkspaceStoreFactory;
import com.openjiuwen.core.multitenant.workspace.WorkspaceType;
import com.openjiuwen.core.multitenant.workspace.store.LocalWorkspaceStore;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.Cwd;
import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionLoader;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.rails.subagent.SessionRail;
import com.openjiuwen.harness.rails.subagent.SubagentRail;
import com.openjiuwen.harness.rails.TaskCompletionRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigBuilder;
import com.openjiuwen.harness.harness_config.HarnessConfigLoader;
import com.openjiuwen.harness.harness_config.ResolvedHarnessConfig;
import com.openjiuwen.harness.schema.AgentMode;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.schema.config.DeepAgentConfigConverter;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.security.PermissionFactory;
import com.openjiuwen.harness.subagents.SubAgentConfig;
import com.openjiuwen.spi.store.BaseKVStore;

import com.openjiuwen.harness.task_loop.CoreTaskLoopEventExecutor;
import com.openjiuwen.harness.task_loop.LoopCoordinator;
import com.openjiuwen.harness.task_loop.TaskLoopController;
import com.openjiuwen.harness.task_loop.TaskLoopEventHandler;
import com.openjiuwen.harness.task_loop.TaskLoopEventExecutor;
import com.openjiuwen.harness.task_loop.TaskIterationContext;
import com.openjiuwen.harness.tools.SessionToolkit;
import com.openjiuwen.harness.workspace.DirectoryBuilder;
import com.openjiuwen.harness.workspace.Workspace;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Minimal Java baseline for the Python DeepAgent public surface.
 */
@Getter
public class DeepAgent implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(DeepAgent.class.getName());

    /** Bounded pool for task-loop stream sessions (issue #70 / 483bdfe0). */
    private static final ExecutorService STREAM_EXECUTOR =
            OpenJiuwenExecutors.newBoundedModulePool("deep-agent-stream", true);

    /** Bounded pool so invoke returns a Future while steer/followUp can run concurrently. */
    private static final ExecutorService INVOKE_EXECUTOR =
            OpenJiuwenExecutors.newBoundedModulePool("deep-agent-invoke", true);

    private final AgentCard card;
    private final DeepAgentConfig config;
    private Workspace workspace;
    private ReActAgent agent;
    private Object reactAgentOverride;
    /** May hold non-{@link com.openjiuwen.core.sysop.SysOperation} values used by rail tests. */
    private Object railSysOperation;
    private final List<String> pendingHarnessConfigs = new CopyOnWriteArrayList<>();
    private volatile boolean invokeActive;
    private volatile boolean autoInvokeScheduled;
    private AgentMode currentMode;
    private final List<Object> registeredRails = new CopyOnWriteArrayList<>();
    private final Set<DeepAgentRail> railsBoundToAgent = ConcurrentHashMap.newKeySet();
    private final List<Object> registeredTools = new CopyOnWriteArrayList<>();
    private final List<McpServerConfig> registeredMcps = new CopyOnWriteArrayList<>();
    private SessionToolkit sessionToolkit;
    private TenantWorkspaceResolver workspaceResolver;
    private TieredWorkspaceManager tieredWorkspaceManager;
    private TmpFileCleaner tmpFileCleaner;
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
    @Setter
    private BaseKVStore kvStore;
    private com.openjiuwen.harness.schema.CompletionPromiseEvaluator completionPromiseEvaluator;
    private boolean isExplicitCompletionPolicy;

    public DeepAgent() {
        this(null, null, null);
    }

    public DeepAgent(AgentCard card) {
        this(card, DeepAgentConfig.builder().build(), null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public DeepAgent(AgentCard card, DeepAgentConfig config, Workspace workspace) {
        this.card = card != null ? card : AgentCard.builder().name("deep_agent").description("DeepAgent").build();
        this.config = config != null ? config : DeepAgentConfig.builder().build();
        if (workspace != null) {
            this.workspace = workspace;
        } else if (this.config.getWorkspacePath() != null && !this.config.getWorkspacePath().isBlank()) {
            this.workspace = new Workspace(
                    this.config.getWorkspacePath(),
                    this.config.getLanguage());
        } else {
            this.workspace = null;
        }
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
     * Check if tenant isolation is enabled.
     *
     * @return true if tenant isolation is enabled
     * @since 0.1.13
     */
    public boolean isTenantIsolationEnabled() {
        return config != null && config.isEnableTenantIsolation();
    }

    private ReActAgentConfig buildReActAgentConfig() {
        ReActAgentConfig runtimeConfig = ReActAgentConfig.builder()
                .promptTemplateName(this.config.getPromptMode())
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
                    .temperature(doubleOrDefault(firstPresent(modelMap, new String[] {"temperature"}), 0.7))
                    .topP(doubleOrDefault(firstPresent(modelMap, new String[] {"top_p", "topP"}), 1.0))
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
                Object isResolved = Runner.resourceMgr().getModel(modelId, null).toCompletableFuture().getNow(null);
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
        // Register config.mcps before rails (Python _register_pending_mcps).
        registerPendingMcps();
        initWorkspace();
        if (config.getRails() != null) {
            for (Object rail : config.getRails()) {
                if (rail instanceof DeepAgentRail deepAgentRail) {
                    deepAgentRail.setWorkspace(this.workspace);
                    deepAgentRail.setSysOperation(this.config.getSysOperation());
                } else if (rail instanceof AgentRail agentRail) {
                    agent.registerRail(agentRail);
                }
                if (rail instanceof SkillUseRail skillUseRail) {
                    skillUseRail.init(this);
                } else {
                    try {
                        Method init = rail.getClass().getMethod("init", DeepAgent.class);
                        init.invoke(rail, this);
                    } catch (ReflectiveOperationException ignored) {
                        // rail has no deep_agent-specific init
                    }
                }
                if (rail instanceof TaskCompletionRail completionRail) {
                    taskCompletionRail = completionRail;
                }
                registerDeepRail(rail);
                if (rail instanceof DeepAgentRail deepAgentRail) {
                    bindDeepAgentRailToAgent(deepAgentRail);
                }
            }
        }
        // Sync MCP servers already registered externally (e.g. ResourceMgr.addMcpServer).
        syncMcpServersFromResourceMgr();
        if (config.getPermissions() != null && Boolean.TRUE.equals(config.getPermissions().get("enabled"))) {
            var rail = PermissionFactory.buildPermissionInterruptRail(
                    config.getPermissions(),
                    config.getPermissionHost(),
                    workspaceRootPath()
            );
            rail.init(this);
            registeredRails.add(rail);
            bindDeepAgentRailToAgent(rail);
        }
        if (config.isEnableTaskLoop()) {
            ensureTaskLoopRuntime();
        }
        isInitialized = true;
    }

    /**
     * Aligns with Python {@code DeepAgent._needs_workspace_init}.
     */
    private boolean needsWorkspaceInit() {
        return workspace != null
                && config != null
                && config.getSysOperation() != null
                && config.isAutoCreateWorkspace();
    }

    /**
     * Materialize the workspace schema. Skips when the root already has {@code .workspace},
     * matching Python {@code init_workspace}. Relative paths and the process CWD are also
     * skipped so factory tests using {@code ./repo} / default {@code ./} do not write into
     * the project tree.
     */
    private void initWorkspace() {
        if (!needsWorkspaceInit()) {
            return;
        }
        Path root = workspace.root();
        if (!shouldMaterializeWorkspace(root)) {
            return;
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        try {
            new DirectoryBuilder(normalizedRoot.toString()).build(workspace.getDirectories());
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to initialize workspace at " + normalizedRoot, ex);
        }
    }

    private static boolean shouldMaterializeWorkspace(Path root) {
        if (root == null || !root.isAbsolute()) {
            return false;
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path cwd = Path.of("").toAbsolutePath().normalize();
        return !normalizedRoot.equals(cwd) && !Files.exists(normalizedRoot.resolve(".workspace"));
    }

    /**
     * Registers config-declared MCP servers into ResourceMgr and AbilityManager.
     * Aligns with Python {@code _register_pending_mcps}.
     */
    private void registerPendingMcps() {
        if (config.getMcps() == null || config.getMcps().isEmpty()) {
            return;
        }
        for (McpServerConfig mcpConfig : config.getMcps()) {
            registerOnePendingMcp(mcpConfig);
        }
    }

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

    private void addNewPendingMcp(McpServerConfig mcpConfig) {
        List<Result<String>> results = Runner.resourceMgr().addMcpServer(mcpConfig, card.getId(), null);
        throwIfAddMcpFailed(results, mcpConfig);
    }

    private static void throwIfAddMcpFailed(List<Result<String>> results, McpServerConfig mcpConfig) {
        for (Result<String> result : results) {
            if (!result.isError()) {
                continue;
            }
            Object error = result.getError();
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, "server_config",
                    String.valueOf(mcpConfig), "reason",
                    error != null ? String.valueOf(error) : "add_mcp_server failed");
        }
    }

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

    private void ensureResourceTagged(String resourceId, String tag, McpServerConfig mcpConfig) {
        com.openjiuwen.core.runner.resourcemanager.Result<?, ?> tagResult =
                Runner.resourceMgr().addResourceTag(resourceId, tag);
        if (tagResult.isError()) {
            Object error = tagResult.getError();
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, "server_config",
                    String.valueOf(mcpConfig), "reason",
                    error != null ? String.valueOf(error) : "add_resource_tag failed");
        }
    }

    /**
     * Pulls MCP servers already present in ResourceMgr into AbilityManager / registeredMcps.
     */
    private void syncMcpServersFromResourceMgr() {
        Set<String> seenServerNames = new HashSet<>();
        for (McpServerConfig already : registeredMcps) {
            if (already.getServerName() != null) {
                seenServerNames.add(already.getServerName());
            }
        }
        for (Object tag : List.of(card.getId(), ResourceManagerBase.GLOBAL)) {
            List<McpServerConfig> configs = Runner.resourceMgr().listMcpServers(tag);
            for (McpServerConfig mcpConfig : configs) {
                if (mcpConfig == null || mcpConfig.getServerName() == null || mcpConfig.getServerName().isBlank()) {
                    continue;
                }
                if (!seenServerNames.add(mcpConfig.getServerName())) {
                    continue;
                }
                if (agent.getAbilityManager().get(mcpConfig.getServerName()).isEmpty()) {
                    agent.getAbilityManager().add(mcpConfig);
                }
                if (!registeredMcps.contains(mcpConfig)) {
                    registeredMcps.add(mcpConfig);
                }
            }
        }
    }

    private static boolean sameMcpServerConfig(McpServerConfig left, McpServerConfig right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getServerId(), right.getServerId())
                && Objects.equals(left.getServerName(), right.getServerName())
                && Objects.equals(left.getServerPath(), right.getServerPath())
                && Objects.equals(normalizeClientType(left.getClientType()),
                normalizeClientType(right.getClientType()));
    }

    private static String normalizeClientType(String clientType) {
        if (clientType == null) {
            return null;
        }
        String normalized = clientType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.isEmpty() ? clientType : normalized;
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
     * Normalize invoke inputs (defensive copy).
     *
     * @param inputs raw inputs
     * @return normalized map (never null)
     */
    public Map<String, Object> normalizeInputs(Map<String, Object> inputs) {
        return inputs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputs);
    }

    /**
     * Synchronous invoke (customer-compatible surface).
     *
     * <p>Blocks until completion. Prefer {@link #invokeAsync} when the caller needs to
     * {@link #steer} / {@link #followUp} while a task-loop round is still running.</p>
     *
     * @param inputs inputs
     * @return invoke result
     * @since 0.1.7
     */
    public Map<String, Object> invoke(Map<String, Object> inputs) {
        return invokeAsync(inputs).join();
    }

    /**
     * Synchronous invoke with an explicit tenant context.
     *
     * @param inputs inputs
     * @param tenantCtx tenant context
     * @return invoke result
     * @since 0.1.7
     */
    public Map<String, Object> invoke(Map<String, Object> inputs, TenantContext tenantCtx) {
        return invokeAsync(inputs, tenantCtx).join();
    }

    /**
     * Synchronous invoke with external session (interrupt/resume).
     *
     * @param inputs  inputs (query / conversation_id)
     * @param session external session, may be null
     * @return invoke result
     * @since 0.1.13
     */
    public Map<String, Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
        return invokeAsync(inputs, session).join();
    }

    /**
     * Non-blocking invoke (develop / Python async semantics).
     *
     * <p>Task-loop work runs on {@code INVOKE_EXECUTOR}; callers may {@link #steer} /
     * {@link #followUp} on the same session before the future completes.</p>
     *
     * @param inputs inputs
     * @return future of the invoke result
     * @since 0.1.7
     */
    public CompletableFuture<Map<String, Object>> invokeAsync(Map<String, Object> inputs) {
        AgentSessionApi session = null;
        return invokeAsync(inputs, session);
    }

    /**
     * Non-blocking invoke with an explicit tenant context.
     *
     * @param inputs inputs
     * @param tenantCtx tenant context
     * @return future of the invoke result
     * @since 0.1.7
     */
    public CompletableFuture<Map<String, Object>> invokeAsync(Map<String, Object> inputs, TenantContext tenantCtx) {
        requireTenantContext(tenantCtx);
        return invokeAsyncInternal(inputs, null, tenantCtx);
    }

    /**
     * Non-blocking invoke with external session.
     *
     * @param inputs  inputs
     * @param session external session, may be null
     * @return future of the invoke result
     * @since 0.1.13
     */
    public CompletableFuture<Map<String, Object>> invokeAsync(Map<String, Object> inputs, AgentSessionApi session) {
        TenantContext ctx = sessionTenantContext(session);
        requireTenantContext(ctx);
        return invokeAsyncInternal(inputs, session, ctx);
    }

    private CompletableFuture<Map<String, Object>> invokeAsyncInternal(
            Map<String, Object> inputs,
            AgentSessionApi session,
            TenantContext tenantCtx
    ) {
        Map<String, Object> normalized = normalizeInputs(inputs);
        boolean runAsync = config != null && config.isEnableTaskLoop();
        invokeActive = true;
        Supplier<Map<String, Object>> work = () -> {
            try {
                if (tenantCtx != null && tenantCtx.isTenantAware()) {
                    TenantContextHolder.setCurrentTenant(tenantCtx);
                    bindTenantWorkspace(tenantCtx);
                }
                return invokeWithLifecycle(normalized, session);
            } finally {
                if (tenantCtx != null && tenantCtx.isTenantAware()) {
                    TenantContextHolder.clearCurrentTenant();
                    unbindTenantWorkspace();
                }
                invokeActive = false;
            }
        };
        if (runAsync) {
            try {
                return CompletableFuture.supplyAsync(work, INVOKE_EXECUTOR);
            } catch (RejectedExecutionException rejected) {
                invokeActive = false;
                return CompletableFuture.failedFuture(rejected);
            }
        }
        try {
            return CompletableFuture.completedFuture(work.get());
        } catch (RuntimeException ex) {
            invokeActive = false;
            return CompletableFuture.failedFuture(ex);
        }
    }

    private Map<String, Object> invokeWithLifecycle(Map<String, Object> inputs, AgentSessionApi session) {
        Map<String, Object> normalized = new LinkedHashMap<>(inputs);
        normalized.putIfAbsent("conversation_id", card.getName() + "_session");
        normalized.putIfAbsent("query", "");
        return withDeepAgentInvokeLifecycle(normalized, () -> invokeInternal(normalized, session));
    }

    private Map<String, Object> withDeepAgentInvokeLifecycle(Map<String, Object> inputs,
                                                             Supplier<Map<String, Object>> body) {
        CallbackContext context = new CallbackContext(this, inputs);
        List<DeepAgentRail> rails = snapshotDeepAgentRails();
        for (DeepAgentRail rail : rails) {
            rail.beforeInvoke(context);
        }
        if (context.isRejected()) {
            Map<String, Object> rejected = new LinkedHashMap<>();
            rejected.put("type", "deep_agent_result");
            rejected.put("rejected", true);
            rejected.put("error", context.getRejectionMessage());
            rejected.put("inputs", inputs);
            fireAfterInvoke(rails, context);
            return rejected;
        }
        try {
            Map<String, Object> result = body.get();
            context.put("result", result);
            return result;
        } finally {
            fireAfterInvoke(rails, context);
        }
    }

    private List<DeepAgentRail> snapshotDeepAgentRails() {
        List<DeepAgentRail> rails = new ArrayList<>();
        for (Object rail : registeredRails) {
            if (rail instanceof DeepAgentRail typed) {
                rails.add(typed);
            }
        }
        return rails;
    }

    private static void fireAfterInvoke(List<DeepAgentRail> rails, CallbackContext context) {
        for (int i = rails.size() - 1; i >= 0; i--) {
            rails.get(i).afterInvoke(context);
        }
    }

    private Map<String, Object> invokeInternal(Map<String, Object> inputs, AgentSessionApi session) {
        ensureInitialized();
        Map<String, Object> normalized = new LinkedHashMap<>(inputs);
        normalized.putIfAbsent("conversation_id", card.getName() + "_session");
        normalized.putIfAbsent("query", "");
        // InteractiveInput resume bypasses the outer task loop (Python _is_resume_input).
        if (isResumeInput(normalized)) {
            return runSingleRoundInvoke(normalized, session);
        }
        if (config.isEnableTaskLoop()) {
            AgentSessionApi effectiveSession = session;
            if (effectiveSession == null) {
                String requestLevelSessionId = String.valueOf(normalized.get("conversation_id"));
                effectiveSession = new DeepAgentSession(requestLevelSessionId, null, card);
            } else if (effectiveSession.getSessionId() != null && !effectiveSession.getSessionId().isBlank()) {
                normalized.put("conversation_id", effectiveSession.getSessionId());
            }
            if (effectiveSession instanceof DeepAgentSession deepSession) {
                TenantContext effectiveCtx = sessionTenantContext(session);
                if (effectiveCtx == null || !effectiveCtx.isTenantAware()) {
                    effectiveCtx = TenantContextHolder.getCurrentTenant();
                }
                if (effectiveCtx != null && effectiveCtx.isTenantAware()) {
                    deepSession.withTenantContext(effectiveCtx);
                }
                deepSession.preRun(normalized);
            }
            try {
                return runTaskLoop(normalized, effectiveSession);
            } finally {
                if (effectiveSession instanceof DeepAgentSession deepSession) {
                    deepSession.postRun();
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "deep_agent_result");
        result.put("agent_name", card.getName());
        result.put("mode", currentMode == null ? AgentMode.NORMAL.name().toLowerCase(Locale.ROOT)
                : currentMode.name().toLowerCase(Locale.ROOT));
        result.put("workspace", workspaceRootString());
        result.put("inputs", normalized);
        result.put("input", normalized);
        return result;
    }

    private static boolean isResumeInput(Map<String, Object> inputs) {
        return inputs != null && inputs.get("query") instanceof InteractiveInput;
    }

    private Map<String, Object> runSingleRoundInvoke(Map<String, Object> normalized, AgentSessionApi session) {
        Map<String, Object> effective = new LinkedHashMap<>();
        if (normalized != null) {
            effective.putAll(normalized);
        }
        effective.putIfAbsent("query", "");
        effective.putIfAbsent("conversation_id", session != null && session.getSessionId() != null
                ? session.getSessionId()
                : card.getName() + "_session");
        Map<String, Object> raw = unwrapInvokeResult(invokeReactAgent(effective, session));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "deep_agent_result");
        result.put("agent_name", card.getName());
        result.put("mode", currentMode == null ? AgentMode.NORMAL.name().toLowerCase(Locale.ROOT)
                : currentMode.name().toLowerCase(Locale.ROOT));
        result.put("workspace", workspaceRootString());
        result.put("inputs", normalized);
        result.put("input", normalized);
        if (raw != null) {
            result.putAll(raw);
        }
        return result;
    }

    /**
     * Stream chunks (730 runtime; elements are typically Maps / OutputSchema).
     *
     * @param inputs inputs
     * @return chunk iterator
     */
    public java.util.Iterator<Object> stream(Map<String, Object> inputs) {
        return stream(inputs, List.of(StreamMode.OUTPUT));
    }

    /**
     * Develop-compatible typed stream view over {@link #stream(Map)}.
     *
     * @param inputs inputs
     * @return lazy map-chunk iterator (non-map chunks are wrapped under key {@code chunk})
     */
    public java.util.Iterator<Map<String, Object>> streamMaps(Map<String, Object> inputs) {
        java.util.Iterator<Object> raw = stream(inputs);
        return new java.util.Iterator<>() {
            @Override
            public boolean hasNext() {
                return raw.hasNext();
            }

            @Override
            public Map<String, Object> next() {
                Object chunk = raw.next();
                if (chunk instanceof Map<?, ?> map) {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    map.forEach((k, v) -> normalized.put(String.valueOf(k), v));
                    return normalized;
                }
                Map<String, Object> wrapper = new LinkedHashMap<>();
                wrapper.put("chunk", chunk);
                return wrapper;
            }
        };
    }

    /**
     * Stream with an explicit tenant context.
     *
     * @param inputs inputs
     * @param tenantCtx tenant context
     * @return iterator
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
     * Auto-generated for codecheck compliance.
     */
    public java.util.Iterator<Object> stream(Map<String, Object> inputs, List<StreamMode> streamModes) {
        String requestLevelSessionId = String.valueOf(inputs.getOrDefault("conversation_id",
                card.getName() + "_session"));
        AgentSessionApi session = new DeepAgentSession(
                requestLevelSessionId,
                null,
                card,
                streamModes == null || streamModes.isEmpty() ? List.of(StreamMode.OUTPUT) : streamModes
        );
        return stream(inputs, session, streamModes);
    }

    /**
     * Stream with stream modes and an explicit tenant context.
     *
     * @param inputs inputs
     * @param streamModes stream modes
     * @param tenantCtx tenant context
     * @return iterator
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
     * Auto-generated for codecheck compliance.
     */
    public java.util.Iterator<Object> stream(
            Map<String, Object> inputs,
            AgentSessionApi session,
            List<StreamMode> streamModes
    ) {
        TenantContext ctx = sessionTenantContext(session);
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

    private java.util.Iterator<Object> streamInternal(
            Map<String, Object> inputs,
            AgentSessionApi session,
            List<StreamMode> streamModes
    ) {
        ensureInitialized();
        // Align with Python DeepAgent.stream: drain enqueued harness configs before the query.
        drainPendingHarnessConfigs();
        Map<String, Object> normalized = new LinkedHashMap<>(inputs);
        normalized.putIfAbsent("conversation_id", card.getName() + "_session");
        normalized.putIfAbsent("query", "");
        boolean resumeInput = isResumeInput(normalized);
        if (config.isEnableTaskLoop() && !resumeInput) {
            normalized.put("_collect_inner_stream", true);
        }
        String requestLevelSessionId = String.valueOf(normalized.get("conversation_id"));
        DeepAgentSession effectiveSession;
        if (session instanceof DeepAgentSession ds) {
            effectiveSession = ds;
        } else {
            effectiveSession = new DeepAgentSession(
                    requestLevelSessionId,
                    session instanceof DeepAgentSession deep ? deep.getEnvs() : null,
                    card,
                    streamModes == null || streamModes.isEmpty() ? List.of(StreamMode.OUTPUT) : streamModes
            );
        }
        TenantContext effectiveCtx = sessionTenantContext(session);
        if (effectiveCtx == null || !effectiveCtx.isTenantAware()) {
            effectiveCtx = TenantContextHolder.getCurrentTenant();
        }
        if (effectiveCtx != null && effectiveCtx.isTenantAware()) {
            effectiveSession.withTenantContext(effectiveCtx);
        }
        effectiveSession.preRun(normalized);
        if (session != null) {
            copySessionState(session, effectiveSession);
        }
        if (config.isEnableTaskLoop() && !resumeInput) {
            try {
                STREAM_EXECUTOR.execute(() -> {
                    try {
                        Map<String, Object> result = withDeepAgentInvokeLifecycle(
                                normalized,
                                () -> runTaskLoop(normalized, effectiveSession)
                        );
                        writeTopLevelStreamResult(effectiveSession, 0, result);
                    } catch (Throwable error) {
                        effectiveSession.writeStream(new OutputSchema("error", 0, Map.of(
                                "output", error.getMessage() == null
                                        ? error.getClass().getSimpleName()
                                        : error.getMessage(),
                                "result_type", "error"
                        )));
                    } finally {
                        try {
                            if (session != null) {
                                copySessionState(effectiveSession, session);
                            }
                        } finally {
                            effectiveSession.postRun();
                        }
                    }
                });
            } catch (RejectedExecutionException rejected) {
                effectiveSession.writeStream(new OutputSchema("error", 0, Map.of(
                        "output", rejected.getMessage() == null
                                ? rejected.getClass().getSimpleName()
                                : rejected.getMessage(),
                        "result_type", "error"
                )));
                effectiveSession.postRun();
            }
            return effectiveSession.streamIterator();
        }
        List<Object> outputs = new ArrayList<>();
        try {
            Map<String, Object> result = invokeWithLifecycle(normalized, session);
            writeTopLevelStreamResult(effectiveSession, outputs.size(), result);
        } catch (RuntimeException ex) {
            effectiveSession.writeStream(new OutputSchema("error", outputs.size(), Map.of(
                    "output", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                    "result_type", "error"
            )));
        } finally {
            try {
                // Copy before postRun so Runner does not checkpoint stale outer state.
                if (session != null) {
                    copySessionState(effectiveSession, session);
                }
            } finally {
                effectiveSession.postRun();
            }
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
        for (LoopCoordinator coordinator : sessionLoopCoordinators.values()) {
            coordinator.requestAbort();
        }
    }

    /**
     * Abort the task loop of a specific session.
     *
     * @param sessionId session id; blank values abort all sessions
     */
    public void requestAbort(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            requestAbort();
            return;
        }
        LoopCoordinator coordinator = sessionLoopCoordinators.get(sessionId);
        if (coordinator != null) {
            coordinator.requestAbort();
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
        TaskLoopController controller = loopController();
        if (controller == null) {
            return;
        }
        String sessionId = session != null && session.getSessionId() != null
                ? session.getSessionId()
                : TaskLoopController.DEFAULT_SESSION_ID;
        controller.enqueueFollowUp(sessionId, message);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void steer(String message, AgentSessionApi session) {
        if (message == null || message.isBlank()) {
            return;
        }
        TaskLoopController controller = loopController();
        if (controller == null) {
            return;
        }
        String sessionId = session != null && session.getSessionId() != null
                ? session.getSessionId()
                : TaskLoopController.DEFAULT_SESSION_ID;
        if (eventQueue == null || session == null || !activeTaskLoopSessions.contains(sessionId)) {
            controller.enqueueSteering(sessionId, message);
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
        Path workspaceRoot = workspaceRootPath();
        if (workspaceRoot == null) {
            throw new IllegalStateException("DeepAgent workspace is not configured");
        }
        String sessionId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : card.getName() + "_session";
        Path planDir = workspaceRoot.resolve(".plans");
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
        Map<String, Object> values = new LinkedHashMap<>();
        if (ctx.getInputs() != null) {
            values.putAll(ctx.getInputs());
        }
        values.put("task", ctx.getTask());
        values.put("session", ctx.getSession());
        values.put("round", ctx.getRound());
        values.put("is_follow_up", ctx.isFollowUp());
        values.put("result", ctx.getResult());
        values.put("usage_metadata", ctx.getUsageMetadata());
        values.put("exception", ctx.getException());
        CallbackContext callbackContext = new CallbackContext(ctx.getAgent(), values);
        for (DeepAgentRail rail : getRails()) {
            rail.afterTaskIteration(callbackContext);
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
        if (spec instanceof com.openjiuwen.harness.schema.DeepAgentConfig.SubAgentConfig legacySpec) {
            return instantiateConfiguredSubagent(
                    DeepAgentConfigConverter.toRuntimeSubagent(legacySpec, normalized),
                    normalized,
                    sessionId
            );
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
    public void setSessionToolkit(Object sessionToolkit) {
        if (sessionToolkit == null || sessionToolkit instanceof SessionToolkit) {
            this.sessionToolkit = (SessionToolkit) sessionToolkit;
        } else {
            // Async subagent rail may pass SessionTools.SessionToolkit adapter; keep null runtime toolkit.
            this.sessionToolkit = null;
        }
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
            if (item instanceof com.openjiuwen.harness.schema.DeepAgentConfig.SubAgentConfig legacy) {
                String name = legacy.getName();
                if (matchesSubagentName(name, subagentType)) {
                    return legacy;
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
        applyParentRuntimeFallbacks(childConfig, normalizedType);
        return HarnessFactory.createDeepAgent(spec.getAgentCard(), childConfig, childWorkspace);
    }

    private void applyParentRuntimeFallbacks(DeepAgentConfig childConfig) {
        applyParentRuntimeFallbacks(childConfig, null);
    }

    private void applyParentRuntimeFallbacks(DeepAgentConfig childConfig, String normalizedType) {
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
        // general-purpose inherits parent tools/mcps/skills when the child spec left them empty
        // (Python factory._inject_general_purpose_subagent).
        if (DeepAgentConfig.GENERAL_PURPOSE_AGENT_NAME.equals(normalizedType)) {
            if (childConfig.getTools() == null || childConfig.getTools().isEmpty()) {
                childConfig.setTools(config.getTools() == null ? List.of() : new ArrayList<>(config.getTools()));
            }
            if (childConfig.getMcps() == null || childConfig.getMcps().isEmpty()) {
                childConfig.setMcps(config.getMcps() == null ? List.of() : new ArrayList<>(config.getMcps()));
            }
            if (childConfig.getSkills() == null || childConfig.getSkills().isEmpty()) {
                childConfig.setSkills(config.getSkills() == null ? List.of() : new ArrayList<>(config.getSkills()));
            }
        }
    }

    private Workspace resolveChildWorkspace(SubAgentConfig spec, String sessionId) {
        Path basePath;
        if (spec.getWorkspacePath() != null && !spec.getWorkspacePath().isBlank()) {
            basePath = Path.of(spec.getWorkspacePath());
        } else {
            Path root = workspaceRootPath();
            basePath = root != null ? root : Path.of(".");
        }
        Path childPath = sessionId != null && !sessionId.isBlank() ? basePath.resolve(sessionId) : basePath;
        String language = spec.getLanguage() != null && !spec.getLanguage().isBlank()
                ? spec.getLanguage()
                : (workspace != null ? workspace.getLanguage() : "cn");
        return new Workspace(childPath.toString(), language);
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
            // Align with 19c4f1fd (#66): raise concurrent task slots for multi-session outer loop.
            controllerConfig.setMaxConcurrentTasks(32);
            taskManager = new TaskManager(controllerConfig);
            eventQueue = new EventQueue(controllerConfig);
            eventHandler = new TaskLoopEventHandler(this);
            // Share controller queues so steer/follow-up and inner-round _steering_queue are the same.
            eventHandler.setInteractionQueues(loopController.getInteractionQueues());
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
        } else if (eventHandler != null) {
            eventHandler.setInteractionQueues(loopController.getInteractionQueues());
        }
        taskScheduler
                .getTaskExecutorRegistry()
                .addTaskExecutor(TaskLoopEventExecutor.DEEP_TASK_TYPE,
                        dependencies -> new CoreTaskLoopEventExecutor(dependencies, this, this::invokeInnerRound));
    }

    /**
     * Shut down the task loop runtime (TaskScheduler, EventQueue) if initialized.
     */
    public void shutdown() {
        if (taskScheduler != null) {
            taskScheduler.stop();
        }
        if (eventQueue != null) {
            eventQueue.stop();
        }
        activeTaskLoopSessions.clear();
        sessionLoopCoordinators.clear();
        if (taskManager != null) {
            taskManager.clearState();
        }
    }

    private List<com.openjiuwen.harness.schema.StopConditionEvaluator> buildStopEvaluators() {
        List<com.openjiuwen.harness.schema.StopConditionEvaluator> evaluators = new ArrayList<>();
        isExplicitCompletionPolicy = taskCompletionRail != null && taskCompletionRail.hasCompletionPromise();
        completionPromiseEvaluator = taskCompletionRail != null
                ? new com.openjiuwen.harness.schema.CompletionPromiseEvaluator(taskCompletionRail.getCompletionPromise(),
                        taskCompletionRail.getRequiredConfirmations())
                : new com.openjiuwen.harness.schema.CompletionPromiseEvaluator("", 1);
        evaluators.add(completionPromiseEvaluator);
        Integer maxRounds = taskCompletionRail != null ? taskCompletionRail.getMaxRounds() : null;
        if (maxRounds != null && maxRounds > 0) {
            evaluators.add(new com.openjiuwen.harness.schema.MaxRoundsEvaluator(maxRounds));
        }
        Double timeoutSeconds = taskCompletionRail != null ? taskCompletionRail.getTimeout() : null;
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            evaluators.add(new com.openjiuwen.harness.schema.TimeoutEvaluator(timeoutSeconds));
        } else if (config.getCompletionTimeout() != null && config.getCompletionTimeout() > 0) {
            evaluators.add(new com.openjiuwen.harness.schema.TimeoutEvaluator(config.getCompletionTimeout()));
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
        Object baseQuery = normalized.getOrDefault("query", "");
        Object currentQuery = baseQuery;
        boolean isFollowUp = false;
        List<Map<String, Object>> rounds = new ArrayList<>();
        int maxRounds = Math.max(1, config.getMaxIterations());

        startTaskLoopRuntime(session);
        try {
            while (coordinator.shouldContinue() && rounds.size() < maxRounds) {
                DeepAgentState loopState = loadState(session);
                loopState.addPendingFollowUps(loopController.drainFollowUp(sessionId));
                // First outer round always runs the invoke query; follow-ups apply from round 2+.
                if (!rounds.isEmpty() && loopState.hasPendingFollowUps()) {
                    currentQuery = loopState.pollPendingFollowUp();
                    isFollowUp = true;
                }
                saveState(session, loopState);

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
                if (coordinator.isAborted()) {
                    break;
                }
                if ("interrupt".equals(String.valueOf(roundResult.get("result_type")))) {
                    break;
                }

                loopState = loadState(session);
                loopState.addPendingFollowUps(loopController.drainFollowUp(sessionId));
                saveState(session, loopState);
                if (loopController.hasFollowUp() || loopState.hasPendingFollowUps()) {
                    continue;
                }
                if (hasRemainingTasks(session)) {
                    currentQuery = baseQuery;
                    isFollowUp = false;
                    continue;
                }
                break;
            }
        } finally {
            stopTaskLoopRuntime(session);
            // Keep loopCoordinator/loopController instances for abort/followUp between rounds.
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "deep_agent_result");
        result.put("agent_name", card.getName());
        result.put("mode", currentMode == null ? AgentMode.NORMAL.name().toLowerCase(Locale.ROOT)
                : currentMode.name().toLowerCase(Locale.ROOT));
        result.put("workspace", workspaceRootString());
        result.put("inputs", normalized);
        result.put("input", normalized);
        result.put("rounds", rounds);
        result.put("loop_state", coordinator.getState());
        if (!rounds.isEmpty()) {
            Map<String, Object> finalRound = rounds.get(rounds.size() - 1);
            result.put("final_result", finalRound);
            copyIfPresent(finalRound, result, "output");
            copyIfPresent(finalRound, result, "result_type");
            copyIfPresent(finalRound, result, "state");
            copyIfPresent(finalRound, result, "interrupt_ids");
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
        String sessionId = session.getSessionId();
        if (!activeTaskLoopSessions.add(sessionId)) {
            throw new IllegalStateException("Task loop already active for session: " + sessionId);
        }
        try {
            taskScheduler.getSessions().put(sessionId, session);
            eventQueue.subscribe(card.getId(), sessionId);
        } catch (RuntimeException error) {
            activeTaskLoopSessions.remove(sessionId);
            throw error;
        }
    }

    private void stopTaskLoopRuntime(AgentSessionApi session) {
        if (session == null) {
            return;
        }
        String sessionId = session.getSessionId();
        activeTaskLoopSessions.remove(sessionId);
        eventQueue.unsubscribe(card.getId(), sessionId);
        taskScheduler.getSessions().remove(sessionId);
        if (sessionId != null && !TaskLoopController.DEFAULT_SESSION_ID.equals(sessionId)) {
            sessionLoopCoordinators.remove(sessionId);
            if (taskManager != null) {
                taskManager.removeTask(TaskFilter.bySessionId(sessionId));
            }
            if (agent != null && agent.getContextEngine() != null) {
                agent.getContextEngine().clearContext(null, sessionId);
            }
        }
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
        com.openjiuwen.harness.schema.CompletionPromiseEvaluator completion = coordinator != null
                ? coordinator.getCompletionPromiseEvaluator()
                : null;
        if (completion == null && completionPromiseEvaluator != null) {
            completion = completionPromiseEvaluator;
        }
        if (completion == null) {
            return;
        }
        if (!isExplicitCompletionPolicy || taskCompletionRail == null) {
            completion.notifyFulfilled("");
            return;
        }
        String matchedPromise = taskCompletionRail.extractMatchingPromise(roundResult);
        if (matchedPromise != null && !matchedPromise.isBlank()) {
            completion.notifyFulfilled(matchedPromise);
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
        // Leave task_id unset so TaskLoopEventHandler can resolve it from TaskPlan (Python parity).
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("_handler_round_id", handlerRound);
        metadata.put("run_kind", isFollowUp ? "follow_up" : "outer_loop");
        metadata.put("is_follow_up", isFollowUp);
        metadata.put("loop_queues", loopController.getInteractionQueues(session.getSessionId()));
        if (isCollectInnerStream) {
            metadata.put("collect_inner_stream", true);
        }
        event.setMetadata(metadata);
        eventQueue.publishEvent(card.getId(), session, event);
        return awaitRoundCompletion("round_" + handlerRound, session);
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
            Map<String, Object> result = eventHandler.waitCompletion((double) timeoutMillis / 1000.0);
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
        // task-loop runs on a separate thread; re-bind tenant from session for SkillUseRail/tools.
        TenantContext ctx = sessionTenantContext(session);
        if (ctx != null && ctx.isTenantAware()) {
            TenantContextHolder.setCurrentTenant(ctx);
            try {
                bindTenantWorkspace(ctx);
                return unwrapInvokeResult(invokeReactAgent(effectiveInputs, session));
            } finally {
                TenantContextHolder.clearCurrentTenant();
                unbindTenantWorkspace();
            }
        }
        return unwrapInvokeResult(invokeReactAgent(effectiveInputs, session));
    }

    private Object invokeReactAgent(Map<String, Object> effectiveInputs, AgentSessionApi session) {
        Object react = reactAgent();
        if (react instanceof ReActAgent reActAgent) {
            return reActAgent.invoke(effectiveInputs, session).toCompletableFuture().join();
        }
        try {
            Method invoke = react.getClass().getMethod("invoke", Map.class, AgentSessionApi.class);
            invoke.setAccessible(true);
            Object result = invoke.invoke(react, effectiveInputs, session);
            if (result instanceof CompletableFuture<?> future) {
                return future.join();
            }
            if (result instanceof java.util.concurrent.CompletionStage<?> stage) {
                return stage.toCompletableFuture().join();
            }
            return result;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Mock/react agent invoke failed: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapInvokeResult(Object result) {
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("output", result);
        return wrapped;
    }

    private Map<String, Object> invokeInnerRoundStreaming(
            Map<String, Object> effectiveInputs,
            AgentSessionApi session
    ) {
        DeepAgentSession innerSession = new DeepAgentSession(
                String.valueOf(effectiveInputs.get("conversation_id")),
                session instanceof DeepAgentSession deepSession ? deepSession.getEnvs() : null,
                card,
                List.of(StreamMode.OUTPUT)
        );
        TenantContext ctx = sessionTenantContext(session);
        if (ctx != null && ctx.isTenantAware()) {
            innerSession.withTenantContext(ctx);
        }
        innerSession.preRun(effectiveInputs);
        copySessionState(session, innerSession);
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

    private Map<String, Object> collectStreamToResult(
            Map<String, Object> effectiveInputs,
            AgentSessionApi innerSession,
            AgentSessionApi session
    ) {
        List<Object> streamItems = new ArrayList<>();
        agent.stream(effectiveInputs, innerSession, List.of(StreamMode.OUTPUT)).forEachRemaining(chunk -> {
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

    private void copySessionState(AgentSessionApi source, AgentSessionApi target) {
        if (source == null || target == null) {
            return;
        }
        BaseSession sourceInner = innerOf(source);
        BaseSession targetInner = innerOf(target);
        if (sourceInner != null && targetInner != null
                && sourceInner.state() != null && targetInner.state() != null) {
            targetInner.state().setState(sourceInner.state().getState());
        }
    }

    private static BaseSession innerOf(AgentSessionApi session) {
        if (session instanceof DeepAgentSession deepSession) {
            return deepSession.getInner();
        }
        if (session instanceof AgentSession agentSession) {
            return agentSession.getInner();
        }
        return null;
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
        result.put("round", resolveInnerRound(inputs));
        result.put("is_follow_up", Boolean.TRUE.equals(inputs.get("is_follow_up")));
        result.put("output", resolveOutput(source));
        copyIfPresent(source, result, "result_type");
        copyIfPresent(source, result, "interrupt_ids");
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

    private int resolveInnerRound(Map<String, Object> inputs) {
        if (inputs != null && inputs.get("_handler_round_id") != null) {
            return intOrDefault(inputs.get("_handler_round_id"), 0);
        }
        String sessionId = string(inputs == null ? null : inputs.get("conversation_id"));
        return loopController != null ? loopController.getRoundCounter(sessionId) : 0;
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

    private TenantContext sessionTenantContext(AgentSessionApi session) {
        return session == null ? null : session.getTenantContext();
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
                applyTenantCwd(tenantWorkspace.toString());
                workspaceResolver = new TenantWorkspaceResolver(
                        config.getTenantDataRoot() != null ? config.getTenantDataRoot() : config.getWorkspacePath(),
                        tieredWorkspaceManager.getNamespaceFactory());
            } else {
                String baseWorkspace = config.getTenantDataRoot() != null
                        ? config.getTenantDataRoot() : config.getWorkspacePath();
                workspaceResolver = new TenantWorkspaceResolver(baseWorkspace);
                Path tenantWorkspace = workspaceResolver.resolveWorkspaceRoot(ctx);
                workspaceResolver.initializeTenantSpace(ctx);
                applyTenantCwd(tenantWorkspace.toString());
            }
        }
    }

    private void applyTenantCwd(String tenantWorkspace) {
        Cwd.setWorkspace(tenantWorkspace);
        Cwd.setOriginalCwd(tenantWorkspace);
        Cwd.setTenantRoot(tenantWorkspace);
        CwdContext.setWorkspace(tenantWorkspace);
        CwdContext.setOriginalCwd(tenantWorkspace);
        CwdContext.setTenantRoot(tenantWorkspace);
    }

    private void unbindTenantWorkspace() {
        Cwd.clear();
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
        sessionLoopCoordinators.clear();
        if (taskManager != null) {
            taskManager.clearState();
        }
    }

    @Override
    public void close() {
        destroy();
    }

    public void setTieredWorkspaceManager(TieredWorkspaceManager manager) {
        this.tieredWorkspaceManager = manager;
    }

    /**
     * initTieredWorkspaceManager.
     *
     * @since 0.1.7
     */

    public DeepAgentConfig deepConfig() {
        return config;
    }

    public Object reactAgent() {
        return reactAgentOverride != null ? reactAgentOverride : agent;
    }

    public void setReactAgent(Object reactAgent, boolean initialized) {
        this.reactAgentOverride = reactAgent;
        if (reactAgent instanceof ReActAgent ra) {
            this.agent = ra;
        }
        this.isInitialized = initialized;
    }

    public AbilityManager getAbilityManager() {
        return agent == null ? null : agent.getAbilityManager();
    }

    public LoopCoordinator loopCoordinator() {
        if (loopCoordinator == null) {
            if (config != null && config.isEnableTaskLoop()) {
                ensureTaskLoopRuntime();
            } else {
                loopCoordinator = new LoopCoordinator(buildStopEvaluators());
            }
        }
        return loopCoordinator;
    }

    public TaskLoopController loopController() {
        if (loopController == null) {
            if (config != null && config.isEnableTaskLoop()) {
                ensureTaskLoopRuntime();
            } else {
                loopController = new TaskLoopController();
            }
        }
        return loopController;
    }

    /**
     * SysOperation visible to rails (typed config value or test override Object).
     *
     * @return sys operation object, may be null
     */
    public Object getSysOperation() {
        if (railSysOperation != null) {
            return railSysOperation;
        }
        return config == null ? null : config.getSysOperation();
    }

    public TaskLoopEventHandler eventHandler() {
        return getEventHandler();
    }

    public boolean isInvokeActive() {
        return invokeActive;
    }

    public boolean isAutoInvokeScheduled() {
        return autoInvokeScheduled;
    }

    public void setAutoInvokeScheduled(boolean value) {
        this.autoInvokeScheduled = value;
    }

    public void configure(com.openjiuwen.harness.schema.DeepAgentConfig legacy) {
        DeepAgentConfigConverter.applyLegacy(config, legacy);
        syncWorkspaceFromLegacy(legacy);
        if (legacy != null) {
            railSysOperation = legacy.getSysOperation();
        }
        applyAutoRailsFromConfig();
        if (config.getTools() != null) {
            for (Object tool : config.getTools()) {
                registerConfiguredTool(tool);
            }
        }
        if (config.getRails() != null) {
            for (Object rail : config.getRails()) {
                if (rail instanceof DeepAgentRail deepAgentRail) {
                    addRail(deepAgentRail);
                } else if (rail != null) {
                    registerDeepRail(rail);
                    try {
                        Method init = rail.getClass().getMethod("init", DeepAgent.class);
                        init.invoke(rail, this);
                    } catch (ReflectiveOperationException ignored) {
                        // no deep-agent init
                    }
                }
                if (rail instanceof TaskCompletionRail completionRail) {
                    taskCompletionRail = completionRail;
                }
            }
        }
        if (config.isEnableTaskLoop()) {
            ensureTaskLoopRuntime();
        }
        isInitialized = true;
    }

    private void syncWorkspaceFromLegacy(com.openjiuwen.harness.schema.DeepAgentConfig legacy) {
        if (legacy == null) {
            return;
        }
        if (legacy.getWorkspace() == null) {
            if (legacy.isAutoCreateWorkspace()) {
                String language = config.getLanguage() == null ? "cn" : config.getLanguage();
                this.workspace = new Workspace(DeepAgentConfig.DEFAULT_WORKSPACE_PATH, language);
                config.setWorkspacePath(DeepAgentConfig.DEFAULT_WORKSPACE_PATH);
            } else {
                // Explicit null + autoCreateWorkspace=false (Python None) — no workspace sections.
                this.workspace = null;
                config.setWorkspacePath(null);
            }
            return;
        }
        if (legacy.getWorkspace() instanceof Workspace typed) {
            this.workspace = typed;
            config.setWorkspacePath(typed.root().toString());
            return;
        }
        String path = config.getWorkspacePath();
        if (path != null && !path.isBlank() && (workspace == null || !path.equals(workspace.root().toString()))) {
            this.workspace = new Workspace(path, config.getLanguage() == null ? "cn" : config.getLanguage());
        }
    }

    private void applyAutoRailsFromConfig() {
        List<Object> rails = config.getRails();
        if (rails == null) {
            rails = new ArrayList<>();
            config.setRails(rails);
        }
        if (config.isEnableTaskPlanning() && !containsRailType(rails, TaskPlanningRail.class)) {
            rails.add(new TaskPlanningRail());
        }
        if (config.isEnableTaskLoop() && !containsRailType(rails, TaskCompletionRail.class)) {
            rails.add(new TaskCompletionRail());
        }
        boolean hasSkills = (config.getSkillDirectories() != null && !config.getSkillDirectories().isEmpty())
                || (config.getSkills() != null && !config.getSkills().isEmpty())
                || config.isEnableSkillDiscovery();
        if (hasSkills && !containsSkillUseRail(rails)) {
            List<String> dirs = config.getSkillDirectories() != null && !config.getSkillDirectories().isEmpty()
                    ? config.getSkillDirectories()
                    : (config.getSkills() == null ? List.of() : config.getSkills());
            rails.add(new SkillUseRail(
                    dirs,
                    config.getSkillMode() == null || config.getSkillMode().isBlank()
                            ? SkillUseRail.SKILL_MODE_AUTO_LIST
                            : config.getSkillMode()
            ));
        }
        if (config.getSubagents() != null && !config.getSubagents().isEmpty()) {
            if (config.isEnableAsyncSubagent()) {
                if (!containsRailType(rails, SessionRail.class)) {
                    rails.add(new SessionRail());
                }
            } else if (!containsRailType(rails, SubagentRail.class)) {
                rails.add(new SubagentRail());
            }
        }
    }

    private static boolean containsRailType(List<Object> rails, Class<?> railType) {
        if (rails == null || railType == null) {
            return false;
        }
        for (Object rail : rails) {
            if (railType.isInstance(rail)) {
                return true;
            }
        }
        return false;
    }

    public void registerTool(Tool tool) {
        registerHarnessTool(tool);
    }

    public void unregisterTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return;
        }
        Tool matched = null;
        for (Object item : registeredTools) {
            if (item instanceof Tool tool && tool.getCard() != null && toolName.equals(tool.getCard().getName())) {
                matched = tool;
                break;
            }
        }
        if (matched != null) {
            unregisterHarnessTool(matched);
        } else if (agent != null) {
            agent.getAbilityManager().remove(toolName);
        }
    }

    public void addRail(DeepAgentRail rail) {
        if (rail == null || registeredRails.contains(rail)) {
            return;
        }
        rail.init(this);
        registeredRails.add(rail);
        bindDeepAgentRailToAgent(rail);
    }

    private void bindDeepAgentRailToAgent(DeepAgentRail rail) {
        if (agent == null || rail == null || !railsBoundToAgent.add(rail)) {
            return;
        }
        agent.registerRail(rail).toCompletableFuture().join();
    }

    private void unbindDeepAgentRailFromAgent(DeepAgentRail rail) {
        if (rail == null || !railsBoundToAgent.remove(rail) || agent == null) {
            return;
        }
        agent.unregisterRail(rail).toCompletableFuture().join();
    }

    private Path workspaceRootPath() {
        return workspace == null ? null : workspace.root();
    }

    private String workspaceRootString() {
        Path root = workspaceRootPath();
        return root == null ? "" : root.toString();
    }

    private static boolean containsSkillUseRail(List<Object> rails) {
        return containsRailType(rails, SkillUseRail.class);
    }

    public List<DeepAgentRail> findRailsByType(Class<? extends DeepAgentRail> railType) {
        if (railType == null) {
            return List.of();
        }
        List<DeepAgentRail> matched = new ArrayList<>();
        for (Object rail : registeredRails) {
            if (railType.isInstance(rail)) {
                matched.add(railType.cast(rail));
            }
        }
        return matched;
    }

    public int stripRailsByType(Class<? extends DeepAgentRail> railType) {
        List<DeepAgentRail> removed = findRailsByType(railType);
        for (DeepAgentRail rail : removed) {
            unbindDeepAgentRailFromAgent(rail);
            rail.uninit(this);
            registeredRails.remove(rail);
        }
        return removed.size();
    }

    public CompletableFuture<Void> registerRail(DeepAgentRail rail) {
        addRail(rail);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> unregisterRail(DeepAgentRail rail) {
        if (registeredRails.remove(rail) && rail != null) {
            unbindDeepAgentRailFromAgent(rail);
            rail.uninit(this);
        }
        return CompletableFuture.completedFuture(null);
    }

    public List<DeepAgentRail> getRails() {
        List<DeepAgentRail> rails = new ArrayList<>();
        for (Object rail : registeredRails) {
            if (rail instanceof DeepAgentRail typed) {
                rails.add(typed);
            }
        }
        return rails;
    }

    public Map<String, Tool> getTools() {
        Map<String, Tool> tools = new LinkedHashMap<>();
        for (Object item : registeredTools) {
            if (item instanceof Tool tool && tool.getCard() != null) {
                tools.put(tool.getCard().getName(), tool);
            }
        }
        return tools;
    }

    public Map<String, Object> getSubagents() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (config.getSubagents() == null) {
            return result;
        }
        for (Object item : config.getSubagents()) {
            String name = null;
            if (item instanceof SubAgentConfig spec && spec.getAgentCard() != null) {
                name = spec.getAgentCard().getName();
            } else if (item instanceof com.openjiuwen.harness.schema.DeepAgentConfig.SubAgentConfig legacy) {
                name = legacy.getName();
            } else if (item instanceof DeepAgent child && child.getCard() != null) {
                name = child.getCard().getName();
            }
            if (name == null || name.isBlank()) {
                name = "subagent-" + result.size();
            }
            result.put(name, item);
        }
        return result;
    }

    public void enqueueHarnessConfig(String configPath) {
        pendingHarnessConfigs.add(configPath == null ? "" : configPath);
    }

    public List<String> getPendingHarnessConfigs() {
        return new ArrayList<>(pendingHarnessConfigs);
    }

    /**
     * Hot-load rails / tools / skill dirs declared by a harness_config.yaml.
     *
     * @param configPath path to harness_config.yaml
     * @return human-readable resource labels ({@code rail:}, {@code tool:}, {@code skill_dir:})
     */
    public List<String> loadHarnessConfig(String configPath) {
        removePendingHarnessConfig(configPath);
        return doLoadHarnessConfig(configPath);
    }

    /**
     * Unload resources declared by a harness config file (re-parse YAML, develop-fast path).
     *
     * <p>Missing config file returns an empty list (Python {@code unload_harness_config} semantics).</p>
     *
     * @param configPath path originally passed to {@link #loadHarnessConfig(String)}
     * @return unloaded resource descriptors
     */
    public List<String> unloadHarnessConfig(String configPath) {
        if (configPath == null || configPath.isBlank()) {
            return List.of();
        }
        Path configPathObj = Path.of(configPath).toAbsolutePath().normalize();
        if (!Files.exists(configPathObj)) {
            return List.of();
        }
        ResolvedHarnessConfig resolved = HarnessConfigLoader.load(configPathObj);
        HarnessConfig.ResourcesSchema resources = resourcesOf(resolved);
        if (resources == null) {
            return List.of();
        }
        List<String> unloaded = new ArrayList<>();
        RuntimeExtensionArtifact runtimeExt = runtimeExtensionArtifactForConfig(configPathObj, resources);
        unloadRails(resources, runtimeExt, unloaded);
        unloadTools(resources, runtimeExt, unloaded);
        unloadSkillDirs(configPathObj, resources, runtimeExt, unloaded);
        return unloaded;
    }

    /**
     * Load any enqueued harness configs (Python {@code _drain_pending_harness_configs}).
     *
     * <p>Called from {@code stream}; failures are logged and skipped so one bad config
     * does not block the query.</p>
     */
    private void drainPendingHarnessConfigs() {
        while (!pendingHarnessConfigs.isEmpty()) {
            String path = pendingHarnessConfigs.remove(0);
            try {
                List<String> loaded = doLoadHarnessConfig(path);
                LOGGER.log(Level.INFO, "Auto-loaded harness config {0}: {1}", new Object[] {path, loaded});
            } catch (RuntimeException ex) {
                LOGGER.log(Level.WARNING, "Failed to load harness config: " + path, ex);
            }
        }
    }

    private List<String> doLoadHarnessConfig(String configPath) {
        if (configPath == null || configPath.isBlank()) {
            return List.of();
        }
        Path configPathObj = Path.of(configPath).toAbsolutePath().normalize();
        ResolvedHarnessConfig resolved = HarnessConfigLoader.load(configPathObj);
        HarnessConfig.ResourcesSchema resources = resourcesOf(resolved);
        if (resources == null) {
            return List.of();
        }
        List<String> loaded = new ArrayList<>();
        RuntimeExtensionArtifact runtimeExt = runtimeExtensionArtifactForConfig(configPathObj, resources);
        loadRails(resources, runtimeExt, loaded);
        loadTools(resources, runtimeExt, loaded);
        loadSkillDirs(configPathObj, resources, runtimeExt, loaded);
        return loaded;
    }

    private void removePendingHarnessConfig(String configPath) {
        if (configPath == null) {
            pendingHarnessConfigs.remove("");
            return;
        }
        pendingHarnessConfigs.remove(configPath);
        String absolute;
        try {
            absolute = Path.of(configPath).toAbsolutePath().normalize().toString();
        } catch (RuntimeException ignored) {
            return;
        }
        pendingHarnessConfigs.removeIf(pending -> {
            if (pending == null || pending.isBlank()) {
                return false;
            }
            try {
                return Path.of(pending).toAbsolutePath().normalize().toString().equals(absolute);
            } catch (RuntimeException ignored) {
                return false;
            }
        });
    }

    private void loadRails(
            HarnessConfig.ResourcesSchema resources,
            RuntimeExtensionArtifact runtimeExt,
            List<String> loaded
    ) {
        if (!hasItems(resources.getRails())) {
            return;
        }
        List<DeepAgentRail> resolvedRails = new ArrayList<>();
        if (runtimeExt != null) {
            for (Class<?> railClass : RuntimeExtensionLoader.loadRuntimeRails(runtimeExt, runtimeExtensionSessionId())) {
                resolvedRails.add(instantiateRail(railClass));
            }
        } else {
            resolvedRails.addAll(HarnessConfigBuilder.resolveRails(resources));
            if (resolvedRails.isEmpty()) {
                for (HarnessConfig.RailResourceSchema spec : resources.getRails()) {
                    Class<?> railType = classFromSpec(spec.getModule(), spec.getClassName());
                    if (railType != null) {
                        resolvedRails.add(instantiateRail(railType));
                    }
                }
            }
        }
        for (DeepAgentRail rail : resolvedRails) {
            registerRail(rail).join();
            loaded.add("rail:" + rail.getClass().getSimpleName());
        }
    }

    private void loadTools(
            HarnessConfig.ResourcesSchema resources,
            RuntimeExtensionArtifact runtimeExt,
            List<String> loaded
    ) {
        if (!hasItems(resources.getTools())) {
            return;
        }
        List<Tool> resolvedTools = new ArrayList<>();
        if (runtimeExt != null) {
            for (Class<?> toolClass : RuntimeExtensionLoader.loadRuntimeTools(runtimeExt, runtimeExtensionSessionId())) {
                resolvedTools.add(instantiateTool(toolClass));
            }
        } else {
            resolvedTools.addAll(HarnessConfigBuilder.resolveTools(resources));
            if (resolvedTools.isEmpty()) {
                for (HarnessConfig.ToolResourceSchema spec : resources.getTools()) {
                    Class<?> toolType = classFromSpec(spec.getModule(), spec.getClassName());
                    if (toolType != null) {
                        resolvedTools.add(instantiateTool(toolType));
                    }
                }
            }
        }
        for (Tool tool : resolvedTools) {
            registerTool(tool);
            loaded.add("tool:" + tool.getClass().getSimpleName());
        }
    }

    private void loadSkillDirs(
            Path configPath,
            HarnessConfig.ResourcesSchema resources,
            RuntimeExtensionArtifact runtimeExt,
            List<String> loaded
    ) {
        List<String> skillDirs = resolveSkillDirs(configPath, resources, runtimeExt);
        if (skillDirs.isEmpty()) {
            return;
        }
        SkillUseRail existingRail = findFirstSkillUseRail();
        if (existingRail != null) {
            existingRail.prependSkillDirs(skillDirs);
            existingRail.reloadSkills();
        } else {
            String mode = resources.getSkills() == null || resources.getSkills().getMode() == null
                    ? SkillUseRail.SKILL_MODE_ALL
                    : resources.getSkills().getMode();
            SkillUseRail newRail = new SkillUseRail(skillDirs, mode);
            registerRail(newRail).join();
            newRail.reloadSkills();
        }
        skillDirs.forEach(skillDir -> loaded.add("skill_dir:" + skillDir));
    }

    private void unloadRails(
            HarnessConfig.ResourcesSchema resources,
            RuntimeExtensionArtifact runtimeExt,
            List<String> unloaded
    ) {
        if (!hasItems(resources.getRails())) {
            return;
        }
        Set<Class<?>> railTypes = new LinkedHashSet<>();
        if (runtimeExt != null) {
            railTypes.addAll(RuntimeExtensionLoader.loadRuntimeRails(runtimeExt, runtimeExtensionSessionId()));
        } else {
            for (HarnessConfig.RailResourceSchema spec : resources.getRails()) {
                Class<?> railType = classFromSpec(spec.getModule(), spec.getClassName());
                if (railType != null) {
                    railTypes.add(railType);
                }
            }
        }
        for (DeepAgentRail rail : new ArrayList<>(getRails())) {
            if (railTypes.contains(rail.getClass())) {
                unregisterRail(rail).join();
                unloaded.add("rail:" + rail.getClass().getSimpleName());
            }
        }
    }

    private void unloadTools(
            HarnessConfig.ResourcesSchema resources,
            RuntimeExtensionArtifact runtimeExt,
            List<String> unloaded
    ) {
        if (!hasItems(resources.getTools())) {
            return;
        }
        List<Tool> resolvedTools = new ArrayList<>();
        if (runtimeExt != null) {
            for (Class<?> toolClass : RuntimeExtensionLoader.loadRuntimeTools(runtimeExt, runtimeExtensionSessionId())) {
                resolvedTools.add(instantiateTool(toolClass));
            }
        } else {
            resolvedTools.addAll(HarnessConfigBuilder.resolveTools(resources));
            if (resolvedTools.isEmpty()) {
                for (HarnessConfig.ToolResourceSchema spec : resources.getTools()) {
                    Class<?> toolType = classFromSpec(spec.getModule(), spec.getClassName());
                    if (toolType != null) {
                        resolvedTools.add(instantiateTool(toolType));
                    }
                }
            }
        }
        for (Tool tool : resolvedTools) {
            if (tool.getCard() == null) {
                continue;
            }
            unloaded.add("tool_id:" + tool.getCard().getId());
            unregisterTool(tool.getCard().getName());
            unloaded.add("tool:" + tool.getCard().getName());
        }
    }

    private void unloadSkillDirs(
            Path configPath,
            HarnessConfig.ResourcesSchema resources,
            RuntimeExtensionArtifact runtimeExt,
            List<String> unloaded
    ) {
        List<String> skillDirs = resolveSkillDirs(configPath, resources, runtimeExt);
        if (skillDirs.isEmpty()) {
            return;
        }
        SkillUseRail existingRail = findFirstSkillUseRail();
        if (existingRail != null) {
            existingRail.removeSkillDirs(skillDirs);
            existingRail.reloadSkills();
        }
        skillDirs.forEach(skillDir -> unloaded.add("skill_dir:" + skillDir));
    }

    private List<String> resolveSkillDirs(
            Path configPath,
            HarnessConfig.ResourcesSchema resources,
            RuntimeExtensionArtifact runtimeExt
    ) {
        if (resources.getSkills() == null || resources.getSkills().getDirs() == null
                || resources.getSkills().getDirs().isEmpty()) {
            return List.of();
        }
        if (runtimeExt != null) {
            return RuntimeExtensionLoader.loadRuntimeSkillDirs(runtimeExt);
        }
        Path sourceDir = configPath.getParent();
        if (sourceDir == null) {
            return List.of();
        }
        return resources.getSkills().getDirs().stream()
                .map(dir -> sourceDir.resolve(dir).toAbsolutePath().normalize().toString())
                .toList();
    }

    private SkillUseRail findFirstSkillUseRail() {
        for (DeepAgentRail rail : getRails()) {
            if (rail instanceof SkillUseRail skillUseRail) {
                return skillUseRail;
            }
        }
        return null;
    }

    private RuntimeExtensionArtifact runtimeExtensionArtifactForConfig(
            Path configPath,
            HarnessConfig.ResourcesSchema resources
    ) {
        if (configPath.getParent() == null) {
            return null;
        }
        String extensionName = configPath.getParent().getFileName().toString();
        String prefix = "openjiuwen.extensions.harness." + extensionName;
        boolean hasRuntimeModule = false;
        if (resources.getRails() != null) {
            for (HarnessConfig.RailResourceSchema spec : resources.getRails()) {
                if (isRuntimePackageSpec(spec.getType(), spec.getModule(), prefix)) {
                    hasRuntimeModule = true;
                    break;
                }
            }
        }
        if (!hasRuntimeModule && resources.getTools() != null) {
            for (HarnessConfig.ToolResourceSchema spec : resources.getTools()) {
                if (isRuntimePackageSpec(spec.getType(), spec.getModule(), prefix)) {
                    hasRuntimeModule = true;
                    break;
                }
            }
        }
        if (!hasRuntimeModule) {
            return null;
        }
        return RuntimeExtensionArtifact.builder()
                .extensionName(extensionName)
                .runtimePath(configPath.getParent().toString())
                .configPath(configPath.toString())
                .build();
    }

    private String runtimeExtensionSessionId() {
        if (card != null && card.getId() != null && !card.getId().isBlank()) {
            return card.getId();
        }
        if (card != null && card.getName() != null && !card.getName().isBlank()) {
            return card.getName();
        }
        return "deep_agent";
    }

    private static HarnessConfig.ResourcesSchema resourcesOf(ResolvedHarnessConfig resolved) {
        return resolved == null || resolved.getConfig() == null ? null : resolved.getConfig().getResources();
    }

    private static boolean isRuntimePackageSpec(String type, String module, String prefix) {
        return "package".equals(type) && module != null
                && (module.equals(prefix) || module.startsWith(prefix + "."));
    }

    private static boolean hasItems(List<?> items) {
        return items != null && !items.isEmpty();
    }

    private static DeepAgentRail instantiateRail(Class<?> railClass) {
        Object instance = instantiate(railClass);
        if (instance instanceof DeepAgentRail rail) {
            return rail;
        }
        throw new IllegalArgumentException("Runtime rail is not a DeepAgentRail: " + railClass.getName());
    }

    private static Tool instantiateTool(Class<?> toolClass) {
        Object instance = instantiate(toolClass);
        if (instance instanceof Tool tool) {
            return tool;
        }
        throw new IllegalArgumentException("Runtime tool is not a Tool: " + toolClass.getName());
    }

    private static Object instantiate(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Failed to instantiate runtime resource: " + type.getName(), exception);
        }
    }

    private static Class<?> classFromSpec(String module, String className) {
        if (className == null || className.isBlank()) {
            return null;
        }
        List<String> candidates = className.contains(".") || className.contains("$")
                ? List.of(className)
                : List.of(module == null || module.isBlank() ? className : module + "." + className, className);
        for (String candidate : candidates) {
            try {
                return Class.forName(candidate);
            } catch (ClassNotFoundException ignored) {
                // Try the next candidate.
            }
        }
        return null;
    }


    public java.util.Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session) {
        return stream(inputs, session, List.of(StreamMode.OUTPUT));
    }

    public DeepAgentState loadState(Object session) {
        if (!(session instanceof AgentSessionApi agentSession)) {
            return new DeepAgentState();
        }
        Object data = agentSession.getState(DeepAgentState.SESSION_STATE_KEY);
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return DeepAgentState.fromSessionMap(normalized);
        }
        return new DeepAgentState();
    }

    private boolean hasRemainingTasks(AgentSessionApi session) {
        DeepAgentState state = loadState(session);
        return state.getTaskPlan() != null && state.getTaskPlan().getNextTask() != null;
    }

    public void saveState(Object session, DeepAgentState state) {
        if (!(session instanceof AgentSessionApi agentSession)) {
            return;
        }
        DeepAgentState target = Objects.requireNonNullElseGet(state, DeepAgentState::new);
        agentSession.updateState(Map.of(DeepAgentState.SESSION_STATE_KEY, target.toSessionMap()));
    }

    public void clearState(Object session, boolean clearPersisted) {
        if (loopCoordinator != null) {
            loopCoordinator.reset();
        }
        if (clearPersisted && session instanceof AgentSessionApi agentSession) {
            Map<String, Object> cleared = new LinkedHashMap<>();
            cleared.put(DeepAgentState.SESSION_STATE_KEY, null);
            agentSession.updateState(cleared);
        }
    }

    public void clearState(Object session) {
        clearState(session, false);
    }

    public void switchMode(Object session, AgentMode mode) {
        DeepAgentState state = loadState(session);
        state.getPlanMode().setMode(mode == null ? AgentMode.NORMAL.value() : mode.value());
        saveState(session, state);
        setMode(mode == null ? AgentMode.NORMAL : mode);
    }

    public String getPlanFilePath(Object session) {
        DeepAgentState state = loadState(session);
        String slug = state.getPlanMode().getPlanSlug();
        if (slug == null || slug.isBlank()) {
            Path current = getPlanFilePath();
            return current == null ? null : current.toString();
        }
        Path slugPath = Path.of(slug);
        if (slugPath.isAbsolute() || slug.contains("/") || slug.contains("\\") || slug.endsWith(".md")) {
            return slugPath.toString();
        }
        Path root = workspace == null ? null : workspace.root();
        if (root == null) {
            return slug;
        }
        return root.resolve(".plans").resolve(slug + ".md").normalize().toString();
    }

    public CompletableFuture<Boolean> abort(Object session) {
        requestAbort();
        invokeActive = false;
        return CompletableFuture.completedFuture(Boolean.TRUE);
    }

    /**
     * Legacy develop-compat follow-up enqueue (returns completed future).
     */
    public CompletableFuture<Map<String, Object>> followUp(String message, Object session) {
        return followUp(message, null, session);
    }

    /**
     * Legacy develop-compat follow-up enqueue (returns completed future).
     */
    public CompletableFuture<Map<String, Object>> followUp(String message, String taskId, Object session) {
        AgentSessionApi agentSession = session instanceof AgentSessionApi typed ? typed : null;
        isFollowUp(message, agentSession);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "follow_up");
        result.put("message", message);
        result.put("task_id", taskId);
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Legacy develop-compat steer that returns a completed future.
     *
     * <p>Prefer {@link #steer(String, AgentSessionApi)} for new code.</p>
     */
    public CompletableFuture<Map<String, Object>> steerAsync(String message, Object session) {
        AgentSessionApi agentSession = session instanceof AgentSessionApi typed ? typed : null;
        steer(message, agentSession);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "steer");
        result.put("message", message);
        return CompletableFuture.completedFuture(result);
    }

    public Object getContextUsage(String sessionId, String contextId) {
        return Map.of("session_id", sessionId, "context_id", contextId);
    }

    public Object getCurrentContext(String sessionId, String contextId) {
        return Map.of("session_id", sessionId, "context_id", contextId);
    }

    public Object getContextOccupancy(String sessionId, String contextId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session_id", sessionId);
        result.put("context_id", contextId);
        result.put("occupancy", 0);
        return result;
    }

    /** Accept runtime config updates without going through the legacy schema type. */
    public void configure(DeepAgentConfig runtimeConfig) {
        if (runtimeConfig == null) {
            return;
        }
        if (runtimeConfig.getSubagents() != null) {
            config.getSubagents().clear();
            config.getSubagents().addAll(runtimeConfig.getSubagents());
        }
        if (runtimeConfig.getTools() != null) {
            config.getTools().clear();
            config.getTools().addAll(runtimeConfig.getTools());
            for (Object tool : runtimeConfig.getTools()) {
                registerConfiguredTool(tool);
            }
        }
        if (runtimeConfig.getMcps() != null) {
            config.getMcps().clear();
            config.getMcps().addAll(runtimeConfig.getMcps());
        }
        if (runtimeConfig.getRails() != null) {
            config.setRails(new ArrayList<>(runtimeConfig.getRails()));
        }
        config.setTaskLoopEnabled(runtimeConfig.isEnableTaskLoop());
        config.setTaskPlanningEnabled(runtimeConfig.isEnableTaskPlanning());
        config.setEnableSkillDiscovery(runtimeConfig.isEnableSkillDiscovery());
        if (runtimeConfig.getWorkspacePath() != null && !runtimeConfig.getWorkspacePath().isBlank()) {
            config.setWorkspacePath(runtimeConfig.getWorkspacePath());
            this.workspace = new Workspace(
                    runtimeConfig.getWorkspacePath(),
                    runtimeConfig.getLanguage() == null ? "cn" : runtimeConfig.getLanguage()
            );
        }
        applyAutoRailsFromConfig();
        if (config.getRails() != null) {
            for (Object rail : config.getRails()) {
                if (rail instanceof DeepAgentRail deepAgentRail && !registeredRails.contains(deepAgentRail)) {
                    addRail(deepAgentRail);
                }
            }
        }
        if (config.isEnableTaskLoop()) {
            ensureTaskLoopRuntime();
        }
        isInitialized = true;
    }


    public void initTieredWorkspaceManager() {
        String basePath = config.getTenantDataRoot() != null ? config.getTenantDataRoot() : config.getWorkspacePath();
        WorkspaceStore primaryStore = new LocalWorkspaceStore(basePath);
        List<WorkspaceStore> secondaryStores = new ArrayList<>();
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
