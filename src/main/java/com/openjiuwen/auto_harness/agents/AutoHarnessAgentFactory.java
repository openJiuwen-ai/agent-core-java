/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.agents;

import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.rails.ContextRail;
import com.openjiuwen.auto_harness.rails.EditSafetyRail;
import com.openjiuwen.auto_harness.rails.ExperienceRail;
import com.openjiuwen.auto_harness.rails.SecurityRail;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.rails.LspRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.cli.rails.ToolTrackingRail;
import com.openjiuwen.harness.tools.WebFetchWebpageTool;
import com.openjiuwen.harness.tools.WebFreeSearchTool;
import com.openjiuwen.harness.workspace.Workspace;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Logger;

/**
 * Auto Harness agent factories.
 * <p>
 * Provides factory methods for creating various DeepAgent instances used in
 * the auto-harness pipeline: implement, assess, plan, eval, select_pipeline,
 * pr_draft, and learnings agents.
 * <p>
 * Mirrors Python's {@code factory} module in
 * {@code openjiuwen.auto_harness.agents.factory}.
 */
public final class AutoHarnessAgentFactory {

    private static final Logger logger = Logger.getLogger(AutoHarnessAgentFactory.class.getName());

    private static final Path PACKAGE_DIR = resolvePackageDir();

    private static final String SKILLS_DIR = PACKAGE_DIR.resolve("skills").toString();
    private static final Path PROMPTS_DIR = PACKAGE_DIR.resolve("prompts");

    private AutoHarnessAgentFactory() {
        // Utility class - prevent instantiation
    }

