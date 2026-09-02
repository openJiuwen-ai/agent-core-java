/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.memory.lite.LiteMemoryToolContextBase;
import com.openjiuwen.core.memory.lite.MemoryIndexManager;
import com.openjiuwen.core.memory.lite.MemoryManagerParams;
import com.openjiuwen.core.memory.lite.MemorySettings;
import com.openjiuwen.core.memory.lite.MemoryToolContext;
import com.openjiuwen.core.memory.lite.MemoryToolOps;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.MemoryPromptSections;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataRegistry;
import com.openjiuwen.harness.workspace.Workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Public class MemoryRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class MemoryRail extends DeepAgentRail {
    /**
     * SECTION_PRIORITY.
     * 
     * @since 0.1.7
     */
    protected static final int SECTION_PRIORITY = 55;

    private final EmbeddingConfig embeddingConfig;
    private final boolean isProactive;

    /**
     * LinkedHashSet<>.
     * 
     * @since 0.1.7
     */
    private final Set<String> ownedToolNames = new LinkedHashSet<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<Tool> ownedTools = new ArrayList<>();

    /**
     * owner.
     * 
     * @since 0.1.7
     */
    protected DeepAgent owner;

    /**
     * toolContext.
     * 
     * @since 0.1.7
     */
    protected LiteMemoryToolContextBase toolContext;

    /**
     * manager.
     * 
     * @since 0.1.7
     */
    protected MemoryIndexManager manager;

    private boolean isManagerInitialized;

    /**
     * MemoryRail.
     * 
     * @since 0.1.7
     */
    public MemoryRail() {
        this(null, true);
    }

    /**
     * MemoryRail.
     * 
     * @param embeddingConfig embeddingConfig
     * @param isProactive isProactive
     * @since 0.1.7
     */
    public MemoryRail(EmbeddingConfig embeddingConfig, boolean isProactive) {
        this.embeddingConfig = embeddingConfig;
        this.isProactive = isProactive;
    }

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 80;
    }

    /**
     * init.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void init(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent)) {
            return;
        }
        this.owner = deepAgent;
        Path memoryDir = deepAgent.getWorkspace().getNodePath(sectionName());
        this.toolContext = createToolContext(deepAgent, memoryDir);
        registerMemoryTools(deepAgent);
    }

    /**
     * uninit.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void uninit(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            for (Tool tool : List.copyOf(ownedTools)) {
                deepAgent.unregisterHarnessTool(tool);
            }
            deepAgent.getAgent().getPromptBuilder().removeSection(sectionName());
        }
        ownedToolNames.clear();
        ownedTools.clear();
        manager = null;
        toolContext = null;
        owner = null;
        isManagerInitialized = false;
    }

    /**
     * beforeInvoke.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        initializeManager(ctx);
    }

    /**
     * beforeModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (owner == null) {
            return;
        }
        owner.getAgent().getPromptBuilder().removeSection(sectionName());
        String language = owner.getWorkspace().getLanguage();
        boolean isReadOnly = isReadOnlyRun(ctx);
        String content = buildMemoryPrompt(language, isReadOnly, isProactive);
        owner.getAgent().getPromptBuilder()
                .addSection(new PromptSection(sectionName(), Map.of(languageKey(language), content), SECTION_PRIORITY));
        injectSystemMessage(ctx, content);
    }

    /**
     * toolNames.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> toolNames() {
        return List.of("memory_search", "memory_get", "write_memory", "edit_memory", "read_memory");
    }

    /**
     * sectionName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String sectionName() {
        return "memory";
    }

    /**
     * isManagerInitialized.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isManagerInitialized() {
        return isManagerInitialized;
    }

    /**
     * isProactive.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isProactive() {
        return isProactive;
    }

    /**
     * registeredToolNames.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Set<String> registeredToolNames() {
        return Set.copyOf(ownedToolNames);
    }

    /**
     * hasMemoryPromptSection.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasMemoryPromptSection() {
        return owner != null && owner.getAgent().getPromptBuilder().hasSection(sectionName());
    }

    /**
     * embeddingConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public EmbeddingConfig embeddingConfig() {
        return embeddingConfig;
    }

    /**
     * createToolContext.
     * 
     * @param deepAgent deepAgent
     * @param memoryDir memoryDir
     * @return the result
     * @since 0.1.7
     */
    protected LiteMemoryToolContextBase createToolContext(DeepAgent deepAgent, Path memoryDir) {
        Workspace workspace = deepAgent.getWorkspace();
        MemorySettings settings = MemorySettings.create(memoryDir.toString(), Map.of());
        return new MemoryToolContext(workspace, settings, agentId(deepAgent), embeddingConfig, sysOperation(deepAgent),
                null);
    }

    /**
     * registerMemoryTools.
     * 
     * @param deepAgent deepAgent
     * @since 0.1.7
     */
    protected void registerMemoryTools(DeepAgent deepAgent) {
        for (String toolName : toolNames()) {
            if (isWriteTool(toolName) && isReadOnlyRail()) {
                continue;
            }
            LocalFunction tool = createTool(toolName, deepAgent);
            if (tool == null) {
                continue;
            }
            deepAgent.registerHarnessTool(tool);
            ownedTools.add(tool);
            ownedToolNames.add(tool.getCard().getName());
        }
    }

    /**
     * createTool.
     * 
     * @param toolName toolName
     * @param deepAgent deepAgent
     * @return the result
     * @since 0.1.7
     */
    protected LocalFunction createTool(String toolName, DeepAgent deepAgent) {
        String language = deepAgent.getWorkspace().getLanguage();
        String id = agentId(deepAgent) + "." + sectionName() + "." + toolName;
        return new LocalFunction(ToolMetadataRegistry.buildToolCard(toolName, id, language),
                inputs -> invokeMemoryTool(toolName, inputs));
    }

    /**
     * invokeMemoryTool.
     * 
     * @param toolName toolName
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    protected Object invokeMemoryTool(String toolName, Map<String, Object> inputs) {
        MemoryToolContext ctx = toolContext instanceof MemoryToolContext memoryToolContext ? memoryToolContext : null;
        return switch (toolName) {
            case "memory_search" ->
                MemoryToolOps.memorySearchWithContext(ctx, stringArg(inputs, "query"), intArg(inputs, "max_results"),
                        doubleArg(inputs, "min_score"), stringArgOrNull(inputs, "session_key"));
            case "memory_get" -> MemoryToolOps.memoryGetWithContext(ctx, stringArg(inputs, "path"),
                    intArg(inputs, "from_line"), intArg(inputs, "lines"));
            case "read_memory" -> MemoryToolOps.readMemoryWithContext(ctx, stringArg(inputs, "path"),
                    intArg(inputs, "offset"), intArg(inputs, "limit"));
            case "write_memory" -> MemoryToolOps.writeMemoryWithContext(ctx, stringArg(inputs, "path"),
                    stringArg(inputs, "content"), booleanArg(inputs, "append"));
            case "edit_memory" -> MemoryToolOps.editMemoryWithContext(ctx, stringArg(inputs, "path"),
                    stringArg(inputs, "old_text"), stringArg(inputs, "new_text"));
            default -> Map.of("success", false, "error", "Unknown memory tool: " + toolName);
        };
    }

    /**
     * buildMemoryPrompt.
     * 
     * @param language language
     * @param isReadOnly isReadOnly
     * @param isProactiveMemory isProactiveMemory
     * @return the result
     * @since 0.1.7
     */
    protected String buildMemoryPrompt(String language, boolean isReadOnly, boolean isProactiveMemory) {
        String lang = language == null || language.isBlank() ? PromptSection.DEFAULT_LANGUAGE : language;
        return MemoryPromptSections.buildMemorySection(lang, isReadOnly, isProactiveMemory).render(lang);
    }

    /**
     * initializeManager.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    protected void initializeManager(AgentCallbackContext ctx) {
        if (isManagerInitialized || toolContext == null) {
            return;
        }
        try {
            Files.createDirectories(owner.getWorkspace().getNodePath(sectionName()));
            manager = MemoryIndexManager.get(new MemoryManagerParams(agentId(owner), owner.getWorkspace(),
                    toolContext.getSettings(), embeddingConfig, sysOperation(owner), sectionName()));
            isManagerInitialized = manager != null;
            if (isManagerInitialized) {
                toolContext.ensureManager();
            }
        } catch (IOException ignored) {
            isManagerInitialized = false;
        }
    }

    /**
     * isReadOnlyRail.
     * 
     * @return the result
     * @since 0.1.7
     */
    protected boolean isReadOnlyRail() {
        return false;
    }

    /**
     * isWriteTool.
     * 
     * @param toolName toolName
     * @return the result
     * @since 0.1.7
     */
    protected boolean isWriteTool(String toolName) {
        return "write_memory".equals(toolName) || "edit_memory".equals(toolName);
    }

    /**
     * agentId.
     * 
     * @param deepAgent deepAgent
     * @return the result
     * @since 0.1.7
     */
    protected static String agentId(DeepAgent deepAgent) {
        return deepAgent.getCard() != null && deepAgent.getCard().getId() != null
                && !deepAgent.getCard().getId().isBlank() ? deepAgent.getCard().getId() : "default";
    }

    /**
     * sysOperation.
     * 
     * @param deepAgent deepAgent
     * @return the result
     * @since 0.1.7
     */
    protected static SysOperation sysOperation(DeepAgent deepAgent) {
        return deepAgent.getConfig() != null ? deepAgent.getConfig().getSysOperation() : null;
    }

    /**
     * stringArg.
     * 
     * @param inputs inputs
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    protected static String stringArg(Map<String, Object> inputs, String key) {
        Object value = inputs != null ? inputs.get(key) : null;
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * stringArgOrNull.
     * 
     * @param inputs inputs
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    protected static String stringArgOrNull(Map<String, Object> inputs, String key) {
        Object value = inputs != null ? inputs.get(key) : null;
        return value == null ? null : String.valueOf(value);
    }

    /**
     * intArg.
     * 
     * @param inputs inputs
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    protected static Integer intArg(Map<String, Object> inputs, String key) {
        Object value = inputs != null ? inputs.get(key) : null;
        if (value == null) {
            return nullValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return nullValue();
        }
    }

    /**
     * doubleArg.
     * 
     * @param inputs inputs
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    protected static Double doubleArg(Map<String, Object> inputs, String key) {
        Object value = inputs != null ? inputs.get(key) : null;
        if (value == null) {
            return nullValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return nullValue();
        }
    }

    /**
     * booleanArg.
     * 
     * @param inputs inputs
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    protected static boolean booleanArg(Map<String, Object> inputs, String key) {
        Object value = inputs != null ? inputs.get(key) : null;
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * languageKey.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    protected static String languageKey(String language) {
        return language == null || language.isBlank() ? PromptSection.DEFAULT_LANGUAGE : language;
    }

    /**
     * isReadOnlyRun.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    protected static boolean isReadOnlyRun(AgentCallbackContext ctx) {
        Object runKind = ctx != null && ctx.getExtra() != null ? ctx.getExtra().get("run_kind") : null;
        if (runKind == null) {
            return false;
        }
        String kind = String.valueOf(runKind);
        return "cron".equalsIgnoreCase(kind) || "heartbeat".equalsIgnoreCase(kind);
    }

    /**
     * injectSystemMessage.
     * 
     * @param ctx ctx
     * @param content content
     * @since 0.1.7
     */
    protected static void injectSystemMessage(AgentCallbackContext ctx, String content) {
        if (!(ctx.getInputs() instanceof ModelCallInputs inputs) || content == null || content.isBlank()) {
            return;
        }
        List<Object> messages =
            inputs.getMessages() == null ? new ArrayList<>() : new ArrayList<>(inputs.getMessages());
        messages.add(new com.openjiuwen.core.foundation.llm.schema.SystemMessage(content));
        inputs.setMessages(messages);
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
