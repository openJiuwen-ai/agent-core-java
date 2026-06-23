/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.cli.prompts.CliPromptBuilder;
import com.openjiuwen.harness.cli.rails.TokenTrackingRail;
import com.openjiuwen.harness.cli.rails.ToolTrackingRail;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.context_engineer.ContextAssembleRail;
import com.openjiuwen.harness.rails.interrupt.AskUserRail;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import com.openjiuwen.harness.rails.memory.MemoryRail;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.subagents.BrowserAgentFactory;
import com.openjiuwen.harness.subagents.CodeAgentFactory;
import com.openjiuwen.harness.subagents.ResearchAgentFactory;
import com.openjiuwen.harness.tools.WebTools;
import com.openjiuwen.harness.workspace.Workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * CLI agent factory and helper functions.
 *
 * <p>Mirrors Python's module-level factory helpers in
 * {@code openjiuwen/harness/cli/agent/factory.py}.</p>
 */
public final class CliAgentFactory {

    public static final List<String> DEFAULT_SKILL_DIRS = List.of(
            "~/.openjiuwen/workspace/skills",
            "~/.claude/skills",
            "~/.codex/skills",
            "~/.jiuwenclaw/workspace/skills"
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CliAgentFactory() {
    }

    public static List<String> defaultSkillDirs() {
        return new ArrayList<>(DEFAULT_SKILL_DIRS);
    }

    public static Path getCliContentBaseDir() {
        return Path.of("openjiuwen", "harness", "cli", "prompts", "workspace_content");
    }

    public static String loadCliContent(String language, String filePath) {
        Path fullPath = getCliContentBaseDir()
                .resolve(language == null || language.isBlank() ? "en" : language)
                .resolve(filePath == null ? "" : filePath)
                .normalize();
        if (!Files.exists(fullPath)) {
            return "";
        }
        try {
            return Files.readString(fullPath);
        } catch (IOException ignored) {
            return "";
        }
    }

    public static Workspace buildCliWorkspace(Map<String, Object> cfg, String language) {
        String workspaceRoot = stringValue(cfg.get("workspace"), "./");
        String resolvedLanguage = language == null || language.isBlank() ? "en" : language;
        Workspace workspace = new Workspace(workspaceRoot, resolvedLanguage);
        String cliIdentity = loadCliContent(resolvedLanguage, "IDENTITY.md");
        if (!cliIdentity.isBlank()) {
            Map<String, Object> identity = new LinkedHashMap<>();
            identity.put("name", "IDENTITY.md");
            identity.put("description", "Identity credentials and permissions");
            identity.put("path", "IDENTITY.md");
            identity.put("is_file", true);
            identity.put("children", List.of());
            identity.put("default_content", cliIdentity);
            workspace.setDirectory(identity);
        }
        return workspace;
    }

    public static MemoryRail buildMemoryRail(Map<String, Object> cfg) {
        String apiKey = firstNonBlank(
                System.getenv("EMBEDDING_API_KEY"),
                stringValue(cfg.get("api_key"), ""));
        if (apiKey.isBlank()) {
            return null;
        }
        Map<String, Object> embeddingConfig = new LinkedHashMap<>();
        embeddingConfig.put("model_name", firstNonBlank(
                System.getenv("EMBEDDING_MODEL_NAME"), "text-embedding-3-small"));
        embeddingConfig.put("api_base", firstNonBlank(
                System.getenv("EMBEDDING_BASE_URL"),
                stringValue(cfg.get("api_base"), "")));
        embeddingConfig.put("api_key", apiKey);
        return new MemoryRail(embeddingConfig);
    }

    public static List<DeepAgentConfig.SubAgentConfig> buildSubagents(Object model) {
        List<DeepAgentConfig.SubAgentConfig> subagents = new ArrayList<>();
        subagents.add(CodeAgentFactory.buildCodeAgentConfig(
                model, null, null, null, null, List.of(new SysOperationRail()),
                false, 15, null, null, null, null, "en", null, null));
        subagents.add(ResearchAgentFactory.buildResearchAgentConfig(
                model, null, null, null, null, List.of(new SysOperationRail()),
                false, 15, null, null, null, null, "en", null));
        try {
            subagents.add(BrowserAgentFactory.buildBrowserAgentConfig(
                    model, null, null, null, null, null, null, "en", false, 25));
        } catch (RuntimeException ignored) {
            // Python logs and skips the optional browser subagent when unavailable.
        }
        return subagents;
    }

    public static List<McpServerConfig> loadMcpConfigs() {
        Path mcpPath = Path.of(System.getProperty("user.home"), ".openjiuwen", "mcp.json");
        if (!Files.exists(mcpPath)) {
            return List.of();
        }
        try {
            Map<String, Object> data = MAPPER.readValue(
                    Files.readString(mcpPath),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            Object serversValue = data.get("mcpServers");
            if (!(serversValue instanceof Map<?, ?> servers)) {
                return List.of();
            }
            List<McpServerConfig> configs = new ArrayList<>();
            for (Map.Entry<?, ?> entry : servers.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> spec)) {
                    continue;
                }
                configs.add(McpServerConfig.builder()
                        .serverName(String.valueOf(entry.getKey()))
                        .serverPath(stringValue(firstValue(spec, "url", "server_path"), ""))
                        .clientType(stringValue(firstValue(spec, "transport", "client_type"), "stdio"))
                        .params(filterNoneValues(mcpParams(spec)))
                        .authHeaders(stringStringMap(spec.get("auth_headers")))
                        .build());
            }
            return configs;
        } catch (IOException | RuntimeException ignored) {
            return List.of();
        }
    }

