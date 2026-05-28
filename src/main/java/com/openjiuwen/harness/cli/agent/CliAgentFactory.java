/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.agent;

import java.util.*;
import java.nio.file.*;
import java.io.*;

/**
 * CLI agent factory — creates agents from CLI config.
 * 
 * <p>Mirrors Python's openjiuwen/harness/cli/agent/factory.py
 * Ported from Python: agent-core-0.1.12/openjiuwen/harness/cli/agent/factory.py
 * 
 * Provides:
 * - create_agent() — build a DeepAgent with rails
 * - LocalBackend — direct SDK Runner backend (MVP)
 * - create_backend() — backend factory
 */
public final class CliAgentFactory {

    // Default skill directories (priority high → low)
    private static final List<String> DEFAULT_SKILL_DIRS = Arrays.asList(
        "~/.openjiuwen/workspace/skills",
        "~/.claude/skills",
        "~/.codex/skills",
        "~/.jiuwenclaw/workspace/skills"
    );

    private CliAgentFactory() {
    }

    /**
     * Create an agent from the given config.
     * 
     * Mirrors Python's create_agent(cfg) function.
     * 
     * @param config CLI configuration map
     * @return AgentAndTracker tuple containing agent and token tracker
     */
    public static AgentAndTracker createFromConfig(Map<String, Object> config) {
        // Extract configuration values
        String provider = (String) config.getOrDefault("provider", "openai");
        String model = (String) config.getOrDefault("model", "gpt-4");
        String apiKey = (String) config.get("api_key");
        String apiBase = (String) config.get("api_base");
        Integer maxTokens = (Integer) config.getOrDefault("max_tokens", 4096);
        String cwd = (String) config.getOrDefault("cwd", System.getProperty("user.dir"));
        String workspace = (String) config.getOrDefault("workspace", cwd);
        Integer maxIterations = (Integer) config.getOrDefault("max_iterations", 100);

        // Build system prompt
        String systemPrompt = buildSystemPrompt(cwd, model, provider);

        // Create rails list
        List<Object> rails = new ArrayList<>();
        
        // Add token tracking rail
        rails.add(new TokenTrackingRailPlaceholder());
        
        // Add tool tracking rail
        rails.add(new ToolTrackingRailPlaceholder());
        
        // Add filesystem operation rail
        rails.add(new SysOperationRailPlaceholder());
        
        // Add ask user rail
        rails.add(new AskUserRailPlaceholder());
        
        // Add confirm interrupt rail for dangerous operations
        rails.add(new ConfirmInterruptRailPlaceholder(Arrays.asList("bash", "write_file", "edit_file")));
        
        // Add skill rail with default skill directories
        rails.add(new SkillUseRailPlaceholder(DEFAULT_SKILL_DIRS));

        // Build workspace
        WorkspacePlaceholder workspaceObj = buildCliWorkspace(workspace, "en");

        // Create subagents list
        List<Object> subagents = buildSubagents(model);

        // Load MCP configs from ~/.openjiuwen/mcp.json
        List<Object> mcpConfigs = loadMcpConfigs();

        // Build extra kwargs
        Map<String, Object> extraKwargs = new HashMap<>();
        if (!mcpConfigs.isEmpty()) {
            extraKwargs.put("mcps", mcpConfigs);
        }

        // Create deep agent - delegate to DeepAgentFactory
        Object agent = createDeepAgent(
            model,
            provider,
            apiKey,
            apiBase,
            maxTokens,
            systemPrompt,
            rails,
            subagents,
            maxIterations,
            workspaceObj,
            extraKwargs
        );

        return new AgentAndTracker(agent, new TokenTrackingRailPlaceholder());
    }

    /**
     * Build CLI-specific workspace with overridden IDENTITY.md.
     */
    private static WorkspacePlaceholder buildCliWorkspace(String workspacePath, String language) {
        return new WorkspacePlaceholder(workspacePath, language);
    }

    /**
     * Build subagent configs for the CLI agent.
     * Creates configs for: code_agent, research_agent, browser_agent.
     */
    private static List<Object> buildSubagents(String model) {
        List<Object> subagents = new ArrayList<>();
        
        // Code agent
        subagents.add(new SubAgentConfigPlaceholder("code_agent", 
            "Software engineering and coding tasks", model));
        
        // Research agent
        subagents.add(new SubAgentConfigPlaceholder("research_agent",
            "Research and investigation tasks", model));
        
        // Browser agent (optional)
        try {
            subagents.add(new SubAgentConfigPlaceholder("browser_agent",
                "Browser automation via Playwright", model));
        } catch (Exception e) {
            // Browser subagent not available - silently skip
        }
        
        return subagents;
    }

    /**
     * Load MCP server configs from ~/.openjiuwen/mcp.json.
     */
    private static List<Object> loadMcpConfigs() {
        List<Object> configs = new ArrayList<>();
        String homeDir = System.getProperty("user.home");
        Path mcpPath = Paths.get(homeDir, ".openjiuwen", "mcp.json");
        
        if (!Files.exists(mcpPath)) {
            return configs;
        }
        
        try {
            String content = Files.readString(mcpPath);
            // Parse JSON and extract mcpServers
            // Placeholder - actual implementation would parse JSON
        } catch (IOException e) {
            // Failed to load MCP config - return empty list
        }
        
        return configs;
    }

