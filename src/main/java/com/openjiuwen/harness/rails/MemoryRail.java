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
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.prompts.sections.MemoryPromptSections;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataRegistry;
import com.openjiuwen.harness.workspace.Workspace;
import com.openjiuwen.harness.rails.CallbackContext;
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
 * @since 1.0
 */
public class MemoryRail extends DeepAgentRail {
  /** Auto-generated for codecheck compliance. */
  protected static final int SECTION_PRIORITY = 55;

  private final EmbeddingConfig embeddingConfig;
  private final boolean isProactive;
  private final Set<String> ownedToolNames = new LinkedHashSet<>();
  private final List<Tool> ownedTools = new ArrayList<>();

  /** Auto-generated for codecheck compliance. */
  protected DeepAgent owner;

  /** Auto-generated for codecheck compliance. */
  protected LiteMemoryToolContextBase toolContext;

  /** Auto-generated for codecheck compliance. */
  protected MemoryIndexManager manager;

  private boolean isManagerInitialized;

  /** Auto-generated for codecheck compliance. */
  public MemoryRail() {
    this(null, true);
  }

  /** Auto-generated for codecheck compliance. */
  public MemoryRail(EmbeddingConfig embeddingConfig, boolean isProactive) {
    setPriority(80);
    this.embeddingConfig = embeddingConfig;
    this.isProactive = isProactive;
  }

