/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.memory.external.Mem0MemoryProvider;
import com.openjiuwen.core.memory.external.MemoryProvider;
import com.openjiuwen.core.memory.external.OpenJiuwenMemoryProvider;
import com.openjiuwen.core.memory.external.OpenVikingMemoryProvider;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.rails.AgentModeRail;
import com.openjiuwen.harness.rails.CodingMemoryRail;
import com.openjiuwen.harness.rails.ContextAssembleRail;
import com.openjiuwen.harness.rails.ContextProcessorRail;
import com.openjiuwen.harness.rails.ExternalMemoryRail;
import com.openjiuwen.harness.rails.HeartbeatRail;
import com.openjiuwen.harness.rails.LspRail;
import com.openjiuwen.harness.rails.McpRail;
import com.openjiuwen.harness.rails.MemoryRail;
import com.openjiuwen.harness.rails.ProgressiveToolRail;
import com.openjiuwen.harness.rails.SecurityRail;
import com.openjiuwen.harness.rails.SessionRail;
import com.openjiuwen.harness.rails.SkillUseRail;
import com.openjiuwen.harness.rails.SkillCreateRail;
import com.openjiuwen.harness.rails.SubagentRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.TaskCompletionRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.rails.TeamSkillCreateRail;
import com.openjiuwen.harness.rails.TeamSkillRail;
import com.openjiuwen.harness.rails.VerificationContractRail;
import com.openjiuwen.harness.rails.VerificationRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.tools.BashTool;
import com.openjiuwen.harness.tools.CodeTool;
import com.openjiuwen.harness.tools.FilesystemTool;
import com.openjiuwen.harness.tools.WebFetchWebpageTool;
import com.openjiuwen.harness.tools.WebFreeSearchTool;
import com.openjiuwen.harness.tools.WebPaidSearchTool;
import com.openjiuwen.harness.workspace.Workspace;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * HarnessConfigBuilder.
 * 
 * @since 0.1.7
 */
public final class HarnessConfigBuilder {
    /**
     * Trusted class prefix.
     * <p>
     * Package resources reference tool and rail implementation classes by name and
     * load them reflectively. Loading is restricted to classes within the
     * application's own namespace so that untrusted classes cannot be loaded and
     * executed via static initializers.
     *
     * @since 0.1.7
     */
    private static final String TRUSTED_CLASS_PREFIX = "com.openjiuwen.";

    private static final Map<String, Function<Path, List<Object>>> BUILTIN_TOOLS = new LinkedHashMap<>();

    /**
     * LinkedHashMap<>.
     *
     * @since 0.1.7
     */
    private static final Map<String, BiFunction<Path, HarnessConfig.RailResourceSchema, Object>> BUILTIN_RAILS =
        new LinkedHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private static final Map<String, HarnessToolProvider> TOOL_ENTRY_POINTS = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private static final Map<String, HarnessRailProvider> RAIL_ENTRY_POINTS = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private static final Map<Class<?>, String> TOOL_CLASS_TO_GROUP = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private static final Map<Class<?>, String> RAIL_CLASS_TO_NAME = new ConcurrentHashMap<>();

