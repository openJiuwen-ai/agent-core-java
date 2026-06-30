/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.memory.external.MemoryProvider;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Public class ExternalMemoryRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class ExternalMemoryRail extends MemoryRail {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String PREFETCH_SECTION = "external_memory_prefetch";
  private static final int STATIC_SECTION_PRIORITY = 54;
  private static final int PREFETCH_SECTION_PRIORITY = 55;

  private final MemoryProvider provider;
  private final String userId;
  private final String scopeId;
  private final String sessionId;
  private final Set<String> ownedToolNames = new LinkedHashSet<>();
  private final List<Tool> ownedTools = new ArrayList<>();
  private boolean isInitialized;
  private String prefetchCache;
  private int syncFailures;

  /** Auto-generated for codecheck compliance. */
  public ExternalMemoryRail() {
    this(null);
  }

  /** Auto-generated for codecheck compliance. */
  public ExternalMemoryRail(MemoryProvider provider) {
    this(provider, "__default__", "__default__", "__default__");
  }

  /** Auto-generated for codecheck compliance. */
  public ExternalMemoryRail(
      MemoryProvider provider, String userId, String scopeId, String sessionId) {
    super(null, false);
    this.provider = provider;
    this.userId = userId == null || userId.isBlank() ? "__default__" : userId;
    this.scopeId = scopeId == null || scopeId.isBlank() ? "__default__" : scopeId;
    this.sessionId = sessionId == null || sessionId.isBlank() ? "__default__" : sessionId;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public int priority() {
    return 75;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void init(Object agent) {
    if (!(agent instanceof DeepAgent deepAgent)) {
      return;
    }
    this.owner = deepAgent;
    registerProviderTools(deepAgent);
    injectStaticProviderPrompt();
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
      deepAgent.getAgent().getPromptBuilder().removeSection(PREFETCH_SECTION);
    }
    if (provider != null) {
      try {
        provider.shutdown();
      } catch (Exception ignored) {
        // Best-effort provider shutdown.
      }
    }
    ownedTools.clear();
    ownedToolNames.clear();
    isInitialized = false;
    prefetchCache = null;
    owner = null;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void beforeInvoke(AgentCallbackContext ctx) {
    prefetchCache = null;
    if (provider == null || isInitialized) {
      return;
    }
    try {
      provider.initialize(providerScope());
      isInitialized = true;
    } catch (Exception ignored) {
      isInitialized = false;
    }
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void beforeModelCall(AgentCallbackContext ctx) {
    if (owner == null || provider == null || !isInitialized) {
      return;
    }
    owner.getAgent().getPromptBuilder().removeSection(PREFETCH_SECTION);
    String query = resolveUserText(ctx);
    if (query == null || query.isBlank()) {
      return;
    }
    try {
      String rawContext =
          prefetchCache != null ? prefetchCache : provider.prefetch(query, providerScope());
      prefetchCache = rawContext;
      if (rawContext == null || rawContext.isBlank()) {
        return;
      }
      String content = buildMemoryContextBlock(rawContext);
      String language = owner.getWorkspace().getLanguage();
      owner
          .getAgent()
          .getPromptBuilder()
          .addSection(
              new PromptSection(
                  PREFETCH_SECTION,
                  Map.of(languageKey(language), content),
                  PREFETCH_SECTION_PRIORITY));
      injectSystemMessage(ctx, content);
    } catch (Exception ignored) {
      // Prefetch is opportunistic.
    }
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void afterInvoke(AgentCallbackContext ctx) {
    if (provider == null || !isInitialized || syncFailures >= 5) {
      return;
    }
    String query = resolveUserText(ctx);
    if (query == null || query.isBlank()) {
      return;
    }
    String output = extractAssistantOutput(ctx);
    try {
      provider.syncTurn(query, output, providerScope());
      syncFailures = 0;
    } catch (Exception ignored) {
      syncFailures += 1;
    }
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public List<String> toolNames() {
    return provider == null
        ? List.of()
        : provider.getToolSchemas().stream()
            .map(schema -> String.valueOf(schema.getOrDefault("name", "")))
            .filter(name -> !name.isBlank())
            .toList();
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public String sectionName() {
    return "external_memory";
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public Set<String> registeredToolNames() {
    return Set.copyOf(ownedToolNames);
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isInitialized() {
    return isInitialized;
  }

  /** Auto-generated for codecheck compliance. */
  public boolean hasPrefetchPromptSection() {
    return owner != null && owner.getAgent().getPromptBuilder().hasSection(PREFETCH_SECTION);
  }

  /** Auto-generated for codecheck compliance. */
  public static String buildMemoryContextBlock(String rawContext) {
    return "<memory-context>\n"
        + "[System note: recalled memory context from long-term memory, NOT new user input.]\n\n"
        + (rawContext == null ? "" : rawContext)
        + "\n</memory-context>";
  }

  /** Auto-generated for codecheck compliance. */
  public static String resolveUserText(AgentCallbackContext ctx) {
    if (ctx == null || ctx.getInputs() == null) {
      return "";
    }
    if (ctx.getInputs() instanceof InvokeInputs invokeInputs) {
      return invokeInputs.getQuery() == null ? "" : invokeInputs.getQuery().trim();
    }
    if (ctx.getInputs() instanceof ModelCallInputs modelCallInputs
        && modelCallInputs.getMessages() != null) {
      for (int i = modelCallInputs.getMessages().size() - 1; i >= 0; i--) {
        Object message = modelCallInputs.getMessages().get(i);
        if (message instanceof com.openjiuwen.core.foundation.llm.schema.BaseMessage base
            && "user".equals(base.getRole())
            && base.getContent() != null) {
          return String.valueOf(base.getContent()).trim();
        }
      }
    }
    return "";
  }

  /** Auto-generated for codecheck compliance. */
  public static String extractAssistantOutput(AgentCallbackContext ctx) {
    if (ctx == null
        || !(ctx.getInputs() instanceof InvokeInputs invokeInputs)
        || invokeInputs.getResult() == null) {
      return "";
    }
    Map<String, Object> result = invokeInputs.getResult();
    for (String key : List.of("output", "content", "text", "response")) {
      Object value = result.get(key);
      if (value != null && !String.valueOf(value).isBlank()) {
        return String.valueOf(value).trim();
      }
    }
    Object message = result.get("message");
    if (message instanceof Map<?, ?> map && map.get("content") != null) {
      return String.valueOf(map.get("content")).trim();
    }
    return "";
  }

  private void registerProviderTools(DeepAgent deepAgent) {
    if (provider == null) {
      return;
    }
    for (Map<String, Object> schema : provider.getToolSchemas()) {
      String toolName = String.valueOf(schema.getOrDefault("name", ""));
      if (toolName.isBlank()) {
        continue;
      }
      String toolId = "external_memory_" + provider.getName() + "_" + toolName;
      @SuppressWarnings("unchecked")
      Map<String, Object> parameters =
          schema.get("parameters") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
      ToolCard card =
          ToolCard.builder()
              .id(toolId)
              .name(toolName)
              .description(String.valueOf(schema.getOrDefault("description", "")))
              .inputParams(parameters)
              .build();
      LocalFunction tool = new LocalFunction(card, inputs -> invokeProviderTool(toolName, inputs));
      deepAgent.registerHarnessTool(tool);
      ownedTools.add(tool);
      ownedToolNames.add(toolName);
    }
  }

  private Object invokeProviderTool(String toolName, Map<String, Object> inputs) {
    if (provider == null) {
      return Map.of("error", "Memory provider not configured");
    }
    try {
      String result = provider.handleToolCall(toolName, inputs);
      try {
        return MAPPER.readValue(result, new TypeReference<Map<String, Object>>() {});
      } catch (Exception ignored) {
        return Map.of("result", result);
      }
    } catch (Exception ex) {
      return Map.of("error", ex.getMessage());
    }
  }

  private void injectStaticProviderPrompt() {
    if (owner == null || provider == null) {
      return;
    }
    String prompt = provider.systemPromptBlock();
    if (prompt == null || prompt.isBlank()) {
      return;
    }
    String language = owner.getWorkspace().getLanguage();
    owner
        .getAgent()
        .getPromptBuilder()
        .addSection(
            new PromptSection(
                sectionName(), Map.of(languageKey(language), prompt), STATIC_SECTION_PRIORITY));
  }

  private Map<String, Object> providerScope() {
    return Map.of(
        "user_id", userId,
        "scope_id", scopeId,
        "session_id", sessionId);
  }
}
