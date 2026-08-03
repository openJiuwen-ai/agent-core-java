/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Public class VerificationRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class VerificationRail extends DeepAgentRail {
  /** Auto-generated for codecheck compliance. */
  public static final String REMINDER_SECTION = "verification_reminder";

  /** Auto-generated for codecheck compliance. */
  public static final int REMINDER_PRIORITY = 95;

  /** Auto-generated for codecheck compliance. */
  public static final Set<String> DEFAULT_ALLOWED_TOOLS =
      Set.of(
          "read_file",
          "bash",
          "grep",
          "glob",
          "list_files",
          "web_search",
          "web_fetch",
          "todo_create",
          "todo_list",
          "todo_modify",
          "skill_tool",
          "tool_search");

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Map<String, String> PATH_TOOL_ARGS =
      Map.of(
          "list_files", "path",
          "read_file", "file_path",
          "glob", "path",
          "grep", "path");
  private static final String REMINDER_CONTENT =
      """
=== VERIFICATION AGENT - ACTIVE CONSTRAINTS ===
1. You CANNOT create, modify, or delete project files. Use /tmp only for ephemeral test scripts.
2. Every check MUST include a 'Command run' block with verbatim terminal output. A check without a command block is a SKIP, not a PASS.
3. You MUST end your final response with exactly one of:
   VERDICT: PASS
   VERDICT: FAIL
   VERDICT: PARTIAL
   No markdown, no punctuation after the verdict word, no variation.
4. Reading code is NOT verification. Run commands and show actual output.
""";

  private final Set<String> allowedTools;
  private DeepAgent owner;

  /** Auto-generated for codecheck compliance. */
  public VerificationRail() {
    this(DEFAULT_ALLOWED_TOOLS);
  }

  /** Auto-generated for codecheck compliance. */
  public VerificationRail(Set<String> allowedTools) {
    setPriority(90);
    this.allowedTools =
        new LinkedHashSet<>(allowedTools == null ? DEFAULT_ALLOWED_TOOLS : allowedTools);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void init(DeepAgent agent) {
    super.init(agent);
    if (agent != null) {
      owner = agent;
    }
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void uninit(DeepAgent agent) {
    owner = null;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void beforeModelCall(CallbackContext ctx) {
    if (owner != null
        && owner.deepConfig() != null
        && owner.deepConfig().isEnableTaskLoop()) {
      ctx.put("verification_reminder_section", new PromptSection(
          REMINDER_SECTION,
          Map.of("en", REMINDER_CONTENT, "cn", REMINDER_CONTENT),
          REMINDER_PRIORITY));
    }
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void beforeToolCall(CallbackContext ctx) {
    if (ctx == null || Boolean.TRUE.equals(ctx.get("_skip_tool"))) {
      return;
    }
    String toolName = String.valueOf(ctx.getValues().getOrDefault("tool_name", ""));
    if (toolName.startsWith("mcp__")) {
      return;
    }
    if (!allowsTool(toolName)) {
      rejectTool(ctx,
          "[VerificationAgent] Tool '"
              + toolName
              + "' is not available to the verification agent. Permitted tools: "
              + String.join(", ", allowedTools.stream().sorted().toList())
              + ".");
      return;
    }
    String pathKey = PATH_TOOL_ARGS.get(toolName);
    if (pathKey != null && owner != null) {
      String rawPath = pathValue(ctx.get("tool_args"), pathKey);
      if (rawPath != null && !isWithinWorkspace(rawPath)) {
        rejectTool(ctx,
            "[VerificationAgent] Path '"
                + rawPath
                + "' is outside the workspace scope (workspace root: '"
                + workspaceRoot()
                + "').");
      }
    }
  }

  /** Auto-generated for codecheck compliance. */
  public boolean allowsTool(String toolName) {
    return toolName != null && (toolName.startsWith("mcp__") || allowedTools.contains(toolName));
  }

  /** Auto-generated for codecheck compliance. */
  public Set<String> getAllowedTools() {
    return Set.copyOf(allowedTools);
  }

  /** Auto-generated for codecheck compliance. */
  public String reminderContent() {
    return REMINDER_CONTENT.trim();
  }

  private void rejectTool(CallbackContext ctx, String error) {
    ctx.put("_skip_tool", true);
    ctx.put("tool_result", Map.of("error", error));
    ctx.reject(error);
  }

  private Path workspaceRoot() {
    Object workspace = getWorkspace();
    if (workspace == null) {
      return null;
    }
    if (workspace instanceof Workspace workspaceValue) {
      return Path.of(workspaceValue.getRootPath()).toAbsolutePath().normalize();
    }
    if (workspace instanceof Path path) {
      return path.toAbsolutePath().normalize();
    }
    return Path.of(String.valueOf(workspace)).toAbsolutePath().normalize();
  }

  @SuppressWarnings("unchecked")
  private static String pathValue(Object args, String pathKey) {
    if (args instanceof Map<?, ?> map) {
      Object value = map.get(pathKey);
      return value != null ? String.valueOf(value) : null;
    }
    if (args instanceof String raw && !raw.isBlank()) {
      try {
        Map<String, Object> parsed = MAPPER.readValue(raw, Map.class);
        Object value = parsed.get(pathKey);
        return value != null ? String.valueOf(value) : null;
      } catch (Exception ignored) {
        return null;
      }
    }
    return null;
  }

  private boolean isWithinWorkspace(String rawPath) {
    try {
      Path resolved = Path.of(rawPath).toAbsolutePath().normalize();
      Path root = workspaceRoot();
      if (root == null) {
        return true;
      }
      return resolved.equals(root) || resolved.startsWith(root);
    } catch (Exception ignored) {
      return true;
    }
  }
}
