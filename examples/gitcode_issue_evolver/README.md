# GitCode Issue Evolver Example

这是一个面向演示的 GitCode Issue 自动修复服务。它可以定时轮询 GitCode Issue，也可以接收签名 Webhook；之后在独立 sparse Worktree 中运行受限 ReAct Agent，依次执行 Java 编译和 JiuwenTestJava 精确 smoke Gate，再由非 Agent 组件提交、推送分支、创建 PR，并在原 Issue 中回复 PR 地址。系统没有 Merge 能力，Review 与 Merge 始终由人工完成。

## 演示范围

默认模板面向：

- 目标仓库：`openJiuwen/agent-core-java`
- 发布仓库：必须替换为用户自己的 Fork，例如 `<fork-owner>/agent-core-java`
- 基线分支：`730`
- 新示例触发方式：启动后立即扫描，此后每 15 分钟轮询一次
- 轮询准入条件：扫描快照过去 24 小时内创建、状态为 open、精确带有 `bug` 标签
- 修改范围：`src/main/java/**`、`src/test/java/**`
- 第一段 Gate：`mvn -B -ntp -DskipTests test-compile`
- 第二段 Gate：安装当前修复版本，并在独立 `jiuwen-test-java` 检出中运行 1～3 个配置锁定的精确 smoke 类

smoke Gate 不运行完整测试仓套件，也不允许模型选择测试类、Maven 参数、仓库或路径；测试仓只作为 Controller 输入，不进入 Agent 文件工具写入范围。

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
- 一个独立、可读且布局为 Maven `src/test/java` 的 JiuwenTestJava Git 检出；运行账户必须可写其 `target/`

## 本地配置

在仓库根目录执行：

    Copy-Item examples/gitcode_issue_evolver/config/evolver-config.example.json examples/gitcode_issue_evolver/config/evolver-config.local.json
    Copy-Item examples/gitcode_issue_evolver/config/evolver-secrets.example.json examples/gitcode_issue_evolver/config/evolver-secrets.local.json

编辑两个 `.local.json` 文件：

- `evolver-config.local.json` 保存仓库坐标、端口、目录和 Assignee，不保存密钥。必须替换 `publishRepository` 和 `assignees` 占位符。
- `evolver-secrets.local.json` 保存 Evolver Bot 的 GitCode Token；只有 `webhook` 或 `both` 模式才需要至少 32 个随机 UTF-8 字节的 Webhook Secret。

`triggerMode` 可取 `polling`、`webhook`、`both`。未配置该字段的旧配置仍按 `webhook` 运行；新示例使用 `polling`。`gitCodeToken` 始终属于 Evolver Bot，用于读取 Issue/PR、推送分支、创建 PR 和评论，不得与用户提交 Issue 时使用的个人 PAT 混用。

Bugfix Agent 使用与 Feature Evolver 共用的模型可靠性 harness：有限超时、空响应检测、瞬时模型重试、接近上下文上限时压缩，以及对历史工具结果的截断。`maxPrimaryRepairRounds`、`maxDiagnosticRepairRounds` 和 `maxTransientStageRetries` 默认分别为 5、3、5。Agent 在同一层级的同一 conversation 中接收 Controller 修复反馈；零参数 `runApprovedGate` 的命令、范围和选择器完全由服务决定，Agent 最终返回后 Controller 仍会强制复验。

`smokeTestEnabled` 开启发布前 smoke；`smokeTestRepository` 指向独立测试仓检出，`smokeTestSelectors` 配置 1～3 个精确 Java 类名，`smokeTestTimeoutMinutes` 限制安装当前修复版本和运行 smoke 的总时长。测试仓更新必须在服务停止时完成，随后重启服务以冻结新的 smoke 指纹。

如需允许运维人员跳过创建时间窗口、手动检查仓库中全部已开启 Issue，可在 loopback 部署中设置 `"manualFullScanEnabled": true`。该功能只在 `bindHost` 为 `127.0.0.1` 且 `triggerMode` 为 `polling` 或 `both` 时可用。触发命令为：

    curl -X POST -H 'X-Issue-Evolver-Admin: full-scan' http://127.0.0.1:8081/admin/poll/full

HTTP 202 表示全扫已异步排队，409 表示定时扫描或另一轮全扫仍在运行。全扫分页读取全部 open Issue，并再次进行大小写精确的 `triggerLabel` 校验；它不读取或修改滚动扫描断点，但仍使用终身 Issue 准入，因此不会重复创建 Job。

### CodeCheck 云端反馈闭环

设置 `codeCheckFeedbackEnabled: true` 后，PR 对账只信任精确登录名
`codeCheckBotLogin`（默认 `openJiuwen-bot`）的评论。评论报告 CodeCheck 失败且包含受支持的
OpenLibing 报告链接时，服务通过固定 HTTPS 源、固定路径、禁止重定向和响应大小限制的只读
适配器提取结构化报错，将同一评论版本原子去重后交给 Controller 在同一 PR 分支返工。

任务只有在 PR 已合入且精确带有 `codeCheckSuccessLabel`（默认 `ci-successful`）时才进入
`MERGED`。PR 已合入但尚无该标签时仍保持 `WAITING_REVIEW`。GitCode Bot PAT 永远不会发送给
OpenLibing。受控适配器使用报告链接中的不透明 TASK ID、UUID 和 Project ID 直接执行匿名 POST，
不访问易触发 WAF 的 HTML 页面，不使用 HEAD 探测，也不发送 Cookie、CSRF、GitCode PAT 或模型
凭据。完整报告 URL 视为敏感的能力链接，不写入公开日志或 Agent Transcript。

