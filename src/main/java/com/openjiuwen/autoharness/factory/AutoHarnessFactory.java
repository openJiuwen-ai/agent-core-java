/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.factory;

import com.openjiuwen.autoharness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.autoharness.rails.AutoHarnessContextRail;
import com.openjiuwen.autoharness.rails.AutoHarnessExperienceRail;
import com.openjiuwen.autoharness.rails.EditSafetyRail;
import com.openjiuwen.autoharness.rails.SecurityRail;
import com.openjiuwen.autoharness.schema.AutoHarnessConfig;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.rails.LspRail;
import com.openjiuwen.harness.rails.SkillUseRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.rails.ToolTrackingRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.subagents.BrowserAgentFactory;
import com.openjiuwen.harness.subagents.ExploreAgentFactory;
import com.openjiuwen.harness.subagents.SubAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AutoHarnessFactory.
 * 
 * @since 0.1.7
 */
public final class AutoHarnessFactory {
    private static final String PACKAGE_SKILLS_RESOURCE = "openjiuwen/auto_harness/skills";
    private static final String PACKAGE_PROMPTS_RESOURCE = "openjiuwen/auto_harness/prompts/";
    private static final String CI_GATE_RESOURCE = "com/openjiuwen/autoharness/resources/ci_gate.yaml";

    /**
     * AutoHarnessFactory.
     * 
     * @since 0.1.7
     */
    private AutoHarnessFactory() {
    }