    static {
        BUILTIN_TOOLS.put("filesystem", root -> List.of(new FilesystemTool(root.toString())));
        BUILTIN_TOOLS.put("shell", root -> List.of(new BashTool()));
        BUILTIN_TOOLS.put("bash", root -> List.of(new BashTool()));
        BUILTIN_TOOLS.put("code", root -> List.of(new CodeTool()));
        BUILTIN_TOOLS.put("web_search", root -> List.of(new WebFreeSearchTool(), new WebPaidSearchTool()));
        BUILTIN_TOOLS.put("web_fetch", root -> List.of(new WebFetchWebpageTool()));

        BUILTIN_RAILS.put("task_planning", HarnessConfigBuilder::createTaskPlanningRail);
        BUILTIN_RAILS.put("agent_mode", (root, spec) -> new AgentModeRail());
        BUILTIN_RAILS.put("session", (root, spec) -> new SessionRail());
        BUILTIN_RAILS.put("subagent", (root, spec) -> new SubagentRail());
        BUILTIN_RAILS.put("sys_operation", (root, spec) -> new SysOperationRail());
        BUILTIN_RAILS.put("security", (root, spec) -> new SecurityRail());
        BUILTIN_RAILS.put("skill_use", (root, spec) -> new SkillUseRail());
        BUILTIN_RAILS.put("heartbeat", (root, spec) -> new HeartbeatRail());
        BUILTIN_RAILS.put("lsp", (root, spec) -> new LspRail());
        BUILTIN_RAILS.put("mcp", (root, spec) -> new McpRail());
        BUILTIN_RAILS.put("progressive_tool", HarnessConfigBuilder::createProgressiveToolRail);
        BUILTIN_RAILS.put("task_completion", HarnessConfigBuilder::createTaskCompletionRail);
        BUILTIN_RAILS.put("context_assemble", (root, spec) -> new ContextAssembleRail());
        BUILTIN_RAILS.put("context_processor", HarnessConfigBuilder::createContextProcessorRail);
        BUILTIN_RAILS.put("memory", HarnessConfigBuilder::createMemoryRail);
        BUILTIN_RAILS.put("coding_memory", HarnessConfigBuilder::createCodingMemoryRail);
        BUILTIN_RAILS.put("external_memory", HarnessConfigBuilder::createExternalMemoryRail);
        BUILTIN_RAILS.put("verification_contract", (root, spec) -> new VerificationContractRail());
        BUILTIN_RAILS.put("verification", HarnessConfigBuilder::createVerificationRail);
        BUILTIN_RAILS.put("skill_use", HarnessConfigBuilder::createSkillUseRail);
        BUILTIN_RAILS.put("skill_create", HarnessConfigBuilder::createSkillCreateRail);
        BUILTIN_RAILS.put("team_skill_create", HarnessConfigBuilder::createTeamSkillCreateRail);
        BUILTIN_RAILS.put("team_skill", HarnessConfigBuilder::createTeamSkillRail);

        TOOL_CLASS_TO_GROUP.put(FilesystemTool.class, "filesystem");
        TOOL_CLASS_TO_GROUP.put(BashTool.class, "shell");
        TOOL_CLASS_TO_GROUP.put(CodeTool.class, "code");
        TOOL_CLASS_TO_GROUP.put(WebFreeSearchTool.class, "web_search");
        TOOL_CLASS_TO_GROUP.put(WebPaidSearchTool.class, "web_search");
        TOOL_CLASS_TO_GROUP.put(WebFetchWebpageTool.class, "web_fetch");

        RAIL_CLASS_TO_NAME.put(TaskPlanningRail.class, "task_planning");
        RAIL_CLASS_TO_NAME.put(AgentModeRail.class, "agent_mode");
        RAIL_CLASS_TO_NAME.put(SessionRail.class, "session");
        RAIL_CLASS_TO_NAME.put(SubagentRail.class, "subagent");
        RAIL_CLASS_TO_NAME.put(SysOperationRail.class, "sys_operation");
        RAIL_CLASS_TO_NAME.put(SecurityRail.class, "security");
        RAIL_CLASS_TO_NAME.put(SkillUseRail.class, "skill_use");
        RAIL_CLASS_TO_NAME.put(HeartbeatRail.class, "heartbeat");
        RAIL_CLASS_TO_NAME.put(LspRail.class, "lsp");
        RAIL_CLASS_TO_NAME.put(McpRail.class, "mcp");
        RAIL_CLASS_TO_NAME.put(ProgressiveToolRail.class, "progressive_tool");
        RAIL_CLASS_TO_NAME.put(TaskCompletionRail.class, "task_completion");
        RAIL_CLASS_TO_NAME.put(ContextAssembleRail.class, "context_assemble");
        RAIL_CLASS_TO_NAME.put(ContextProcessorRail.class, "context_processor");
        RAIL_CLASS_TO_NAME.put(MemoryRail.class, "memory");
        RAIL_CLASS_TO_NAME.put(CodingMemoryRail.class, "coding_memory");
        RAIL_CLASS_TO_NAME.put(ExternalMemoryRail.class, "external_memory");
        RAIL_CLASS_TO_NAME.put(VerificationContractRail.class, "verification_contract");
        RAIL_CLASS_TO_NAME.put(VerificationRail.class, "verification");
        RAIL_CLASS_TO_NAME.put(SkillCreateRail.class, "skill_create");
        RAIL_CLASS_TO_NAME.put(TeamSkillCreateRail.class, "team_skill_create");
        RAIL_CLASS_TO_NAME.put(TeamSkillRail.class, "team_skill");
    }

    /**
     * HarnessConfigBuilder.
     * 
     * @since 0.1.7
     */
    private HarnessConfigBuilder() {
    }

    /**
     * registerToolProvider.
     * 
     * @param provider provider
     * @since 0.1.7
     */
    public static void registerToolProvider(HarnessToolProvider provider) {
        TOOL_ENTRY_POINTS.put(provider.name(), provider);
    }

    /**
     * registerRailProvider.
     * 
     * @param provider provider
     * @since 0.1.7
     */
    public static void registerRailProvider(HarnessRailProvider provider) {
        RAIL_ENTRY_POINTS.put(provider.name(), provider);
    }

    /**
     * build.
     * 
     * @param isResolved isResolved
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent build(ResolvedHarnessConfig isResolved) {
        HarnessConfig config = isResolved.config();
        Path workspaceRoot = resolveWorkspaceRoot(isResolved);
        List<Object> tools = resolveTools(config.getResources(), workspaceRoot);
        List<Object> rails = resolveRails(config.getResources(), workspaceRoot);
        List<McpServerConfig> mcps = resolveMcps(config.getResources());

        DeepAgentConfig deepConfig =
            DeepAgentConfig.builder().systemPrompt(isResolved.systemPrompt() != null ? isResolved.systemPrompt() : "")
                    .maxIterations(config.getMaxIterations() != null ? config.getMaxIterations() : 15)
                    .language(config.getLanguage() != null ? config.getLanguage() : "cn")
                    .workspacePath(workspaceRoot.toString()).completionTimeout(config.getCompletionTimeout())
                    .tools(new ArrayList<>(tools)).rails(new ArrayList<>(rails)).mcps(new ArrayList<>(mcps))
                    .extraPromptSections(toPromptSections(isResolved.extraSections()))
                    .skillDirectories(resolveSkillDirs(config.getResources(), isResolved.sourcePath()))
                    .skillMode(resolveSkillMode(config.getResources())).build();

        AgentCard card = AgentCard.builder().id(config.getId())
                .name(config.getName() != null && !config.getName().isBlank() ? config.getName() : "harness_agent")
                .description(config.getDescription() != null ? config.getDescription() : "").build();

        writeFileSections(isResolved.fileSections(), workspaceRoot, deepConfig.getLanguage());
        return HarnessFactory.createDeepAgent(card, deepConfig,
                Workspace.builder().rootPath(workspaceRoot.toString()).language(deepConfig.getLanguage()).build());
    }

    /**
     * generateHarnessConfigYaml.
     * 
     * @param card card
     * @param systemPrompt systemPrompt
     * @param tools tools
     * @param rails rails
     * @param language language
     * @param maxIterations maxIterations
     * @param completionTimeout completionTimeout
     * @return the result
     * @since 0.1.7
     */
    public static String generateHarnessConfigYaml(AgentCard card, String systemPrompt, List<Object> tools,
            List<Object> rails, String language, Integer maxIterations, Double completionTimeout) {
        HarnessConfig.PromptsSchema prompts = null;
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            prompts = HarnessConfig.PromptsSchema.builder().sections(List.of(HarnessConfig.SectionSchema.builder()
                    .name("identity").priority(10).content(Map.of("cn", systemPrompt, "en", systemPrompt)).build()))
                    .build();
        }

