/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.factory;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.SecurityRail;
import com.openjiuwen.harness.rails.SessionRail;
import com.openjiuwen.harness.rails.SkillUseRail;
import com.openjiuwen.harness.rails.SubagentRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.TaskCompletionRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.security.ToolPermissionHost;
import com.openjiuwen.harness.subagents.SubAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStoreFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * HarnessFactory.
 * 
 * @since 0.1.7
 */
public final class HarnessFactory {
    private static final String GENERAL_PURPOSE_NAME = "general-purpose";
    private static final String GENERAL_PURPOSE_DESC_CN =
        "用于研究复杂问题、搜索文件与内容、执行多步骤任务。该智能体拥有与主代理完全相同的全部工具权限。" + "适合用于隔离上下文与 Token 消耗，并完成特定的复杂任务，因为它拥有与主代理完全相同的全部能力。";
    private static final String GENERAL_PURPOSE_DESC_EN =
        "General-purpose agent for researching complex questions, searching for files and content, "
                + "and executing multi-step tasks. This agent has access to all tools as the main agent. "
                + "The general-purpose agent is suitable for isolating context and token consumption, "
                + "and completing specific complex tasks";

    /**
     * HarnessFactory.
     * 
     * @since 0.1.7
     */
    private HarnessFactory() {
    }

