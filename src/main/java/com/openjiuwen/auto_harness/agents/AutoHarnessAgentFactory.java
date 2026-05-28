/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.agents;

import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    private static final Path PACKAGE_DIR = Path.of(System.getProperty("user.dir", "."))
            .resolve("agent-core-java-0.1.12")
            .resolve("src")
            .resolve("main")
            .resolve("resources")
            .resolve("auto_harness");

    private static final String SKILLS_DIR = PACKAGE_DIR.resolve("skills").toString();
    private static final Path PROMPTS_DIR = PACKAGE_DIR.resolve("prompts");

    private AutoHarnessAgentFactory() {
        // Utility class - prevent instantiation
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
            // TaskPlanningRail would be added here if available
            // For now, we skip this as it requires additional implementation
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
        
        // Convert tools to ToolCards
        List<ToolCard> toolCards = new ArrayList<>();
        for (Tool t : tools) {
            toolCards.add(t.getCard());
        }
        agentConfig.setTools(toolCards);
        agentConfig.setRails(rails);
        agentConfig.setMaxIterations(resolveAgentIterations(config, "implement", 30));

        // Note: subagents, workspace, language, sysOperation would be set via reflection
        // or additional config methods in a full implementation

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
        agentConfig.setRails(rails);
        agentConfig.setMaxIterations(resolveAgentIterations(config, "assess", 30));

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
        agentConfig.setRails(rails);
        agentConfig.setMaxIterations(resolveAgentIterations(config, "plan", 15));

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
        agentConfig.setRails(rails);
        agentConfig.setMaxIterations(resolveAgentIterations(config, "select_pipeline", 10));

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

        // Standard rails would be added here:
        // - ToolTrackingRail
        // - SysOperationRail
        // - AutoHarnessContextRail
        // - LspRail
        // - AutoHarnessExperienceRail
        // - SecurityRail
        // - EditSafetyRail (or override)

        // For now, we add a placeholder for the edit safety rail
        if (editSafetyRail != null) {
            rails.add(editSafetyRail);
        }

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

        // Readonly rails would be added here:
        // - ToolTrackingRail
        // - SysOperationRail
        // - AutoHarnessContextRail
        // - LspRail
        // - AutoHarnessExperienceRail

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
        // In a full implementation, this would create a SkillUseRail
        // with skills_dir and enabled_skills
        // For now, return null as skill rails require additional infrastructure
        return null;
    }

    // ── Helper Methods ───────────────────────────────────────

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
     * Render simple placeholders without touching JSON braces.
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
        AgentCard card = new AgentCard();
        try {
            // Use reflection to set fields since AgentCard may not have setters
            java.lang.reflect.Field nameField = AgentCard.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(card, name);

            java.lang.reflect.Field descField = AgentCard.class.getDeclaredField("description");
            descField.setAccessible(true);
            descField.set(card, description);
        } catch (Exception e) {
            logger.warning("Failed to set AgentCard fields: " + e.getMessage());
        }
        return card;
    }
}
