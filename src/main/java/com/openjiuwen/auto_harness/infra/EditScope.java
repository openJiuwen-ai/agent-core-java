/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.core.sys_operation.Cwd;

import java.nio.file.Path;
import java.util.List;

/**
 * Shared edit-scope rules for auto-harness planning and implementation.
 * <p>
 * Mirrors Python's module helpers in
 * {@code openjiuwen/auto_harness/infra/edit_scope.py}.
 */
public final class EditScope {

    public static final List<String> ALLOWED_SOURCE_EDIT_PREFIXES = List.of(
            "openjiuwen/harness/",
            "openjiuwen/core/"
    );
    public static final List<String> ALLOWED_SUPPORT_EDIT_PREFIXES = List.of(
            "tests/",
            "examples/",
            "docs/en/",
            "docs/zh/"
    );
    public static final List<String> ALLOWED_EDIT_PREFIXES = List.of(
            "openjiuwen/harness/",
            "openjiuwen/core/",
            "tests/",
            "examples/",
            "docs/en/",
            "docs/zh/"
    );

    private EditScope() {
    }

    public static String normalizeRepoPath(String path) {
        String raw = path == null ? "" : path.trim();
        if (raw.isEmpty()) {
            return "";
        }

        Path currentCwd = Path.of(Cwd.getCwd()).toAbsolutePath().normalize();
        String projectRootValue = Cwd.getProjectRoot();
        Path projectRoot = Path.of(
                projectRootValue == null || projectRootValue.isBlank() ? currentCwd.toString() : projectRootValue
        ).toAbsolutePath().normalize();

        Path expanded = expandUser(raw);
        Path resolved = expanded.isAbsolute()
                ? expanded.normalize()
                : currentCwd.resolve(expanded).normalize();
        if (resolved.startsWith(projectRoot)) {
            return projectRoot.relativize(resolved).toString().replace('\\', '/');
        }
        return resolved.toString().replace('\\', '/');
    }

    public static boolean isAllowedRepoEditPath(String path) {
        String normalized = normalizeRepoPath(path);
        return ALLOWED_EDIT_PREFIXES.stream().anyMatch(normalized::startsWith);
    }

    public static String renderEditScope() {
        return renderEditScope("本轮允许变更范围");
    }

    public static String renderEditScope(String header) {
        return """
                %s:
                - 源码路径只允许 `openjiuwen/harness/**`、`openjiuwen/core/**`
                - `openjiuwen/harness/**`、`openjiuwen/core/**` 下的模块内 README/Markdown 视为源码目录内容，可正常修改，例如 `openjiuwen/harness/cli/README.md`
                - 配套文件允许新增或修改 `tests/**`、`examples/**`
                - 如需新增或更新仓库级文档，只能写入 `docs/en/` 和 `docs/zh/` 下的 Markdown 文件；不要在 `docs/` 根目录或其他子目录新增文档
                - 不要修改 `openjiuwen/auto_harness/**` 或其他源码目录
                - 如果任务必须改到范围外路径，停止并明确报告范围冲突，不要自行越界
                """.formatted(header);
    }

    private static Path expandUser(String raw) {
        if (!raw.startsWith("~")) {
            return Path.of(raw);
        }
        String userHome = System.getProperty("user.home", "");
        if (raw.equals("~")) {
            return Path.of(userHome);
        }
        if (raw.startsWith("~/") || raw.startsWith("~\\")) {
            return Path.of(userHome).resolve(raw.substring(2));
        }
        return Path.of(raw);
    }
}
