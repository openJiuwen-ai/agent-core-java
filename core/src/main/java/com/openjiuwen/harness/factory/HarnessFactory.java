/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.factory;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.utils.IsolatedActions;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.TaskCompletionRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.rails.security.SecurityRail;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.rails.subagent.SessionRail;
import com.openjiuwen.harness.rails.subagent.SubagentRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.security.ToolPermissionHost;
import com.openjiuwen.harness.subagents.SubAgentConfig;
import com.openjiuwen.harness.tools.CheckpointerRedisTodoStorageProvider;
import com.openjiuwen.harness.workspace.Workspace;
import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStoreFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Auto-generated for codecheck compliance.
 */
public final class HarnessFactory {
    private static final String GENERAL_PURPOSE_DESC_CN =
            "用于研究复杂问题、搜索文件与内容、执行多步骤任务。该智能体拥有与主代理完全相同的全部工具权限。"
                    + "适合用于隔离上下文与 Token 消耗，并完成特定的复杂任务，因为它拥有与主代理完全相同的全部能力。";
    private static final String GENERAL_PURPOSE_DESC_EN =
            "General-purpose agent for researching complex questions, searching for files and content, "
                    + "and executing multi-step tasks. This agent has access to all tools as the main agent. "
                    + "The general-purpose agent is suitable for isolating context and token consumption, "
                    + "and completing specific complex tasks";

    private HarnessFactory() {
    }

    /**
     * createDeepAgent.
     *
     * <p>Generates the instance-unique owner token before enriching the
     * config so every global registration performed during creation (the
     * default sys_operation, configured tool instances) is claimed under
     * the same ownership key.</p>
     *
     * @param card card
     * @param config config
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createDeepAgent(AgentCard card, DeepAgentConfig config, Workspace workspace) {
        AgentCard effectiveCard = card != null ? card : AgentCard.builder()
                .name("deep_agent")
                .description("DeepAgent instance")
                .build();
        ensureCardIdentity(effectiveCard);
        String ownerToken = newOwnerToken(effectiveCard);
        AtomicReference<DeepAgentConfig> enrichedConfig = new AtomicReference<>();
        IsolatedActions.IsolatedOutcome<DeepAgent> outcome = IsolatedActions.callIsolated(() -> {
            DeepAgentConfig effectiveConfig = enrichConfig(effectiveCard, config, workspace, ownerToken);
            enrichedConfig.set(effectiveConfig);
            if (CheckpointerRedisTodoStorageProvider.TYPE.equals(effectiveConfig.getTodoStorageType())
                    && effectiveConfig.getKvStoreConfig() != null && !effectiveConfig.getKvStoreConfig().isEmpty()) {
                throw new IllegalArgumentException("checkpointer_redis cannot use an independent KV store");
            }
            Workspace effectiveWorkspace = resolveWorkspace(effectiveConfig, workspace);
            registerToolInstances(effectiveConfig.getTools(), ownerToken);
            DeepAgent agent = new DeepAgent(effectiveCard, effectiveConfig, effectiveWorkspace, ownerToken);
            injectKvStore(agent, effectiveConfig);
            // Register tools/rails eagerly so getTools()/getRails() work before first invoke.
            agent.ensureInitialized();
            return agent;
        });
        if (outcome.hasFailure()) {
            // The creation claimed global entries under ownerToken (the
            // default sys_operation, the configured tool instances); a
            // creation that ends without delivering the agent must release
            // those claims or they stay pinned in the ownership registry
            // forever. Releasing a never-made claim is a no-op, so this
            // covers a failure at any step.
            releaseOwnershipClaims(effectiveCard, enrichedConfig.get(), ownerToken);
            Throwable failure = outcome.failure();
            if (failure instanceof Error error) {
                throw error;
            }
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw new IllegalStateException("unexpected checked failure creating the deep agent", failure);
        }
        return outcome.value();
    }

    /**
     * Releases the global ownership claims a failed agent creation made
     * under its owner token: the default sys_operation (registered by
     * enrichConfig when no sys_operation was configured) and the configured
     * tool instances (claimed by registerToolInstances and re-claimed by
     * the registration sequence). Failures during the release are logged
     * and swallowed — the original creation failure is what propagates.
     *
     * @param card the effective agent card naming the default sys_operation
     * @param effectiveConfig the enriched config, or null when enrichment itself failed
     * @param ownerToken the owner token the claims were made under
     * @since 0.1.16
     */
    private static void releaseOwnershipClaims(AgentCard card, DeepAgentConfig effectiveConfig, String ownerToken) {
        IsolatedActions.runIsolated(() -> {
            Runner.resourceMgr().removeSysOperationOwnedBy(sysOperationId(card), ownerToken);
            releaseToolClaims(effectiveConfig, ownerToken);
            return null;
        }).ifPresent(error -> Loggers.AGENT.error(
                "[createDeepAgent] ownership claim release failed for token '{}'", ownerToken, error));
    }