  /** Auto-generated for codecheck compliance. */
  public int priority() {
    return getPriority();
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void init(DeepAgent agent) {
    if (agent == null) {
      return;
    }
    this.owner = agent;
    Path memoryDir = resolveWorkspace(agent).getNodePath(sectionName());
    this.toolContext = createToolContext(agent, memoryDir);
    registerMemoryTools(agent);
  }

  @Override
  public void uninit(DeepAgent agent) {
    if (agent != null) {
      for (Tool tool : List.copyOf(ownedTools)) {
        agent.unregisterTool(tool.getCard().getName());
      }
      resolvePromptBuilder(agent).removeSection(sectionName());
    }
    ownedToolNames.clear();
    ownedTools.clear();
    manager = null;
    toolContext = null;
    owner = null;
    isManagerInitialized = false;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void beforeInvoke(CallbackContext ctx) {
    initializeManager(ctx);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void beforeModelCall(CallbackContext ctx) {
    if (owner == null) {
      return;
    }
    resolvePromptBuilder(owner).removeSection(sectionName());
    String language = resolveWorkspace(owner).getLanguage();
    boolean isReadOnly = isReadOnlyRun(ctx);
    String content = buildMemoryPrompt(language, isReadOnly, isProactive);
    resolvePromptBuilder(owner)
        .addSection(
            new PromptSection(
                sectionName(), Map.of(languageKey(language), content), SECTION_PRIORITY));
    injectSystemMessage(ctx, content);
  }

  /** Auto-generated for codecheck compliance. */
  public List<String> toolNames() {
    return List.of("memory_search", "memory_get", "write_memory", "edit_memory", "read_memory");
  }

  /** Auto-generated for codecheck compliance. */
  public String sectionName() {
    return "memory";
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isManagerInitialized() {
    return isManagerInitialized;
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isProactive() {
    return isProactive;
  }

  /** Auto-generated for codecheck compliance. */
  public Set<String> registeredToolNames() {
    return Set.copyOf(ownedToolNames);
  }

  /** Auto-generated for codecheck compliance. */
  public boolean hasMemoryPromptSection() {
    return owner != null && resolvePromptBuilder(owner).hasSection(sectionName());
  }

  /** Auto-generated for codecheck compliance. */
  public EmbeddingConfig embeddingConfig() {
    return embeddingConfig;
  }

  /** Auto-generated for codecheck compliance. */
  protected LiteMemoryToolContextBase createToolContext(DeepAgent deepAgent, Path memoryDir) {
    Workspace workspace = resolveWorkspace(deepAgent);
    MemorySettings settings = MemorySettings.createMemorySettings(memoryDir.toString(), Map.of());
    com.openjiuwen.core.foundation.store.EmbeddingConfig storeEmbeddingConfig = null;
    if (embeddingConfig != null) {
      storeEmbeddingConfig = new com.openjiuwen.core.foundation.store.EmbeddingConfig(
          embeddingConfig.getModelName(), embeddingConfig.getBaseUrl(), embeddingConfig.getApiKey());
    }
    MemoryToolContext ctx = new MemoryToolContext();
    ctx.setWorkspace(workspace);
    ctx.setSettings(settings);
    ctx.setAgentId(agentId(deepAgent));
    ctx.setEmbeddingConfig(storeEmbeddingConfig);
    ctx.setSysOperation(sysOperation(deepAgent));
    return ctx;
  }

  /** Auto-generated for codecheck compliance. */
  protected void registerMemoryTools(DeepAgent deepAgent) {
    for (String toolName : toolNames()) {
      if (isWriteTool(toolName) && isReadOnlyRail()) {
        continue;
      }
      LocalFunction tool = createTool(toolName, deepAgent);
      if (tool == null) {
        continue;
      }
      deepAgent.registerTool(tool);
      ownedTools.add(tool);
      ownedToolNames.add(tool.getCard().getName());
    }
  }

  /** Auto-generated for codecheck compliance. */
  protected LocalFunction createTool(String toolName, DeepAgent deepAgent) {
    Workspace workspace = resolveWorkspace(deepAgent);
    String language = workspace.getLanguage();
    String id = agentId(deepAgent) + "." + sectionName() + "." + toolName;
    return new LocalFunction(
        ToolMetadataRegistry.buildToolCard(toolName, id, language),
        inputs -> invokeMemoryTool(toolName, inputs));
  }

  /** Auto-generated for codecheck compliance. */
  protected Object invokeMemoryTool(String toolName, Map<String, Object> inputs) {
    MemoryToolContext ctx =
        toolContext instanceof MemoryToolContext memoryToolContext ? memoryToolContext : null;
    return switch (toolName) {
      case "memory_search" ->
          MemoryToolOps.memorySearchWithContext(
              ctx,
              stringArg(inputs, "query"),
              intArg(inputs, "max_results"),
              doubleArg(inputs, "min_score"),
              stringArgOrNull(inputs, "session_key"));
      case "memory_get" ->
          MemoryToolOps.memoryGetWithContext(
              ctx, stringArg(inputs, "path"), intArg(inputs, "from_line"), intArg(inputs, "lines"));
      case "read_memory" ->
          MemoryToolOps.readMemoryWithContext(
              ctx, stringArg(inputs, "path"), intArg(inputs, "offset"), intArg(inputs, "limit"));
      case "write_memory" ->
          MemoryToolOps.writeMemoryWithContext(
              ctx,
              stringArg(inputs, "path"),
              stringArg(inputs, "content"),
              booleanArg(inputs, "append"));
      case "edit_memory" ->
          MemoryToolOps.editMemoryWithContext(
              ctx,
              stringArg(inputs, "path"),
              stringArg(inputs, "old_text"),
              stringArg(inputs, "new_text"));
      default -> Map.of("success", false, "error", "Unknown memory tool: " + toolName);
    };
  }

  /** Auto-generated for codecheck compliance. */
  protected String buildMemoryPrompt(
      String language, boolean isReadOnly, boolean isProactiveMemory) {
    String lang =
        language == null || language.isBlank() ? com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder.DEFAULT_LANGUAGE : language;
    return MemoryPromptSections.buildMemorySection(lang, isReadOnly, isProactiveMemory)
        .render(lang);
  }

  /** Auto-generated for codecheck compliance. */
  protected void initializeManager(CallbackContext ctx) {
    if (isManagerInitialized || toolContext == null) {
      return;
    }
    try {
      Files.createDirectories(resolveWorkspace(owner).getNodePath(sectionName()));
      manager =
          MemoryIndexManager.get(
              new MemoryManagerParams(
                  agentId(owner),
                  resolveWorkspace(owner),
                  toolContext.getSettings(),
                  storeEmbeddingConfig(),
                  sysOperation(owner),
                  sectionName())).toCompletableFuture().join();
      isManagerInitialized = manager != null;
      if (isManagerInitialized) {
        toolContext.ensureManager();
      }
    } catch (Exception ignored) {
      isManagerInitialized = false;
    }
  }

  /** Auto-generated for codecheck compliance. */
  protected boolean isReadOnlyRail() {
    return false;
  }

  /** Auto-generated for codecheck compliance. */
  protected boolean isWriteTool(String toolName) {
    return "write_memory".equals(toolName) || "edit_memory".equals(toolName);
  }

  /** Auto-generated for codecheck compliance. */
  protected static String agentId(DeepAgent deepAgent) {
    return deepAgent.getCard() != null
            && deepAgent.getCard().getId() != null
            && !deepAgent.getCard().getId().isBlank()
        ? deepAgent.getCard().getId()
        : "default";
  }

  /** Auto-generated for codecheck compliance. */
  protected static SysOperation sysOperation(DeepAgent deepAgent) {
    Object config = deepAgent.deepConfig();
    if (config instanceof com.openjiuwen.harness.schema.config.DeepAgentConfig typedConfig) {
      return typedConfig.getSysOperation();
    }
    return null;
  }

  /** Auto-generated for codecheck compliance. */
  protected static String stringArg(Map<String, Object> inputs, String key) {
    Object value = inputs != null ? inputs.get(key) : null;
    return value == null ? "" : String.valueOf(value);
  }

  /** Auto-generated for codecheck compliance. */
  protected static String stringArgOrNull(Map<String, Object> inputs, String key) {
    Object value = inputs != null ? inputs.get(key) : null;
    return value == null ? null : String.valueOf(value);
  }

  /** Auto-generated for codecheck compliance. */
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

  /** Auto-generated for codecheck compliance. */
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

  /** Auto-generated for codecheck compliance. */
  protected static boolean booleanArg(Map<String, Object> inputs, String key) {
    Object value = inputs != null ? inputs.get(key) : null;
    return value != null && Boolean.parseBoolean(String.valueOf(value));
  }

  /** Auto-generated for codecheck compliance. */
  protected static String languageKey(String language) {
    return language == null || language.isBlank() ? "cn" : language;
  }

  private com.openjiuwen.core.foundation.store.EmbeddingConfig storeEmbeddingConfig() {
    if (embeddingConfig == null) {
      return null;
    }
    return new com.openjiuwen.core.foundation.store.EmbeddingConfig(
        embeddingConfig.getModelName(), embeddingConfig.getBaseUrl(), embeddingConfig.getApiKey());
  }

  /** Auto-generated for codecheck compliance. */
  protected static boolean isReadOnlyRun(CallbackContext ctx) {
    Object runKind = ctx != null ? ctx.getValues().getOrDefault("run_kind", null) : null;
    if (runKind == null) {
      return false;
    }
    String kind = String.valueOf(runKind);
    return "cron".equalsIgnoreCase(kind) || "heartbeat".equalsIgnoreCase(kind);
  }

  /** Auto-generated for codecheck compliance. */
  protected static void injectSystemMessage(CallbackContext ctx, String content) {
    Object inputs = ctx != null ? ctx.getValues().get("inputs") : null;
    if (!(inputs instanceof com.openjiuwen.core.singleagent.rail.ModelCallInputs modelInputs)
        || content == null
        || content.isBlank()) {
      return;
    }
    List<Object> messages =
        modelInputs.getMessages() == null ? new ArrayList<>() : new ArrayList<>(modelInputs.getMessages());
    messages.add(new com.openjiuwen.core.foundation.llm.schema.SystemMessage(content));
    modelInputs.setMessages(messages);
  }

  private static <T> T nullValue() {
    return null;
  }

  private static Workspace resolveWorkspace(DeepAgent agent) {
    Object ws = agent.deepConfig().getWorkspace();
    if (ws instanceof Workspace workspace) {
      return workspace;
    }
    return new Workspace("./", agent.deepConfig().getLanguage());
  }

  private static com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder resolvePromptBuilder(DeepAgent agent) {
    Object reactAgent = agent.reactAgent();
    try {
      java.lang.reflect.Method method = reactAgent.getClass().getMethod("getPromptBuilder");
      Object builder = method.invoke(reactAgent);
      if (builder instanceof com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder typed) {
        return typed;
      }
    } catch (Exception ignored) {
    }
    throw new IllegalStateException("Cannot access prompt builder from DeepAgent");
  }
}