    public static DeepAgentConfig.VisionModelConfig loadVisionConfig(Map<String, Object> cfg) {
        return loadVisionConfig(cfg, System.getenv());
    }

    static DeepAgentConfig.VisionModelConfig loadVisionConfig(Map<String, Object> cfg, Map<String, String> env) {
        Map<String, String> resolvedEnv = env == null ? Map.of() : env;
        DeepAgentConfig.VisionModelConfig config = hasEnvValue(resolvedEnv, "VISION_API_KEY")
                ? DeepAgentConfig.VisionModelConfig.fromEnvironment(resolvedEnv)
                : new DeepAgentConfig.VisionModelConfig();
        if (!hasEnvValue(resolvedEnv, "VISION_API_KEY")) {
            config.setApiKey(stringValue(cfg.get("api_key"), ""));
            config.setBaseUrl(stringValue(cfg.get("api_base"), DeepAgentConfig.DEFAULT_OPENAI_BASE_URL));
        }
        return config;
    }

    public static DeepAgentConfig.AudioModelConfig loadAudioConfig(Map<String, Object> cfg) {
        return loadAudioConfig(cfg, System.getenv());
    }

    static DeepAgentConfig.AudioModelConfig loadAudioConfig(Map<String, Object> cfg, Map<String, String> env) {
        Map<String, String> resolvedEnv = env == null ? Map.of() : env;
        DeepAgentConfig.AudioModelConfig config = hasEnvValue(resolvedEnv, "AUDIO_API_KEY")
                ? DeepAgentConfig.AudioModelConfig.fromEnvironment(resolvedEnv)
                : new DeepAgentConfig.AudioModelConfig();
        if (!hasEnvValue(resolvedEnv, "AUDIO_API_KEY")) {
            config.setApiKey(stringValue(cfg.get("api_key"), ""));
            config.setBaseUrl(stringValue(cfg.get("api_base"), DeepAgentConfig.DEFAULT_OPENAI_BASE_URL));
        }
        return config;
    }

