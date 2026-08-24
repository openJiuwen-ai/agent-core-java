/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.agents;

import com.openjiuwen.auto_harness.infra.SkillSourceManager;
import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.prompts.PromptSections;
import com.openjiuwen.auto_harness.rails.AutoHarnessContextRail;
import com.openjiuwen.auto_harness.rails.AutoHarnessExperienceRail;
import com.openjiuwen.auto_harness.rails.EditSafetyRail;
import com.openjiuwen.auto_harness.rails.SecurityRail;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.LspRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.subagents.BrowserAgentFactory;
import com.openjiuwen.harness.subagents.ExploreAgent;
import com.openjiuwen.harness.tools.WebTools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Auto Harness agent factory helpers.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/auto_harness/agents/factory.py}.</p>
 */
public final class AutoHarnessAgentFactory {

    private static final Path PACKAGE_DIR = resolvePackageDir();
    private static final Path SKILLS_DIR = PACKAGE_DIR.resolve("skills");
    private static final Path PROMPTS_DIR = PACKAGE_DIR.resolve("prompts");

    private AutoHarnessAgentFactory() {
    }

    public static SysOperation buildTrustedLocalSysOperation(String agentName) {
        LocalWorkConfig workConfig = new LocalWorkConfig();
        workConfig.setShellAllowlist(null);
        workConfig.setRestrictToSandbox(false);
        return new SysOperation(new SysOperationCard(
                agentName + "_trusted_local",
                OperationMode.LOCAL,
                workConfig
        ));
    }

    public static DeepAgent createAutoHarnessAgent(AutoHarnessConfig config) {
        return createAutoHarnessAgent(
                config,
                null,
                null,
                true,
                null,
                true,
                true,
                true,
                null,
                null
        );
    }

    public static DeepAgent createAutoHarnessAgent(
            AutoHarnessConfig config,
            String workspaceOverride,
            AgentRail editSafetyRail,
            boolean enableEditSafety,
            List<String> skillNames,
            boolean enableTaskLoop,
            boolean enableTaskPlanning,
            boolean enableProgressRepeat,
            List<DeepAgentRail> extraRails,
            List<Tool> extraTools
    ) {
        AutoHarnessConfig resolvedConfig = requireConfig(config);
        List<DeepAgentRail> rails = buildRails(
                resolvedConfig,
                editSafetyRail,
                enableEditSafety
        );
        rails.add(buildSkillRail(
                resolvedConfig,
                skillNames == null || skillNames.isEmpty()
                        ? List.of("implement", "verify", "communicate")
                        : skillNames
        ));
        if (enableTaskPlanning) {
            rails.add(new TaskPlanningRail(enableProgressRepeat, 5, Map.of()));
        }
        if (extraRails != null) {
            rails.addAll(extraRails);
        }

        List<Tool> tools = new ArrayList<>();
        if (extraTools != null) {
            tools.addAll(extraTools);
        }

        String workspace = workspaceOverride == null || workspaceOverride.isBlank()
                ? resolvedConfig.getWorkspace()
                : workspaceOverride;
        String systemPrompt = renderAutoHarnessSystemPrompt(resolvedConfig);
        Map<String, DeepAgentConfig.SubAgentConfig> subagents = buildSubagents(resolvedConfig, workspace);

        return createConfiguredAgent(
                resolvedConfig,
                new AgentCard("auto-harness", "auto-harness", "Auto Harness implementation agent"),
                systemPrompt,
                tools,
                subagents,
                rails,
                workspace,
                enableTaskLoop,
                enableTaskPlanning,
                true,
                resolvedConfig.resolveAgentIterations("implement", 30),
                resolvedConfig.getModelTimeoutSecs(),
                buildTrustedLocalSysOperation("auto-harness"),
                resolvedConfig.getModel()
        );
    }

    public static DeepAgent createCommitAgent(
            AutoHarnessConfig config,
            String workspaceOverride,
            List<DeepAgentRail> extraRails
    ) {
        return createAutoHarnessAgent(
                config,
                workspaceOverride,
                null,
                true,
                List.of("commit", "communicate"),
                false,
                false,
                false,
                extraRails,
                null
        );
    }

    public static List<DeepAgentRail> buildRails(AutoHarnessConfig config) {
        return buildRails(config, null, true);
    }