        HarnessConfig.ResourcesSchema resources =
            HarnessConfig.ResourcesSchema.builder().tools(toToolSpecs(tools)).rails(toRailSpecs(rails)).build();

        HarnessConfig config = HarnessConfig.builder().id(card != null ? card.getId() : null)
                .name(card != null ? card.getName() : null).description(card != null ? card.getDescription() : null)
                .language(language != null ? language : "cn").maxIterations(maxIterations)
                .completionTimeout(completionTimeout).prompts(prompts).resources(resources.hasAny() ? resources : null)
                .build();
        return new Yaml().dump(config.toYamlMap());
    }

    /**
     * resolveWorkspaceRoot.
     * 
     * @param isResolved isResolved
     * @return the result
     * @since 0.1.7
     */
    private static Path resolveWorkspaceRoot(ResolvedHarnessConfig isResolved) {
        HarnessConfig.WorkspaceSchema workspace = isResolved.config().getWorkspace();
        if (workspace != null && workspace.getRootPath() != null && !workspace.getRootPath().isBlank()) {
            return isResolved.sourcePath().getParent().resolve(workspace.getRootPath()).toAbsolutePath().normalize();
        }
        return isResolved.sourcePath().getParent().toAbsolutePath().normalize();
    }

    /**
     * resolveTools.
     * 
     * @param resources resources
     * @param workspaceRoot workspaceRoot
     * @return the result
     * @since 0.1.7
     */
    private static List<Object> resolveTools(HarnessConfig.ResourcesSchema resources, Path workspaceRoot) {
        if (resources == null || resources.getTools().isEmpty()) {
            return List.of();
        }
        List<Object> tools = new ArrayList<>();
        for (HarnessConfig.ToolResourceSchema spec : resources.getTools()) {
            String type = spec.getType();
            if ("builtin".equals(type)) {
                List<String> names = !spec.getNames().isEmpty()
                        ? spec.getNames()
                        : spec.getName() == null ? List.of() : List.of(spec.getName());
                for (String name : names) {
                    Function<Path, List<Object>> factory = BUILTIN_TOOLS.get(name);
                    if (factory == null) {
                        throw new IllegalArgumentException("Unknown builtin tool group: " + name);
                    }
                    tools.addAll(factory.apply(workspaceRoot));
                }
            } else if ("package".equals(type)) {
                tools.add(instantiateTool(spec.getModule(), spec.getClassName(), workspaceRoot));
            } else if ("entry_point".equals(type)) {
                tools.add(resolveToolEntryPoint(spec.getName(), workspaceRoot));
            } else {
                throw new IllegalArgumentException("Unsupported tool resource type: " + type);
            }
        }
        return tools;
    }

    /**
     * resolveRails.
     * 
     * @param resources resources
     * @param workspaceRoot workspaceRoot
     * @return the result
     * @since 0.1.7
     */
    private static List<Object> resolveRails(HarnessConfig.ResourcesSchema resources, Path workspaceRoot) {
        if (resources == null || resources.getRails().isEmpty()) {
            return List.of();
        }
        List<Object> rails = new ArrayList<>();
        for (HarnessConfig.RailResourceSchema spec : resources.getRails()) {
            String type = spec.getType();
            if ("builtin".equals(type)) {
                BiFunction<Path, HarnessConfig.RailResourceSchema, Object> factory = BUILTIN_RAILS.get(spec.getName());
                if (factory == null) {
                    throw new IllegalArgumentException("Unknown builtin rail: " + spec.getName());
                }
                rails.add(factory.apply(workspaceRoot, spec));
            } else if ("package".equals(type)) {
                rails.add(instantiateNoArgs(spec.getModule(), spec.getClassName()));
            } else if ("entry_point".equals(type)) {
                rails.add(resolveRailEntryPoint(spec.getName()));
            } else {
                throw new IllegalArgumentException("Unsupported rail resource type: " + type);
            }
        }
        return rails;
    }

    /**
     * createProgressiveToolRail.
     * 
     * @param root root
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    private static ProgressiveToolRail createProgressiveToolRail(Path root, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = railConfig(spec);
        return new ProgressiveToolRail(stringList(config.get("default_visible_tools")),
                stringList(config.get("always_visible_tools")), intValue(config.get("max_loaded_tools"), 20));
    }

    /**
     * createTaskPlanningRail.
     * 
     * @param root root
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    private static TaskPlanningRail createTaskPlanningRail(Path root, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = railConfig(spec);
        return new TaskPlanningRail(booleanValue(config.get("enable_progress_repeat"), false),
                intValue(config.get("list_tool_call_interval"), 20), stringMap(config.get("model_selection")));
    }

    /**
     * createTaskCompletionRail.
     * 
     * @param root root
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    private static TaskCompletionRail createTaskCompletionRail(Path root, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = railConfig(spec);
        return new TaskCompletionRail(stringValue(config.get("task_instruction"), null),
                stringValue(config.get("completion_promise"), null), intValue(config.get("required_confirmations"), 1),
                booleanValue(config.get("allow_promise_details"), false), optionalInteger(config.get("max_rounds")),
                optionalDuration(config));
    }

    /**
     * createContextProcessorRail.
     * 
     * @param root root
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    private static ContextProcessorRail createContextProcessorRail(Path root, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = railConfig(spec);
        return new ContextProcessorRail(booleanValue(config.get("preset"), true),
                stringList(config.get("processor_keys")), booleanValue(config.get("session_memory_enabled"), false));
    }

    /**
     * createMemoryRail.
     * 
     * @param root root
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    private static MemoryRail createMemoryRail(Path root, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = railConfig(spec);
        return new MemoryRail(null, booleanValue(config.get("isProactive"), true));
    }

    /**
     * createCodingMemoryRail.
     * 
     * @param root root
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    private static CodingMemoryRail createCodingMemoryRail(Path root, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = railConfig(spec);
        String configuredDir = stringValue(config.get("coding_memory_dir"), null);
        if (configuredDir != null && !Path.of(configuredDir).isAbsolute()) {
            configuredDir = root.resolve(configuredDir).toString();
        }
        return new CodingMemoryRail(configuredDir, null, booleanValue(config.get("isProactive"), true));
    }

    /**
     * createExternalMemoryRail.
     * 
     * @param root root
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    private static ExternalMemoryRail createExternalMemoryRail(Path root, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = railConfig(spec);
        MemoryProvider provider = createMemoryProvider(config);
        return new ExternalMemoryRail(provider,
                stringValue(firstPresent(config, new String[]{"user_id", "userId"}), "__default__"),
                stringValue(firstPresent(config, new String[]{"scope_id", "scopeId"}), "__default__"),
                stringValue(firstPresent(config, new String[]{"session_id", "sessionId"}), "__default__"));
    }

    /**
     * createMemoryProvider.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private static MemoryProvider createMemoryProvider(Map<String, Object> config) {
        String providerName =
            stringValue(firstPresent(config, new String[]{"provider", "provider_name", "providerName"}), "");
        if (providerName.isBlank()) {
            return nullValue();
        }
        return switch (providerName.toLowerCase(java.util.Locale.ROOT)) {
            case "openjiuwen", "jiuwen", "default" -> new OpenJiuwenMemoryProvider(providerConfig(config), null, null);
            case "mem0" -> new Mem0MemoryProvider();
            case "openviking", "viking" -> new OpenVikingMemoryProvider();
            default -> throw new IllegalArgumentException("Unknown external memory provider: " + providerName);
        };
    }

    /**
     * providerConfig.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> providerConfig(Map<String, Object> config) {
        Object nested = firstPresent(config, new String[]{"provider_config", "providerConfig", "config"});
        if (nested instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>(config);
        result.remove("provider");
        result.remove("provider_name");
        result.remove("providerName");
        return result;
    }

    /**
     * createVerificationRail.
     * 
     * @param root root
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    private static VerificationRail createVerificationRail(Path root, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = railConfig(spec);
        List<String> allowedTools = stringList(config.get("allowed_tools"));
        return allowedTools.isEmpty() ? new VerificationRail() : new VerificationRail(Set.copyOf(allowedTools));
    }

    /**
     * createSkillUseRail.
     * 
     * @param root root
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    private static SkillUseRail createSkillUseRail(Path root, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = railConfig(spec);
        List<String> skillDirs = stringList(config.get("skills_dir"));
        if (skillDirs.isEmpty()) {
            skillDirs = stringList(config.get("skills_dirs"));
        }
        return new SkillUseRail(skillDirs.stream().map(dir -> resolveRootPath(root, dir)).toList(),
                stringValue(config.get("skill_mode"), "auto_list"), stringList(config.get("enabled_skills")),
                stringList(config.get("disabled_skills")), remoteSkillSources(config));
    }

    /**
     * remoteSkillSources.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private static List<SkillUseRail.RemoteSkillSource> remoteSkillSources(Map<String, Object> config) {
        Object raw =
            firstPresent(config, new String[]{"remote_skills", "remoteSkills", "github_skills", "githubSkills"});
        if (raw == null) {
            return List.of();
        }
        List<?> entries = raw instanceof List<?> list ? list : List.of(raw);
        List<SkillUseRail.RemoteSkillSource> result = new ArrayList<>();
        for (Object entry : entries) {
            if (entry instanceof String spec && !spec.isBlank()) {
                result.add(remoteSkillSourceFromString(spec));
            } else if (entry instanceof Map<?, ?> map) {
                result.add(remoteSkillSourceFromMap(map));
            } else {
                // no-op
            }
        }
        return result;
    }

    /**
     * remoteSkillSourceFromString.
     * 
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    private static SkillUseRail.RemoteSkillSource remoteSkillSourceFromString(String spec) {
        String[] parts = spec.split("#", 2);
        String ref = parts.length > 1 ? parts[1] : "HEAD";
        String[] pathParts = parts[0].split("/", 3);
        return new SkillUseRail.RemoteSkillSource(pathParts.length > 0 ? pathParts[0] : "",
                pathParts.length > 1 ? pathParts[1] : "", ref, pathParts.length > 2 ? pathParts[2] : "", "");
    }

    /**
     * remoteSkillSourceFromMap.
     * 
     * @param source source
     * @return the result
     * @since 0.1.7
     */
    private static SkillUseRail.RemoteSkillSource remoteSkillSourceFromMap(Map<?, ?> source) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        String repoValue = stringValue(firstPresent(map, new String[]{"repo", "repository"}), "");
        String owner = stringValue(firstPresent(map, new String[]{"owner", "repo_owner", "repoOwner"}), "");
        String repo = stringValue(firstPresent(map, new String[]{"name", "repo_name", "repoName"}), "");
        if (!repoValue.isBlank()) {
            String[] parts = repoValue.split("/", 2);
            if (owner.isBlank() && parts.length > 0) {
                owner = parts[0];
            }
            if (repo.isBlank() && parts.length > 1) {
                repo = parts[1];
            }
        }
        return new SkillUseRail.RemoteSkillSource(owner, repo,
                stringValue(firstPresent(map, new String[]{"ref", "tree_ref", "treeRef", "branch"}), "HEAD"),
                stringValue(firstPresent(map, new String[]{"directory", "dir", "path"}), ""),
                stringValue(firstPresent(map, new String[]{"token", "github_token", "githubToken"}), ""));
    }

    /**
     * createSkillCreateRail.
     * 
     * @param root root
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    private static SkillCreateRail createSkillCreateRail(Path root, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = railConfig(spec);
        return new SkillCreateRail(resolveRootPath(root, stringValue(config.get("skills_dir"), "skills")),
                stringValue(config.get("language"), "cn"), booleanValue(config.get("auto_trigger"), true),
                intValue(config.get("tool_call_threshold"), 10), intValue(config.get("tool_diversity_threshold"), 5));
    }

    /**
     * createTeamSkillCreateRail.
     * 
     * @param root root
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    private static TeamSkillCreateRail createTeamSkillCreateRail(Path root, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = railConfig(spec);
        return new TeamSkillCreateRail(resolveRootPath(root, stringValue(config.get("skills_dir"), "skills")),
                stringValue(config.get("language"), "cn"), booleanValue(config.get("auto_trigger"), true),
                intValue(config.get("min_team_members_for_create"), 2));
    }

    /**
     * createTeamSkillRail.
     * 
     * @param root root
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    private static TeamSkillRail createTeamSkillRail(Path root, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = railConfig(spec);
        return new TeamSkillRail(resolveRootPath(root, stringValue(config.get("skills_dir"), "skills")),
                stringValue(config.get("language"), "cn"));
    }

    /**
     * railConfig.
     * 
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> railConfig(HarnessConfig.RailResourceSchema spec) {
        return spec != null && spec.getConfig() != null ? spec.getConfig() : Map.of();
    }

    /**
     * resolveRootPath.
     * 
     * @param root root
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    private static String resolveRootPath(Path root, String path) {
        if (path == null || path.isBlank()) {
            return root.resolve("skills").toString();
        }
        Path candidate = Path.of(path);
        return candidate.isAbsolute() ? candidate.toString() : root.resolve(candidate).toString();
    }

    /**
     * stringValue.
     * 
     * @param isValue isValue
     * @param isFallback isFallback
     * @return the result
     * @since 0.1.7
     */
    private static String stringValue(Object isValue, String isFallback) {
        return isValue == null || String.valueOf(isValue).isBlank() ? isFallback : String.valueOf(isValue);
    }

    /**
     * firstPresent.
     * 
     * @param config config
     * @param keys keys
     * @return the result
     * @since 0.1.7
     */
    private static Object firstPresent(Map<String, Object> config, String[] keys) {
        if (config == null) {
            return nullValue();
        }
        for (String key : keys) {
            if (config.containsKey(key)) {
                return config.get(key);
            }
        }
        return nullValue();
    }

    /**
     * stringList.
     * 
     * @param isValue isValue
     * @return the result
     * @since 0.1.7
     */
    private static List<String> stringList(Object isValue) {
        if (isValue == null) {
            return List.of();
        }
        if (isValue instanceof List<?> list) {
            return list.stream().filter(item -> item != null && !String.valueOf(item).isBlank()).map(String::valueOf)
                    .toList();
        }
        if (isValue instanceof String raw) {
            if (raw.isBlank()) {
                return List.of();
            }
            return java.util.Arrays.stream(raw.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
        }
        return List.of(String.valueOf(isValue));
    }

    /**
     * stringMap.
     * 
     * @param isValue isValue
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, String> stringMap(Object isValue) {
        if (!(isValue instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey()).trim();
            if (!key.isBlank()) {
                result.put(key, String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    /**
     * intValue.
     * 
     * @param isValue isValue
     * @param isFallback isFallback
     * @return the result
     * @since 0.1.7
     */
    private static int intValue(Object isValue, int isFallback) {
        Integer parsed = optionalInteger(isValue);
        if (parsed != null) {
            return parsed;
        }
        return isFallback;
    }

    /**
     * optionalInteger.
     * 
     * @param isValue isValue
     * @return the result
     * @since 0.1.7
     */
    private static Integer optionalInteger(Object isValue) {
        if (isValue == null || String.valueOf(isValue).isBlank()) {
            return nullValue();
        }
        if (isValue instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(isValue));
    }

    /**
     * booleanValue.
     * 
     * @param isValue isValue
     * @param isFallback isFallback
     * @return the result
     * @since 0.1.7
     */
    private static boolean booleanValue(Object isValue, boolean isFallback) {
        if (isValue == null || String.valueOf(isValue).isBlank()) {
            return isFallback;
        }
        if (isValue instanceof Boolean boolValue) {
            return boolValue;
        }
        return Boolean.parseBoolean(String.valueOf(isValue));
    }

    /**
     * optionalDuration.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private static Duration optionalDuration(Map<String, Object> config) {
        Object seconds = config.get("timeout_seconds");
        if (seconds != null) {
            return Duration.ofSeconds(longValue(seconds));
        }
        Object millis = config.get("timeout_millis");
        if (millis != null) {
            return Duration.ofMillis(longValue(millis));
        }
        return nullValue();
    }

    /**
     * longValue.
     * 
     * @param isValue isValue
     * @return the result
     * @since 0.1.7
     */
    private static long longValue(Object isValue) {
        if (isValue instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(isValue));
    }

    /**
     * Converts harness MCP resource specs into {@link McpServerConfig} instances.
     * <p>
     * For stdio transports, {@code command}/{@code args}/{@code env} are placed in {@code params}
     * and {@code serverPath} keeps the command only (aligned with Python).
     *
     * @param resources harness resources section; may be null
     * @return MCP server configs (empty when none are declared)
     * @since 0.1.7
     */
    private static List<McpServerConfig> resolveMcps(HarnessConfig.ResourcesSchema resources) {
        if (resources == null || resources.getMcps().isEmpty()) {
            return List.of();
        }
        List<McpServerConfig> result = new ArrayList<>();
        for (HarnessConfig.McpResourceSchema spec : resources.getMcps()) {
            String clientType = spec.getType() != null && !spec.getType().isBlank() ? spec.getType() : "stdio";
            String command = spec.getCommand() == null ? "" : spec.getCommand().trim();
            Map<String, Object> params = new LinkedHashMap<>();
            if ("stdio".equals(clientType)) {
                if (!command.isBlank()) {
                    params.put("command", command);
                }
                if (spec.getArgs() != null && !spec.getArgs().isEmpty()) {
                    params.put("args", new ArrayList<>(spec.getArgs()));
                }
                if (spec.getEnv() != null && !spec.getEnv().isEmpty()) {
                    params.put("env", new LinkedHashMap<>(spec.getEnv()));
                }
            }
            String serverPath = !command.isBlank() ? command : ("stdio".equals(clientType) ? "stdio" : "");
            result.add(McpServerConfig.builder().serverName(!command.isBlank() ? command : "mcp_server")
                    .serverPath(serverPath).clientType(clientType).params(params).build());
        }
        return result;
    }

    /**
     * toPromptSections.
     * 
     * @param sections sections
     * @return the result
     * @since 0.1.7
     */
    private static List<Map<String, Object>> toPromptSections(List<ResolvedSection> sections) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ResolvedSection section : sections) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", section.name());
            item.put("priority", section.priority());
            item.put("content", new LinkedHashMap<>(section.content()));
            result.add(item);
        }
        return result;
    }

    /**
     * resolveSkillDirs.
     * 
     * @param resources resources
     * @param sourcePath sourcePath
     * @return the result
     * @since 0.1.7
     */
    private static List<String> resolveSkillDirs(HarnessConfig.ResourcesSchema resources, Path sourcePath) {
        if (resources == null || resources.getSkills() == null || resources.getSkills().getDirs().isEmpty()) {
            return List.of();
        }
        return resources.getSkills().getDirs().stream()
                .map(dir -> sourcePath.getParent().resolve(dir).toAbsolutePath().normalize().toString()).toList();
    }

    /**
     * resolveSkillMode.
     * 
     * @param resources resources
     * @return the result
     * @since 0.1.7
     */
    private static String resolveSkillMode(HarnessConfig.ResourcesSchema resources) {
        if (resources == null || resources.getSkills() == null || resources.getSkills().getMode() == null) {
            return "all";
        }
        return resources.getSkills().getMode();
    }

    /**
     * writeFileSections.
     * 
     * @param fileSections fileSections
     * @param workspaceRoot workspaceRoot
     * @param language language
     * @since 0.1.7
     */
    private static void writeFileSections(List<ResolvedFileSection> fileSections, Path workspaceRoot, String language) {
        if (fileSections == null || fileSections.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(workspaceRoot);
            for (ResolvedFileSection section : fileSections) {
                String content = pickLanguage(section.content(), language);
                if (content == null || content.isBlank()) {
                    continue;
                }
                Path target = workspaceRoot.resolve(section.filename()).normalize();
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.writeString(target, content);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * pickLanguage.
     * 
     * @param content content
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private static String pickLanguage(Map<String, String> content, String language) {
        if (content == null || content.isEmpty()) {
            return nullValue();
        }
        String selected = content.get(language);
        if (selected != null && !selected.isBlank()) {
            return selected;
        }
        if (content.containsKey("cn")) {
            return content.get("cn");
        }
        return content.get("en");
    }

    /**
     * instantiateTool.
     * 
     * @param module module
     * @param className className
     * @param workspaceRoot workspaceRoot
     * @return the result
     * @since 0.1.7
     */
    private static Object instantiateTool(String module, String className, Path workspaceRoot) {
        String qualifiedName = module + "." + className;
        if (!isTrustedClass(qualifiedName)) {
            throw new IllegalArgumentException("Tool class is not trusted: " + qualifiedName);
        }
        try {
            Class<?> type = Class.forName(qualifiedName);
            try {
                Constructor<?> ctor = type.getConstructor(String.class);
                return ctor.newInstance(workspaceRoot.toString());
            } catch (NoSuchMethodException ignored) {
                return instantiateNoArgs(module, className);
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Cannot instantiate tool " + qualifiedName, ex);
        }
    }

    /**
     * instantiateNoArgs.
     * 
     * @param module module
     * @param className className
     * @return the result
     * @since 0.1.7
     */
    private static Object instantiateNoArgs(String module, String className) {
        String qualifiedName = module + "." + className;
        if (!isTrustedClass(qualifiedName)) {
            throw new IllegalArgumentException("Class is not trusted: " + qualifiedName);
        }
        try {
            Class<?> type = Class.forName(qualifiedName);
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Cannot instantiate " + qualifiedName, ex);
        }
    }

    /**
     * isTrustedClass.
     *
     * @param qualifiedName qualifiedName
     * @return the result
     * @since 0.1.7
     */
    private static boolean isTrustedClass(String qualifiedName) {
        return qualifiedName != null && qualifiedName.startsWith(TRUSTED_CLASS_PREFIX);
    }

    /**
     * resolveToolEntryPoint.
     * 
     * @param name name
     * @param workspaceRoot workspaceRoot
     * @return the result
     * @since 0.1.7
     */
    private static Object resolveToolEntryPoint(String name, Path workspaceRoot) {
        HarnessToolProvider provider = TOOL_ENTRY_POINTS.get(name);
        if (provider != null) {
            return provider.create(workspaceRoot);
        }
        for (HarnessToolProvider loaded : ServiceLoader.load(HarnessToolProvider.class)) {
            if (loaded.name().equals(name)) {
                TOOL_ENTRY_POINTS.put(name, loaded);
                return loaded.create(workspaceRoot);
            }
        }
        throw new IllegalArgumentException("Harness tool entry point not found: " + name);
    }

    /**
     * resolveRailEntryPoint.
     * 
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    private static Object resolveRailEntryPoint(String name) {
        HarnessRailProvider provider = RAIL_ENTRY_POINTS.get(name);
        if (provider != null) {
            return provider.create();
        }
        for (HarnessRailProvider loaded : ServiceLoader.load(HarnessRailProvider.class)) {
            if (loaded.name().equals(name)) {
                RAIL_ENTRY_POINTS.put(name, loaded);
                return loaded.create();
            }
        }
        throw new IllegalArgumentException("Harness rail entry point not found: " + name);
    }

    /**
     * toToolSpecs.
     * 
     * @param tools tools
     * @return the result
     * @since 0.1.7
     */
    private static List<HarnessConfig.ToolResourceSchema> toToolSpecs(List<Object> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<String> builtinGroups = new ArrayList<>();
        List<HarnessConfig.ToolResourceSchema> extras = new ArrayList<>();
        for (Object tool : tools) {
            String group = TOOL_CLASS_TO_GROUP.get(tool.getClass());
            if (group != null) {
                if (!builtinGroups.contains(group)) {
                    builtinGroups.add(group);
                }
            } else {
                extras.add(HarnessConfig.ToolResourceSchema.builder().type("package")
                        .module(tool.getClass().getPackageName()).className(tool.getClass().getSimpleName()).build());
            }
        }
        if (!builtinGroups.isEmpty()) {
            extras.add(0, HarnessConfig.ToolResourceSchema.builder().type("builtin").names(builtinGroups).build());
        }
        return extras;
    }

    /**
     * toRailSpecs.
     * 
     * @param rails rails
     * @return the result
     * @since 0.1.7
     */
    private static List<HarnessConfig.RailResourceSchema> toRailSpecs(List<Object> rails) {
        if (rails == null || rails.isEmpty()) {
            return List.of();
        }
        List<HarnessConfig.RailResourceSchema> specs = new ArrayList<>();
        for (Object rail : rails) {
            String name = RAIL_CLASS_TO_NAME.get(rail.getClass());
            if (name != null) {
                specs.add(HarnessConfig.RailResourceSchema.builder().type("builtin").name(name)
                        .config(toRailConfig(rail)).build());
            } else {
                specs.add(HarnessConfig.RailResourceSchema.builder().type("package")
                        .module(rail.getClass().getPackageName()).className(rail.getClass().getSimpleName()).build());
            }
        }
        return specs;
    }

    /**
     * toRailConfig.
     * 
     * @param rail rail
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> toRailConfig(Object rail) {
        Map<String, Object> config = new LinkedHashMap<>();
        if (rail instanceof ProgressiveToolRail progressive) {
            putIfNotEmpty(config, "default_visible_tools", new ArrayList<>(progressive.getDefaultVisibleTools()));
            putIfNotEmpty(config, "always_visible_tools", new ArrayList<>(progressive.getAlwaysVisibleTools()));
            putIfNotDefault(config, "max_loaded_tools", progressive.getMaxLoadedTools(), 20);
            return config;
        }
        if (rail instanceof TaskPlanningRail planning) {
            putIfTrue(config, "enable_progress_repeat", planning.isEnableProgressRepeat());
            putIfNotDefault(config, "list_tool_call_interval", planning.getListToolCallInterval(), 20);
            if (!planning.getModelSelection().isEmpty()) {
                config.put("model_selection", planning.getModelSelection());
            }
            return config;
        }
        if (rail instanceof TaskCompletionRail completion) {
            putIfNotBlank(config, "task_instruction", completion.getTaskInstruction());
            putIfNotBlank(config, "completion_promise", completion.getCompletionPromise());
            putIfNotDefault(config, "required_confirmations", completion.getRequiredConfirmations(), 1);
            putIfTrue(config, "allow_promise_details", completion.isAllowPromiseDetails());
            if (completion.getMaxRounds() != null) {
                config.put("max_rounds", completion.getMaxRounds());
            }
            if (completion.getTimeout() != null) {
                config.put("timeout_millis", completion.getTimeout().toMillis());
            }
            return config;
        }
        if (rail instanceof ContextProcessorRail processor) {
            putIfFalse(config, "preset", processor.isPreset());
            putIfNotEmpty(config, "processor_keys", processor.getProcessorKeys());
            putIfTrue(config, "session_memory_enabled", processor.isSessionMemoryEnabled());
            return config;
        }
        if (rail instanceof CodingMemoryRail codingMemory) {
            putIfNotBlank(config, "coding_memory_dir", codingMemory.codingMemoryDir());
            putIfFalse(config, "isProactive", codingMemory.isProactive());
            return config;
        }
        if (rail instanceof MemoryRail memory) {
            putIfFalse(config, "isProactive", memory.isProactive());
            return config;
        }
        if (rail instanceof VerificationRail verification) {
            if (!verification.getAllowedTools().equals(VerificationRail.DEFAULT_ALLOWED_TOOLS)) {
                config.put("allowed_tools", new ArrayList<>(verification.getAllowedTools()));
            }
            return config;
        }
        if (rail instanceof SkillUseRail skillUse) {
            putIfNotEmpty(config, "skills_dir", new ArrayList<>(skillUse.configuredSkillDirectories()));
            putIfNotDefault(config, "skill_mode", skillUse.skillMode(), "auto_list");
            putIfNotEmpty(config, "enabled_skills", new ArrayList<>(skillUse.enabledSkills()));
            putIfNotEmpty(config, "disabled_skills", new ArrayList<>(skillUse.disabledSkills()));
            if (!skillUse.remoteSkillSources().isEmpty()) {
                List<Map<String, Object>> remoteSkills = new ArrayList<>();
                for (SkillUseRail.RemoteSkillSource source : skillUse.remoteSkillSources()) {
                    Map<String, Object> remoteSkill = new LinkedHashMap<>();
                    putIfNotBlank(remoteSkill, "owner", source.owner());
                    putIfNotBlank(remoteSkill, "repo", source.repo());
                    putIfNotDefault(remoteSkill, "ref", source.ref(), "HEAD");
                    putIfNotBlank(remoteSkill, "directory", source.directory());
                    putIfNotBlank(remoteSkill, "token", source.token());
                    if (!remoteSkill.isEmpty()) {
                        remoteSkills.add(remoteSkill);
                    }
                }
                if (!remoteSkills.isEmpty()) {
                    config.put("remote_skills", remoteSkills);
                }
            }
            return config;
        }
        if (rail instanceof SkillCreateRail skillCreate) {
            putIfNotBlank(config, "skills_dir", skillCreate.getSkillsDir());
            putIfNotDefault(config, "language", skillCreate.getLanguage(), "cn");
            putIfFalse(config, "auto_trigger", skillCreate.isAutoTrigger());
            putIfNotDefault(config, "tool_call_threshold", skillCreate.getToolCallThreshold(), 10);
            putIfNotDefault(config, "tool_diversity_threshold", skillCreate.getToolDiversityThreshold(), 5);
            return config;
        }
        if (rail instanceof TeamSkillCreateRail teamSkillCreate) {
            putIfNotBlank(config, "skills_dir", teamSkillCreate.getSkillsDir());
            putIfNotDefault(config, "language", teamSkillCreate.getLanguage(), "cn");
            putIfFalse(config, "auto_trigger", teamSkillCreate.isAutoTrigger());
            putIfNotDefault(config, "min_team_members_for_create", teamSkillCreate.getMinTeamMembersForCreate(), 2);
            return config;
        }
        if (rail instanceof TeamSkillRail teamSkill) {
            putIfNotBlank(config, "skills_dir", teamSkill.getSkillsDir());
            putIfNotDefault(config, "language", teamSkill.getLanguage(), "cn");
            return config;
        }
        return config;
    }

    /**
     * putIfNotEmpty.
     * 
     * @param config config
     * @param key key
     * @param values values
     * @since 0.1.7
     */
    private static void putIfNotEmpty(Map<String, Object> config, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            config.put(key, values);
        }
    }

    /**
     * putIfNotBlank.
     * 
     * @param config config
     * @param key key
     * @param isValue isValue
     * @since 0.1.7
     */
    private static void putIfNotBlank(Map<String, Object> config, String key, String isValue) {
        if (isValue != null && !isValue.isBlank()) {
            config.put(key, isValue);
        }
    }

    /**
     * putIfTrue.
     * 
     * @param config config
     * @param key key
     * @param isValue isValue
     * @since 0.1.7
     */
    private static void putIfTrue(Map<String, Object> config, String key, boolean isValue) {
        if (isValue) {
            config.put(key, true);
        }
    }

    /**
     * putIfFalse.
     * 
     * @param config config
     * @param key key
     * @param isValue isValue
     * @since 0.1.7
     */
    private static void putIfFalse(Map<String, Object> config, String key, boolean isValue) {
        if (!isValue) {
            config.put(key, false);
        }
    }

    /**
     * putIfNotDefault.
     * 
     * @param config config
     * @param key key
     * @param isValue isValue
     * @param defaultValue defaultValue
     * @since 0.1.7
     */
    private static void putIfNotDefault(Map<String, Object> config, String key, int isValue, int defaultValue) {
        if (isValue != defaultValue) {
            config.put(key, isValue);
        }
    }

    /**
     * putIfNotDefault.
     * 
     * @param config config
     * @param key key
     * @param isValue isValue
     * @param defaultValue defaultValue
     * @since 0.1.7
     */
    private static void putIfNotDefault(Map<String, Object> config, String key, String isValue, String defaultValue) {
        if (isValue != null && !isValue.equalsIgnoreCase(defaultValue)) {
            config.put(key, isValue);
        }
    }

    /**
     * Public interface HarnessToolProvider used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public interface HarnessToolProvider {
        /**
         * name.
         * 
         * @return the result
         * @since 0.1.7
         */
        String name();

        /**
         * create.
         * 
         * @param workspaceRoot workspaceRoot
         * @return the result
         * @since 0.1.7
         */
        Object create(Path workspaceRoot);
    }

    /**
     * Public interface HarnessRailProvider used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public interface HarnessRailProvider {
        /**
         * name.
         * 
         * @return the result
         * @since 0.1.7
         */
        String name();

        /**
         * create.
         * 
         * @return the result
         * @since 0.1.7
         */
        Object create();
    }

    /**
     * nullValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static <T> T nullValue() {
        return null;
    }
}