    public static AgentBundle createAgent(Map<String, Object> rawConfig) {
        Map<String, Object> cfg = normalizeConfig(rawConfig);
        Model model = initModel(cfg);
        String systemPrompt = CliPromptBuilder.buildSystemPrompt(
                stringValue(cfg.get("cwd"), System.getProperty("user.dir")),
                stringValue(cfg.get("model"), "gpt-4o"),
                stringValue(cfg.get("provider"), "OpenAI"),
                "en");

        TokenTrackingRail tracker = new TokenTrackingRail();
        ToolTrackingRail toolTracker = new ToolTrackingRail();
        List<DeepAgentRail> rails = new ArrayList<>();
        rails.add(new CliTrackingBridgeRail(tracker, toolTracker));
        rails.add(new SysOperationRail());
        rails.add(new AskUserRail());
        rails.add(new ConfirmInterruptRail(List.of("write_file", "edit_file")));
        rails.add(new SkillUseRail(
                String.join(",", defaultSkillDirs()),
                SkillUseRail.SKILL_MODE_ALL,
                true,
                false,
                null,
                null));
        rails.add(new ContextAssembleRail());
        MemoryRail memoryRail = buildMemoryRail(cfg);
        if (memoryRail != null) {
            rails.add(memoryRail);
        }

        List<Tool> webTools = WebTools.createWebTools(null);
        List<DeepAgentConfig.SubAgentConfig> subagents = buildSubagents(model);
        Workspace workspace = buildCliWorkspace(cfg, "en");

        DeepAgentConfig config = new DeepAgentConfig();
        config.setModel(model);
        config.setSystemPrompt(systemPrompt);
        config.setTools(webTools);
        config.setRails(rails);
        config.setEnableTaskLoop(true);
        config.setEnablePlanMode(true);
        config.setEnableAsyncSubagent(!subagents.isEmpty());
        config.setMaxIterations(intValue(cfg.get("max_iterations"), 30));
        config.setWorkspace(workspace);
        config.setLanguage("en");
        config.setSubagents(toSubagentMap(subagents));
        config.setMcps(new ArrayList<Object>(loadMcpConfigs()));
        config.setVisionModelConfig(loadVisionConfig(cfg));
        config.setAudioModelConfig(loadAudioConfig(cfg));

        DeepAgent agent = new DeepAgent(new AgentCard("cli_agent", "cli_agent", "CLI DeepAgent"));
        agent.configure(config);
        if (agent.deepConfig().getWorkspace() instanceof Workspace typedWorkspace) {
            typedWorkspace.setRootPath(stringValue(cfg.get("workspace"), typedWorkspace.getRootPath()));
        }
        return new AgentBundle(agent, tracker);
    }

    public static LocalBackend createBackend(Map<String, Object> rawConfig) {
        Map<String, Object> cfg = normalizeConfig(rawConfig);
        if (!stringValue(cfg.get("server_url"), "").isBlank()) {
            throw new UnsupportedOperationException(
                    "RemoteBackend is not supported in the MVP. Remove OPENJIUWEN_SERVER_URL to use local mode.");
        }
        return new LocalBackend(cfg);
    }

    public static Map<String, Object> normalizeConfig(Map<String, Object> rawConfig) {
        Map<String, Object> config = new LinkedHashMap<>(CliAgentConfig.defaultConfig());
        if (rawConfig == null) {
            return config;
        }
        rawConfig.forEach((key, value) -> {
            if (value == null || (value instanceof String text && text.isBlank())) {
                return;
            }
            switch (key) {
                case "apiKey" -> config.put("api_key", value);
                case "apiBase" -> config.put("api_base", value);
                case "maxTokens" -> config.put("max_tokens", value);
                case "maxIterations" -> config.put("max_iterations", value);
                case "serverUrl" -> config.put("server_url", value);
                default -> config.put(key, value);
            }
        });
        return config;
    }

    private static Model initModel(Map<String, Object> cfg) {
        loadBuiltInModelClients();
        String provider = stringValue(cfg.get("provider"), "OpenAI");
        String modelName = stringValue(cfg.get("model"), "gpt-4o");
        String apiKey = stringValue(cfg.get("api_key"), "");
        String apiBase = stringValue(cfg.get("api_base"), DeepAgentConfig.DEFAULT_OPENAI_BASE_URL);
        Integer maxTokens = intObject(cfg.get("max_tokens"));
        try {
            return Model.initModel(
                    provider,
                    modelName,
                    apiKey,
                    apiBase,
                    0.95F,
                    0.1F,
                    maxTokens,
                    60.0F,
                    3,
                    false,
                    null);
        } catch (RuntimeException ignored) {
            ModelClientConfig clientConfig = ModelClientConfig.builder()
                    .clientProvider(provider)
                    .apiKey(apiKey)
                    .apiBase(apiBase)
                    .timeout(60.0D)
                    .maxRetries(3)
                    .verifySsl(false)
                    .build();
            ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                    .modelName(modelName)
                    .temperature(0.95D)
                    .topP(0.1D)
                    .maxTokens(maxTokens)
                    .build();
            return new Model(new DeferredModelClient(), clientConfig, requestConfig);
        }
    }

