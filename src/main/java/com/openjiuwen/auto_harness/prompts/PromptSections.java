/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.prompts;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Auto-harness prompt section builder.
 * <p>
 * Mirrors Python's helpers in
 * {@code openjiuwen/auto_harness/prompts/sections.py}.
 */
public final class PromptSections {

    private static final String IDENTITY_PATH = "identity.md";

    private PromptSections() {
    }

    public static String loadIdentity(String promptsDir) {
        Path identityPath = Path.of(promptsDir, IDENTITY_PATH);
        try {
            return Files.readString(identityPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    public static List<PromptSection> buildAutoHarnessSections(
            String ciGateRules,
            String wisdom,
            String promptsDir
    ) {
        String osType = detectOsType();
        String shellName = resolveShellName(osType, gitBashAvailable());
        String osVersion = System.getProperty("os.name", "unknown")
                + " " + System.getProperty("os.version", "");
        return buildAutoHarnessSections(ciGateRules, wisdom, promptsDir, osType, osVersion.trim(), shellName);
    }

    static List<PromptSection> buildAutoHarnessSections(
            String ciGateRules,
            String wisdom,
            String promptsDir,
            String osType,
            String osVersion,
            String shellName
    ) {
        List<PromptSection> sections = new ArrayList<>();

        String identityText = loadIdentity(promptsDir);
        sections.add(new PromptSection(
                "auto_harness_identity",
                Map.of("cn", identityText, "en", identityText),
                10
        ));

        sections.add(platformAdaptationSection(osType, osVersion, shellName));

        if (ciGateRules != null && !ciGateRules.isEmpty()) {
            sections.add(new PromptSection(
                    "auto_harness_ci_gate",
                    Map.of(
                            "cn", "## CI 闸控规则\n\n" + ciGateRules,
                            "en", "## CI Gate Rules\n\n" + ciGateRules
                    ),
                    20
            ));
        }

        if (wisdom != null && !wisdom.isEmpty()) {
            sections.add(new PromptSection(
                    "auto_harness_wisdom",
                    Map.of(
                            "cn", "## 经验库\n\n" + wisdom,
                            "en", "## Experience Library\n\n" + wisdom
                    ),
                    30
            ));
        }

        return sections;
    }

    static PromptSection platformAdaptationSection(String osType, String osVersion, String shellName) {
        String cn = """
                # 运行环境

                - 当前运行平台：`%s`
                - Shell：%s
                - OS 版本：%s

                ## 平台命令差异（仅在必须使用 shell 时参考）

                以下命令差异仅适用于测试、构建、git、包管理、运行脚本等必须调用 shell 的场景。
                文件读取、编辑、搜索仍应优先使用专用工具。

                | 操作 | Windows (`win32`/`win64`) | Linux/macOS (`linux`/`darwin`) |
                |------|---------------------------|-------------------------------|
                | 创建目录 | `mkdir folder` 或 PowerShell `New-Item -ItemType Directory -Path folder` | `mkdir -p folder` |
                | 删除文件 | `del file.txt` 或 PowerShell `Remove-Item file.txt` | `rm file.txt` |
                | 删除目录 | `rmdir /s /q folder` 或 PowerShell `Remove-Item -Recurse folder` | `rm -rf folder` |
                | 查找文件 | `dir /s pattern` 或 PowerShell `Get-ChildItem -Recurse -Filter pattern` | `find . -name pattern` |
                | 环境变量 | `%%VAR%%`（cmd）/ `$VAR`（Git Bash） | `$VAR` |
                | PATH 分隔 | `;` | `:` |
                | 命令串联 | `&&`（条件）或 `&`（无条件） | `&&`（条件）或 `;`（无条件） |

                **特别注意**：Windows 的 `mkdir` 不支持 `-p` 参数。
                在 Windows 上使用 `mkdir -p folder` 会错误创建名为 `-p` 的目录。
                如需创建嵌套目录，请使用 PowerShell `New-Item -ItemType Directory -Path "parent/child" -Force`
                或分步执行 `mkdir parent && mkdir parent\\child`。

                ## Python 跨平台代码规范

                - 路径构建：始终使用 `pathlib.Path`，不要手工拼接 `/` 或 `\\`
                - PATH 拼接：对 `os.environ` 和 `env` 字典中的 PATH 使用 `os.pathsep`
                - venv 路径：Windows 是 `.venv\\Scripts\\python.exe`；Linux/macOS 是 `.venv/bin/python`
                - 临时目录：使用 `tempfile.gettempdir()`，不要假设 `/tmp` 一定存在
                - subprocess：向 `asyncio.create_subprocess_exec` 传 `env` 时，确保 PATH 使用 `os.pathsep`

                ## 局部验证

                局部验证只检查本次任务改动的文件，禁止全仓扫描。
                调用检查工具时优先使用 `python -m ruff check <files>`，不要直接调用 `ruff` CLI，
                以避免跨平台 PATH 问题。
                """.formatted(osType, shellName, osVersion);
        String en = """
                # Environment

                - Current platform: `%s`
                - Shell: %s
                - OS Version: %s

                ## Platform Command Differences (only when shell is required)

                The following command differences apply only where shell execution is required
                (testing, builds, git, package management, running scripts).
                File reading, editing, and searching should still prefer dedicated tools.

                | Operation | Windows (`win32`/`win64`) | Linux/macOS (`linux`/`darwin`) |
                |-----------|---------------------------|-------------------------------|
                | Create directory | `mkdir folder` or PowerShell `New-Item -ItemType Directory -Path folder` | `mkdir -p folder` |
                | Delete file | `del file.txt` or PowerShell `Remove-Item file.txt` | `rm file.txt` |
                | Delete directory | `rmdir /s /q folder` or PowerShell `Remove-Item -Recurse folder` | `rm -rf folder` |
                | Find file | `dir /s pattern` or PowerShell `Get-ChildItem -Recurse -Filter pattern` | `find . -name pattern` |
                | Env variable | `%%VAR%%` (cmd) / `$VAR` (Git Bash) | `$VAR` |
                | PATH separator | `;` | `:` |
                | Command chaining | `&&` (conditional) or `&` (unconditional) | `&&` (conditional) or `;` (unconditional) |

                **WARNING**: Windows `mkdir` does not support the `-p` flag.
                Using `mkdir -p folder` on Windows incorrectly creates a directory named `-p`.
                To create nested directories on Windows, use PowerShell
                `New-Item -ItemType Directory -Path "parent/child" -Force`
                or step-by-step creation with `mkdir parent && mkdir parent\\child`.

                ## Python Cross-Platform Code Rules

                - Path construction: always use `pathlib.Path`; never concatenate `/` or `\\`
                - PATH joining: use `os.pathsep` in `os.environ` and `env` dicts; never hardcode `:` or `;`
                - venv path: Windows is `.venv\\Scripts\\python.exe`; Linux/macOS is `.venv/bin/python`
                - Temp directory: use `tempfile.gettempdir()`; do not assume `/tmp` exists
                - subprocess: when passing `env` to `asyncio.create_subprocess_exec`, ensure PATH uses `os.pathsep`

                ## Local Verification

                Local verification only checks files changed by the current task; full-repo scans are forbidden.
                Use `python -m ruff check <files>` instead of direct CLI calls like `ruff`
                to avoid cross-platform PATH problems.
                """.formatted(osType, shellName, osVersion);
        return new PromptSection(
                "auto_harness_platform_adaptation",
                Map.of("cn", cn, "en", en),
                89
        );
    }

    static String resolveShellName(String osType, boolean gitBashAvailable) {
        if ("win32".equals(osType)) {
            return gitBashAvailable ? "Git Bash / cmd.exe" : "cmd.exe";
        }
        if ("darwin".equals(osType)) {
            return "zsh (default) / bash";
        }
        return "bash";
    }

    private static String detectOsType() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return "win32";
        }
        if (osName.contains("mac")) {
            return "darwin";
        }
        return "linux";
    }

    private static boolean gitBashAvailable() {
        String programFiles = System.getenv().getOrDefault("PROGRAMFILES", "");
        if (programFiles.isBlank()) {
            return false;
        }
        return Path.of(programFiles, "Git", "bin", "bash.exe").toFile().isFile();
    }
}