    public static List<DeepAgentRail> buildRails(
            AutoHarnessConfig config,
            AgentRail editSafetyRail,
            boolean enableEditSafety
    ) {
        AutoHarnessConfig resolvedConfig = requireConfig(config);
        List<DeepAgentRail> rails = new ArrayList<>();
        rails.add(new SysOperationRail());
        rails.add(bridge(new AutoHarnessContextRail(true)));
        rails.add(new LspRail());
        rails.add(new AutoHarnessExperienceRail(
                resolvedConfig.getResolvedExperienceDir(),
                resolvedConfig.getLanguage()
        ));
        rails.add(bridge(new SecurityRail(
                resolvedConfig.getImmutableFiles(),
                resolvedConfig.getHighImpactPrefixes()
        )));
        if (enableEditSafety) {
            rails.add(bridge(editSafetyRail == null ? new EditSafetyRail() : editSafetyRail));
        }
        return rails;
    }

    public static Map<String, DeepAgentConfig.SubAgentConfig> buildSubagents(
            AutoHarnessConfig config,
            String workspace
    ) {
        AutoHarnessConfig resolvedConfig = requireConfig(config);
        String resolvedLanguage = resolveLanguage(resolvedConfig.getLanguage());
        String subagentWorkspace = workspace == null || workspace.isBlank()
                ? resolvedConfig.getWorkspace()
                : workspace;
        Map<String, DeepAgentConfig.SubAgentConfig> subagents = new LinkedHashMap<>();
        subagents.put("explore_agent", ExploreAgent.buildExploreAgentConfig(
                resolvedConfig.getModel(),
                new AgentCard("explore_agent", "explore_agent", ExploreAgent.defaultDescription(resolvedLanguage)),
                null,
                null,
                null,
                resolvedLanguage,
                false,
                resolvedConfig.resolveAgentIterations("explore_subagent", 20)
        ));
        subagents.get("explore_agent").setWorkspace(subagentWorkspace);
        try {
            DeepAgentConfig.SubAgentConfig browser = BrowserAgentFactory.buildBrowserAgentConfig(
                    resolvedConfig.getModel(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    resolvedLanguage,
                    false,
                    resolvedConfig.resolveAgentIterations("browser_subagent", 20)
            );
            browser.setWorkspace(subagentWorkspace);
            subagents.put("browser_agent", browser);
        } catch (RuntimeException ignored) {
            // Python logs and continues when browser subagent construction is unavailable.
        }
        return subagents;
    }

    public static String loadCiGateRules(AutoHarnessConfig config) {
        AutoHarnessConfig resolvedConfig = requireConfig(config);
        Path path = resolvedConfig.getCiGateConfig() == null || resolvedConfig.getCiGateConfig().isBlank()
                ? PACKAGE_DIR.resolve("resources").resolve("ci_gate.yaml")
                : Path.of(resolvedConfig.getCiGateConfig());
        if (!Files.exists(path)) {
            return "";
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    public static String loadPrompt(String filename) {
        try {
            return Files.readString(PROMPTS_DIR.resolve(filename), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    public static String renderPrompt(String template, Map<String, String> values) {
        String rendered = template == null ? "" : template;
        if (values == null) {
            return rendered;
        }
        for (Map.Entry<String, String> item : values.entrySet()) {
            rendered = rendered.replace("{" + item.getKey() + "}", item.getValue() == null ? "" : item.getValue());
        }
        return rendered;
    }

    public static List<DeepAgentRail> buildReadonlyRails(AutoHarnessConfig config) {
        AutoHarnessConfig resolvedConfig = requireConfig(config);
        List<DeepAgentRail> rails = new ArrayList<>();
        rails.add(new SysOperationRail());
        rails.add(bridge(new AutoHarnessContextRail(true)));
        rails.add(new LspRail());
        rails.add(new AutoHarnessExperienceRail(
                resolvedConfig.getResolvedExperienceDir(),
                resolvedConfig.getLanguage()
        ));
        return rails;
    }

    public static SkillUseRail buildSkillRail(AutoHarnessConfig config, List<String> skillNames) {
        AutoHarnessConfig resolvedConfig = requireConfig(config);
        List<String> roots = new ArrayList<>();
        roots.add(SKILLS_DIR.toString());
        roots.addAll(resolvedConfig.getSkillsDirs());
        if (AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE.equals(resolvedConfig.getPipelinePreference())) {
            roots.addAll(SkillSourceManager.communitySkillCacheSkillDirs(resolvedConfig));
        }
        List<String> existingRoots = roots.stream()
                .map(Path::of)
                .filter(Files::isDirectory)
                .map(Path::toString)
                .toList();
        List<String> enabledSkills = skillNames == null ? List.of() : List.copyOf(skillNames);
        return new SkillUseRail(
                String.join(",", existingRoots),
                SkillUseRail.SKILL_MODE_ALL,
                true,
                true,
                enabledSkills,
                null
        );
    }

    public static List<Tool> buildResearchTools(AutoHarnessConfig config) {
        requireConfig(config);
        return List.of(
                new WebTools.WebFreeSearchTool(null),
                new WebTools.WebFetchWebpageTool()
        );
    }

    public static DeepAgent createAssessAgent(AutoHarnessConfig config, List<DeepAgentRail> extraRails) {
        AutoHarnessConfig resolvedConfig = requireConfig(config);
        List<DeepAgentRail> rails = buildReadonlyRails(resolvedConfig);
        rails.add(buildSkillRail(resolvedConfig, List.of("assess")));
        addExtraRails(rails, extraRails);
        return createStageAgent(
                resolvedConfig,
                "auto-harness-assess",
                "Auto Harness assessment agent",
                loadPrompt("assess.md"),
                buildResearchTools(resolvedConfig),
                rails,
                resolvedConfig.getWorkspace(),
                true,
                resolvedConfig.resolveAgentIterations("assess", 30),
                resolvedConfig.getModel()
        );
    }

    public static DeepAgent createPlanAgent(AutoHarnessConfig config, List<DeepAgentRail> extraRails) {
        AutoHarnessConfig resolvedConfig = requireConfig(config);
        List<DeepAgentRail> rails = buildReadonlyRails(resolvedConfig);
        rails.add(buildSkillRail(resolvedConfig, List.of("plan")));
        addExtraRails(rails, extraRails);
        return createStageAgent(
                resolvedConfig,
                "auto-harness-plan",
                "Auto Harness planning agent",
                loadPrompt("plan.md"),
                buildResearchTools(resolvedConfig),
                rails,
                resolvedConfig.getWorkspace(),
                true,
                resolvedConfig.resolveAgentIterations("plan", 15),
                resolvedConfig.getModel()
        );
    }

    public static DeepAgent createEvalAgent(AutoHarnessConfig config, List<DeepAgentRail> extraRails) {
        AutoHarnessConfig resolvedConfig = requireConfig(config);
        List<DeepAgentRail> rails = buildReadonlyRails(resolvedConfig);
        rails.add(buildSkillRail(resolvedConfig, List.of("verify", "verify_ext")));
        addExtraRails(rails, extraRails);
        return createStageAgent(
                resolvedConfig,
                "auto-harness-eval",
                "Auto Harness evaluator agent",
                loadPrompt("evaluate.md"),
                List.of(),
                rails,
                resolvedConfig.getWorkspace(),
                true,
                resolvedConfig.resolveAgentIterations("eval", 10),
                resolvedConfig.getModel()
        );
    }

    public static DeepAgent createSelectPipelineAgent(AutoHarnessConfig config, List<DeepAgentRail> extraRails) {
        AutoHarnessConfig resolvedConfig = requireConfig(config);
        List<DeepAgentRail> rails = buildReadonlyRails(resolvedConfig);
        rails.add(buildSkillRail(resolvedConfig, List.of("select_pipeline")));
        addExtraRails(rails, extraRails);
        return createStageAgent(
                resolvedConfig,
                "auto-harness-select-pipeline",
                "Auto Harness pipeline selection agent",
                loadPrompt("select_pipeline.md"),
                buildResearchTools(resolvedConfig),
                rails,
                resolvedConfig.getWorkspace(),
                true,
                resolvedConfig.resolveAgentIterations("select_pipeline", 10),
                resolvedConfig.getModel()
        );
    }

    public static DeepAgent createDesignExtAgent(AutoHarnessConfig config, List<DeepAgentRail> extraRails) {
        AutoHarnessConfig resolvedConfig = requireConfig(config);
        List<DeepAgentRail> rails = buildReadonlyRails(resolvedConfig);
        rails.add(buildSkillRail(resolvedConfig, List.of("design_ext")));
        addExtraRails(rails, extraRails);
        return createStageAgent(
                resolvedConfig,
                "auto-harness-design-ext",
                "Auto Harness extension design agent",
                loadPrompt("design_ext.md"),
                buildResearchTools(resolvedConfig),
                rails,
                resolvedConfig.getWorkspace(),
                true,
                resolvedConfig.resolveAgentIterations("design_ext", 15),
                resolvedConfig.getModel()
        );
    }

    public static DeepAgent createPrDraftAgent(
            AutoHarnessConfig config,
            String workspaceOverride,
            List<DeepAgentRail> extraRails
    ) {
        AutoHarnessConfig resolvedConfig = requireConfig(config);
        List<DeepAgentRail> rails = buildReadonlyRails(resolvedConfig);
        rails.add(buildSkillRail(resolvedConfig, List.of("communicate")));
        addExtraRails(rails, extraRails);
        String workspace = workspaceOverride == null || workspaceOverride.isBlank()
                ? resolvedConfig.getWorkspace()
                : workspaceOverride;
        return createStageAgent(
                resolvedConfig,
                "auto-harness-pr-draft",
                "Auto Harness PR draft agent",
                loadPrompt("pr_draft.md"),
                List.of(),
                rails,
                workspace,
                false,
                resolvedConfig.resolveAgentIterations("pr_draft", 5),
                resolvedConfig.getModel()
        );
    }

    public static DeepAgent createLearningsAgent(
            AutoHarnessConfig config,
            String sessionResults,
            String existingMemories,
            List<DeepAgentRail> extraRails
    ) {
        AutoHarnessConfig resolvedConfig = requireConfig(config);
        List<DeepAgentRail> rails = buildReadonlyRails(resolvedConfig);
        rails.add(buildSkillRail(resolvedConfig, List.of("communicate")));
        addExtraRails(rails, extraRails);
        String prompt = renderPrompt(loadPrompt("learnings.md"), Map.of(
                "session_results", sessionResults == null ? "" : sessionResults,
                "existing_memories", existingMemories == null ? "" : existingMemories
        ));
        return createStageAgent(
                resolvedConfig,
                "auto-harness-learnings",
                "Auto Harness learnings agent",
                prompt,
                List.of(),
                rails,
                resolvedConfig.getWorkspace(),
                false,
                resolvedConfig.resolveAgentIterations("learnings", 5),
                resolvedConfig.getModel()
        );
    }

    public static DeepAgent createMergeExtAgent(
            AutoHarnessConfig config,
            String workspaceOverride,
            List<DeepAgentRail> extraRails
    ) {
        AutoHarnessConfig resolvedConfig = requireConfig(config);
        List<DeepAgentRail> rails = buildReadonlyRails(resolvedConfig);
        addExtraRails(rails, extraRails);
        return createStageAgent(
                resolvedConfig,
                "auto-harness-merge-ext",
                "Auto Harness merge extension repair agent",
                "You are repairing a merged runtime extension. Only modify files inside merged_extensions/. "
                        + "Do not change business logic.",
                buildResearchTools(resolvedConfig),
                rails,
                workspaceOverride,
                false,
                resolvedConfig.resolveAgentIterations("merge_ext", 8),
                resolvedConfig.getModel()
        );
    }

    public static DeepAgent createActivateGuideAgent(AutoHarnessConfig config, List<DeepAgentRail> extraRails) {
        AutoHarnessConfig resolvedConfig = requireConfig(config);
        List<DeepAgentRail> rails = new ArrayList<>();
        addExtraRails(rails, extraRails);
        Object model = resolvedConfig.getPlanModel() == null ? resolvedConfig.getModel() : resolvedConfig.getPlanModel();
        return createStageAgent(
                resolvedConfig,
                "activate-guide",
                "Auto Harness activation guide agent",
                loadPrompt("activate_guide.md"),
                List.of(),
                rails,
                resolvedConfig.getWorkspace(),
                false,
                1,
                model
        );
    }

    public static AgentRailBridge bridge(AgentRail rail) {
        return new AgentRailBridge(rail);
    }

    private static DeepAgent createStageAgent(
            AutoHarnessConfig config,
            String name,
            String description,
            String systemPrompt,
            List<Tool> tools,
            List<DeepAgentRail> rails,
            String workspace,
            boolean enableAsyncSubagent,
            int maxIterations,
            Object model
    ) {
        return createConfiguredAgent(
                config,
                new AgentCard(name, name, description),
                systemPrompt,
                tools,
                buildSubagents(config, workspace),
                rails,
                workspace,
                false,
                false,
                enableAsyncSubagent,
                maxIterations,
                config.getModelTimeoutSecs(),
                buildTrustedLocalSysOperation(name),
                model
        );
    }

    private static DeepAgent createConfiguredAgent(
            AutoHarnessConfig config,
            AgentCard card,
            String systemPrompt,
            List<Tool> tools,
            Map<String, DeepAgentConfig.SubAgentConfig> subagents,
            List<DeepAgentRail> rails,
            String workspace,
            boolean enableTaskLoop,
            boolean enableTaskPlanning,
            boolean enableAsyncSubagent,
            int maxIterations,
            double completionTimeout,
            Object sysOperation,
            Object model
    ) {
        DeepAgentConfig deepConfig = new DeepAgentConfig();
        deepConfig.setModel(model);
        deepConfig.setCard(card);
        deepConfig.setSystemPrompt(systemPrompt);
        deepConfig.setTools(tools == null ? List.of() : List.copyOf(tools));
        deepConfig.setSubagents(subagents == null ? Map.of() : subagents);
        deepConfig.setRails(rails == null ? List.of() : List.copyOf(rails));
        deepConfig.setWorkspace(workspace);
        deepConfig.setLanguage(config.getLanguage());
        deepConfig.setEnableTaskLoop(enableTaskLoop);
        deepConfig.setEnablePlanMode(enableTaskPlanning);
        deepConfig.setEnableAsyncSubagent(enableAsyncSubagent);
        deepConfig.setMaxIterations(maxIterations);
        deepConfig.setCompletionTimeout(completionTimeout);
        deepConfig.setAutoCreateWorkspace(false);
        deepConfig.setSysOperation(sysOperation);

        DeepAgent agent = new DeepAgent(card);
        agent.configure(deepConfig);
        return agent;
    }

    private static String renderAutoHarnessSystemPrompt(AutoHarnessConfig config) {
        List<PromptSection> sections = PromptSections.buildAutoHarnessSections(
                loadCiGateRules(config),
                "",
                PROMPTS_DIR.toString()
        );
        return sections.stream()
                .map(section -> section.render(config.getLanguage()))
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    private static void addExtraRails(List<DeepAgentRail> rails, List<DeepAgentRail> extraRails) {
        if (extraRails != null) {
            rails.addAll(extraRails);
        }
    }

    private static AutoHarnessConfig requireConfig(AutoHarnessConfig config) {
        return Objects.requireNonNull(config, "config");
    }

    private static String resolveLanguage(String language) {
        return "en".equalsIgnoreCase(language) ? "en" : "cn";
    }

    private static Path resolvePackageDir() {
        List<Path> candidates = List.of(
                Path.of("openjiuwen", "auto_harness"),
                Path.of("..", "agent-core-0.1.14", "openjiuwen", "auto_harness"),
                Path.of("agent-core-0.1.14", "openjiuwen", "auto_harness")
        );
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return Path.of("openjiuwen", "auto_harness").toAbsolutePath().normalize();
    }

    /**
     * DeepAgent bridge for lower-level AgentRail implementations.
     *
     * <p>Mirrors Python's direct use of {@code AgentRail} values in
     * {@code openjiuwen/auto_harness/agents/factory.py}.</p>
     */
    public static final class AgentRailBridge extends DeepAgentRail {
        private final AgentRail delegate;

        private AgentRailBridge(AgentRail delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            setPriority(delegate.getPriority());
        }

        public AgentRail getDelegate() {
            return delegate;
        }

        @Override
        public void beforeInvoke(CallbackContext ctx) {
            run(delegate.beforeInvoke(agentContext(ctx)), ctx);
        }

        @Override
        public void afterInvoke(CallbackContext ctx) {
            run(delegate.afterInvoke(agentContext(ctx)), ctx);
        }

        @Override
        public void beforeModelCall(CallbackContext ctx) {
            run(delegate.beforeModelCall(agentContext(ctx)), ctx);
        }

        @Override
        public void afterModelCall(CallbackContext ctx) {
            run(delegate.afterModelCall(agentContext(ctx)), ctx);
        }

        @Override
        public void beforeToolCall(CallbackContext ctx) {
            run(delegate.beforeToolCall(agentContext(ctx)), ctx);
        }

        @Override
        public void afterToolCall(CallbackContext ctx) {
            run(delegate.afterToolCall(agentContext(ctx)), ctx);
        }

        @Override
        public void beforeTaskIteration(CallbackContext ctx) {
            run(delegate.beforeTaskIteration(agentContext(ctx)), ctx);
        }

        @Override
        public void afterTaskIteration(CallbackContext ctx) {
            run(delegate.afterTaskIteration(agentContext(ctx)), ctx);
        }

        private static AgentCallbackContext agentContext(CallbackContext ctx) {
            AgentCallbackContext bridged = new AgentCallbackContext();
            bridged.setInputs(ctx == null ? Map.of() : ctx.getValues());
            bridged.setExtra(ctx == null ? Map.of() : ctx.getValues());
            return bridged;
        }

        private static void run(CompletionStage<Void> stage, CallbackContext ctx) {
            if (stage != null) {
                stage.toCompletableFuture().join();
            }
            if (ctx != null) {
                ctx.getValues().put("agent_rail_bridge", true);
            }
        }
    }
}
