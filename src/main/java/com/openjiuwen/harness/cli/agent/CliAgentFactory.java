/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.cli.rails.TokenTrackingRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.AskUserRail;
import com.openjiuwen.harness.rails.ConfirmInterruptRail;
import com.openjiuwen.harness.rails.SkillUseRail;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CLI agent factory — creates agents from CLI config.
 * <p>
 * Mirrors Python's {@code factory} in
 * {@code openjiuwen.harness.cli.agent.factory}.
 * <p>
 * Provides:
 * <ul>
 *   <li>{@link #createAgent(CliAgentConfig)} — build a DeepAgent with rails</li>
 *   <li>{@link LocalBackend} — direct SDK Runner backend (MVP)</li>
 *   <li>{@link #createBackend(CliAgentConfig)} — backend factory</li>
 * </ul>
 */
public final class CliAgentFactory {

    private CliAgentFactory() {
    }

    /**
     * Create an agent from the given CLI config.
     * <p>
     * Mirrors Python's {@code create_agent} function.
     *
     * @param cfg CLI configuration providing provider, model, api_key, etc.
     * @return AgentResult containing the DeepAgent and TokenTrackingRail
     */
    public static AgentResult createAgent(CliAgentConfig cfg) {
        // Initialize model from config
        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setProvider(cfg.getProvider());
        modelConfig.setModelName(cfg.getModel());
        modelConfig.setApiKey(cfg.getApiKey());
        modelConfig.setApiBase(cfg.getApiBase());
        modelConfig.setMaxTokens(cfg.getMaxTokens());
        
        Model model = new Model(modelConfig);

        // Build system prompt using CliPromptBuilder
        String systemPrompt = CliPromptBuilder.buildSystemPrompt(
            cfg.getCwd(),
            cfg.getModel(),
            cfg.getProvider()
        );

        // Create token tracker
        TokenTrackingRail tracker = new TokenTrackingRail();

        // Build rails list
        List<Object> rails = new ArrayList<>();
        rails.add(tracker);
        
        // SysOperationRail for file system tools
        SysOperationRail fsRail = new SysOperationRail();
        rails.add(fsRail);

        // AskUserRail for user interaction
        rails.add(new AskUserRail());

        // ConfirmInterruptRail for dangerous operations
        ConfirmInterruptRail confirmRail = new ConfirmInterruptRail();
        confirmRail.setToolNames(List.of("bash", "write_file", "edit_file"));
        rails.add(confirmRail);

        // SkillUseRail for skill loading
        SkillUseRail skillRail = new SkillUseRail();
        skillRail.setSkillsDir(getDefaultSkillDirs());
        skillRail.setSkillMode("all");
        skillRail.setIncludeTools(false);
        rails.add(skillRail);

        // Build CLI workspace
        Workspace workspace = buildCliWorkspace(cfg, "en");

        // Build web tools
        List<Object> tools = createWebTools("en");

        // Build subagents (code_agent, research_agent, browser_agent)
        List<Object> subagents = buildSubagents(model);

        // Create agent card
        AgentCard card = new AgentCard();
        card.setName("cli-agent");
        card.setDescription("CLI DeepAgent for task execution");

        // Create DeepAgent
        DeepAgent agent = new DeepAgent(card);
        
        DeepAgentConfig deepConfig = new DeepAgentConfig();
        deepConfig.setCard(card);
        deepConfig.setModel(model);
        deepConfig.setSystemPrompt(systemPrompt);
        deepConfig.setTools(tools);
        deepConfig.setRails(rails);
        deepConfig.setEnableTaskLoop(true);
        deepConfig.setEnableTaskPlanning(true);
        deepConfig.setEnableAsyncSubagent(!subagents.isEmpty());
        deepConfig.setMaxIterations(cfg.getMaxIterations());
        deepConfig.setWorkspace(workspace);
        deepConfig.setLanguage("en");

        agent.configure(deepConfig);

        // Override workspace root path to match config
        if (agent.getConfig() instanceof DeepAgentConfig dc && dc.getWorkspace() != null) {
            dc.getWorkspace().setRootPath(cfg.getWorkspace());
        }

        return new AgentResult(agent, tracker);
    }

    /**
     * Create an agent from the given config map (legacy API).
     *
     * @param config configuration map with keys: provider, model, api_key, etc.
     * @return DeepAgent instance
     */
    public static DeepAgent createFromConfig(Map<String, Object> config) {
        CliAgentConfig cfg = CliAgentConfig.fromMap(config);
        AgentResult result = createAgent(cfg);
        return result.getAgent();
    }

    /**
     * Build CLI workspace with overridden IDENTITY.md.
     * <p>
     * Mirrors Python's {@code _build_cli_workspace} function.
     */
    private static Workspace buildCliWorkspace(CliAgentConfig cfg, String language) {
        Workspace workspace = new Workspace();
        workspace.setRootPath(cfg.getWorkspace());
        workspace.setLanguage(language);
        // Load CLI-specific content overrides
        workspace.loadCliContent(language);
        return workspace;
    }

    /**
     * Get default skill directories.
     * <p>
     * Mirrors Python's {@code _default_skill_dirs} function.
     */
    private static List<String> getDefaultSkillDirs() {
        String home = System.getProperty("user.home");
        return List.of(
            home + "/.openjiuwen/workspace/skills",
            home + "/.claude/skills",
            home + "/.codex/skills",
            home + "/.jiuwenclaw/workspace/skills"
        );
    }

    /**
     * Create web tools.
     * <p>
     * Mirrors Python's {@code create_web_tools} function.
     */
    private static List<Object> createWebTools(String language) {
        // Web tools factory - delegates to WebTools class
        return com.openjiuwen.harness.tools.WebTools.createWebTools(language);
    }

    /**
     * Build subagent configs.
     * <p>
     * Mirrors Python's {@code _build_subagents} function.
     */
    private static List<Object> buildSubagents(Model model) {
        List<Object> subagents = new ArrayList<>();
        // Code agent
        subagents.add(buildCodeAgentConfig(model));
        // Research agent
        subagents.add(buildResearchAgentConfig(model));
        // Browser agent (optional)
        try {
            subagents.add(buildBrowserAgentConfig(model, "en"));
        } catch (Exception e) {
            // Browser agent not available, skip silently
        }
        return subagents;
    }

    private static Object buildCodeAgentConfig(Model model) {
        // Delegate to CodeAgent builder
        return com.openjiuwen.harness.subagents.CodeAgent.buildConfig(model, "en");
    }

    private static Object buildResearchAgentConfig(Model model) {
        // Delegate to ResearchAgent builder
        return com.openjiuwen.harness.subagents.ResearchAgent.buildConfig(model, "en");
    }

    private static Object buildBrowserAgentConfig(Model model, String language) {
        // Delegate to BrowserAgent builder
        return com.openjiuwen.harness.subagents.BrowserAgent.buildConfig(model, language);
    }

    /**
     * Create backend from config.
     * <p>
     * Mirrors Python's {@code create_backend} function.
     */
    public static LocalBackend createBackend(CliAgentConfig cfg) {
        return new LocalBackend(cfg);
    }

    /**
     * Result of agent creation containing agent and tracker.
     */
    public static class AgentResult {
        private final DeepAgent agent;
        private final TokenTrackingRail tracker;

        public AgentResult(DeepAgent agent, TokenTrackingRail tracker) {
            this.agent = agent;
            this.tracker = tracker;
        }

        public DeepAgent getAgent() {
            return agent;
        }

        public TokenTrackingRail getTracker() {
            return tracker;
        }
    }

    /**
     * Backend that calls the SDK Runner directly.
     * <p>
     * Mirrors Python's {@code LocalBackend} class.
     */
    public static class LocalBackend {
        private final CliAgentConfig cfg;
        private DeepAgent agent;
        private TokenTrackingRail tracker;
        private String sessionId;

        public LocalBackend(CliAgentConfig cfg) {
            this.cfg = cfg;
            this.sessionId = "cli-" + UUID.randomUUID().toString().substring(0, 8);
        }

        /**
         * Create the agent and start the Runner.
         */
        public void start() {
            AgentResult result = createAgent(cfg);
            this.agent = result.getAgent();
            this.tracker = result.getTracker();
            // Runner.start() would be called here in async context
        }

        /**
         * Stop the Runner.
         */
        public void stop() {
            // Runner.stop() would be called here in async context
        }

        /**
         * Stream output for the given query.
         */
        public Object runStreaming(String query, String sessionId) {
            // Delegate to agent streaming execution
            return agent.invoke(query, sessionId != null ? sessionId : this.sessionId);
        }

        /**
         * Abort the currently running query.
         */
        public void abort() {
            if (agent != null) {
                agent.abort();
            }
        }

        public DeepAgent getAgent() {
            return agent;
        }

        public TokenTrackingRail getTracker() {
            return tracker;
        }

        public String getSessionId() {
            return sessionId;
        }
    }
}