    /**
     * createAutoHarnessOrchestrator.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static AutoHarnessOrchestrator createAutoHarnessOrchestrator(AutoHarnessConfig config) {
        return new AutoHarnessOrchestrator(config);
    }

    /**
     * createAutoHarnessAgent.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createAutoHarnessAgent(AutoHarnessConfig config) {
        return createAutoHarnessAgent(config, null, null, null, null, null, true, true, true);
    }

    /**
     * createAutoHarnessAgent.
     * 
     * @param config config
     * @param workspaceOverride workspaceOverride
     * @param editSafetyRail editSafetyRail
     * @param skillNames skillNames
     * @param extraRails extraRails
     * @param extraTools extraTools
     * @param isTaskLoopEnabled isTaskLoopEnabled
     * @param isTaskPlanningEnabled isTaskPlanningEnabled
     * @param isProgressRepeatEnabled isProgressRepeatEnabled
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createAutoHarnessAgent(AutoHarnessConfig config, String workspaceOverride,
            EditSafetyRail editSafetyRail, List<String> skillNames, List<Object> extraRails, List<Object> extraTools,
            boolean isTaskLoopEnabled, boolean isTaskPlanningEnabled, boolean isProgressRepeatEnabled) {
        AutoHarnessConfig effective = config != null ? config : AutoHarnessConfig.builder().build();
        String workspace =
            workspaceOverride != null && !workspaceOverride.isBlank() ? workspaceOverride : effective.getWorkspace();
        List<Object> rails = buildRails(effective, editSafetyRail);
        List<String> effectiveSkillNames =
            skillNames != null ? skillNames : List.of("implement", "verify", "communicate");
        rails.add(buildSkillRail(effective, effectiveSkillNames));
        if (isTaskPlanningEnabled) {
            rails.add(new TaskPlanningRail(isProgressRepeatEnabled, 20));
        }
        if (extraRails != null) {
            rails.addAll(extraRails);
        }
        DeepAgentConfig deepConfig = DeepAgentConfig.builder().systemPrompt(buildAutoHarnessSystemPrompt(effective))
                .model(effective.getModel()).language(resolveLanguage(effective)).workspacePath(workspace)
                .isTaskLoopEnabled(isTaskLoopEnabled).isTaskPlanningEnabled(isTaskPlanningEnabled)
                .isAsyncSubagentEnabled(true).maxIterations(effective.resolveAgentIterations("implement", 30))
                .tools(extraTools != null ? new ArrayList<>(extraTools) : List.of()).rails(rails)
                .subagents(buildSubagents(effective, workspace))
                .sysOperation(buildTrustedLocalSysOperation("auto-harness", workspace)).restrictToWorkDir(false)
                .build();
        return HarnessFactory.createDeepAgent(
                AgentCard.builder().name("auto-harness").description("自主优化 harness 框架的编码 agent").build(), deepConfig,
                Workspace.builder().rootPath(workspace).language(resolveLanguage(effective)).build());
    }

    /**
     * createCommitAgent.
     * 
     * @param config config
     * @param workspaceOverride workspaceOverride
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createCommitAgent(AutoHarnessConfig config, String workspaceOverride) {
        return createAutoHarnessAgent(config, workspaceOverride, null, List.of("commit", "communicate"), null, null,
                false, false, false);
    }

    /**
     * createAssessAgent.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createAssessAgent(AutoHarnessConfig config) {
        return createReadonlyAgent(config, "auto-harness-assess", "评估代码库当前状态", "assess", true);
    }

    /**
     * createPlanAgent.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createPlanAgent(AutoHarnessConfig config) {
        return createReadonlyAgent(config, "auto-harness-plan", "制定优化任务列表", "plan", true);
    }

    /**
     * createSelectPipelineAgent.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createSelectPipelineAgent(AutoHarnessConfig config) {
        return createReadonlyAgent(config, "auto-harness-select-pipeline", "选择最合适的优化流水线", "select_pipeline", true);
    }

    /**
     * createEvalAgent.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createEvalAgent(AutoHarnessConfig config) {
        return createReadonlyAgent(config, "auto-harness-eval", "评审代码变更质量", "verify", false);
    }

    /**
     * createPrDraftAgent.
     * 
     * @param config config
     * @param workspaceOverride workspaceOverride
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createPrDraftAgent(AutoHarnessConfig config, String workspaceOverride) {
        AutoHarnessConfig effective = config != null ? config : AutoHarnessConfig.builder().build();
        String workspace =
            workspaceOverride != null && !workspaceOverride.isBlank() ? workspaceOverride : effective.getWorkspace();
        List<Object> rails = buildReadonlyRails(effective);
        rails.add(buildSkillRail(effective, List.of("communicate")));
        return createAgent(effective, "auto-harness-pr-draft", "根据任务事实生成 GitCode PR draft", workspace, rails, List.of(),
                false, false, effective.resolveAgentIterations("pr_draft", 5), loadPrompt("pr_draft.md"), false);
    }

    /**
     * createLearningsAgent.
     * 
     * @param config config
     * @param sessionResults sessionResults
     * @param existingMemories existingMemories
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createLearningsAgent(AutoHarnessConfig config, String sessionResults,
            String existingMemories) {
        AutoHarnessConfig effective = config != null ? config : AutoHarnessConfig.builder().build();
        List<Object> rails = buildReadonlyRails(effective);
        rails.add(buildSkillRail(effective, List.of("communicate")));
        String prompt =
            loadPrompt("learnings.md").replace("{session_results}", sessionResults != null ? sessionResults : "")
                    .replace("{existing_memories}", existingMemories != null ? existingMemories : "");
        return createAgent(effective, "auto-harness-learnings", "反思 session 结果并提取经验", effective.getWorkspace(), rails,
                List.of(), false, false, effective.resolveAgentIterations("learnings", 5), prompt, false);
    }

    /**
     * buildRails.
     * 
     * @param config config
     * @param editSafetyRail editSafetyRail
     * @return the result
     * @since 0.1.7
     */
    public static List<Object> buildRails(AutoHarnessConfig config, EditSafetyRail editSafetyRail) {
        AutoHarnessConfig effective = config != null ? config : AutoHarnessConfig.builder().build();
        return new ArrayList<>(
                List.of(new ToolTrackingRail(), new SysOperationRail(), new AutoHarnessContextRail(true), new LspRail(),
                        new AutoHarnessExperienceRail(effective.experiencePath().toString(),
                                resolveLanguage(effective)),
                        new SecurityRail(effective.resolveImmutableFiles(), effective.getHighImpactPrefixes()),
                        editSafetyRail != null ? editSafetyRail : new EditSafetyRail()));
    }

