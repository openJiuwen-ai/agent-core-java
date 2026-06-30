/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.harness.workspace.Workspace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** General lite memory tool implementations. */
public final class MemoryToolOps {
  private MemoryToolOps() {}

  /** Auto-generated for codecheck compliance. */
  public static Map.Entry<Boolean, String> validateMemoryPath(String path, Workspace workspace) {
    if (workspace == null) {
      return Map.entry(false, "Workspace not initialized");
    }
    if (path.contains("..") || path.startsWith("/")) {
      return Map.entry(false, "Invalid path: directory traversal not allowed");
    }
    String basename = Path.of(path).getFileName().toString();
    Path memoryDir = workspace.getNodePath("memory");
    Path isResolved;
    if ("USER.md".equals(basename)) {
      isResolved = workspace.root().resolve("USER.md");
    } else if ("MEMORY.md".equals(basename)) {
      isResolved = memoryDir.resolve("MEMORY.md");
    } else if (LiteMemoryInternal.isDailyMemoryFilename(basename)) {
      isResolved = memoryDir.resolve("daily_memory").resolve(basename);
    } else {
      isResolved = memoryDir.resolve(basename);
    }
    return Map.entry(true, isResolved.normalize().toString());
  }

  /** Auto-generated for codecheck compliance. */
  public static Map<String, Object> memorySearchWithContext(
      MemoryToolContext ctx, String query, Integer maxResults, Double minScore, String sessionKey) {
    if (ctx == null || !ctx.ensureManager() || ctx.getManager() == null) {
      return Map.of(
          "results", List.of(), "disabled", true, "error", "Memory manager not available");
    }
    try {
      Map<String, Object> opts = new LinkedHashMap<>();
      if (maxResults != null) {
        opts.put("max_results", maxResults);
      }
      if (minScore != null) {
        opts.put("min_score", minScore);
      }
      if (sessionKey != null) {
        opts.put("session_key", sessionKey);
      }
      List<Map<String, Object>> results = ctx.getManager().search(query, opts);
      for (Map<String, Object> result : results) {
        int start = Integer.parseInt(String.valueOf(result.get("start_line")));
        int end = Integer.parseInt(String.valueOf(result.get("end_line")));
        result.put(
            "citation",
            start == end
                ? result.get("path") + "#L" + start
                : result.get("path") + "#L" + start + "-L" + end);
      }
      Map<String, Object> status = ctx.getManager().status();
      return Map.of(
          "results",
          results,
          "provider",
          status.get("provider"),
          "model",
          status.get("model"),
          "disabled",
          false);
    } catch (IOException | NumberFormatException e) {
      return Map.of("results", List.of(), "disabled", true, "error", e.getMessage());
    }
  }

  /** Auto-generated for codecheck compliance. */
  public static Map<String, Object> memoryGetWithContext(
      MemoryToolContext ctx, String path, Integer fromLine, Integer lines) {
    Workspace workspace = ctx != null ? ctx.getWorkspace() : null;
    Map.Entry<Boolean, String> valid = validateMemoryPath(path, workspace);
    if (!valid.getKey()) {
      return Map.of("path", path, "text", "", "disabled", true, "error", valid.getValue());
    }
    if (ctx == null || !ctx.ensureManager() || ctx.getManager() == null) {
      return Map.of(
          "path",
          valid.getValue(),
          "text",
          "",
          "disabled",
          true,
          "error",
          "Memory manager not available");
    }
    try {
      Map<String, Object> rf = ctx.getManager().readFile(valid.getValue(), fromLine, lines);
      Map<String, Object> result = new LinkedHashMap<>(rf);
      result.put("disabled", false);
      return result;
    } catch (IOException e) {
      return Map.of(
          "path", valid.getValue(), "text", "", "disabled", true, "error", e.getMessage());
    }
  }

