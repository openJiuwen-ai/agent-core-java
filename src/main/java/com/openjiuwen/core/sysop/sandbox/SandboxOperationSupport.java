/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.result.BaseResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdChunkData;
import com.openjiuwen.core.sysop.result.ExecuteCmdData;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeChunkData;
import com.openjiuwen.core.sysop.result.ExecuteCodeData;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SandboxOperationSupport {
  private SandboxOperationSupport() {}

  static LocalWorkConfig toLocalWorkConfig(SandboxGatewayConfig config) {
    Map<String, Object> params =
        config != null && config.getParams() != null ? config.getParams() : Map.of();
    String rootPath =
        stringParam(
            params,
            "root_path",
            stringParam(params, "work_dir", Path.of(".").toAbsolutePath().normalize().toString()));
    List<String> allowlist = listParam(params, "shell_allowlist");
    LocalWorkConfig.LocalWorkConfigBuilder builder = LocalWorkConfig.builder().workDir(rootPath);
    builder.restrictToSandbox(true);
    builder.sandboxRoot(List.of(Path.of(rootPath).toAbsolutePath().normalize().toString()));
    if (allowlist != null) {
      builder.shellAllowlist(allowlist);
    }
    return builder.build();
  }

  static Path sandboxRoot(SandboxGatewayConfig config) {
    return Path.of(toLocalWorkConfig(config).getWorkDir()).toAbsolutePath().normalize();
  }

  static String resolveIsolationKey(SandboxGatewayConfig config) {
    if (config != null && config.getIsolation() != null) {
      if (config.getIsolation().getCustomId() != null
          && !config.getIsolation().getCustomId().isBlank()) {
        return config.getIsolation().getCustomId();
      }
      if (config.getIsolation().getPrefix() != null
          && !config.getIsolation().getPrefix().isBlank()) {
        return config.getIsolation().getPrefix() + ":" + sandboxRoot(config);
      }
    }
    return "sandbox:" + sandboxRoot(config);
  }

  static Map<String, Object> params(Object[] kvPairs) {
    Map<String, Object> params = new LinkedHashMap<>();
    for (int i = 0; i + 1 < kvPairs.length; i += 2) {
      params.put(String.valueOf(kvPairs[i]), kvPairs[i + 1]);
    }
    return params;
  }

  static String normalizeShellCwd(SandboxGatewayConfig config, String cwd) {
    Path root = sandboxRoot(config);
    Path target;
    if (cwd == null || cwd.isBlank()) {
      target = root;
    } else {
      Path raw = Path.of(cwd);
      target =
          raw.isAbsolute()
              ? raw.toAbsolutePath().normalize()
              : root.resolve(raw).toAbsolutePath().normalize();
    }
    if (!target.startsWith(root)) {
      throw new IllegalArgumentException(
          "Access denied: cwd " + target + " traverses outside " + root);
    }
    if (target.equals(root)) {
      return ".";
    }
    return root.relativize(target).toString();
  }

  static String wrapCodeWithSandboxCwd(String code, String language, SandboxGatewayConfig config) {
    Path root = sandboxRoot(config);
    String escaped = root.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    if ("python".equals(language)) {
      return "import os\nos.chdir(\"" + escaped + "\")\n" + code;
    }
    if ("javascript".equals(language)) {
      return "process.chdir(\"" + escaped + "\");\n" + code;
    }
    return code;
  }

  static ExecuteCmdResult buildShellError(
      String execution, String errorMsg, String command, String cwd) {
    return BaseResult.buildOperationErrorResult(
        StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR,
        execution,
        errorMsg,
        ExecuteCmdResult::new,
        ExecuteCmdData.builder().command(command).cwd(cwd == null ? "." : cwd).build());
  }

  static ExecuteCodeResult buildCodeError(
      String execution, String errorMsg, String code, String language) {
    return BaseResult.buildOperationErrorResult(
        StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR,
        execution,
        errorMsg,
        ExecuteCodeResult::new,
        ExecuteCodeData.builder().codeContent(code).language(language).build());
  }

  static ExecuteCmdStreamResult buildShellStreamError(
      String execution, String errorMsg, String command, String cwd) {
    return BaseResult.buildOperationErrorResult(
        StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR,
        execution,
        errorMsg,
        ExecuteCmdStreamResult::new,
        ExecuteCmdChunkData.builder()
            .chunkIndex(0)
            .exitCode(-1)
            .type("stderr")
            .metadata(Map.of("command", command, "cwd", cwd == null ? "." : cwd))
            .build());
  }

  static ExecuteCodeStreamResult buildCodeStreamError(
      String execution, String errorMsg, String code, String language) {
    return BaseResult.buildOperationErrorResult(
        StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR,
        execution,
        errorMsg,
        ExecuteCodeStreamResult::new,
        ExecuteCodeChunkData.builder()
            .chunkIndex(0)
            .exitCode(-1)
            .type("stderr")
            .metadata(Map.of("code", code, "language", language))
            .build());
  }

  private static String stringParam(Map<String, Object> params, String key, String defaultValue) {
    Object value = params.get(key);
    return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
  }

  @SuppressWarnings("unchecked")
  private static List<String> listParam(Map<String, Object> params, String key) {
    Object value = params.get(key);
    if (value == null) {
      return List.of();
    }
    if (value instanceof List<?> list) {
      List<String> result = new ArrayList<>();
      for (Object item : list) {
        result.add(String.valueOf(item));
      }
      return result;
    }
    if (value instanceof String text) {
      return Arrays.stream(text.split(","))
          .map(String::trim)
          .filter(part -> !part.isBlank())
          .toList();
    }
    return List.of();
  }
}