    /**
     * buildReadonlyRails.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static List<Object> buildReadonlyRails(AutoHarnessConfig config) {
        AutoHarnessConfig effective = config != null ? config : AutoHarnessConfig.builder().build();
        return new ArrayList<>(List.of(new ToolTrackingRail(), new SysOperationRail(), new AutoHarnessContextRail(true),
                new LspRail(),
                new AutoHarnessExperienceRail(effective.experiencePath().toString(), resolveLanguage(effective))));
    }

    /**
     * createReadonlyAgent.
     * 
     * @param config config
     * @param name name
     * @param description description
     * @param skillName skillName
     * @param isResearchToolsEnabled isResearchToolsEnabled
     * @return the result
     * @since 0.1.7
     */
    private static DeepAgent createReadonlyAgent(AutoHarnessConfig config, String name, String description,
            String skillName, boolean isResearchToolsEnabled) {
        AutoHarnessConfig effective = config != null ? config : AutoHarnessConfig.builder().build();
        List<Object> rails = buildReadonlyRails(effective);
        rails.add(buildSkillRail(effective, List.of(skillName)));
        return createAgent(effective, name, description, effective.getWorkspace(), rails,
                isResearchToolsEnabled ? buildResearchTools() : List.of(), true, false,
                resolveIterations(effective, skillName), loadPrompt(skillNameToPrompt(skillName)));
    }

    /**
     * createAgent.
     * 
     * @param config config
     * @param name name
     * @param description description
     * @param workspace workspace
     * @param rails rails
     * @param tools tools
     * @param isAsyncSubagentEnabled isAsyncSubagentEnabled
     * @param isTaskPlanningEnabled isTaskPlanningEnabled
     * @param maxIterations maxIterations
     * @return the result
     * @since 0.1.7
     */
    private static DeepAgent createAgent(AutoHarnessConfig config, String name, String description, String workspace,
            List<Object> rails, List<Object> tools, boolean isAsyncSubagentEnabled, boolean isTaskPlanningEnabled,
            int maxIterations) {
        return createAgent(config, name, description, workspace, rails, tools, isAsyncSubagentEnabled,
                isTaskPlanningEnabled, maxIterations, "");
    }

    /**
     * createAgent.
     * 
     * @param config config
     * @param name name
     * @param description description
     * @param workspace workspace
     * @param rails rails
     * @param tools tools
     * @param isAsyncSubagentEnabled isAsyncSubagentEnabled
     * @param isTaskPlanningEnabled isTaskPlanningEnabled
     * @param maxIterations maxIterations
     * @param systemPrompt systemPrompt
     * @return the result
     * @since 0.1.7
     */
    private static DeepAgent createAgent(AutoHarnessConfig config, String name, String description, String workspace,
            List<Object> rails, List<Object> tools, boolean isAsyncSubagentEnabled, boolean isTaskPlanningEnabled,
            int maxIterations, String systemPrompt) {
        return createAgent(config, name, description, workspace, rails, tools, isAsyncSubagentEnabled,
                isTaskPlanningEnabled, maxIterations, systemPrompt, true);
    }

    /**
     * createAgent.
     * 
     * @param config config
     * @param name name
     * @param description description
     * @param workspace workspace
     * @param rails rails
     * @param tools tools
     * @param isAsyncSubagentEnabled isAsyncSubagentEnabled
     * @param isTaskPlanningEnabled isTaskPlanningEnabled
     * @param maxIterations maxIterations
     * @param systemPrompt systemPrompt
     * @param isSubagentsIncluded isSubagentsIncluded
     * @return the result
     * @since 0.1.7
     */
    private static DeepAgent createAgent(AutoHarnessConfig config, String name, String description, String workspace,
            List<Object> rails, List<Object> tools, boolean isAsyncSubagentEnabled, boolean isTaskPlanningEnabled,
            int maxIterations, String systemPrompt, boolean isSubagentsIncluded) {
        DeepAgentConfig deepConfig = DeepAgentConfig.builder().systemPrompt(systemPrompt).model(config.getModel())
                .language(resolveLanguage(config)).workspacePath(workspace)
                .isAsyncSubagentEnabled(isAsyncSubagentEnabled).isTaskPlanningEnabled(isTaskPlanningEnabled)
                .maxIterations(maxIterations).tools(new ArrayList<>(tools)).rails(rails)
                .subagents(isSubagentsIncluded ? buildSubagents(config, workspace) : List.of())
                .sysOperation(buildTrustedLocalSysOperation(name, workspace)).restrictToWorkDir(false).build();
        return HarnessFactory.createDeepAgent(AgentCard.builder().name(name).description(description).build(),
                deepConfig, Workspace.builder().rootPath(workspace).language(resolveLanguage(config)).build());
    }