  /** Auto-generated for codecheck compliance. */
  public static Map<String, Object> writeMemoryWithContext(
      MemoryToolContext ctx, String path, String content, boolean isAppend) {
    Workspace workspace = ctx != null ? ctx.getWorkspace() : null;
    Map.Entry<Boolean, String> valid = validateMemoryPath(path, workspace);
    if (!valid.getKey()) {
      return Map.of("success", false, "path", path, "error", valid.getValue());
    }
    try {
      Path resolvedPath = Path.of(valid.getValue());
      Files.createDirectories(resolvedPath.getParent());
      boolean existedBeforeWrite = Files.exists(resolvedPath) && Files.size(resolvedPath) > 0;
      if (isAppend) {
        Files.writeString(
            resolvedPath,
            content,
            StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND);
      } else {
        Files.writeString(
            resolvedPath,
            content,
            StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
      }
      if (ctx != null && ctx.ensureManager() && ctx.getManager() != null) {
        ctx.getManager().sync("write_memory");
      }
      return Map.of(
          "success",
          true,
          "path",
          resolvedPath.toString(),
          "fullPath",
          resolvedPath.toString(),
          "appended",
          isAppend,
          "fileExisted",
          existedBeforeWrite);
    } catch (IOException e) {
      return Map.of("success", false, "path", path, "error", e.getMessage());
    }
  }

  /** Auto-generated for codecheck compliance. */
  public static Map<String, Object> editMemoryWithContext(
      MemoryToolContext ctx, String path, String oldText, String newText) {
    Workspace workspace = ctx != null ? ctx.getWorkspace() : null;
    Map.Entry<Boolean, String> valid = validateMemoryPath(path, workspace);
    if (!valid.getKey()) {
      return Map.of("success", false, "path", path, "error", valid.getValue());
    }
    try {
      Path isResolved = Path.of(valid.getValue());
      String content =
          Files.exists(isResolved) ? Files.readString(isResolved, StandardCharsets.UTF_8) : "";
      if (!content.contains(oldText)) {
        return Map.of(
            "success",
            false,
            "path",
            path,
            "error",
            "old_text not found in file. Use read_memory tool to check exact content.");
      }
      if (content.indexOf(oldText) != content.lastIndexOf(oldText)) {
        return Map.of(
            "success",
            false,
            "path",
            path,
            "error",
            "old_text appears multiple times in file. Be more specific.");
      }
      Files.writeString(
          isResolved,
          content.replace(oldText, newText),
          StandardCharsets.UTF_8,
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
      if (ctx != null && ctx.ensureManager() && ctx.getManager() != null) {
        ctx.getManager().sync("edit_memory");
      }
      return Map.of(
          "success", true, "path", isResolved.toString(), "replaced", oldText, "new_text", newText);
    } catch (IOException e) {
      return Map.of("success", false, "path", path, "error", e.getMessage());
    }
  }

  /** Auto-generated for codecheck compliance. */
  public static Map<String, Object> readMemoryWithContext(
      MemoryToolContext ctx, String path, Integer offset, Integer limit) {
    Workspace workspace = ctx != null ? ctx.getWorkspace() : null;
    Map.Entry<Boolean, String> valid = validateMemoryPath(path, workspace);
    if (!valid.getKey()) {
      return Map.of("success", false, "path", path, "content", "", "error", valid.getValue());
    }
    try {
      Path isResolved = Path.of(valid.getValue());
      List<String> allLines =
          Files.exists(isResolved)
              ? Files.readAllLines(isResolved, StandardCharsets.UTF_8)
              : List.of();
      int total = allLines.size();
      int startIdx = offset == null ? 0 : Math.max(0, offset - 1);
      int endIdx = limit == null ? total : Math.min(total, startIdx + limit);
      return Map.of(
          "success",
          true,
          "path",
          isResolved.toString(),
          "content",
          String.join("\n", allLines.subList(startIdx, endIdx)),
          "totalLines",
          total,
          "start_line",
          startIdx + 1,
          "end_line",
          endIdx,
          "truncated",
          limit != null && endIdx < total);
    } catch (IOException e) {
      return Map.of("success", false, "path", path, "content", "", "error", e.getMessage());
    }
  }
}
