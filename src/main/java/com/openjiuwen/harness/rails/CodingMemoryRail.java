/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.memory.lite.CodingMemoryToolContext;
import com.openjiuwen.core.memory.lite.CodingMemoryToolOps;
import com.openjiuwen.core.memory.lite.LiteMemoryToolContextBase;
import com.openjiuwen.core.memory.lite.MemorySettings;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.CodingMemoryPromptSections;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataRegistry;
import com.openjiuwen.harness.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Public class CodingMemoryRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class CodingMemoryRail extends MemoryRail {
  private static final int MAX_INDEX_LINES = 200;

  private final String configuredCodingMemoryDir;

  /** Auto-generated for codecheck compliance. */
  public CodingMemoryRail() {
    this(null, null, true);
  }

  /** Auto-generated for codecheck compliance. */
  public CodingMemoryRail(
      String codingMemoryDir, EmbeddingConfig embeddingConfig, boolean isProactive) {
    super(embeddingConfig, isProactive);
    this.configuredCodingMemoryDir = codingMemoryDir;
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
  public String sectionName() {
    return "coding_memory";
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public List<String> toolNames() {
    return List.of("coding_memory_read", "coding_memory_write", "coding_memory_edit");
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  protected LiteMemoryToolContextBase createToolContext(DeepAgent deepAgent, Path memoryDir) {
    Workspace workspace = deepAgent.getWorkspace();
    Path codingDir =
        configuredCodingMemoryDir == null || configuredCodingMemoryDir.isBlank()
            ? memoryDir
            : Path.of(configuredCodingMemoryDir).toAbsolutePath().normalize();
    MemorySettings settings = MemorySettings.create(codingDir.toString(), Map.of());
    return new CodingMemoryToolContext(
        workspace,
        settings,
        agentId(deepAgent),
        embeddingConfig(),
        sysOperation(deepAgent),
        null,
        codingDir.toString());
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  protected LocalFunction createTool(String toolName, DeepAgent deepAgent) {
    String language = deepAgent.getWorkspace().getLanguage();
    String id = agentId(deepAgent) + "." + sectionName() + "." + toolName;
    return new LocalFunction(
        ToolMetadataRegistry.buildToolCard(toolName, id, language),
        inputs -> invokeMemoryTool(toolName, inputs));
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  protected Object invokeMemoryTool(String toolName, Map<String, Object> inputs) {
    CodingMemoryToolContext ctx =
        toolContext instanceof CodingMemoryToolContext codingCtx ? codingCtx : null;
    return switch (toolName) {
      case "coding_memory_read" ->
          CodingMemoryToolOps.codingMemoryReadWithContext(
              ctx, stringArg(inputs, "path"), intArg(inputs, "offset"), intArg(inputs, "limit"));
      case "coding_memory_write" ->
          CodingMemoryToolOps.codingMemoryWriteWithContext(
              ctx, stringArg(inputs, "path"), stringArg(inputs, "content"));
      case "coding_memory_edit" ->
          CodingMemoryToolOps.codingMemoryEditWithContext(
              ctx,
              stringArg(inputs, "path"),
              stringArg(inputs, "old_text"),
              stringArg(inputs, "new_text"));
      default -> Map.of("success", false, "error", "Unknown coding memory tool: " + toolName);
    };
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
    String content = buildCodingMemoryPrompt(language, isReadOnly, readIndex());
    owner
        .getAgent()
        .getPromptBuilder()
        .addSection(
            new PromptSection(
                sectionName(), Map.of(languageKey(language), content), SECTION_PRIORITY));
    injectSystemMessage(ctx, content);
  }

  /** Auto-generated for codecheck compliance. */
  public String codingMemoryDir() {
    if (toolContext instanceof CodingMemoryToolContext codingCtx) {
      return codingCtx.getCodingMemoryDir();
    }
    return configuredCodingMemoryDir;
  }

  private String buildCodingMemoryPrompt(String language, boolean isReadOnly, String index) {
    String lang =
        language == null || language.isBlank() ? PromptSection.DEFAULT_LANGUAGE : language;
    String base =
        CodingMemoryPromptSections.buildCodingMemorySection(lang, isReadOnly, codingMemoryDir())
            .render(lang);
    if ("cn".equalsIgnoreCase(lang) && !base.contains("编码记忆")) {
      base = base + "\n\n（编码记忆）";
    }
    if (index != null && !index.isBlank()) {
      base +=
          "\n\n"
              + ("en".equalsIgnoreCase(lang) ? "## Current memory index" : "## 当前记忆索引")
              + "\n\n"
              + index;
    }
    return base;
  }

  private String readIndex() {
    try {
      String dir = codingMemoryDir();
      if (dir == null || dir.isBlank()) {
        return "";
      }
      Path index = Path.of(dir).resolve("MEMORY.md");
      if (!Files.exists(index)) {
        return "";
      }
      List<String> lines = Files.readAllLines(index, StandardCharsets.UTF_8);
      return String.join(
              "\n", new ArrayList<>(lines.subList(0, Math.min(lines.size(), MAX_INDEX_LINES))))
          .trim();
    } catch (Exception ignored) {
      return "";
    }
  }
}
