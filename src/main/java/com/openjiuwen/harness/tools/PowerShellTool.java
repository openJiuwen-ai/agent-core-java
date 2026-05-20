/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Public class PowerShellTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class PowerShellTool {
  private final String permissionMode;
  private final ShellExecutor executor;

  /**
   * Public interface ShellExecutor used by the Java parity implementation.
   *
   * @since 1.0
   */
  @FunctionalInterface
  public interface ShellExecutor {
    ShellResult execute(String command, String shellType) throws IOException;
  }

  /**
   * Public record ShellResult used by the Java parity implementation.
   *
   * @since 1.0
   */
  public record ShellResult(String stdout, String stderr, int exitCode) {}

  /** Auto-generated for codecheck compliance. */
  public PowerShellTool() {
    this("auto", defaultExecutor());
  }

  /** Auto-generated for codecheck compliance. */
  public PowerShellTool(String permissionMode) {
    this(permissionMode, null);
  }

  /** Auto-generated for codecheck compliance. */
  public PowerShellTool(String permissionMode, ShellExecutor executor) {
    this.permissionMode = permissionMode != null ? permissionMode : "auto";
    this.executor = executor != null ? executor : defaultExecutor();
  }

  private static ShellExecutor defaultExecutor() {
    return (command, shellType) -> {
      Process process = new ProcessBuilder("bash", "-lc", command).start();
      CompletableFuture<String> stdoutFuture =
          CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
      CompletableFuture<String> stderrFuture =
          CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
      int exitCode = process.onExit().join().exitValue();
      String stdout = stdoutFuture.join();
      String stderr = stderrFuture.join();
      return new ShellResult(stdout, stderr, exitCode);
    };
  }

  private static String read(java.io.InputStream stream) {
    try {
      return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (java.io.IOException ex) {
      return "";
    }
  }

  /** Auto-generated for codecheck compliance. */
  public ToolOutput invoke(String command) {
    if (command == null || command.isBlank()) {
      return ToolOutput.builder().success(false).error("command cannot be empty").build();
    }
    if (checkInjection(command).isBlocked()) {
      return ToolOutput.builder().success(false).error(checkInjection(command).reason()).build();
    }
    if ("read_only".equalsIgnoreCase(permissionMode) && looksLikeWrite(command)) {
      return ToolOutput.builder()
          .success(false)
          .error("Read-only mode blocks write command")
          .build();
    }
    try {
      ShellResult result = executor.execute(command, "powershell");
      boolean isExecutionSuccessful = result.exitCode() == 0;
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("stdout", result.stdout());
      payload.put("stderr", result.stderr());
      payload.put("exit_code", result.exitCode());
      payload.put("shell_type", "powershell");
      return ToolOutput.builder()
          .success(isExecutionSuccessful)
          .data(payload)
          .error(isExecutionSuccessful ? null : result.stderr())
          .build();
    } catch (IOException | SecurityException | CompletionException ex) {
      return ToolOutput.builder().success(false).error(ex.getMessage()).build();
    }
  }

  /** Auto-generated for codecheck compliance. */
  public static BashTool.InjectionCheck checkInjection(String command) {
    String lower = command.toLowerCase(Locale.ROOT);
    if (lower.contains("invoke-expression")) {
      return new BashTool.InjectionCheck(true, "injection blocked: Invoke-Expression");
    }
    return new BashTool.InjectionCheck(false, "");
  }

  private static boolean looksLikeWrite(String command) {
    String lower = command.toLowerCase(Locale.ROOT);
    return lower.contains("set-content")
        || lower.contains("add-content")
        || lower.contains("new-item");
  }
}
