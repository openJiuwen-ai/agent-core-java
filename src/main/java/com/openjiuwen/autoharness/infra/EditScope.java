/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.infra;

import com.openjiuwen.core.sysop.cwd.CwdContext;
import java.nio.file.Path;
import java.util.List;

/** Auto-generated for codecheck compliance. */
public final class EditScope {
  private static final String DEFAULT_HEADER = "本轮允许变更范围";
  private static final List<String> ALLOWED_EDIT_PREFIXES =
      List.of(
          "openjiuwen/harness/", "openjiuwen/core/", "tests/", "examples/", "docs/en/", "docs/zh/");

  private EditScope() {}

  /** Auto-generated for codecheck compliance. */
  public static String normalizeRepoPath(String path) {
    String raw = path == null ? "" : path.trim();
    if (raw.isEmpty()) {
      return "";
    }

    Path currentCwd = Path.of(CwdContext.getCwd()).toAbsolutePath().normalize();
    Path projectRoot =
        Path.of(
                CwdContext.getProjectRoot() == null
                    ? CwdContext.getCwd()
                    : CwdContext.getProjectRoot())
            .toAbsolutePath()
            .normalize();
    Path expanded = Path.of(expandUser(raw));
    Path isResolved =
        expanded.isAbsolute()
            ? expanded.toAbsolutePath().normalize()
            : currentCwd.resolve(expanded).toAbsolutePath().normalize();
    if (isResolved.startsWith(projectRoot)) {
      return projectRoot.relativize(isResolved).toString().replace('\\', '/');
    }
    return isResolved.toString().replace('\\', '/');
  }

  /** Auto-generated for codecheck compliance. */
  public static boolean isAllowedRepoEditPath(String path) {
    String normalized = normalizeRepoPath(path);
    for (String prefix : ALLOWED_EDIT_PREFIXES) {
      if (normalized.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  /** Auto-generated for codecheck compliance. */
  public static String renderEditScope() {
    return renderEditScope(DEFAULT_HEADER);
  }

  /** Auto-generated for codecheck compliance. */
  public static String renderEditScope(String header) {
    String effectiveHeader = header == null || header.isBlank() ? DEFAULT_HEADER : header;
    return effectiveHeader
        + ":\n"
        + "- 源码路径只允许 `openjiuwen/harness/**`、`openjiuwen/core/**`\n"
        + "- `openjiuwen/harness/**`、`openjiuwen/core/**` 下的模块内 README/Markdown 视为源码目录内容，可正常修改，例如"
        + " `openjiuwen/harness/cli/README.md`\n"
        + "- 配套文件允许新增或修改 `tests/**`、`examples/**`\n"
        + "- 如果任务需要新增或更新仓库级文档，只能写入 `docs/en/` 和 `docs/zh/` 下的 Markdown 文件；不要在 `docs/`"
        + " 根目录或其他子目录新增文档\n"
        + "- 不要修改 `openjiuwen/auto_harness/**` 或其他源码目录\n"
        + "- 如果任务必须改到范围外路径，停止并明确报告范围冲突，不要自行越界";
  }

  private static String expandUser(String raw) {
    if (raw.equals("~")) {
      return System.getProperty("user.home", raw);
    }
    if (raw.startsWith("~/") || raw.startsWith("~\\")) {
      return Path.of(System.getProperty("user.home", raw), raw.substring(2)).toString();
    }
    return raw;
  }
}