    /**
     * buildSubagents.
     * 
     * @param config config
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    private static List<Object> buildSubagents(AutoHarnessConfig config, String workspace) {
        List<Object> subagents = new ArrayList<>();
        String language = resolveLanguage(config);
        subagents.add(ExploreAgentFactory.buildExploreAgentConfig(language,
                java.util.Map.of("max_iterations", config.resolveAgentIterations("explore_subagent", 20))));
        try {
            subagents.add(BrowserAgentFactory.buildBrowserAgentConfig(
                    com.openjiuwen.harness.tools.browser.BrowserRuntimeSettings.builder().build(), language,
                    java.util.Map.of("max_iterations", config.resolveAgentIterations("browser_subagent", 20))));
        } catch (Exception ignored) {
            // Python skips browser subagent when browser dependencies are unavailable.
        }
        for (Object subagent : subagents) {
            if (subagent instanceof SubAgentConfig spec) {
                spec.setWorkspacePath(workspace);
            }
        }
        return subagents;
    }

    /**
     * buildSkillRail.
     * 
     * @param config config
     * @param skillNames skillNames
     * @return the result
     * @since 0.1.7
     */
    private static SkillUseRail buildSkillRail(AutoHarnessConfig config, List<String> skillNames) {
        List<String> requestedSkills = skillNames != null ? skillNames : List.of();
        List<String> candidateRoots = new ArrayList<>();
        candidateRoots.add(packageSkillsDir());
        if (config != null && config.getSkillsDirs() != null) {
            candidateRoots.addAll(config.getSkillsDirs());
        }
        List<Path> roots = new ArrayList<>();
        List<String> skillDirectories = new ArrayList<>();
        for (String root : candidateRoots) {
            if (root == null || root.isBlank()) {
                continue;
            }
            Path rootPath = Path.of(root).toAbsolutePath().normalize();
            if (!Files.isDirectory(rootPath)) {
                continue;
            }
            boolean hasRequestedSkill = requestedSkills.stream()
                    .anyMatch(skillName -> skillName != null && Files.isDirectory(rootPath.resolve(skillName)));
            if (!hasRequestedSkill) {
                continue;
            }
            roots.add(rootPath);
            skillDirectories.add(rootPath.toString());
        }
        Set<String> enabledSkills = new LinkedHashSet<>();
        for (String skillName : requestedSkills) {
            if (skillName == null || skillName.isBlank()) {
                continue;
            }
            String normalized = skillName.trim();
            boolean isAvailable = roots.stream().anyMatch(root -> Files.isDirectory(root.resolve(normalized)));
            if (isAvailable) {
                enabledSkills.add(normalized);
            }
        }
        return new SkillUseRail(skillDirectories, "all", List.copyOf(enabledSkills), List.of());
    }