    private static Path resolvePackageDir() {
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                cwd.resolve("src").resolve("main").resolve("resources").resolve("auto_harness"),
                cwd.resolve("agent-core-java-0.1.12")
                        .resolve("src").resolve("main").resolve("resources").resolve("auto_harness")
        );
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        URL resource = AutoHarnessAgentFactory.class.getClassLoader().getResource("auto_harness");
        if (resource != null && "file".equals(resource.getProtocol())) {
            try {
                return Path.of(resource.toURI());
            } catch (Exception ignored) {
                // Fall through to the module-layout default below.
            }
        }
        return candidates.get(0);
    }

    // ── SysOperation Builder ───────────────────────────────────────

    /**
     * Build a permissive local SysOperation for trusted auto-harness runs.
     *
     * @param agentName Name of the agent for the SysOperation ID
     * @return A configured SysOperation with local mode and no restrictions
     */
    public static SysOperation buildTrustedLocalSysOperation(String agentName) {
        SysOperationCard card = SysOperationCard.builder()
                .id(agentName + "_trusted_local")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder()
                        .shellAllowlist(null)
                        .build())
                .build();
        return new SysOperation(card);
    }

    // ── Main Agent Factory ───────────────────────────────────────

    /**
     * Create the main task implementation agent.
     *
     * @param config               The auto-harness configuration
     * @param workspaceOverride    Optional workspace override path
     * @param editSafetyRail       Optional edit safety rail
     * @param skillNames           Optional list of skill names to enable
     * @param enableTaskLoop       Whether to enable task loop
     * @param enableTaskPlanning   Whether to enable task planning
     * @param enableProgressRepeat Whether to enable progress repeat
     * @param extraRails           Additional rails to attach
     * @param extraTools           Additional tools to attach
     * @return Configured DeepAgent instance
     */
    public static DeepAgent createAutoHarnessAgent(
            AutoHarnessConfig config,
            String workspaceOverride,
            AgentRail editSafetyRail,
            List<String> skillNames,
            boolean enableTaskLoop,
            boolean enableTaskPlanning,
            boolean enableProgressRepeat,
            List<AgentRail> extraRails,
            List<Tool> extraTools) {

        List<AgentRail> rails = buildRails(config, editSafetyRail);

        List<String> skills = skillNames != null ? skillNames : List.of("implement", "verify", "communicate");
        rails.add(buildSkillRail(config, skills));

        if (enableTaskPlanning) {
            rails.add(new TaskPlanningRail());
        }

        if (extraRails != null) {
            rails.addAll(extraRails);
        }

        List<Tool> tools = new ArrayList<>();
        if (extraTools != null) {
            tools.addAll(extraTools);
        }

        String systemPrompt = buildAutoHarnessSystemPrompt(config);
        String workspace = workspaceOverride != null ? workspaceOverride : config.getWorkspace();

        DeepAgentConfig agentConfig = new DeepAgentConfig();
        agentConfig.setCard(createAgentCard("auto-harness", "自主优化 harness 框架的编码 agent"));
        agentConfig.setSystemPrompt(systemPrompt);
        
        agentConfig.setTools(toToolCards(tools));
        agentConfig.setRails(rails);
        agentConfig.setMaxIterations(resolveAgentIterations(config, "implement", 30));
        configureRuntime(agentConfig, config, workspace, "auto-harness");

        return HarnessFactory.createDeepAgent(agentConfig);
    }

    /**
     * Create the main task implementation agent with default settings.
     *
     * @param config The auto-harness configuration
     * @return Configured DeepAgent instance
     */
    public static DeepAgent createAutoHarnessAgent(AutoHarnessConfig config) {
        return createAutoHarnessAgent(config, null, null, null, true, true, true, null, null);
    }

    // ── Specialized Agent Factories ───────────────────────────────────────

    /**
     * Create the dedicated commit-stage agent.
     *
     * @param config            The auto-harness configuration
     * @param workspaceOverride Optional workspace override path
     * @return Configured DeepAgent instance for commits
     */
    public static DeepAgent createCommitAgent(AutoHarnessConfig config, String workspaceOverride) {
        return createAutoHarnessAgent(
                config,
                workspaceOverride,
                null,
                List.of("commit", "communicate"),
                false,
                false,
                false,
                null,
                null
        );
    }

    /**
     * Create the assessment-stage agent.
     *
     * @param config The auto-harness configuration
     * @return Configured DeepAgent instance for assessment
     */
    public static DeepAgent createAssessAgent(AutoHarnessConfig config) {
        String prompt = loadPrompt("assess.md");
        List<AgentRail> rails = buildReadonlyRails(config);
        rails.add(buildSkillRail(config, List.of("assess")));

        DeepAgentConfig agentConfig = new DeepAgentConfig();
        agentConfig.setCard(createAgentCard("auto-harness-assess", "评估代码库当前状态"));
        agentConfig.setSystemPrompt(prompt);
        agentConfig.setTools(toToolCards(buildResearchTools(config)));
        agentConfig.setRails(rails);
        agentConfig.setMaxIterations(resolveAgentIterations(config, "assess", 30));
        configureRuntime(agentConfig, config, config.getWorkspace(), "auto-harness-assess");

        return HarnessFactory.createDeepAgent(agentConfig);
    }

    /**
     * Create the planning-stage agent.
     *
     * @param config The auto-harness configuration
     * @return Configured DeepAgent instance for planning
     */
    public static DeepAgent createPlanAgent(AutoHarnessConfig config) {
        String prompt = loadPrompt("plan.md");
        List<AgentRail> rails = buildReadonlyRails(config);
        rails.add(buildSkillRail(config, List.of("plan")));

        DeepAgentConfig agentConfig = new DeepAgentConfig();
        agentConfig.setCard(createAgentCard("auto-harness-plan", "制定优化任务列表"));
        agentConfig.setSystemPrompt(prompt);
        agentConfig.setTools(toToolCards(buildResearchTools(config)));
        agentConfig.setRails(rails);
        agentConfig.setMaxIterations(resolveAgentIterations(config, "plan", 15));
        configureRuntime(agentConfig, config, config.getWorkspace(), "auto-harness-plan");

        return HarnessFactory.createDeepAgent(agentConfig);
    }

    /**
     * Create the evaluator agent used by the verify fix loop.
     *
     * @param config The auto-harness configuration
     * @return Configured DeepAgent instance for evaluation
     */
    public static DeepAgent createEvalAgent(AutoHarnessConfig config) {
        String prompt = loadPrompt("evaluate.md");
        List<AgentRail> rails = buildReadonlyRails(config);
        rails.add(buildSkillRail(config, List.of("verify")));

        DeepAgentConfig agentConfig = new DeepAgentConfig();
        agentConfig.setCard(createAgentCard("auto-harness-eval", "评审代码变更质量"));
        agentConfig.setSystemPrompt(prompt);
        agentConfig.setRails(rails);
        agentConfig.setMaxIterations(resolveAgentIterations(config, "eval", 10));
        configureRuntime(agentConfig, config, config.getWorkspace(), "auto-harness-eval");

        return HarnessFactory.createDeepAgent(agentConfig);
    }

    /**
     * Create the pipeline-selection agent.
     *
     * @param config The auto-harness configuration
     * @return Configured DeepAgent instance for pipeline selection
     */
    public static DeepAgent createSelectPipelineAgent(AutoHarnessConfig config) {
        String prompt = loadPrompt("select_pipeline.md");
        List<AgentRail> rails = buildReadonlyRails(config);
        rails.add(buildSkillRail(config, List.of("select_pipeline")));

        DeepAgentConfig agentConfig = new DeepAgentConfig();
        agentConfig.setCard(createAgentCard("auto-harness-select-pipeline", "选择最合适的优化流水线"));
        agentConfig.setSystemPrompt(prompt);
        agentConfig.setTools(toToolCards(buildResearchTools(config)));
        agentConfig.setRails(rails);
        agentConfig.setMaxIterations(resolveAgentIterations(config, "select_pipeline", 10));
        configureRuntime(agentConfig, config, config.getWorkspace(), "auto-harness-select-pipeline");

        return HarnessFactory.createDeepAgent(agentConfig);
    }

    /**
     * Create the communicate-only agent used for PR drafts.
     *
     * @param config            The auto-harness configuration
     * @param workspaceOverride Optional workspace override path
     * @return Configured DeepAgent instance for PR drafts
     */
    public static DeepAgent createPrDraftAgent(AutoHarnessConfig config, String workspaceOverride) {
        String prompt = loadPrompt("pr_draft.md");
        String workspace = workspaceOverride != null ? workspaceOverride : config.getWorkspace();
        List<AgentRail> rails = buildReadonlyRails(config);
        rails.add(buildSkillRail(config, List.of("communicate")));

        DeepAgentConfig agentConfig = new DeepAgentConfig();
        agentConfig.setCard(createAgentCard("auto-harness-pr-draft", "根据任务事实生成 GitCode PR draft"));
        agentConfig.setSystemPrompt(prompt);
        agentConfig.setRails(rails);
        agentConfig.setMaxIterations(resolveAgentIterations(config, "pr_draft", 5));
        configureRuntime(agentConfig, config, workspace, "auto-harness-pr-draft");

        return HarnessFactory.createDeepAgent(agentConfig);
    }

    /**
     * Create the session learnings agent.
     *
     * @param config            The auto-harness configuration
     * @param sessionResults    The session results string
     * @param existingMemories  The existing memories string
     * @return Configured DeepAgent instance for learnings extraction
     */
    public static DeepAgent createLearningsAgent(
            AutoHarnessConfig config,
            String sessionResults,
            String existingMemories) {

        String template = loadPrompt("learnings.md");
        String prompt = renderPrompt(template,
                "session_results", sessionResults != null ? sessionResults : "",
                "existing_memories", existingMemories != null ? existingMemories : "");

        List<AgentRail> rails = buildReadonlyRails(config);
        rails.add(buildSkillRail(config, List.of("communicate")));

        DeepAgentConfig agentConfig = new DeepAgentConfig();
        agentConfig.setCard(createAgentCard("auto-harness-learnings", "反思 session 结果并提取经验"));
        agentConfig.setSystemPrompt(prompt);
        agentConfig.setRails(rails);
        agentConfig.setMaxIterations(resolveAgentIterations(config, "learnings", 5));
        configureRuntime(agentConfig, config, config.getWorkspace(), "auto-harness-learnings");

        return HarnessFactory.createDeepAgent(agentConfig);
    }

    // ── Rail Builders ───────────────────────────────────────

    /**
     * Build the standard rails for writable task stages.
     *
     * @param config          The auto-harness configuration
     * @param editSafetyRail  Optional edit safety rail override
     * @return List of configured rails
     */
    private static List<AgentRail> buildRails(AutoHarnessConfig config, AgentRail editSafetyRail) {
        List<AgentRail> rails = new ArrayList<>();

        rails.add(new ToolTrackingRail());
        rails.add(new SysOperationRail());
        rails.add(new ContextRail(true));
        rails.add(new LspRail());
        rails.add(new ExperienceRail(config.getResolvedExperienceDir(), config.getLanguage()));
        rails.add(new SecurityRail(config.resolveImmutableFiles(), config.getHighImpactPrefixes()));
        rails.add(editSafetyRail != null ? editSafetyRail : new EditSafetyRail());

        return rails;
    }

    /**
     * Build readonly rails for assess/plan/eval style stages.
     *
     * @param config The auto-harness configuration
     * @return List of configured readonly rails
     */
    private static List<AgentRail> buildReadonlyRails(AutoHarnessConfig config) {
        List<AgentRail> rails = new ArrayList<>();

        rails.add(new ToolTrackingRail());
        rails.add(new SysOperationRail());
        rails.add(new ContextRail(true));
        rails.add(new LspRail());
        rails.add(new ExperienceRail(config.getResolvedExperienceDir(), config.getLanguage()));

        return rails;
    }

    /**
     * Build a skill rail from package-local and configured skill roots.
     *
     * @param config     The auto-harness configuration
     * @param skillNames List of skill names to enable
     * @return Configured skill rail
     */
    private static AgentRail buildSkillRail(AutoHarnessConfig config, List<String> skillNames) {
        List<String> skillsDir = new ArrayList<>();
        List<String> requestedSkills = skillNames != null ? skillNames : List.of();
        List<String> roots = new ArrayList<>();
        roots.add(SKILLS_DIR);
        roots.addAll(config.getSkillsDirs());
        for (String root : roots) {
            if (root == null || root.isBlank()) {
                continue;
            }
            Path rootPath = Path.of(root);
            if (!Files.isDirectory(rootPath)) {
                continue;
            }
            boolean hasRequestedSkill = requestedSkills.stream()
                    .anyMatch(skillName -> Files.isDirectory(rootPath.resolve(skillName)));
            if (hasRequestedSkill) {
                skillsDir.add(rootPath.toString());
            }
        }

        LinkedHashSet<String> enabledSkills = new LinkedHashSet<>();
        for (String skillName : requestedSkills) {
            for (String root : skillsDir) {
                if (Files.isDirectory(Path.of(root).resolve(skillName))) {
                    enabledSkills.add(skillName);
                    break;
                }
            }
        }

        return new SkillUseRail(
                skillsDir,
                SkillUseRail.SKILL_MODE_ALL,
                true,
                true,
                enabledSkills,
                null
        );
    }

    // ── Helper Methods ───────────────────────────────────────

    private static List<Tool> buildResearchTools(AutoHarnessConfig config) {
        return List.of(new WebFreeSearchTool("en"), new WebFetchWebpageTool());
    }

    private static List<ToolCard> toToolCards(List<Tool> tools) {
        List<ToolCard> cards = new ArrayList<>();
        if (tools == null) {
            return cards;
        }
        for (Tool tool : tools) {
            if (tool != null && tool.getCard() != null) {
                cards.add(tool.getCard());
            }
        }
        return cards;
    }

    private static void configureRuntime(
            DeepAgentConfig agentConfig,
            AutoHarnessConfig config,
            String workspace,
            String agentName) {
        String resolvedWorkspace = workspace != null ? workspace : "";
        agentConfig.setWorkspace(new Workspace(resolvedWorkspace, config.getLanguage()));
        SysOperation sysOperation = buildTrustedLocalSysOperation(agentName);
        agentConfig.setSysOperation(sysOperation);
        agentConfig.setSysOperationId(agentName + "_trusted_local");
    }

    /**
     * Build the auto-harness system prompt.
     *
     * @param config The auto-harness configuration
     * @return The system prompt string
     */
    private static String buildAutoHarnessSystemPrompt(AutoHarnessConfig config) {
        StringBuilder sb = new StringBuilder();

        // Load CI gate rules
        String ciGateRules = loadCiGateRules(config);
        if (!ciGateRules.isEmpty()) {
            sb.append("CI Gate Rules:\n").append(ciGateRules).append("\n\n");
        }

        // Add other prompt sections as needed
        return sb.toString();
    }

    /**
     * Load CI gate rules text for prompt construction.
     *
     * @param config The auto-harness configuration
     * @return CI gate rules text or empty string
     */
    private static String loadCiGateRules(AutoHarnessConfig config) {
        Path path;
        if (config.getCiGateConfig() != null && !config.getCiGateConfig().isBlank()) {
            path = Path.of(config.getCiGateConfig());
        } else {
            path = PACKAGE_DIR.resolve("resources").resolve("ci_gate.yaml");
        }

        if (Files.exists(path)) {
            try {
                return Files.readString(path);
            } catch (IOException e) {
                logger.warning("Failed to load CI gate rules: " + e.getMessage());
            }
        }
        return "";
    }

    /**
     * Load a prompt template from the package prompt directory.
     *
     * @param filename The prompt filename
     * @return The prompt template content
     */
    private static String loadPrompt(String filename) {
        Path path = PROMPTS_DIR.resolve(filename);
        if (Files.exists(path)) {
            try {
                return Files.readString(path);
            } catch (IOException e) {
                logger.warning("Failed to load prompt " + filename + ": " + e.getMessage());
            }
        }
        return "";
    }

    /**
     * Render simple template variables without touching JSON braces.
     *
     * @param template The template string
     * @param values   Key-value pairs for replacement
     * @return The rendered string
     */
    private static String renderPrompt(String template, String... values) {
        if (template == null || values == null || values.length % 2 != 0) {
            return template != null ? template : "";
        }

        String rendered = template;
        for (int i = 0; i < values.length; i += 2) {
            String key = values[i];
            String value = values[i + 1];
            if (key != null && value != null) {
                rendered = rendered.replace("{" + key + "}", value);
            }
        }
        return rendered;
    }

    /**
     * Resolve agent iterations from config with fallback.
     *
     * @param config     The auto-harness configuration
     * @param agentName  The agent name
     * @param defaultVal Default value if not configured
     * @return The resolved iterations count
     */
    private static int resolveAgentIterations(AutoHarnessConfig config, String agentName, int defaultVal) {
        if (config == null) {
            return defaultVal;
        }
        return config.resolveAgentIterations(agentName, defaultVal);
    }

    /**
     * Create an AgentCard with name and description.
     *
     * @param name        The agent name
     * @param description The agent description
     * @return Configured AgentCard
     */
    private static AgentCard createAgentCard(String name, String description) {
        return AgentCard.builder()
                .name(name)
                .description(description)
                .build();
    }
}
