# GitCode Issue Evolver Example

这是一个面向演示的 GitCode Issue 自动修复服务。它可以定时轮询 GitCode Issue，也可以接收签名 Webhook；之后在独立 sparse Worktree 中运行受限 ReAct Agent，执行 Java 编译 Gate，再由非 Agent 组件提交、推送分支、创建 PR，并在原 Issue 中回复 PR 地址。系统没有 Merge 能力，Review 与 Merge 始终由人工完成。

## 演示范围

默认模板面向：

- 目标仓库：`openJiuwen/agent-core-java`
- 发布仓库：必须替换为用户自己的 Fork，例如 `<fork-owner>/agent-core-java`
- 基线分支：`730`
- 新示例触发方式：启动后立即扫描，此后每 15 分钟轮询一次
- 轮询准入条件：扫描快照过去 24 小时内创建、状态为 open、精确带有 `bug` 标签
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
- `cloudflared`（仅 `webhook` 或 `both` 模式需要）
- 已创建的 GitCode Fork，以及满足最小权限的机器 Token
- 目标仓库已准备 `bug` 标签
- 目标仓库和发布仓库具备配置中要求的基线分支关系

## 本地配置

在仓库根目录执行：

    Copy-Item examples/gitcode_issue_evolver/config/evolver-config.example.json examples/gitcode_issue_evolver/config/evolver-config.local.json
    Copy-Item examples/gitcode_issue_evolver/config/evolver-secrets.example.json examples/gitcode_issue_evolver/config/evolver-secrets.local.json

编辑两个 `.local.json` 文件：

- `evolver-config.local.json` 保存仓库坐标、端口、目录和 Assignee，不保存密钥。必须替换 `publishRepository` 和 `assignees` 占位符。
- `evolver-secrets.local.json` 保存 Evolver Bot 的 GitCode Token；只有 `webhook` 或 `both` 模式才需要至少 32 个随机 UTF-8 字节的 Webhook Secret。

`triggerMode` 可取 `polling`、`webhook`、`both`。未配置该字段的旧配置仍按 `webhook` 运行；新示例使用 `polling`。`gitCodeToken` 始终属于 Evolver Bot，用于读取 Issue/PR、推送分支、创建 PR 和评论，不得与用户提交 Issue 时使用的个人 PAT 混用。

模型配置使用仓库统一的 `examples/apiconfig.json`。该文件与 secrets 文件必须由用户预先配置；安装型 Agent 不得打开、打印、总结、复制或代写其内容。

不要提交填有真实值的 `examples/apiconfig.json`、任何 `.local.json`、日志或运行目录。运行数据和 Worktree 默认放在仓库外的相邻目录；`worktreeRoot` 不得位于本地基线仓库或可信 Skill 目录内部。

## 使用公开 Skill 启动

显式要求具备受控 Shell 的安装型 Agent 使用 `$gitcode-issue-evolver`，或直接在仓库根目录执行它唯一的入口：

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File resources/skills/gitcode-issue-evolver/scripts/manage.ps1 -Action Start -RepositoryRoot "<repository-root>"

入口先检查工具链、Example、非密运行配置和本地文件，再编译并启动服务。polling-only 只等待本地 `/health/ready`，不检查或启动 cloudflared；`webhook`/`both` 会额外启动 Cloudflare Quick Tunnel 并等待公网健康地址。结构化输出不会包含 Token、模型 API Key 或 Webhook Secret。

其他操作：

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File resources/skills/gitcode-issue-evolver/scripts/manage.ps1 -Action Check -RepositoryRoot "<repository-root>"
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File resources/skills/gitcode-issue-evolver/scripts/manage.ps1 -Action Status -RepositoryRoot "<repository-root>"
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File resources/skills/gitcode-issue-evolver/scripts/manage.ps1 -Action Stop -RepositoryRoot "<repository-root>"

Linux/macOS 可运行 Example 层确定性测试入口（会先编译 Example）：

    bash examples/gitcode_issue_evolver/scripts/test-demo.sh

该入口不连接 GitCode，使用内存 HTTP 响应、可注入时钟和临时 SQLite 验证窗口、标签、分页断点、跨通道终身去重、迁移回填及 PR 对账。

polling-only 无需公网入站地址或 GitCode Webhook。`webhook`/`both` 的 Quick Tunnel URL 每次可能变化，用户必须人工把返回的 URL 配置到 GitCode，选择 Issue 和 Pull Request 事件，并使用本地 secrets 文件中的同一个 Webhook Secret。Skill 不会修改 GitCode 设置。

## 运行流程

1. polling 启动后立即冻结 `[now-24h, now]` 窗口，按创建时间升序、每页 100 条读取 open Issue；每轮最多 10 页，超限则持久化下一页断点。
2. 服务再次校验创建时间、open 状态和大小写精确的 `bug` 标签；`webhook`/`both` 同时支持签名 Webhook，Webhook 触发标签也来自 `triggerLabel`。
3. Worker 获取 Issue 和评论，确认 Issue 仍为 open，且不存在活动任务或未关闭 PR。
4. Worktree Manager 从配置的基线创建短路径、`--no-checkout` sparse Worktree，只检出 `src/main` 和 `src/test`。
5. ReAct Agent 加载可信暂存的 `coding-standard` 与 `gitcode-issue-evolver-worker`，并只使用受限文件工具。
6. 服务执行编译 Gate；通过后由受控 Committer 只暂存经过验证的精确文件。
7. Publisher 复核 Issue、分支、SHA、路径和 Gate，再推送发布仓库、创建 PR、指定 Assignee 并评论原 Issue。
8. 每轮 polling 也会轮转检查 `WAITING_REVIEW` PR，将已合并或已关闭任务对账为 `MERGED` 或 `CLOSED`；不会执行 Merge。

Webhook 与 polling 使用同一条 SQLite Issue 准入记录竞争。同一仓库的同一 Issue 在服务重启、跨触发通道和跨终态后都不会再次自动进入流程；Job 内已有的失败重试规则保持不变。瞬时轮询失败会在下一周期重试并且不推进分页断点，`/health/ready` 仍返回 200，同时仅公开触发模式和最近扫描结果，不公开原始异常。

## 安全边界

Issue 修复 Agent 不接收 GitCode Token，也不拥有 Shell、HTTP、Git、Push、PR 或 Merge 工具。Token 只在非 Agent Publisher 的 Git/API 调用中使用。Issue、评论和 Webhook 字段均按不可信数据处理；绝对路径、路径穿越、符号链接逃逸和 sparse 范围外修改会被拒绝。

这是 Demo，不适合作为无人值守生产服务。Quick Tunnel、文件 secrets 和单进程 SQLite 都是本地展示取舍。
