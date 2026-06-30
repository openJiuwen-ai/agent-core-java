/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.lsp.core.LSPServerManager;
import com.openjiuwen.harness.tools.lsp.LspOperation;
import com.openjiuwen.harness.tools.lsp.LspToolSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class LspTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class LspTool {
  private final Path workspace;
  private final LSPServerManager manager;

  /** Auto-generated for codecheck compliance. */
  public LspTool(String workspace) {
    this(workspace, null);
  }

  /** Auto-generated for codecheck compliance. */
  public LspTool(String workspace, LSPServerManager manager) {
    this.workspace = workspace != null ? Path.of(workspace).toAbsolutePath().normalize() : null;
    this.manager = manager;
  }

  /** Auto-generated for codecheck compliance. */
  public ToolOutput invoke(Map<String, Object> inputs) {
    try {
      String rawOperation = String.valueOf(inputs.get("operation"));
      LspOperation operation = LspOperation.fromValue(rawOperation);
      String filePath =
          inputs.get("file_path") != null
              ? LspToolSupport.resolvePath(String.valueOf(inputs.get("file_path")), workspace)
              : null;
      if (operation != LspOperation.WORKSPACE_SYMBOL && (filePath == null || filePath.isBlank())) {
        return ToolOutput.builder().success(false).error("file_path is required").build();
      }
      String method = LspToolSupport.operationToMethod(operation);
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("operation", operation.value());
      payload.put("method", method);
      payload.put("file_path", filePath);
      payload.put("needs_gitignore_filter", LspToolSupport.needsGitignoreFilter(operation));
      Object rawResult =
          manager != null
              ? manager.request(filePath, method, requestParams(operation, filePath, inputs))
              : null;
      if (rawResult != null) {
        payload.put("raw_result", rawResult);
        payload.put("formatted", LspToolSupport.formatResult(operation, rawResult));
      }
      return ToolOutput.builder().success(true).data(payload).build();
    } catch (Exception ex) {
      return ToolOutput.builder()
          .success(false)
          .error("LSP tool error: " + ex.getMessage())
          .build();
    }
  }

  private static Map<String, Object> requestParams(
      LspOperation operation, String filePath, Map<String, Object> inputs) {
    Map<String, Object> params = new LinkedHashMap<>();
    if (operation == LspOperation.WORKSPACE_SYMBOL) {
      params.put("query", String.valueOf(inputs.getOrDefault("query", "")));
      return params;
    }
    params.put("textDocument", Map.of("uri", Path.of(filePath).toUri().toString()));
    if (requiresPosition(operation)) {
      params.put(
          "position",
          Map.of(
              "line", zeroBasedInt(inputs.get("line")),
              "character", zeroBasedInt(inputs.get("character"))));
    }
    if (operation == LspOperation.FIND_REFERENCES) {
      params.put(
          "context",
          Map.of("includeDeclaration", !Boolean.FALSE.equals(inputs.get("include_declaration"))));
    }
    if (operation == LspOperation.DOCUMENT_SYMBOL
        && filePath != null
        && Files.isRegularFile(Path.of(filePath))) {
      params.put("workDoneToken", filePath);
    }
    return params;
  }

  private static boolean requiresPosition(LspOperation operation) {
    return switch (operation) {
      case GO_TO_DEFINITION,
              FIND_REFERENCES,
              GO_TO_IMPLEMENTATION,
              PREPARE_CALL_HIERARCHY,
              INCOMING_CALLS,
              OUTGOING_CALLS ->
          true;
      case DOCUMENT_SYMBOL, WORKSPACE_SYMBOL -> false;
    };
  }

  private static int zeroBasedInt(Object value) {
    if (value instanceof Number number) {
      return Math.max(0, number.intValue() - 1);
    }
    if (value != null) {
      try {
        return Math.max(0, Integer.parseInt(String.valueOf(value)) - 1);
      } catch (NumberFormatException ignored) {
        return 0;
      }
    }
    return 0;
  }
}
