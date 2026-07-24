# GitCode Issue Evolver Example

这是一个面向演示的 GitCode Issue 自动修复服务。它接收签名 Webhook，在独立 sparse Worktree 中运行受限 ReAct Agent，执行 Java 编译 Gate，再由非 Agent 组件提交、推送分支、创建 PR，并在原 Issue 中回复 PR 地址。系统没有 Merge 能力，Review 与 Merge 始终由人工完成。

## 演示范围

默认模板面向：

- 目标仓库：`openJiuwen/agent-core-java`
- 发布仓库：必须替换为用户自己的 Fork，例如 `<fork-owner>/agent-core-java`
- 基线分支：`730`
- 触发条件：Issue 的 `update` Webhook 中明确新增 `bug` 标签
- 修改范围：`src/main/java/**`、`src/test/java/**`
- 验证命令：`mvn -B -ntp -DskipTests test-compile`

该 Gate 只编译主代码和测试代码，不执行仓库测试，适合早期 Demo，不代表正式 CI 结论。

## 两类 Skill

- `resources/skills/gitcode-issue-evolver` 是安装型 Agent 使用的公开 Skill。它只调用确定性的 `manage.ps1`，用于预检、启动、查看状态和停止服务。
- `examples/gitcode_issue_evolver/skills/gitcode-issue-evolver-worker` 是 Issue 修复 Agent 的运行期 Skill。它约束分析、最小修改和停止条件，不负责搭建服务。

安装型 Agent 必须与 Issue 修复 Agent 隔离。前者可以拥有受控的 `readFile` 和 `executeCmd`；后者只有 Worktree 内的 `readFile`、`searchFiles` 和 `writeFile`，没有 Shell、HTTP、GitCode、Push、PR 或 Merge 工具。

## 目录

    examples/gitcode_issue_evolver/
    |-- config/                         # 非密配置和本地 secrets 模板
    |-- scripts/                        # 编译、启动、Quick Tunnel 和停止脚本
    |-- skills/gitcode-issue-evolver-worker/
    |-- src/main/java/                  # Example 自持有的服务实现
    |-- .gitignore
    `-- README.md

所有新增 Java 实现均位于 Example 下。本示例不要求修改 `src/main/java/com/**`、`src/test/**` 或 `pom.xml`。

## 前置条件

- Windows 与 `powershell.exe`
- JDK 17，且 `java`、`javac` 可从 `PATH` 访问
- Maven，且 `mvn.cmd` 可从 `PATH` 访问
- Git
- `cloudflared`
- 已创建的 GitCode Fork，以及满足最小权限的机器 Token
- 目标仓库已准备 `bug` 标签
- 目标仓库和发布仓库具备配置中要求的基线分支关系

## 本地配置

在仓库根目录执行：

    Copy-Item examples/gitcode_issue_evolver/config/evolver-config.example.json examples/gitcode_issue_evolver/config/evolver-config.local.json
    Copy-Item examples/gitcode_issue_evolver/config/evolver-secrets.example.json examples/gitcode_issue_evolver/config/evolver-secrets.local.json

编辑两个 `.local.json` 文件：

- `evolver-config.local.json` 保存仓库坐标、端口、目录和 Assignee，不保存密钥。必须替换 `publishRepository` 和 `assignees` 占位符。
- `evolver-secrets.local.json` 只保存 GitCode Token 和 Webhook Secret。Webhook Secret 至少使用 32 个随机 UTF-8 字节。

模型配置使用仓库统一的 `examples/apiconfig.json`。该文件与 secrets 文件必须由用户预先配置；安装型 Agent 不得打开、打印、总结、复制或代写其内容。

不要提交填有真实值的 `examples/apiconfig.json`、任何 `.local.json`、日志或运行目录。运行数据和 Worktree 默认放在仓库外的相邻目录；`worktreeRoot` 不得位于本地基线仓库或可信 Skill 目录内部。

## 使用公开 Skill 启动

显式要求具备受控 Shell 的安装型 Agent 使用 `$gitcode-issue-evolver`，或直接在仓库根目录执行它唯一的入口：

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File resources/skills/gitcode-issue-evolver/scripts/manage.ps1 -Action Start -RepositoryRoot "<repository-root>"

入口先检查工具链、Example、非密运行配置和本地文件，再编译 Example、启动服务、以 HTTP/2 启动 Cloudflare Quick Tunnel，并等待本地和公网 `/health/ready` 返回 200。成功输出包含本地健康地址、公网健康地址和临时 `/webhooks/gitcode` URL，不包含 Token、模型 API Key 或 Webhook Secret。

其他操作：

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File resources/skills/gitcode-issue-evolver/scripts/manage.ps1 -Action Check -RepositoryRoot "<repository-root>"
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File resources/skills/gitcode-issue-evolver/scripts/manage.ps1 -Action Status -RepositoryRoot "<repository-root>"
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File resources/skills/gitcode-issue-evolver/scripts/manage.ps1 -Action Stop -RepositoryRoot "<repository-root>"

Quick Tunnel URL 每次可能变化。用户必须人工把返回的 URL 配置到 GitCode，选择 Issue 和 Pull Request 事件，并使用本地 secrets 文件中的同一个 Webhook Secret。Skill 不会修改 GitCode 设置。

## 运行流程

1. GitCode 发送 Issue Webhook；服务校验 JSON、1 MiB 上限、HMAC、Delivery ID 和目标仓库。
2. 只有明确新增 `bug` 标签的 update 事件进入 SQLite JobStore。
3. Worker 获取 Issue 和评论，确认 Issue 仍为 open，且不存在活动任务或未关闭 PR。
4. Worktree Manager 从配置的基线创建短路径、`--no-checkout` sparse Worktree，只检出 `src/main` 和 `src/test`。
5. ReAct Agent 加载可信暂存的 `coding-standard` 与 `gitcode-issue-evolver-worker`，并只使用受限文件工具。
6. 服务执行编译 Gate；通过后由受控 Committer 只暂存经过验证的精确文件。
7. Publisher 复核 Issue、分支、SHA、路径和 Gate，再推送发布仓库、创建 PR、指定 Assignee 并评论原 Issue。
8. PR Webhook 只把 Job 更新为 `MERGED` 或 `CLOSED`，不会执行 Merge。

## 安全边界

Issue 修复 Agent 不接收 GitCode Token，也不拥有 Shell、HTTP、Git、Push、PR 或 Merge 工具。Token 只在非 Agent Publisher 的 Git/API 调用中使用。Issue、评论和 Webhook 字段均按不可信数据处理；绝对路径、路径穿越、符号链接逃逸和 sparse 范围外修改会被拒绝。

这是 Demo，不适合作为无人值守生产服务。Quick Tunnel、文件 secrets 和单进程 SQLite 都是本地展示取舍。