    /**
     * createDeepAgent.
     * 
     * @param card card
     * @param config config
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createDeepAgent(AgentCard card, DeepAgentConfig config, Workspace workspace) {
        AgentCard effectiveCard =
            card != null ? card : AgentCard.builder().name("deep_agent").description("DeepAgent instance").build();
        ensureCardIdentity(effectiveCard);
        DeepAgentConfig effectiveConfig = enrichConfig(effectiveCard, config, workspace);
        Workspace effectiveWorkspace = resolveWorkspace(effectiveConfig, workspace);
        registerToolInstances(effectiveConfig.getTools());
        DeepAgent agent = new DeepAgent(effectiveCard, effectiveConfig, effectiveWorkspace);
        injectKvStore(agent, effectiveConfig);
        return agent;
    }

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
     * createDeepAgent.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createDeepAgent(DeepAgentConfig config) {
        AgentCard card = AgentCard.builder().name("deep_agent").description("DeepAgent instance").build();
        return createDeepAgent(card, config, null);
    }

    /**
     * createDeepAgent.
     * 
     * @param card card
     * @param config config
     * @param workspace workspace
     * @param permissions permissions
     * @param permissionHost permissionHost
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createDeepAgent(AgentCard card, DeepAgentConfig config, Workspace workspace,
            Map<String, Object> permissions, ToolPermissionHost permissionHost) {
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
     * @return the result
     * @since 0.1.7
     */
    private static DeepAgentConfig enrichConfig(AgentCard card, DeepAgentConfig config, Workspace workspace) {
        DeepAgentConfig source = config != null ? config : DeepAgentConfig.builder().build();
        Workspace effectiveWorkspace = resolveWorkspace(source, workspace);
        String language = resolveLanguage(source.getLanguage(), effectiveWorkspace);

        List<Object> rails = new ArrayList<>(source.getRails() != null ? source.getRails() : List.of());
        List<Object> subagents = new ArrayList<>(source.getSubagents() != null ? source.getSubagents() : List.of());
        List<Object> tools = new ArrayList<>(source.getTools() != null ? source.getTools() : List.of());

        if (source.isGeneralPurposeAgentEnabled()) {
            injectGeneralPurposeSubagent(subagents, language, source, tools);
        }

        addDefaultRailIfAbsent(rails, SecurityRail.class, SecurityRail::new);
        if (source.isTaskPlanningEnabled()) {
            addDefaultRailIfAbsent(rails, TaskPlanningRail.class, TaskPlanningRail::new);
        }
        if (source.isTaskLoopEnabled()) {
            addDefaultRailIfAbsent(rails, TaskCompletionRail.class, TaskCompletionRail::new);
        }
        if (hasConfiguredSkills(source) || source.isEnableSkillDiscovery()) {
            addSkillUseRailIfAbsent(rails, source);
        }
        if (!subagents.isEmpty()) {
            if (source.isAsyncSubagentEnabled()) {
                addDefaultRailIfAbsent(rails, SessionRail.class, SessionRail::new);
            } else {
                addDefaultRailIfAbsent(rails, SubagentRail.class, SubagentRail::new);
            }
        }

        SysOperation sysOperation = source.getSysOperation();
        if (sysOperation == null) {
            String sysOpId = (card.getName() == null || card.getName().isBlank() ? "deep_agent" : card.getName()) + "_"
                    + card.getId();
            Object registered = Runner.resourceMgr().getSysOperation(sysOpId, null, TagMatchStrategy.ALL);
            if (registered instanceof SysOperation existing) {
                sysOperation = existing;
            } else {
                SysOperationCard sysOperationCard =
                    SysOperationCard.builder().id(sysOpId).name(sysOpId).mode(OperationMode.LOCAL)
                            .workConfig(LocalWorkConfig.builder().workDir(effectiveWorkspace.root().toString())
                                    .restrictToSandbox(source.isRestrictToWorkDir()).build())
                            .build();
                Runner.resourceMgr().addSysOperation(sysOperationCard, card.getId());
                Object added = Runner.resourceMgr().getSysOperation(sysOpId, null, TagMatchStrategy.ALL);
                sysOperation = added instanceof SysOperation addedSysOperation
                        ? addedSysOperation
                        : new SysOperation(sysOperationCard);
            }
        }

        return DeepAgentConfig.builder().systemPrompt(source.getSystemPrompt()).maxIterations(source.getMaxIterations())
                .isTaskLoopEnabled(source.isTaskLoopEnabled()).isTaskPlanningEnabled(source.isTaskPlanningEnabled())
                .language(language).defaultMode(source.getDefaultMode())
                .workspacePath(effectiveWorkspace.root().toString()).completionTimeout(source.getCompletionTimeout())
                .permissions(source.getPermissions()).tools(tools).rails(rails)
                .mcps(new ArrayList<>(source.getMcps() != null ? source.getMcps() : List.of())).subagents(subagents)
                .extraPromptSections(new ArrayList<>(
                        source.getExtraPromptSections() != null ? source.getExtraPromptSections() : List.of()))
                .skillDirectories(new ArrayList<>(
                        source.getSkillDirectories() != null ? source.getSkillDirectories() : List.of()))
                .skillMode(source.getSkillMode()).model(source.getModel()).backend(source.getBackend())
                .promptMode(source.getPromptMode())
                .skills(new ArrayList<>(source.getSkills() != null ? source.getSkills() : List.of()))
                .enableSkillDiscovery(source.isEnableSkillDiscovery())
                .factoryKwargs(new java.util.LinkedHashMap<>(
                        source.getFactoryKwargs() != null ? source.getFactoryKwargs() : Map.of()))
                .isAsyncSubagentEnabled(source.isAsyncSubagentEnabled())
                .addGeneralPurposeAgent(source.isGeneralPurposeAgentEnabled())
                .restrictToWorkDir(source.isRestrictToWorkDir()).sysOperation(sysOperation)
                .permissionHost(source.getPermissionHost())
                .enableTenantIsolation(source.isEnableTenantIsolation())
                .tenantDataRoot(source.getTenantDataRoot())
                .todoStorageType(source.getTodoStorageType())
                .sessionStoreType(source.getSessionStoreType())
                .kvStoreConfig(source.getKvStoreConfig()).build();
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
                : "./";
        String language = config != null && config.getLanguage() != null && !config.getLanguage().isBlank()
                ? config.getLanguage()
                : "cn";
        return Workspace.builder().rootPath(rootPath).language(language).build();
    }

    /**
     * ensureCardIdentity.
     * 
     * @param card card
     * @since 0.1.7
     */
    private static void ensureCardIdentity(AgentCard card) {
        if (card.getId() == null || card.getId().isBlank()) {
            card.setId(UUID.randomUUID().toString().replace("-", ""));
        }
        if (card.getName() == null || card.getName().isBlank()) {
            card.setName("deep_agent");
        }
    }