    /**
     * Releases the configured tool-instance claims made under the owner
     * token by registerToolInstances and the registration sequence.
     *
     * @param effectiveConfig the enriched config, or null when enrichment itself failed
     * @param ownerToken the owner token the claims were made under
     * @since 0.1.16
     */
    private static void releaseToolClaims(DeepAgentConfig effectiveConfig, String ownerToken) {
        if (effectiveConfig == null || effectiveConfig.getTools() == null) {
            return;
        }
        for (Object tool : effectiveConfig.getTools()) {
            if (tool instanceof Tool toolInstance) {
                Runner.resourceMgr().removeToolOwnedBy(toolInstance.getCard().getId(), ownerToken);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void injectKvStore(DeepAgent agent, DeepAgentConfig config) {
        Map<String, Object> kvStoreConfig = config.getKvStoreConfig();
        Object typeValue = kvStoreConfig == null ? null : kvStoreConfig.get("type");
        if (!(typeValue instanceof String type)) {
            return;
        }
        Map<String, Object> conf = kvStoreConfig.get("conf") instanceof Map
                ? (Map<String, Object>) kvStoreConfig.get("conf") : Map.of();
        BaseKVStore kvStore = KVStoreFactory.create(type, conf);
        agent.setKvStore(kvStore);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static DeepAgent createDeepAgent(DeepAgentConfig config) {
        AgentCard card = AgentCard.builder()
                .name("deep_agent")
                .description("DeepAgent instance")
                .build();
        return createDeepAgent(card, config, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static DeepAgent createDeepAgent(AgentCard card,
                                            DeepAgentConfig config,
                                            Workspace workspace,
                                            Map<String, Object> permissions,
                                            ToolPermissionHost permissionHost) {
        DeepAgentConfig effectiveConfig = config != null ? config : DeepAgentConfig.builder().build();
        effectiveConfig.setPermissions(permissions);
        effectiveConfig.setPermissionHost(permissionHost);
        return createDeepAgent(card, effectiveConfig, workspace);
    }

    /**
     * enrichConfig.
     *
     * @param card card
     * @param config config
     * @param workspace workspace
     * @param ownerToken owner token claiming the created entries
     * @return the result
     * @since 0.1.7
     */
    private static DeepAgentConfig enrichConfig(AgentCard card, DeepAgentConfig config, Workspace workspace,
            String ownerToken) {
        DeepAgentConfig source = config != null ? config : DeepAgentConfig.builder().build();
        Workspace effectiveWorkspace = resolveWorkspace(source, workspace);
        String language = resolveLanguage(source.getLanguage(), effectiveWorkspace);

        List<Object> subagents = new ArrayList<>(source.getSubagents() != null ? source.getSubagents() : List.of());
        List<Object> tools = new ArrayList<>(source.getTools() != null ? source.getTools() : List.of());

        if (source.isAddGeneralPurposeAgent()) {
            injectGeneralPurposeSubagent(subagents, language, source, tools);
        }

        EffectiveConfigParts parts = new EffectiveConfigParts(language,
                effectiveWorkspace.root().toString(), subagents, tools,
                populateDefaultRails(source, subagents),
                resolveSysOperation(card, source, effectiveWorkspace, ownerToken));
        return buildEffectiveConfig(source, parts);
    }

    /**
     * Resolved values the effective config builder needs beyond the source
     * config itself.
     *
     * @param language resolved language
     * @param workspacePath effective workspace root path
     * @param subagents normalized subagent list
     * @param tools normalized tool list
     * @param rails rails with the defaults populated
     * @param sysOperation resolved sys-operation
     */
    private record EffectiveConfigParts(String language, String workspacePath, List<Object> subagents,
            List<Object> tools, List<Object> rails, SysOperation sysOperation) {
    }

    /**
     * Starts the effective-config builder with the identity, prompt, and
     * interaction settings resolved from the source config.
     *
     * @param source source config
     * @param parts resolved values for the effective config
     * @return partially-filled config builder
     */
    private static DeepAgentConfig.DeepAgentConfigBuilder baseConfigBuilder(DeepAgentConfig source,
            EffectiveConfigParts parts) {
        return DeepAgentConfig.builder()
                .systemPrompt(source.getSystemPrompt())
                .maxIterations(source.getMaxIterations())
                .maxParallelToolCalls(source.getMaxParallelToolCalls())
                .shouldFailTaskOnToolError(source.isShouldFailTaskOnToolError())
                .isTaskLoopEnabled(source.isEnableTaskLoop())
                .isTaskPlanningEnabled(source.isEnableTaskPlanning())
                .language(parts.language())
                .defaultMode(source.getDefaultMode())
                .workspacePath(parts.workspacePath())
                .completionTimeout(source.getCompletionTimeout())
                .permissions(source.getPermissions())
                .tools(parts.tools())
                .rails(parts.rails())
                .mcps(new ArrayList<>(source.getMcps() != null ? source.getMcps() : List.of()))
                .subagents(parts.subagents());
    }

    /**
     * Assembles the effective config from the source settings and the
     * resolved parts.
     *
     * @param source source config
     * @param parts resolved values for the effective config
     * @return the effective config for agent creation
     */
    private static DeepAgentConfig buildEffectiveConfig(DeepAgentConfig source, EffectiveConfigParts parts) {
        return baseConfigBuilder(source, parts)
                .extraPromptSections(new ArrayList<>(
                        source.getExtraPromptSections() != null ? source.getExtraPromptSections() : List.of()))
                .skillDirectories(new ArrayList<>(
                        source.getSkillDirectories() != null ? source.getSkillDirectories() : List.of()))
                .skillMode(source.getSkillMode())
                .model(source.getModel())
                .backend(source.getBackend())
                .modelConfigs(source.getModelConfigs() == null ? new ArrayList<>()
                        : new ArrayList<>(source.getModelConfigs()))
                .promptMode(source.getPromptMode())
                .skills(new ArrayList<>(source.getSkills() != null ? source.getSkills() : List.of()))
                .enableSkillDiscovery(source.isEnableSkillDiscovery())
                .factoryKwargs(new java.util.LinkedHashMap<>(
                        source.getFactoryKwargs() != null ? source.getFactoryKwargs() : Map.of()))
                .isAsyncSubagentEnabled(source.isEnableAsyncSubagent())
                .addGeneralPurposeAgent(source.isAddGeneralPurposeAgent())
                .restrictToWorkDir(source.isRestrictToWorkDir())
                .autoCreateWorkspace(source.isAutoCreateWorkspace())
                .sysOperation(parts.sysOperation())
                .permissionHost(source.getPermissionHost())
                .enableTenantIsolation(source.isEnableTenantIsolation())
                .tenantDataRoot(source.getTenantDataRoot())
                .workspaceSecondaryTiers(source.getWorkspaceSecondaryTiers())
                .workspaceTierConfigs(source.getWorkspaceTierConfigs())
                .todoStorageType(source.isTodoStorageTypeExplicit() ? source.getTodoStorageType() : null)
                .todoStorageConfig(source.getTodoStorageConfig() == null ? null
                        : new LinkedHashMap<>(source.getTodoStorageConfig()))
                .sessionStoreType(source.getSessionStoreType())
                .kvStoreConfig(source.getKvStoreConfig())
                .tmpTtl(source.getTmpTtl())
                .tmpTtlScanInterval(source.getTmpTtlScanInterval())
                .build();
    }

    /**
     * populateDefaultRails.
     *
     * <p>Seeds the working rail list from the configured rails and ensures
     * the defaults: the security rail is always present; the planning,
     * completion, and skill rails follow their toggles or configured
     * skills; a session or plain subagent rail is ensured once subagents
     * exist.</p>
     *
     * @param source source
     * @param subagents subagents
     * @return the result
     * @since 0.1.16
     */
    private static List<Object> populateDefaultRails(DeepAgentConfig source, List<Object> subagents) {
        List<Object> rails = new ArrayList<>(source.getRails() != null ? source.getRails() : List.of());
        addDefaultRailIfAbsent(rails, SecurityRail.class, SecurityRail::new);
        if (source.isEnableTaskPlanning()) {
            addDefaultRailIfAbsent(rails, TaskPlanningRail.class, TaskPlanningRail::new);
        }
        if (source.isEnableTaskLoop()) {
            addDefaultRailIfAbsent(rails, TaskCompletionRail.class, TaskCompletionRail::new);
        }
        if (hasConfiguredSkills(source) || source.isEnableSkillDiscovery()) {
            addSkillUseRailIfAbsent(rails, source);
        }
        if (!subagents.isEmpty()) {
            if (source.isEnableAsyncSubagent()) {
                addDefaultRailIfAbsent(rails, SessionRail.class, SessionRail::new);
            } else {
                addDefaultRailIfAbsent(rails, SubagentRail.class, SubagentRail::new);
            }
        }
        return rails;
    }

    /**
     * resolveSysOperation.
     *
     * <p>Registers the default sys_operation card under the owner token
     * (idempotent: an equivalent existing entry is reused and claimed —
     * the first registrant's work binding stays effective — while a
     * conflicting definition fails fast) and returns the registered
     * instance.</p>
     *
     * @param card card
     * @param source source
     * @param workspace workspace
     * @param ownerToken owner token claiming the entry
     * @return the result
     * @since 0.1.16
     */
    private static SysOperation resolveSysOperation(AgentCard card, DeepAgentConfig source, Workspace workspace,
            String ownerToken) {
        SysOperation configured = source.getSysOperation();
        if (configured != null) {
            return configured;
        }
        String sysOpId = sysOperationId(card);
        SysOperationCard sysOperationCard = SysOperationCard.builder().id(sysOpId).name(sysOpId)
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(workspace.root().toString())
                        .restrictToSandbox(source.isRestrictToWorkDir()).build())
                .build();
        Result<?> added = Runner.resourceMgr().addSysOperation(sysOperationCard, List.of(card.getId()),
                ownerToken);
        throwIfAddResourceFailed(added, sysOpId);
        SysOperation registered = Runner.resourceMgr().getSysOperation(sysOpId);
        if (registered != null) {
            return registered;
        }
        return new SysOperation(sysOperationCard);
    }

    /**
     * sysOperationId.
     *
     * <p>Single source of truth for the default sys-operation id derived
     * from the agent card; {@code DeepAgent.destroy} uses the same formula
     * to release the ownership claimed at creation.</p>
     *
     * @param card card the sys operation is derived from
     * @return the default sys-operation id
     * @since 0.1.16
     */
    public static String sysOperationId(AgentCard card) {
        String name = card != null && card.getName() != null && !card.getName().isBlank() ? card.getName()
                : "deep_agent";
        return name + "_" + (card != null ? card.getId() : null);
    }

    /**
     * resolveWorkspace.
     * 
     * @param config config
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    private static Workspace resolveWorkspace(DeepAgentConfig config, Workspace workspace) {
        if (workspace != null) {
            return workspace;
        }
        String rootPath = config != null && config.getWorkspacePath() != null && !config.getWorkspacePath().isBlank()
                ? config.getWorkspacePath()
                : DeepAgentConfig.DEFAULT_WORKSPACE_PATH;
        String language = config != null && config.getLanguage() != null && !config.getLanguage().isBlank()
                ? config.getLanguage()
                : "cn";
        return new Workspace(rootPath, language);
    }

    private static void ensureCardIdentity(AgentCard card) {
        if (card.getId() == null || card.getId().isBlank()) {
            card.setId(UUID.randomUUID().toString().replace("-", ""));
        }
        if (card.getName() == null || card.getName().isBlank()) {
            card.setName("deep_agent");
        }
    }

    private static String resolveLanguage(String configLanguage, Workspace workspace) {
        if (configLanguage != null && !configLanguage.isBlank()) {
            return configLanguage;
        }
        if (workspace != null && workspace.getLanguage() != null && !workspace.getLanguage().isBlank()) {
            return workspace.getLanguage();
        }
        return "cn";
    }

    /**
     * registerToolInstances.
     *
     * <p>Registers each tool instance globally under the owner token
     * (idempotent: an equivalent existing entry is reused and claimed, a
     * conflicting definition fails fast).</p>
     *
     * @param tools tools
     * @param ownerToken owner token claiming each entry
     * @since 0.1.7
     */
    private static void registerToolInstances(List<Object> tools, String ownerToken) {
        if (tools == null) {
            return;
        }
        for (Object tool : tools) {
            if (!(tool instanceof Tool toolInstance)) {
                continue;
            }
            Result<?> added = Runner.resourceMgr().addTool(toolInstance, "harness", ownerToken);
            throwIfAddResourceFailed(added, toolInstance.getCard().getId());
        }
    }

    private static void injectGeneralPurposeSubagent(List<Object> subagents,
                                                     String language,
                                                     DeepAgentConfig source,
                                                     List<Object> tools) {
        boolean isExistingSkill = subagents.stream().anyMatch(HarnessFactory::isGeneralPurposeSubagent);
        if (isExistingSkill) {
            return;
        }
        List<Object> subagentRails = new ArrayList<>();
        if (source.getRails() != null) {
            for (Object rail : source.getRails()) {
                if (rail instanceof SessionRail || rail instanceof SubagentRail) {
                    continue;
                }
                subagentRails.add(rail);
            }
        }
        addDefaultRailIfAbsent(subagentRails, SysOperationRail.class, SysOperationRail::new);

        String resolvedLanguage = resolveLanguage(language, null);
        String description = "en".equalsIgnoreCase(resolvedLanguage)
                ? GENERAL_PURPOSE_DESC_EN
                : GENERAL_PURPOSE_DESC_CN;
        subagents.add(0, SubAgentConfig.builder()
                .agentCard(AgentCard.builder()
                        .name(DeepAgentConfig.GENERAL_PURPOSE_AGENT_NAME)
                        .description(description)
                        .build())
                .systemPrompt(source.getSystemPrompt())
                .language(resolvedLanguage)
                .maxIterations(source.getMaxIterations())
                .isTaskLoopEnabled(source.isEnableTaskLoop())
                .tools(new ArrayList<>(tools))
                .rails(subagentRails)
                .mcps(new ArrayList<>(source.getMcps() != null ? source.getMcps() : List.of()))
                .skillDirectories(new ArrayList<>(
                        source.getSkillDirectories() != null ? source.getSkillDirectories() : List.of()))
                .skills(new ArrayList<>(source.getSkills() != null ? source.getSkills() : List.of()))
                .enableSkillDiscovery(source.isEnableSkillDiscovery())
                .model(source.getModel())
                .backend(source.getBackend())
                .promptMode(source.getPromptMode())
                .factoryKwargs(new java.util.LinkedHashMap<>(
                        source.getFactoryKwargs() != null ? source.getFactoryKwargs() : Map.of()))
                .restrictToWorkDir(false)
                .build());
    }

    private static boolean isGeneralPurposeSubagent(Object subagent) {
        if (subagent instanceof SubAgentConfig spec) {
            return spec.getAgentCard() != null
                    && DeepAgentConfig.GENERAL_PURPOSE_AGENT_NAME.equals(spec.getAgentCard().getName());
        }
        if (subagent instanceof DeepAgent agent) {
            return agent.getCard() != null
                    && DeepAgentConfig.GENERAL_PURPOSE_AGENT_NAME.equals(agent.getCard().getName());
        }
        return false;
    }

    private static <T> void addDefaultRailIfAbsent(
            List<Object> rails,
            Class<T> railType,
            java.util.function.Supplier<T> supplier
    ) {
        boolean isExistingSkill = rails.stream()
                .filter(Objects::nonNull)
                .anyMatch(railType::isInstance);
        if (!isExistingSkill) {
            rails.add(supplier.get());
        }
    }

    private static void addSkillUseRailIfAbsent(List<Object> rails, DeepAgentConfig source) {
        boolean isExistingSkill = rails.stream()
                .filter(Objects::nonNull)
                .anyMatch(SkillUseRail.class::isInstance);
        if (!isExistingSkill) {
            rails.add(new SkillUseRail(
                    source.getSkillDirectories() != null ? source.getSkillDirectories() : List.of(),
                    source.getSkillMode(),
                    source.getSkills() != null ? source.getSkills() : List.of(),
                    List.of()
            ));
        }
    }

    /**
     * hasConfiguredSkills.
     *
     * @param source source
     * @return the result
     * @since 0.1.7
     */
    private static boolean hasConfiguredSkills(DeepAgentConfig source) {
        return (source.getSkillDirectories() != null && !source.getSkillDirectories().isEmpty())
                || (source.getSkills() != null && !source.getSkills().isEmpty());
    }

    /**
     * newOwnerToken.
     *
     * <p>Generates the instance-unique owner token used as the ownership
     * key for global resource registration. Must be called after
     * {@link #ensureCardIdentity(AgentCard)} so the prefix is stable.</p>
     *
     * @param card card used for the token prefix
     * @return the result
     * @since 0.1.16
     */
    private static String newOwnerToken(AgentCard card) {
        return card.getId() + "#" + UUID.randomUUID();
    }

    /**
     * throwIfAddResourceFailed.
     *
     * <p>Throws when a ResourceMgr add result is an error.</p>
     *
     * @param result add result returned by ResourceMgr
     * @param resourceId resource id used for error context
     * @since 0.1.16
     */
    private static void throwIfAddResourceFailed(Result<?> result, String resourceId) {
        if (!result.isError()) {
            return;
        }
        Object error = result.getError();
        if (error instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (error instanceof Exception exception) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_ADD_ERROR, null, null, exception,
                    Map.of("card", resourceId, "reason", String.valueOf(exception.getMessage())));
        }
        throw ErrorHelper.buildError(StatusCode.RESOURCE_ADD_ERROR, "card", resourceId, "reason",
                error != null ? String.valueOf(error) : "add resource failed");
    }
}
