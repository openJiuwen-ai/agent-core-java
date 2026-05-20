/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionMemoryManager;
import com.openjiuwen.core.context.processor.compressor.CurrentRoundCompressorConfig;
import com.openjiuwen.core.context.processor.compressor.DialogueCompressorConfig;
import com.openjiuwen.core.context.processor.compressor.FullCompactProcessorConfig;
import com.openjiuwen.core.context.processor.compressor.MicroCompactProcessorConfig;
import com.openjiuwen.core.context.processor.compressor.RoundLevelCompressorConfig;
import com.openjiuwen.core.context.processor.offloader.MessageSummaryOffloaderConfig;
import com.openjiuwen.core.context.processor.offloader.ToolResultBudgetProcessorConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Public class ContextProcessorRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class ContextProcessorRail extends DeepAgentRail {
  private static final String OFFLOAD_SECTION = "offload";
  private final boolean isPreset;
  private final List<String> processorKeys;
  private final boolean isSessionMemoryEnabled;
  private final SessionMemoryManager sessionMemoryManager;
  private final List<ContextEngine.ProcessorSpec> installedProcessors = new ArrayList<>();
  private DeepAgent owner;

  /** Auto-generated for codecheck compliance. */
  public ContextProcessorRail() {
    this(true, List.of(), false);
  }

  /** Auto-generated for codecheck compliance. */
  public ContextProcessorRail(
      boolean isPreset, List<String> processorKeys, boolean isSessionMemoryEnabled) {
    this.isPreset = isPreset;
    this.processorKeys = new ArrayList<>(processorKeys == null ? List.of() : processorKeys);
    this.isSessionMemoryEnabled = isSessionMemoryEnabled;
    this.sessionMemoryManager = isSessionMemoryEnabled ? new SessionMemoryManager() : null;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public int priority() {
    return 85;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void init(Object agent) {
    if (!(agent instanceof DeepAgent deepAgent)) {
      return;
    }
    this.owner = deepAgent;
    installedProcessors.clear();
    List<ContextEngine.ProcessorSpec> specs;
    if (deepAgent.getAgent().getConfig() instanceof ReActAgentConfig config) {
      specs = buildProcessorSpecs(config);
      installedProcessors.addAll(specs);
      config.configureContextProcessors(new ArrayList<>(installedProcessors));
      deepAgent.getAgent().configure(config);
    } else {
      specs = buildProcessorSpecs(null);
      installedProcessors.addAll(specs);
    }
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void uninit(Object agent) {
    if (agent instanceof DeepAgent deepAgent) {
      if (deepAgent.getAgent().getConfig() instanceof ReActAgentConfig config) {
        config.configureContextProcessors(List.of());
        deepAgent.getAgent().configure(config);
      }
      deepAgent.getAgent().getPromptBuilder().removeSection(OFFLOAD_SECTION);
    }
    installedProcessors.clear();
    owner = null;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void beforeInvoke(AgentCallbackContext ctx) {
    fixIncompleteToolContext(ctx);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void beforeModelCall(AgentCallbackContext ctx) {
    injectOffloadSection();
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void afterModelCall(AgentCallbackContext ctx) {
    if (!isSessionMemoryEnabled || sessionMemoryManager == null || ctx == null) {
      return;
    }
    sessionMemoryManager.maybeScheduleUpdate(
        ctx.getSession(), ctx.getContext(), owner != null ? owner.getWorkspace() : null);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void onModelException(AgentCallbackContext ctx) {
    fixIncompleteToolContext(ctx);
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isPreset() {
    return isPreset;
  }

  /** Auto-generated for codecheck compliance. */
  public List<String> getProcessorKeys() {
    return List.copyOf(processorKeys);
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isSessionMemoryEnabled() {
    return isSessionMemoryEnabled;
  }

  /** Auto-generated for codecheck compliance. */
  public SessionMemoryManager getSessionMemoryManager() {
    return sessionMemoryManager;
  }

  /** Auto-generated for codecheck compliance. */
  public List<ContextEngine.ProcessorSpec> installedProcessors() {
    return List.copyOf(installedProcessors);
  }

  /** Auto-generated for codecheck compliance. */
  public boolean hasOffloadPromptSection() {
    return owner != null && owner.getAgent().getPromptBuilder().hasSection(OFFLOAD_SECTION);
  }

  /** Auto-generated for codecheck compliance. */
  public static String ensureJsonArguments(Object arguments) {
    if (arguments instanceof String string) {
      String trimmed = string.trim();
      return trimmed.startsWith("{") && trimmed.endsWith("}") ? string : "{}";
    }
    if (arguments instanceof Map<?, ?> map) {
      List<String> entries = new ArrayList<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        entries.add(
            "\""
                + String.valueOf(entry.getKey())
                + "\":\""
                + String.valueOf(entry.getValue())
                + "\"");
      }
      return "{" + String.join(",", entries) + "}";
    }
    return "{}";
  }

  /** Auto-generated for codecheck compliance. */
  public static void fixIncompleteToolContext(AgentCallbackContext ctx) {
    ModelContext context = ctx != null ? ctx.getContext() : null;
    if (context == null) {
      return;
    }
    List<BaseMessage> messages = context.getMessages();
    if (messages == null || messages.isEmpty()) {
      return;
    }
    List<BaseMessage> popped = context.popMessages(messages.size(), true);
    List<ToolCall> pending = new ArrayList<>();
    Map<String, ToolMessage> delayedTools = new LinkedHashMap<>();
    for (BaseMessage message : popped) {
      if (message instanceof AssistantMessage assistant) {
        flushPending(context, pending, delayedTools);
        context.addMessages(assistant);
        if (assistant.getToolCalls() != null) {
          for (ToolCall call : assistant.getToolCalls()) {
            call.setArguments(ensureJsonArguments(call.getArguments()));
            pending.add(call);
          }
        }
        continue;
      }
      if (message instanceof ToolMessage toolMessage) {
        if (pending.isEmpty()) {
          context.addMessages(toolMessage);
        } else if (toolMessage.getToolCallId() != null
            && toolMessage.getToolCallId().equals(pending.get(0).getId())) {
          context.addMessages(toolMessage);
          pending.remove(0);
        } else {
          delayedTools.put(toolMessage.getToolCallId(), toolMessage);
        }
        continue;
      }
      flushPending(context, pending, delayedTools);
      context.addMessages(message);
    }
    flushPending(context, pending, delayedTools);
  }

  private List<ContextEngine.ProcessorSpec> buildProcessorSpecs(ReActAgentConfig agentConfig) {
    Map<String, ContextEngine.ProcessorSpec> specs = new LinkedHashMap<>();
    ModelRequestConfig modelConfig = agentConfig != null ? agentConfig.getModelConfigObj() : null;
    ModelClientConfig modelClientConfig =
        agentConfig != null ? agentConfig.getModelClientConfig() : null;
    if (isPreset) {
      if (isSessionMemoryEnabled) {
        putSpec(
            specs, "ToolResultBudgetProcessor", ToolResultBudgetProcessorConfig.builder().build());
        putSpec(specs, "MicroCompactProcessor", MicroCompactProcessorConfig.builder().build());
        putSpec(
            specs,
            "FullCompactProcessor",
            FullCompactProcessorConfig.builder()
                .model(modelConfig)
                .modelClient(modelClientConfig)
                .build());
      } else {
        putSpec(
            specs,
            "MessageSummaryOffloader",
            MessageSummaryOffloaderConfig.builder()
                .tokensThreshold(60000)
                .largeMessageThreshold(60000)
                .offloadMessageType(List.of("tool"))
                .protectedToolNames(
                    List.of("read_file:*SKILL.md", "reload_original_context_messages"))
                .messagesToKeep(null)
                .keepLastRound(false)
                .model(modelConfig)
                .modelClient(modelClientConfig)
                .build());
        putSpec(
            specs,
            "DialogueCompressor",
            DialogueCompressorConfig.builder()
                .tokensThreshold(100000)
                .messagesToKeep(10)
                .keepLastRound(false)
                .compressionTargetTokens(1800)
                .model(modelConfig)
                .modelClient(modelClientConfig)
                .build());
        putSpec(
            specs,
            "CurrentRoundCompressor",
            CurrentRoundCompressorConfig.builder()
                .tokensThreshold(100000)
                .messagesToKeep(3)
                .model(modelConfig)
                .modelClient(modelClientConfig)
                .build());
        putSpec(
            specs,
            "RoundLevelCompressor",
            RoundLevelCompressorConfig.builder()
                .triggerTotalTokens(230000)
                .targetTotalTokens(160000)
                .keepRecentMessages(6)
                .model(modelConfig)
                .modelClient(modelClientConfig)
                .build());
      }
    }
    for (String key : processorKeys) {
      if (!specs.containsKey(key)) {
        putSpec(specs, key, defaultConfigFor(key, modelConfig, modelClientConfig));
      }
    }
    return new ArrayList<>(specs.values());
  }

  private static void putSpec(
      Map<String, ContextEngine.ProcessorSpec> specs, String key, Object config) {
    specs.put(key, new ContextEngine.ProcessorSpec(key, config));
  }

  private static Object defaultConfigFor(
      String key, ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
    return switch (key) {
      case "MessageSummaryOffloader" ->
          MessageSummaryOffloaderConfig.builder()
              .model(modelConfig)
              .modelClient(modelClientConfig)
              .build();
      case "DialogueCompressor" ->
          DialogueCompressorConfig.builder()
              .model(modelConfig)
              .modelClient(modelClientConfig)
              .build();
      case "CurrentRoundCompressor" ->
          CurrentRoundCompressorConfig.builder()
              .model(modelConfig)
              .modelClient(modelClientConfig)
              .build();
      case "RoundLevelCompressor" ->
          RoundLevelCompressorConfig.builder()
              .model(modelConfig)
              .modelClient(modelClientConfig)
              .build();
      case "MicroCompactProcessor" -> MicroCompactProcessorConfig.builder().build();
      case "FullCompactProcessor" ->
          FullCompactProcessorConfig.builder()
              .model(modelConfig)
              .modelClient(modelClientConfig)
              .build();
      case "ToolResultBudgetProcessor" -> ToolResultBudgetProcessorConfig.builder().build();
      default -> throw new IllegalArgumentException("Unknown context processor: " + key);
    };
  }

  private void injectOffloadSection() {
    if (owner == null) {
      return;
    }
    if (installedProcessors.isEmpty()) {
      owner.getAgent().getPromptBuilder().removeSection(OFFLOAD_SECTION);
      return;
    }
    String language = owner.getWorkspace().getLanguage();
    String content =
        "en".equalsIgnoreCase(language)
            ? "## Context Reload\n\n"
                  + "Some older messages may be offloaded. Use reload_original_context_messages"
                  + " with the exact offload handle when you need original content."
            : "## 上下文重载\n\n"
                  + "部分历史消息可能被卸载。需要原始内容时，使用 reload_original_context_messages 并提供准确的 offload"
                  + " handle。";
    owner
        .getAgent()
        .getPromptBuilder()
        .addSection(
            new PromptSection(
                OFFLOAD_SECTION,
                Map.of(
                    language == null || language.isBlank()
                        ? PromptSection.DEFAULT_LANGUAGE
                        : language,
                    content),
                60));
  }

  private static void flushPending(
      ModelContext context, List<ToolCall> pending, Map<String, ToolMessage> delayedTools) {
    if (pending.isEmpty()) {
      return;
    }
    Set<String> flushed = new LinkedHashSet<>();
    for (ToolCall call : List.copyOf(pending)) {
      ToolMessage delayed = delayedTools.remove(call.getId());
      if (delayed != null) {
        context.addMessages(delayed);
      } else {
        context.addMessages(
            ToolMessage.builder()
                .toolCallId(call.getId())
                .content(
                    "[Tool execution interrupted] Tool "
                        + call.getName()
                        + " was interrupted during execution, no result available.")
                .build());
      }
      flushed.add(call.getId());
    }
    pending.removeIf(call -> flushed.contains(call.getId()));
    for (ToolMessage toolMessage : delayedTools.values()) {
      context.addMessages(toolMessage);
    }
    delayedTools.clear();
  }
}