    /**
     * Build system prompt for CLI agent.
     */
    private static String buildSystemPrompt(String cwd, String model, String provider) {
        // Placeholder - actual implementation would use build_system_prompt from prompts package
        return "CLI Agent System Prompt for " + model;
    }

    /**
     * Create deep agent - delegate to DeepAgentFactory.
     */
    private static Object createDeepAgent(
        String model,
        String provider,
        String apiKey,
        String apiBase,
        Integer maxTokens,
        String systemPrompt,
        List<Object> rails,
        List<Object> subagents,
        Integer maxIterations,
        WorkspacePlaceholder workspace,
        Map<String, Object> extraKwargs
    ) {
        // Placeholder - actual implementation delegates to DeepAgentFactory
        return new DeepAgentPlaceholder(model, provider, apiKey, apiBase, maxTokens, 
            systemPrompt, rails, subagents, maxIterations, workspace, extraKwargs);
    }

    /**
     * Get default skill directories.
     */
    public static List<String> getDefaultSkillDirs() {
        return new ArrayList<>(DEFAULT_SKILL_DIRS);
    }

    // Placeholder classes for rail types (actual implementations exist elsewhere)
    
    public static class AgentAndTracker {
        private final Object agent;
        private final Object tracker;
        
        public AgentAndTracker(Object agent, Object tracker) {
            this.agent = agent;
            this.tracker = tracker;
        }
        
        public Object getAgent() { return agent; }
        public Object getTracker() { return tracker; }
    }

    static class TokenTrackingRailPlaceholder { }
    static class ToolTrackingRailPlaceholder { }
    static class SysOperationRailPlaceholder { }
    static class AskUserRailPlaceholder { }
    static class ConfirmInterruptRailPlaceholder {
        private final List<String> toolNames;
        ConfirmInterruptRailPlaceholder(List<String> toolNames) {
            this.toolNames = toolNames;
        }
    }
    static class SkillUseRailPlaceholder {
        private final List<String> skillsDir;
        SkillUseRailPlaceholder(List<String> skillsDir) {
            this.skillsDir = skillsDir;
        }
    }
    static class WorkspacePlaceholder {
        private final String rootPath;
        private final String language;
        WorkspacePlaceholder(String rootPath, String language) {
            this.rootPath = rootPath;
            this.language = language;
        }
    }
    static class SubAgentConfigPlaceholder {
        private final String name;
        private final String description;
        private final String model;
        SubAgentConfigPlaceholder(String name, String description, String model) {
            this.name = name;
            this.description = description;
            this.model = model;
        }
    }
    static class DeepAgentPlaceholder {
        private final String model;
        private final String provider;
        private final String apiKey;
        private final String apiBase;
        private final Integer maxTokens;
        private final String systemPrompt;
        private final List<Object> rails;
        private final List<Object> subagents;
        private final Integer maxIterations;
        private final WorkspacePlaceholder workspace;
        private final Map<String, Object> extraKwargs;
        
        DeepAgentPlaceholder(String model, String provider, String apiKey, String apiBase,
            Integer maxTokens, String systemPrompt, List<Object> rails, List<Object> subagents,
            Integer maxIterations, WorkspacePlaceholder workspace, Map<String, Object> extraKwargs) {
            this.model = model;
            this.provider = provider;
            this.apiKey = apiKey;
            this.apiBase = apiBase;
            this.maxTokens = maxTokens;
            this.systemPrompt = systemPrompt;
            this.rails = rails;
            this.subagents = subagents;
            this.maxIterations = maxIterations;
            this.workspace = workspace;
            this.extraKwargs = extraKwargs;
        }
    }

    /**
     * LocalBackend — direct SDK Runner backend (MVP).
     * Mirrors Python's LocalBackend class.
     */
    public static class LocalBackend {
        private final Map<String, Object> cfg;
        private Object agent;
        private Object tracker;
        private String sessionId;

        public LocalBackend(Map<String, Object> config) {
            this.cfg = config;
            this.sessionId = "cli-" + UUID.randomUUID().toString().substring(0, 8);
        }

        /**
         * Create the agent and start the Runner.
         */
        public void start() {
            AgentAndTracker result = createFromConfig(cfg);
            this.agent = result.getAgent();
            this.tracker = result.getTracker();
            // Runner.start() placeholder
        }

        /**
         * Stop the Runner.
         */
        public void stop() {
            // Runner.stop() placeholder
        }

        /**
         * Execute query and stream OutputSchema chunks.
         */
        public Iterator<Object> runStreaming(Object query, String sessionId) {
            // Placeholder - actual implementation would stream agent output
            return Collections.emptyIterator();
        }

        /**
         * Abort the currently running query.
         */
        public void abort() {
            // Placeholder - abort current execution
        }

        public Object getAgent() { return agent; }
        public Object getTracker() { return tracker; }
        public String getSessionId() { return sessionId; }
    }

    /**
     * Create backend from config.
     */
    public static LocalBackend createBackend(Map<String, Object> config) {
        return new LocalBackend(config);
    }
}