    private static void loadBuiltInModelClients() {
        for (String className : List.of(
                "com.openjiuwen.core.foundation.llm.model_clients.OpenAIModelClient",
                "com.openjiuwen.core.foundation.llm.model_clients.DashScopeModelClient",
                "com.openjiuwen.core.foundation.llm.model_clients.DeepSeekModelClient",
                "com.openjiuwen.core.foundation.llm.model_clients.SiliconFlowModelClient",
                "com.openjiuwen.core.foundation.llm.model_clients.IntelliRouterModelClient",
                "com.openjiuwen.core.foundation.llm.model_clients.InferenceAffinityModelClient")) {
            try {
                Class.forName(className);
            } catch (ClassNotFoundException ignored) {
                // Optional model clients may not be translated yet.
            }
        }
    }

    private static Map<String, DeepAgentConfig.SubAgentConfig> toSubagentMap(
            List<DeepAgentConfig.SubAgentConfig> subagents) {
        Map<String, DeepAgentConfig.SubAgentConfig> result = new LinkedHashMap<>();
        for (DeepAgentConfig.SubAgentConfig spec : subagents) {
            if (spec != null && spec.getName() != null && !spec.getName().isBlank()) {
                result.put(spec.getName(), spec);
            }
        }
        return result;
    }

    private static Map<String, Object> filterNoneValues(Map<String, Object> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value != null) {
                result.put(key, value);
            }
        });
        return result;
    }

    private static Map<String, Object> mcpParams(Map<?, ?> spec) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("command", spec.get("command"));
        params.put("args", spec.get("args"));
        params.put("env", spec.get("env"));
        params.put("cwd", spec.get("cwd"));
        return params;
    }

    private static Object firstValue(Map<?, ?> map, String first, String second) {
        Object value = map.get(first);
        return value == null ? map.get(second) : value;
    }

    private static Map<String, String> stringStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> {
            if (key != null && mapValue != null) {
                result.put(String.valueOf(key), String.valueOf(mapValue));
            }
        });
        return result;
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return defaultValue;
    }

    private static Integer intObject(Object value) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            return null;
        }
        return intValue(value, 0);
    }

    private static String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? defaultValue : text;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second == null ? "" : second;
    }

    private static boolean hasEnvValue(Map<String, String> env, String name) {
        return !stringValue(env.get(name), "").isBlank();
    }

    /**
     * Tuple returned by {@code create_agent}.
     *
     * <p>Mirrors Python's {@code (agent, tracker)} tuple in
     * {@code openjiuwen/harness/cli/agent/factory.py}.</p>
     *
     * @param agent configured DeepAgent
     * @param tracker token tracking rail
     */
    public record AgentBundle(DeepAgent agent, TokenTrackingRail tracker) {
    }

    /**
     * DeepAgent-compatible holder for CLI tracker rails.
     *
     * <p>Mirrors Python's CLI tracker rails mounted by {@code create_agent} in
     * {@code openjiuwen/harness/cli/agent/factory.py}.</p>
     */
    public static final class CliTrackingBridgeRail extends DeepAgentRail {
        private final TokenTrackingRail tokenTracker;
        private final ToolTrackingRail toolTracker;

        public CliTrackingBridgeRail(TokenTrackingRail tokenTracker, ToolTrackingRail toolTracker) {
            this.tokenTracker = tokenTracker;
            this.toolTracker = toolTracker;
        }

        public TokenTrackingRail getTokenTracker() {
            return tokenTracker;
        }

        public ToolTrackingRail getToolTracker() {
            return toolTracker;
        }
    }

    /**
     * Invoke-time model placeholder used when no Java model client registry is loaded.
     *
     * <p>Mirrors Python's construction-time {@code init_model} behavior in
     * {@code openjiuwen/harness/cli/agent/factory.py}: creating the CLI agent
     * does not perform an external model call.</p>
     */
    private static final class DeferredModelClient implements Model.ModelClient {

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            CompletableFuture<AssistantMessage> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException(
                    "No Java model client registry is available for CLI agent invocation."));
            return failed;
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            throw new IllegalStateException(
                    "No Java model client registry is available for CLI agent streaming.");
        }
    }
}