任务成功后，独立的 `CodingStandardCuratorAgent` 才会读取已净化的结构化 CodeCheck 规则证据。
它没有仓库写入、Shell、网络或发布工具，只能加载完整 `coding-standard-full` 并提出通用预防建议。
Controller 校验规则 ID、分类和内容边界后把经验保存到 SQLite，供后续新 Issue Worker 会话使用；
Curator 不修改权威 Skill，也不阻塞当前 Issue 的成功状态。

模型配置使用仓库统一的 `examples/apiconfig.json`。该文件与 secrets 文件必须由用户预先配置；安装型 Agent 不得打开、打印、总结、复制或代写其内容。

`codingStandardSkill` 必须指向仓库的 `.claude/skills/coding-standard-full`。服务启动时会校验索引及
20 个分类文件全部可读，再复制到 Worktree 外的可信 Skill 根；旧的
`resources/skills/coding-standard` 路径会因规则集不完整而被拒绝。

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

该入口不连接 GitCode，使用内存 HTTP 响应、可注入时钟和临时 SQLite 验证窗口、标签、分页断点、手动全扫、跨通道终身去重、迁移回填、PR 对账，以及 Controller Gate、文件工具边界和故障分类。

polling-only 无需公网入站地址或 GitCode Webhook。`webhook`/`both` 的 Quick Tunnel URL 每次可能变化，用户必须人工把返回的 URL 配置到 GitCode，选择 Issue 和 Pull Request 事件，并使用本地 secrets 文件中的同一个 Webhook Secret。Skill 不会修改 GitCode 设置。

## 运行流程

1. polling 启动后立即冻结 `[now-24h, now]` 窗口，按创建时间升序、每页 100 条读取 open Issue；每轮最多 10 页，超限则持久化下一页断点。
2. 服务再次校验创建时间、open 状态和大小写精确的 `bug` 标签；`webhook`/`both` 同时支持签名 Webhook，Webhook 触发标签也来自 `triggerLabel`。
3. Worker 获取 Issue 和评论，确认 Issue 仍为 open，且不存在活动任务或未关闭 PR。
4. Worktree Manager 从配置的基线创建短路径、`--no-checkout` sparse Worktree，只检出 `src/main` 和 `src/test`。
5. ReAct Agent 显式加载从 `.claude/skills/coding-standard-full` 完整暂存的
   `coding-standard-full` 与 `gitcode-issue-evolver-worker`。它必须先按
   `G.FMT → G.NAM → G.DCL → G.MET → G.CTL → G.EXP → G.ERR → G.CMT → G.OTH`
   读取基线，再按代码场景的规定顺序读取完整分类文件；`resources/skills/coding-standard` 仅是兼容路由。
   Agent 只使用分页、限流、跳过不可解析文件的受限文件工具。主修复最多 5 轮；仍失败时启动独立
   conversation 的诊断 Agent，最多 3 轮。
6. Agent 可调用零参数 `runApprovedGate` 获取结构化反馈；Controller 按 HEAD、当前差异、固定命令、`pom.xml`、`.mvn`、smoke 测试仓内容和精确选择器计算指纹。编译通过后进入 `SMOKE_TESTING`，安装当前 Worktree 版本并运行配置的 JiuwenTestJava smoke；两段都通过才由受控 Committer 暂存精确文件。
7. Publisher 复核 Issue、分支、SHA、路径和 Gate，再推送发布仓库、创建 PR、指定 Assignee 并评论原 Issue。
8. 每轮 polling 也会轮转检查 `WAITING_REVIEW` PR，将已合并或已关闭任务对账为 `MERGED` 或 `CLOSED`；不会执行 Merge。

启用手动全扫后，本机管理员可随时通过管理端点拉取全部已开启、精确符合标签的 Issue。这个操作只绕过 `created_at` 窗口，不绕过标签、状态、终身去重或 Worker 后续校验。

Webhook 与 polling 使用同一条 SQLite Issue 准入记录竞争。同一仓库的同一 Issue 在服务重启、跨触发通道和跨终态后都不会再次自动进入流程。SQLite v8 保存修复轮次、结构化 failure event、包含 smoke 输入的 Gate receipt、`SMOKE_TESTING` 状态、CodeCheck 评论版本对应的 PR HEAD，以及成功 CodeCheck 的 Curator 任务和可信经验，可在重启后恢复有界失败上下文、区分每次推送产生的新机器人评论并按相同指纹复用凭据。瞬时故障进入 `RETRY_SCHEDULED`；无需修改、外部阻塞、自动化耗尽、配置、策略和内部错误分别进入独立终态，不再统一写入 `FAILED_FINAL`。瞬时轮询失败会在下一周期重试并且不推进分页断点，`/health/ready` 仍返回 200，同时仅公开非敏感 Controller 策略和最近扫描结果。

## 安全边界

Issue 修复 Agent 不接收 GitCode Token，也不拥有 Shell、HTTP、Git、Push、PR 或 Merge 工具。Token 只在非 Agent Publisher 的 Git/API 调用中使用。Issue、评论和 Webhook 字段均按不可信数据处理；绝对路径、路径穿越、符号链接逃逸和 sparse 范围外修改会被拒绝。JiuwenTestJava 路径和 smoke 选择器只由 Controller 配置，测试仓不会注册为 Agent 文件工具根目录。

这是 Demo，不适合作为无人值守生产服务。Quick Tunnel、文件 secrets 和单进程 SQLite 都是本地展示取舍。