    /**
     * buildResearchTools.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static List<Object> buildResearchTools() {
        return List.of(new com.openjiuwen.harness.tools.WebFreeSearchTool("en"),
                new com.openjiuwen.harness.tools.WebFetchWebpageTool("en"));
    }

    /**
     * buildAutoHarnessSystemPrompt.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static String buildAutoHarnessSystemPrompt(AutoHarnessConfig config) {
        return buildAutoHarnessSystemPrompt(config, "");
    }

    /**
     * buildAutoHarnessSystemPrompt.
     * 
     * @param config config
     * @param wisdom wisdom
     * @return the result
     * @since 0.1.7
     */
    public static String buildAutoHarnessSystemPrompt(AutoHarnessConfig config, String wisdom) {
        SystemPromptBuilder builder = new SystemPromptBuilder(resolveLanguage(config));
        String identity = loadPrompt("identity.md");
        builder.addSection(
                new PromptSection("auto_harness_identity", java.util.Map.of("cn", identity, "en", identity), 10));
        String ciGateRules = loadCiGateRules(config);
        if (!ciGateRules.isBlank()) {
            builder.addSection(new PromptSection("auto_harness_ci_gate",
                    java.util.Map.of("cn", "## CI 门控规则\n\n" + ciGateRules, "en", "## CI Gate Rules\n\n" + ciGateRules),
                    20));
        }
        if (wisdom != null && !wisdom.isBlank()) {
            builder.addSection(new PromptSection("auto_harness_wisdom",
                    java.util.Map.of("cn", "## 经验库\n\n" + wisdom, "en", "## Experience Library\n\n" + wisdom), 30));
        }
        return builder.build();
    }

    /**
     * resolveLanguage.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private static String resolveLanguage(AutoHarnessConfig config) {
        if (config == null || config.getLanguage() == null || config.getLanguage().isBlank()) {
            return "cn";
        }
        return config.getLanguage();
    }

    /**
     * buildTrustedLocalSysOperation.
     * 
     * @param agentName agentName
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    private static SysOperation buildTrustedLocalSysOperation(String agentName, String workspace) {
        SysOperationCard card = SysOperationCard.builder().id(agentName + "_trusted_local")
                .name(agentName + "_trusted_local").mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig
                        .builder().workDir(Path.of(workspace == null || workspace.isBlank() ? "." : workspace)
                                .toAbsolutePath().normalize().toString())
                        .shellAllowlist(null).restrictToSandbox(false).build())
                .build();
        return new SysOperation(card);
    }

    /**
     * resolveIterations.
     * 
     * @param config config
     * @param skillName skillName
     * @return the result
     * @since 0.1.7
     */
    private static int resolveIterations(AutoHarnessConfig config, String skillName) {
        return switch (skillName) {
            case "plan" -> config.resolveAgentIterations("plan", 15);
            case "select_pipeline" -> config.resolveAgentIterations("select_pipeline", 10);
            case "eval", "verify" -> config.resolveAgentIterations("eval", 10);
            case "communicate" -> config.resolveAgentIterations("communicate", 5);
            default -> config.resolveAgentIterations(skillName, 30);
        };
    }

    /**
     * packageSkillsDir.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static String packageSkillsDir() {
        URL resource = AutoHarnessFactory.class.getClassLoader().getResource(PACKAGE_SKILLS_RESOURCE);
        if (resource == null) {
            return PACKAGE_SKILLS_RESOURCE;
        }
        try {
            return Path.of(resource.toURI()).toString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return PACKAGE_SKILLS_RESOURCE;
        }
    }

    /**
     * loadPrompt.
     * 
     * @param filename filename
     * @return the result
     * @since 0.1.7
     */
    private static String loadPrompt(String filename) {
        try (var stream =
            AutoHarnessFactory.class.getClassLoader().getResourceAsStream(PACKAGE_PROMPTS_RESOURCE + filename)) {
            if (stream == null) {
                return "";
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * loadCiGateRules.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private static String loadCiGateRules(AutoHarnessConfig config) {
        if (config != null && config.getCiGateConfig() != null && !config.getCiGateConfig().isBlank()) {
            try {
                return java.nio.file.Files.readString(Path.of(config.getCiGateConfig()), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                return "";
            }
        }
        try (var stream = AutoHarnessFactory.class.getClassLoader().getResourceAsStream(CI_GATE_RESOURCE)) {
            if (stream == null) {
                return "";
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * skillNameToPrompt.
     * 
     * @param skillName skillName
     * @return the result
     * @since 0.1.7
     */
    private static String skillNameToPrompt(String skillName) {
        return switch (skillName) {
            case "plan" -> "plan.md";
            case "select_pipeline" -> "select_pipeline.md";
            case "verify", "eval" -> "evaluate.md";
            case "communicate" -> "pr_draft.md";
            default -> "assess.md";
        };
    }
}