    /**
     * resolveLanguage.
     * 
     * @param configLanguage configLanguage
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
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
     * @param tools tools
     * @since 0.1.7
     */
    private static void registerToolInstances(List<Object> tools) {
        if (tools == null) {
            return;
        }
        for (Object tool : tools) {
            if (!(tool instanceof Tool toolInstance)) {
                continue;
            }
            if (Runner.resourceMgr().getTool(toolInstance.getCard().getId()) == null) {
                Runner.resourceMgr().addTool(toolInstance, "harness");
            }
        }
    }

    /**
     * injectGeneralPurposeSubagent.
     * 
     * @param subagents subagents
     * @param language language
     * @param source source
     * @param tools tools
     * @since 0.1.7
     */
    private static void injectGeneralPurposeSubagent(List<Object> subagents, String language, DeepAgentConfig source,
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
        String description =
            "en".equalsIgnoreCase(resolvedLanguage) ? GENERAL_PURPOSE_DESC_EN : GENERAL_PURPOSE_DESC_CN;
        subagents.add(0,
                SubAgentConfig.builder()
                        .agentCard(AgentCard.builder().name(GENERAL_PURPOSE_NAME).description(description).build())
                        .systemPrompt(source.getSystemPrompt()).language(resolvedLanguage)
                        .maxIterations(source.getMaxIterations()).isTaskLoopEnabled(source.isTaskLoopEnabled())
                        .tools(new ArrayList<>(tools)).rails(subagentRails)
                        .mcps(new ArrayList<>(source.getMcps() != null ? source.getMcps() : List.of()))
                        .skillDirectories(new ArrayList<>(
                                source.getSkillDirectories() != null ? source.getSkillDirectories() : List.of()))
                        .skills(new ArrayList<>(source.getSkills() != null ? source.getSkills() : List.of()))
                        .enableSkillDiscovery(source.isEnableSkillDiscovery()).model(source.getModel())
                        .backend(source.getBackend()).promptMode(source.getPromptMode())
                        .factoryKwargs(new java.util.LinkedHashMap<>(
                                source.getFactoryKwargs() != null ? source.getFactoryKwargs() : Map.of()))
                        .restrictToWorkDir(false).build());
    }

    /**
     * isGeneralPurposeSubagent.
     * 
     * @param subagent subagent
     * @return the result
     * @since 0.1.7
     */
    private static boolean isGeneralPurposeSubagent(Object subagent) {
        if (subagent instanceof SubAgentConfig spec) {
            return spec.getAgentCard() != null && GENERAL_PURPOSE_NAME.equals(spec.getAgentCard().getName());
        }
        if (subagent instanceof DeepAgent agent) {
            return agent.getCard() != null && GENERAL_PURPOSE_NAME.equals(agent.getCard().getName());
        }
        return false;
    }

    /**
     * addDefaultRailIfAbsent.
     * 
     * @param rails rails
     * @param railType railType
     * @param supplier supplier
     * @since 0.1.7
     */
    private static <T> void addDefaultRailIfAbsent(List<Object> rails, Class<T> railType,
            java.util.function.Supplier<T> supplier) {
        boolean isExistingSkill = rails.stream().filter(Objects::nonNull).anyMatch(railType::isInstance);
        if (!isExistingSkill) {
            rails.add(supplier.get());
        }
    }

    /**
     * addSkillUseRailIfAbsent.
     * 
     * @param rails rails
     * @param source source
     * @since 0.1.7
     */
    private static void addSkillUseRailIfAbsent(List<Object> rails, DeepAgentConfig source) {
        boolean isExistingSkill = rails.stream().filter(Objects::nonNull).anyMatch(SkillUseRail.class::isInstance);
        if (!isExistingSkill) {
            boolean hasTools = rails.stream().filter(Objects::nonNull).noneMatch(SysOperationRail.class::isInstance);
            rails.add(new SkillUseRail(source.getSkillDirectories() != null ? source.getSkillDirectories() : List.of(),
                    source.getSkillMode(), source.getSkills() != null ? source.getSkills() : List.of(), List.of(),
                    List.of(), true, hasTools));
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
}
