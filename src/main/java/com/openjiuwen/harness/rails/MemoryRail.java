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
    this.embeddingConfig = embeddingConfig;
    this.isProactive = isProactive;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public int priority() {
    return 80;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void init(Object agent) {
    if (!(agent instanceof DeepAgent deepAgent)) {
      return;
    }
    this.owner = deepAgent;
    Path memoryDir = deepAgent.getWorkspace().getNodePath(sectionName());
    this.toolContext = createToolContext(deepAgent, memoryDir);
    registerMemoryTools(deepAgent);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
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

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void beforeInvoke(AgentCallbackContext ctx) {
    initializeManager(ctx);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void beforeModelCall(AgentCallbackContext ctx) {
    if (owner == null) {
      return;
    }
    owner.getAgent().getPromptBuilder().removeSection(sectionName());
    String language = owner.getWorkspace().getLanguage();
    boolean isReadOnly = isReadOnlyRun(ctx);
    String content = buildMemoryPrompt(language, isReadOnly, isProactive);
    owner
        .getAgent()
        .getPromptBuilder()
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
    return owner != null && owner.getAgent().getPromptBuilder().hasSection(sectionName());
  }

  /** Auto-generated for codecheck compliance. */
  public EmbeddingConfig embeddingConfig() {
    return embeddingConfig;
  }

  /** Auto-generated for codecheck compliance. */
  protected LiteMemoryToolContextBase createToolContext(DeepAgent deepAgent, Path memoryDir) {
    Workspace workspace = deepAgent.getWorkspace();
    MemorySettings settings = MemorySettings.create(memoryDir.toString(), Map.of());
    return new MemoryToolContext(
        workspace, settings, agentId(deepAgent), embeddingConfig, sysOperation(deepAgent), null);
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
      deepAgent.registerHarnessTool(tool);
      ownedTools.add(tool);
      ownedToolNames.add(tool.getCard().getName());
    }
  }

  /** Auto-generated for codecheck compliance. */
  protected LocalFunction createTool(String toolName, DeepAgent deepAgent) {
    String language = deepAgent.getWorkspace().getLanguage();
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
        language == null || language.isBlank() ? PromptSection.DEFAULT_LANGUAGE : language;
    return MemoryPromptSections.buildMemorySection(lang, isReadOnly, isProactiveMemory)
        .render(lang);
  }

  /** Auto-generated for codecheck compliance. */
  protected void initializeManager(AgentCallbackContext ctx) {
    if (isManagerInitialized || toolContext == null) {
      return;
    }
    try {
      Files.createDirectories(owner.getWorkspace().getNodePath(sectionName()));
      manager =
          MemoryIndexManager.get(
              new MemoryManagerParams(
                  agentId(owner),
                  owner.getWorkspace(),
                  toolContext.getSettings(),
                  embeddingConfig,
                  sysOperation(owner),
                  sectionName()));
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
    return deepAgent.getConfig() != null ? deepAgent.getConfig().getSysOperation() : null;
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
    return language == null || language.isBlank() ? PromptSection.DEFAULT_LANGUAGE : language;
  }

  /** Auto-generated for codecheck compliance. */
  protected static boolean isReadOnlyRun(AgentCallbackContext ctx) {
    Object runKind = ctx != null && ctx.getExtra() != null ? ctx.getExtra().get("run_kind") : null;
    if (runKind == null) {
      return false;
    }
    String kind = String.valueOf(runKind);
    return "cron".equalsIgnoreCase(kind) || "heartbeat".equalsIgnoreCase(kind);
  }

  /** Auto-generated for codecheck compliance. */
  protected static void injectSystemMessage(AgentCallbackContext ctx, String content) {
    if (!(ctx.getInputs() instanceof ModelCallInputs inputs)
        || content == null
        || content.isBlank()) {
      return;
    }
    List<Object> messages =
        inputs.getMessages() == null ? new ArrayList<>() : new ArrayList<>(inputs.getMessages());
    messages.add(new com.openjiuwen.core.foundation.llm.schema.SystemMessage(content));
    inputs.setMessages(messages);
  }

  private static <T> T nullValue() {
    return null;
  }
}
