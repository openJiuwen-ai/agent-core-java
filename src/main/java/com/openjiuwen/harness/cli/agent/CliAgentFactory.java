/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.agent;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.interrupt.AskUserRail;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;

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
        Integer maxTokens = intValue(config.get("max_tokens"), 4096);
        String cwd = (String) config.getOrDefault("cwd", System.getProperty("user.dir"));
        String workspace = (String) config.getOrDefault("workspace", cwd);
        Integer maxIterations = intValue(config.get("max_iterations"), 100);

        // Build system prompt
        String systemPrompt = buildSystemPrompt(cwd, model, provider);

        // Create rails list
        List<AgentRail> rails = new ArrayList<>();
        
        // Add token tracking rail
        TokenTrackingRailPlaceholder tracker = new TokenTrackingRailPlaceholder();
        
        // Add filesystem operation rail
        rails.add(new SysOperationRail());
        
        // Add ask user rail
        rails.add(new AskUserRail());
        
        // Add confirm interrupt rail for dangerous operations
        rails.add(new ConfirmInterruptRail(Arrays.asList("bash", "write_file", "edit_file")));
        
        // Add skill rail with default skill directories
        rails.add(new SkillUseRail(DEFAULT_SKILL_DIRS, SkillUseRail.SKILL_MODE_ALL, true, false, null, null));

        // Build workspace
        Workspace workspaceObj = buildCliWorkspace(workspace, "en");

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
        DeepAgent agent = createDeepAgent(
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

        return new AgentAndTracker(agent, tracker);
    }

    /**
     * Build CLI-specific workspace with overridden IDENTITY.md.
     */
    private static Workspace buildCliWorkspace(String workspacePath, String language) {
        return new Workspace(workspacePath, language);
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
    private static DeepAgent createDeepAgent(
        String model,
        String provider,
        String apiKey,
        String apiBase,
        Integer maxTokens,
        String systemPrompt,
        List<AgentRail> rails,
        List<Object> subagents,
        Integer maxIterations,
        Workspace workspace,
        Map<String, Object> extraKwargs
    ) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setModelClientConfig(ModelClientConfig.builder()
                .clientProvider(provider)
                .apiKey(apiKey != null ? apiKey : "")
                .apiBase(apiBase != null ? apiBase : "")
                .build());
        config.setModelRequestConfig(ModelRequestConfig.builder()
                .modelName(model)
                .maxTokens(maxTokens)
                .build());
        config.setSystemPrompt(systemPrompt);
        config.setMaxIterations(maxIterations != null ? maxIterations : 100);
        config.setWorkspace(workspace);
        config.setRails(rails);
        return HarnessFactory.createDeepAgent(config);
    }

    private static Integer intValue(Object value, Integer defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return defaultValue;
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

    /**
     * LocalBackend — direct SDK Runner backend (MVP).
     * Mirrors Python's LocalBackend class.
     */
    public static class LocalBackend {
        @FunctionalInterface
        public interface AgentProvider {
            AgentAndTracker create(Map<String, Object> config);
        }

        @FunctionalInterface
        public interface StreamingRunner {
            Iterator<Object> run(Object agent, Object inputs, Object session);
        }

        private final Map<String, Object> cfg;
        private final AgentProvider agentProvider;
        private final Runnable runnerStart;
        private final Runnable runnerStop;
        private final StreamingRunner streamingRunner;
        private Object agent;
        private Object tracker;
        private String sessionId;

        public LocalBackend(Map<String, Object> config) {
            this(
                    config,
                    CliAgentFactory::createFromConfig,
                    Runner::start,
                    Runner::stop,
                    (agent, inputs, session) -> Runner.runAgentStreaming(
                            agent,
                            inputs,
                            session,
                            null,
                            List.of(StreamMode.OUTPUT))
            );
        }

        public LocalBackend(
                Map<String, Object> config,
                AgentProvider agentProvider,
                Runnable runnerStart,
                Runnable runnerStop,
                StreamingRunner streamingRunner) {
            this.cfg = config;
            this.agentProvider = agentProvider;
            this.runnerStart = runnerStart;
            this.runnerStop = runnerStop;
            this.streamingRunner = streamingRunner;
            this.sessionId = "cli-" + UUID.randomUUID().toString().substring(0, 8);
        }

        /**
         * Create the agent and start the Runner.
         */
        public void start() {
            AgentAndTracker result = agentProvider.create(cfg);
            this.agent = result.getAgent();
            this.tracker = result.getTracker();
            runnerStart.run();
        }

        /**
         * Stop the Runner.
         */
        public void stop() {
            runnerStop.run();
        }

        /**
         * Execute query and stream OutputSchema chunks.
         */
        public Iterator<Object> runStreaming(Object query, String sessionId) {
            String sid = sessionId != null ? sessionId : this.sessionId;
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("query", query);
            return streamingRunner.run(agent, inputs, sid);
        }

        public Iterator<Object> runStreaming(Object query) {
            return runStreaming(query, null);
        }

        /**
         * Abort the currently running query.
         */
        public void abort() {
            if (agent == null) {
                return;
            }
            try {
                Method abortMethod = agent.getClass().getMethod("abort");
                Object result = abortMethod.invoke(agent);
                if (result instanceof CompletionStage<?> stage) {
                    stage.toCompletableFuture().join();
                }
            } catch (NoSuchMethodException ignored) {
                // Some local agent implementations do not expose an abort hook.
            } catch (Exception ignored) {
                // Keep CLI abort best-effort, matching Python's suppressed abort errors.
            }
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
