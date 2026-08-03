/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.workspace.Workspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Public class ContextAssembleRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class ContextAssembleRail extends DeepAgentRail {
  private static final int WORKSPACE_PRIORITY = 30;
  private static final int TOOLS_PRIORITY = 40;
  private static final int CONTEXT_PRIORITY = 50;
  private static final int MAX_WORKSPACE_ENTRIES = 80;
  private static final int MAX_CONTEXT_FILES = 8;
  private DeepAgent owner;

  public ContextAssembleRail() {
    setPriority(85);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void init(DeepAgent agent) {
    if (agent != null) {
      this.owner = agent;
    }
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void uninit(DeepAgent agent) {
    if (agent != null) {
      for (String sectionName : sectionNames()) {
        resolvePromptBuilder(agent).removeSection(sectionName);
      }
    }
    owner = null;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void beforeModelCall(CallbackContext ctx) {
    if (owner == null) {
      return;
    }
    List<String> injected = new ArrayList<>();
    String language = resolveWorkspace(owner).getLanguage();
    addSection("workspace", buildWorkspaceSection(language), WORKSPACE_PRIORITY, injected);
    addSection("tools", buildToolsSection(language, ctx), TOOLS_PRIORITY, injected);
    addSection("context", buildContextSection(language), CONTEXT_PRIORITY, injected);
    injectSystemMessages(ctx, injected);
  }

  /** Auto-generated for codecheck compliance. */
  public List<String> sectionNames() {
    return List.of("workspace", "tools", "context");
  }

  /** Auto-generated for codecheck compliance. */
  public String describe() {
    return "Assemble workspace, tools, and context prompt sections";
  }

  /** Auto-generated for codecheck compliance. */
  public boolean hasContextSections() {
    return owner != null
        && resolvePromptBuilder(owner).hasSection("workspace")
        && resolvePromptBuilder(owner).hasSection("tools")
        && resolvePromptBuilder(owner).hasSection("context");
  }

  private void addSection(String name, String content, int priority, List<String> injected) {
    resolvePromptBuilder(owner).removeSection(name);
    if (content == null || content.isBlank()) {
      return;
    }
    String language = resolveWorkspace(owner).getLanguage();
    resolvePromptBuilder(owner)
        .addSection(
            new PromptSection(
                name,
                Map.of(
                    language == null || language.isBlank()
                        ? com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder.DEFAULT_LANGUAGE
                        : language,
                    content),
                priority));
    injected.add(content);
  }

  private String buildWorkspaceSection(String language) {
    Path root = resolveWorkspace(owner).root();
    List<String> entries = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(root, 2)) {
      stream
          .filter(path -> !path.equals(root))
          .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
          .limit(MAX_WORKSPACE_ENTRIES)
          .forEach(
              path -> {
                String rel = root.relativize(path).toString().replace('\\', '/');
                entries.add((Files.isDirectory(path) ? "- [dir] " : "- [file] ") + rel);
              });
    } catch (IOException ignored) {
      entries.add("- (workspace listing unavailable)");
    }
    String title = "en".equalsIgnoreCase(language) ? "## Workspace" : "## 工作区";
    return title + "\n\nRoot: " + root + "\n\n" + String.join("\n", entries);
  }

  private String buildToolsSection(String language, CallbackContext ctx) {
    List<ToolInfo> tools = new ArrayList<>();
    if (tools.isEmpty()) {
      tools.addAll(owner.getAbilityManager().listToolInfo());
    }
    List<String> lines =
        tools.stream()
            .map(
                tool ->
                    "- "
                        + tool.getName()
                        + (tool.getDescription() == null || tool.getDescription().isBlank()
                            ? ""
                            : ": " + tool.getDescription()))
            .toList();
    String title = "en".equalsIgnoreCase(language) ? "## Available Tools" : "## 可用工具";
    return title + "\n\n" + String.join("\n", lines);
  }

  private String buildContextSection(String language) {
    Path contextDir = resolveWorkspace(owner).getNodePath("context");
    String title = "en".equalsIgnoreCase(language) ? "## Context Files" : "## 上下文文件";
    if (!Files.isDirectory(contextDir)) {
      return title + "\n\n(no context files)";
    }
    List<String> parts = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(contextDir, 1)) {
      stream
          .filter(Files::isRegularFile)
          .sorted()
          .limit(MAX_CONTEXT_FILES)
          .forEach(path -> parts.add("### " + path.getFileName() + "\n\n" + readSnippet(path)));
    } catch (IOException ignored) {
      parts.add("(context files unavailable)");
    }
    return title + "\n\n" + String.join("\n\n", parts);
  }

  private String readSnippet(Path path) {
    try {
      String content = Files.readString(path);
      return content.length() <= 4000 ? content : content.substring(0, 4000) + "\n...";
    } catch (IOException ignored) {
      return "";
    }
  }

  private void injectSystemMessages(CallbackContext ctx, List<String> sections) {
    if (sections.isEmpty()) {
      return;
    }
    // No direct message injection available in harness.rails.CallbackContext;
    // sections are added via the prompt builder instead.
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
    if (reactAgent instanceof com.openjiuwen.core.singleagent.ReActAgent reActAgent) {
      return reActAgent.getPromptBuilder();
    }
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
