/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Mirrors Python's {@code PowerShellMetadataProvider} in
 * {@code openjiuwen/harness/prompts/tools/powershell.py}.
 */
public final class PowerShellMetadataProvider implements ToolMetadataProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String DESCRIPTION_CN = """
            执行给定的 PowerShell 命令并返回输出。工作目录会在命令之间保持不变；但 shell 状态，例如变量、函数和别名，不会保留。

            重要：本工具用于通过 PowerShell 执行终端操作，例如 git、npm、docker、python 和 PowerShell cmdlet。不要用它做文件搜索、内容搜索、读文件、写文件、编辑文件，除非用户明确要求，或你已经确认专用工具无法完成任务。优先使用 glob、grep、read_file、edit_file、write_file。

            PowerShell 版本兼容：默认按 Windows PowerShell 5.1 兼容方式编写命令，除非你非常确定当前环境是 PowerShell 7+。
             - 不要默认使用 `&&`、`||`、三元表达式 `?:`、空合并 `??`、空条件 `?.`；这些在 5.1 会直接语法报错
             - 条件串联优先使用 `A; if ($?) { B }`
             - 无条件顺序执行用 `A; B`
             - 避免对原生可执行程序手动做 `2>&1` 重定向；stderr 已经会被工具捕获，5.1 下这样做还可能把输出包装成 ErrorRecord，并把 `$?` 变成 `$false`
             - 写给其他工具读取的文件时，优先显式指定 UTF-8 编码
             - `ConvertFrom-Json` 在 5.1 返回 `PSCustomObject`，不要默认假设支持 `-AsHashtable`

            执行前请遵循这些规则：
            1. 目录确认
             - 如果命令会创建新文件或目录，先确认父目录存在且位置正确
            2. 命令编写
             - 路径包含空格时必须使用双引号
             - 尽量使用绝对路径，避免不必要的 `Set-Location` 或 `cd`
             - 优先使用 PowerShell 原生命令和 Verb-Noun 风格，如 `Get-ChildItem`、`Select-String`、`Get-Content`、`New-Item`、`Remove-Item`

            PowerShell 语法说明：
             - 变量使用 `$` 前缀，例如 `$name = "value"`
             - 转义字符是反引号，不是反斜杠
             - 常见别名：`ls`=`Get-ChildItem`，`cd`=`Set-Location`，`cat`=`Get-Content`，`rm`=`Remove-Item`
             - 管道 `|` 传递的是对象，不是 bash 风格的纯文本
             - 筛选和转换优先使用 `Select-Object`、`Where-Object`、`ForEach-Object`
             - 字符串插值写法：`"Hello $name"` 或 `"Hello $($obj.Property)"`
             - 注册表路径使用 PSDrive 前缀：`HKLM:\\...`、`HKCU:\\...`
             - 环境变量读取用 `$env:NAME`，设置用 `$env:NAME = "value"`
             - 调用路径中带空格的原生 exe 时，用调用运算符：`& "C:\\Program Files\\App\\app.exe" arg1 arg2`

            交互与阻塞命令限制（本工具以 `-NonInteractive` 方式运行，因此凡是需要人工输入、确认、弹窗或进入交互界面的命令，都可能失败或一直卡住等待）：
             - 不要使用 `Read-Host`、`Get-Credential`、`Out-GridView`、`$Host.UI.PromptForChoice`、`pause`
             - 破坏性 cmdlet 可能要求确认；当你明确需要执行时，可以考虑加 `-Confirm:$false`，对只读或隐藏项必要时加 `-Force`
             - 不要使用会打开交互编辑器的 git 命令，例如 `git rebase -i`、`git add -i`

            向原生命令传递多行字符串时：
             - 优先使用单引号 here-string：`@' ... '@`，这样 PowerShell 不会展开其中的 `$` 或反引号
             - 结束标记 `'@` 必须顶格并单独占一行，否则会解析失败
             - 除非确实需要变量插值，否则优先使用 `@' ... '@`，不要用 `@" ... "@`
             - 如果原生命令参数中包含 `-`、`@` 等容易继续被 PowerShell 解析的字符，可考虑停止解析标记 `--%`

            使用说明：
             - `command` 参数必填
             - `timeout` 单位为秒，默认 30，最大 300
             - 尽量提供简洁清晰的 `description`
             - 如果输出超过 `max_output_chars`，返回内容会被截断
             - 可将 `background` 设为 true 后台运行命令；不需要自己追加 `&`
             - 不要用 PowerShell 去替代专用工具，除非用户明确要求：文件搜索用 glob，不要用 `Get-ChildItem -Recurse`；内容搜索用 grep，不要用 `Select-String`；读文件用 read_file，不要用 `Get-Content`；写文件用 write_file，不要用 `Set-Content` 或 `Out-File`；编辑文件用 edit_file；与用户沟通时直接输出文本，不要用 `Write-Output` 或 `Write-Host`
             - 多条独立命令应并行发起多次工具调用；存在依赖关系时，在单次调用中串联
             - 只有在你不关心前序命令是否失败时才使用 `;`
             - 不要用换行分隔多条命令；换行只用于字符串或 here-string 内部
             - 不要默认在命令前加 `cd` 或 `Set-Location`；优先使用当前工作目录或 `workdir`
             - 避免无意义的 `Start-Sleep`；长任务优先用后台执行；必须 sleep 时保持 1-5 秒短等待
             - Git 操作中，优先创建新提交而不是 amend；执行 `git reset --hard`、`git push --force`、`git checkout --` 等破坏性操作前先考虑更安全替代方案；除非用户明确要求，不要跳过 hooks（`--no-verify`）或绕过签名（`--no-gpg-sign`、`-c commit.gpgsign=false`）
             - 用户分享代码仓 URL 让你「看看这个仓库」或「分析一下」时，首选动作是 `git clone <url> <本地路径>`；克隆完再用 read_file/grep/glob 等专用工具读源码，比抓取仓库主页能拿到的信息完整得多
            """.stripTrailing();

    private static final String DESCRIPTION_EN = """
            Execute a given PowerShell command and return its output. The working directory persists between commands; shell state, such as variables, functions, and aliases, does not.

            IMPORTANT: This tool is for terminal operations via PowerShell: git, npm, docker, python, and PowerShell cmdlets. Do not use it for file search, content search, reading files, writing files, or editing files unless the user explicitly asks for it or you have verified that the dedicated tools cannot complete the task. Prefer glob, grep, read_file, edit_file, and write_file.

            PowerShell edition compatibility: write commands to be safe for Windows PowerShell 5.1 unless you are highly confident the environment is PowerShell 7+.
             - Do not assume `&&`, `||`, ternary `?:`, null-coalescing `??`, or null-conditional `?.` are available; these parse-fail on 5.1
             - For conditional chaining, prefer `A; if ($?) { B }`
             - For unconditional sequencing, use `A; B`
             - Avoid manual `2>&1` redirection on native executables; stderr is already captured for you, and on 5.1 this can wrap lines as ErrorRecord objects and flip `$?` to `$false`
             - When writing files that other tools will read, prefer explicit UTF-8 encoding
             - `ConvertFrom-Json` returns `PSCustomObject` on 5.1, so do not assume `-AsHashtable` exists

            Before executing the command, follow these steps:
            1. Directory verification
             - If the command will create new files or directories, first verify the parent directory exists and is the correct location
            2. Command execution
             - Always quote file paths containing spaces with double quotes
             - Prefer absolute paths and avoid unnecessary `Set-Location` or `cd`
             - Prefer native cmdlets and Verb-Noun naming such as `Get-ChildItem`, `Select-String`, `Get-Content`, `New-Item`, and `Remove-Item`

            PowerShell syntax notes:
             - Variables use the `$` prefix, for example `$name = "value"`
             - The escape character is backtick, not backslash
             - Common aliases include `ls`=`Get-ChildItem`, `cd`=`Set-Location`, `cat`=`Get-Content`, and `rm`=`Remove-Item`
             - The pipe operator `|` passes objects, not bash-style raw text
             - Prefer `Select-Object`, `Where-Object`, and `ForEach-Object` for filtering and transformation
             - String interpolation uses `"Hello $name"` or `"Hello $($obj.Property)"`
             - Registry access should use PSDrive prefixes such as `HKLM:\\...` and `HKCU:\\...`
             - Read environment variables with `$env:NAME` and set them with `$env:NAME = "value"`
             - To call a native executable whose path contains spaces, use the call operator: `& "C:\\Program Files\\App\\app.exe" arg1 arg2`

            Interactive and blocking command constraints (this tool starts PowerShell with `-NonInteractive`, so commands that require manual input, confirmation prompts, pop-up UI, or interactive editors may fail or hang waiting forever):
             - Never use `Read-Host`, `Get-Credential`, `Out-GridView`, `$Host.UI.PromptForChoice`, or `pause`
             - Destructive cmdlets may prompt for confirmation; when you intentionally want them to proceed, consider `-Confirm:$false` and use `-Force` when needed for read-only or hidden items
             - Never use git commands that open an interactive editor, such as `git rebase -i` or `git add -i`

            When passing multiline strings to native commands:
             - Prefer a single-quoted here-string: `@' ... '@`, so PowerShell does not expand `$` or backticks inside it
             - The closing `'@` must be at column 0 on its own line
             - Prefer `@' ... '@` over `@" ... "@` unless you explicitly need interpolation
             - If a native command argument contains characters PowerShell may continue parsing, such as `-` or `@`, consider the stop-parsing token `--%`

            Usage notes:
             - The `command` argument is required
             - `timeout` is in seconds, default 30 and max 300
             - A short, clear `description` is very helpful and should usually be provided
             - If output exceeds `max_output_chars`, it will be truncated before being returned
             - You can set `background` to true to run the command in the background; do not append `&` yourself
             - Avoid using PowerShell for tasks that have dedicated tools unless explicitly instructed: use glob instead of `Get-ChildItem -Recurse`; use grep instead of `Select-String`; use read_file instead of `Get-Content`; use write_file instead of `Set-Content` or `Out-File`; use edit_file for edits; communicate directly instead of using `Write-Output` or `Write-Host`
             - Use multiple tool calls for independent commands; chain commands only when they must run sequentially
             - Use `;` only when you need sequential execution but do not care whether earlier commands fail
             - Do not use newlines to separate commands; reserve newlines for strings and here-strings
             - Do not prefix commands with `cd` or `Set-Location` by default; prefer the current working directory or `workdir`
             - Avoid unnecessary `Start-Sleep`; prefer background execution for long-running work, and keep any required sleep short (1-5 seconds)
             - For git commands, prefer creating a new commit rather than amending an existing one; before destructive operations such as `git reset --hard`, `git push --force`, or `git checkout --`, consider safer alternatives; never skip hooks (`--no-verify`) or bypass signing (`--no-gpg-sign`, `-c commit.gpgsign=false`) unless the user explicitly asked
             - When a user shares a repo URL and asks you to 'look at' or 'analyze' it, the natural first step is `git clone <url> <local_path>`; after cloning, use read_file/grep/glob on the working tree - it gives you far more than the rendered repository page would.
            """.stripTrailing();

    private static final String SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\",\"description\":\"要执行的 PowerShell 命令\"},\"timeout\":{\"type\":\"integer\",\"description\":\"可选超时时间（秒），默认 300，上限 3600。对于长时间运行的任务，建议适当增大该值以避免任务被提前中断\"},\"workdir\":{\"type\":\"string\",\"description\":\"执行目录（相对或绝对路径），默认工作区根目录；不能越出沙箱\"},\"background\":{\"type\":\"boolean\",\"description\":\"是否后台运行，默认 false；设为 true 时立即返回 PID\"},\"max_output_chars\":{\"type\":\"integer\",\"description\":\"最大输出字符数，0 表示不限制（默认）；非零时上限 20000\"},\"description\":{\"type\":\"string\",\"description\":\"命令描述（可选），用于日志和审计\"}},\"required\":[\"command\"]}";
    private static final String SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\",\"description\":\"PowerShell command to execute\"},\"timeout\":{\"type\":\"integer\",\"description\":\"Optional timeout in seconds, default 300, max 3600. For long-running tasks, it is recommended to increase this value to avoid premature termination\"},\"workdir\":{\"type\":\"string\",\"description\":\"Working directory (relative or absolute path), defaults to workspace root; cannot escape sandbox\"},\"background\":{\"type\":\"boolean\",\"description\":\"Run in background (default false); returns PID immediately when true\"},\"max_output_chars\":{\"type\":\"integer\",\"description\":\"Max output characters; 0 (default) means no limit; non-zero values are capped at 20000\"},\"description\":{\"type\":\"string\",\"description\":\"Optional command description for logging and audit trail\"}},\"required\":[\"command\"]}";

    private static Map<String, Object> parseSchema(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse embedded prompt-tool schema JSON", ex);
        }
    }

    @Override
    public String getName() {
        return "powershell";
    }

    @Override
    public String getDescription(String language) {
        return "en".equals(language) ? DESCRIPTION_EN : DESCRIPTION_CN;
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return parseSchema("en".equals(language) ? SCHEMA_EN : SCHEMA_CN);
    }
}
