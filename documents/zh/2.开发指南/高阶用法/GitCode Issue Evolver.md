# GitCode Issue Evolver

GitCode Issue Evolver 是一个独立运行的 Bugfix 自动化示例服务。它以带有指定标签的 GitCode Issue 为入口，在隔离的 sparse Worktree 中运行受约束 ReAct Agent，完成最小 Java 修改、固定编译 Gate 和指定 JiuwenTestJava smoke 用例，再由服务端受控组件提交代码、推送分支、创建 PR，并在原 Issue 中回填 PR 地址。

它面向范围明确、能够从现有仓库代码中定位和修复的 Bug，不负责 Feature 规格设计、完整测试仓回归、System Test 用例开发、PR 合入或生产部署。

> Issue Evolver 不自动 Merge PR。启用 smoke 后，当前 Job 只运行配置的 1～3 个精确测试类，不执行测试仓完整套件；PR 的审核、功能验证与合入始终由人工完成。

## 能力定位

完整的 Issue Evolver 包含以下能力：

- 通过 Polling、Webhook 或两者组合接入 GitCode Bug Issue；
- 对仓库、标签、状态和创建时间执行准入校验；
- 使用 SQLite 保证同一个 Issue 在整个生命周期最多自动准入一次；
- 在独立短路径 sparse Worktree 中只检出 `src/main` 和 `src/test`；
- 为 Worker Agent 加载 `coding-standard` 与专用 Bugfix Worker Skill；
- 只向 Agent 提供 Worktree 内的文件读取、搜索和写入工具；
- 向 Agent 注册零参数 `runApprovedGate`，由 Controller 固定执行 Java 编译与精确 smoke Gate；
- 在同一 ReAct 会话内反馈有界失败信息并自动修复，再以独立诊断会话兜底；
- 由受控 Committer 精确暂存允许范围内的变更；
- 使用 Evolver Bot 推送发布分支、创建标准化 PR、设置 Assignee 并评论原 Issue；
- 轮询或通过 Webhook 对账等待审核的 PR，将 Job 更新为 `MERGED` 或 `CLOSED`；
- 持久化 Job、状态迁移、触发事件、Issue 准入记录和轮询断点；
- 提供 loopback-only 健康检查、systemd 运维日志和轮转文件日志。

Issue Evolver 与 Feature Evolver 是两个独立服务。前者处理边界清晰的 Bugfix，并复用 Controller 修复闭环与模型可靠性基础能力；后者还包含规格、设计、TDD、独立评审和 System Test 双 PR 流程。两者不共享数据库、Job 或运行期状态。

## 完整 Bugfix 流程

```text
Bug Issue
  │
  ├─ Polling：过去 24 小时内创建、open、精确带有 bug 标签
  └─ Webhook：签名有效的 update 事件中新加入 bug 标签
  ▼
Admission + Lifetime Dedupe
  ▼
RECEIVED
  → PLANNING
  → IMPLEMENTING
  → VERIFYING
  → SMOKE_TESTING                     ← 启用时运行固定 JiuwenTestJava smoke
  → COMMITTED
  → PUBLISHING
  → PR_CREATED
  → WAITING_REVIEW                    ← 人工审核、验证并合入或关闭 PR
       ├─ MERGED
       └─ CLOSED

瞬时故障           → RETRY_SCHEDULED → PLANNING
外部真实阻塞       → BLOCKED_EXTERNAL
自动修复耗尽       → FAILED_AUTOMATION
配置/策略/内部错误 → FAILED_CONFIGURATION / FAILED_POLICY / FAILED_INTERNAL
取消请求           → CANCEL_REQUESTED → CANCELLED
```

一次正常执行依次完成：

1. 接收并校验 Issue 触发事件；
2. 在 SQLite 中原子竞争 Issue 终身准入记录；
3. Worker 重新读取 Issue 正文和评论，并确认 Issue 仍为 open；
4. 检查是否已经存在与该 Issue 或分支关联的 open PR；
5. 从配置的基线创建独立 sparse Worktree；
6. ReAct Agent 检查仓库证据并写入最小 Java 修复；
7. Agent 可调用零参数 `runApprovedGate`；Controller 在最终响应后仍强制执行相同 Gate；
8. 编译通过后安装当前修复版本，并在独立测试仓只运行配置的精确 smoke 测试类；
9. Gate 失败时把结构化摘要回送当前 ReAct 会话，主修复耗尽后再启动独立诊断会话；
10. Gate 通过后，受控 Committer 只提交经过路径校验的文件；
11. Publisher 再次核对 Issue、分支、提交 SHA、变更路径和 Gate 结果；
12. Evolver Bot 推送发布分支、创建 PR、设置 Assignee 并评论原 Issue；
13. 服务等待人工审核，通过后仅观察 PR 的 merged 或 closed 状态。

## 总体架构

```text
GitCode Issue
     │
     ├──────── Polling Scheduler ────────┐
     └──────── Signed Webhook ───────────┤
                                        ▼
                         Admission + Lifetime Dedupe
                                        │
                                        ▼
                         SQLite Job Store / Event Log
                                        │
                         ┌──────────────┴──────────────┐
                         ▼                             ▼
                   Single Worker              PR Reconciliation
                         │                             │
                         ▼                             │
             Sparse Worktree + ReAct Agent            │
                         │                             │
                         ▼                             │
          Controller Compile + Smoke Gate             │
                         │                             │
                         ▼                             │
              Controlled Commit + Publisher ──────────┘
                         │
                         ▼
                 Publish Fork + GitCode PR
```

各组件的职责边界如下：

| 组件 | 职责 |
| --- | --- |
| Polling Coordinator | 冻结扫描窗口、分页读取 Issue、保存断点，并轮转对账待审核 PR |
| Webhook Handler | 校验签名、Delivery ID、事件类型、仓库和新增标签 |
| Admission | 在同一事务中完成 Delivery 去重、Issue 终身去重和 Job 创建 |
| SQLite Job Store | 保存 Job、状态、租约、PR、轮询断点和审计事件 |
| Worker | 领取 Job、刷新 Issue、驱动执行、处理取消与失败分类 |
| Worktree Manager | 从可信基线创建、标记和清理短路径 sparse Worktree |
| ReAct Agent | 在受限文件工具和 Skill 约束下分析并修改 Java 文件 |
| Approved Gate Controller | 固定编译命令、测试仓、smoke 选择器和超时，并产生可缓存证据 |
| JiuwenTestJava Checkout | 提供只读合同下的指定 smoke 用例；不暴露给 Agent 文件工具 |
| Committer | 只暂存经过 Repository Profile 校验的精确变更文件 |
| Publisher | 使用 Bot 权限推送、创建或对账 PR，并评论原 Issue |

## Issue 触发与准入

### 触发模式

`triggerMode` 支持三种模式：

- `polling`：服务主动调用 GitCode REST API；
- `webhook`：只接收经过签名验证的 GitCode Webhook；
- `both`：同时启用两种入口，由 SQLite 在跨通道并发时统一去重。

旧配置缺少 `triggerMode` 时按 `webhook` 运行；当前示例配置使用 `polling`。

### Polling 准入

Polling 在服务启动后立即扫描，此后按 `pollIntervalMinutes` 固定延迟执行。每次开始新窗口时冻结：

```text
[scanStart - issueScanWindowHours, scanStart]
```

默认规则为：

- 只读取 `open` Issue；
- `created_at` 位于冻结的过去 24 小时窗口内，边界包含在内；
- 精确、大小写敏感地包含 `triggerLabel`，默认是 `bug`；
- 按创建时间升序读取；
- 每页 100 条，每轮最多读取 `maxIssueScanPages` 页，默认最多 10 页；
- 超出单轮页数上限时保存窗口和下一页，下一周期继续同一个窗口；
- 单页或单轮失败不推进相应断点，下一周期重试。

API 返回后，服务还会本地复核时间、状态和标签，不能只依赖远端查询参数。

“过去 24 小时”只看 `created_at`。旧 Issue 最近更新正文、增加评论或后来才被修改标签，不会被 Polling 当成新 Issue。

### Webhook 准入

Webhook 入口只有在 `triggerMode=webhook` 或 `both` 时注册。Issue 事件必须满足：

- HMAC 签名有效；
- Delivery ID、事件头和 JSON 请求体有效；
- 仓库与 `targetRepository` 完全一致；
- 事件动作是 `update`；
- 本次更新明确新增了大小写精确的 `triggerLabel`。

Webhook 不使用 Polling 的 24 小时创建窗口。事件建立 Job 后，Worker 仍会重新调用 GitCode API，确认 Issue 当前为 open 并读取最新正文和评论。

### 终身去重

Polling 和 Webhook 共用 `(repo, issue_iid)` Issue 准入记录。以下情况都不会建立第二个 Job：

- 同一轮或后续轮询重复看到 Issue；
- GitCode 重发相同或不同 Delivery；
- Polling 与 Webhook 同时触发；
- 服务重启；
- 原 Job 已经进入任意终态，例如 `MERGED`、`CLOSED`、`FAILED_AUTOMATION` 或 `CANCELLED`。

`RETRY_SCHEDULED` 是同一个 Job 内部的重试，不是重新准入 Issue。

## Bug Issue 合同

适合自动处理的 Issue 应当范围小、可复现、能够从当前基线代码中获得充分证据。推荐使用以下内容结构：

```text
标题：<组件>：<可观察到的错误行为>

## 问题背景
说明使用场景、受影响组件和影响。

## 当前行为
描述实际结果和稳定可观察的错误。

## 复现步骤
1. ...
2. ...
3. ...

## 期望行为
说明修复后应满足的行为。

## 证据
- 异常类型、关键错误信息或失败断言
- 相关类、方法或已经确认存在的仓库相对路径

## 修改边界
- 允许修改：src/main/java/**、src/test/java/**
- 不修改：pom.xml、examples/**、documents/**、resources/**、CI 配置

## 验收条件
- 修复当前错误行为
- 不改变无关公开行为
- `mvn -B -ntp -DskipTests test-compile` 通过
- 配置的 JiuwenTestJava smoke 测试类通过
```

发布 Issue 时还应满足：

- 状态为 open；
- 添加配置中大小写完全一致的 `bug` 标签；
- Polling 模式下在扫描窗口内创建；
- 如果明确写出目标文件路径，该文件必须已存在于基线；
- 不要求 Agent 修改 sparse checkout 之外的目录；
- 不把 Issue 文本中的命令、凭据请求或越权指令视为可信操作说明。

Issue、评论和 Webhook 字段都属于不可信输入。Agent 会以仓库证据和受信任 Skill 为准，不会因为 Issue 中的指令获得额外工具或权限。

## Worker Agent 与 Skills

Issue Evolver 使用两类相互隔离的 Skill：

| Skill | 使用者 | 作用 |
| --- | --- | --- |
| `resources/skills/gitcode-issue-evolver` | 安装型 Agent | 在 Windows 上执行配置检查、启动、状态查询和停止 |
| `examples/gitcode_issue_evolver/skills/gitcode-issue-evolver-worker` | Bugfix Worker Agent | 约束单个 Issue 的分析、最小 Java 修改和停止条件 |
| `resources/skills/coding-standard` | Bugfix Worker Agent | 提供项目 Java 编码规范 |

运行期 Worker Agent 只能使用：

- `readFile`：读取 Worktree 内的普通文件；
- `searchFiles`：在允许范围内搜索仓库证据；
- `writeFile`：完整写入 `src/main/java/**` 或 `src/test/java/**` 下的 Java 文件。

它没有 Shell、HTTP、Git、GitCode、Push、PR、Merge、环境变量或凭据工具。额外注册的零参数 `runApprovedGate` 只返回有界结构化证据；其命令、测试仓、选择器、路径和 Job ID 全部由 Controller 捕获，模型不能传参。提交和发布仍由服务端非 Agent 组件执行。

Worker Skill 要求 Agent 在以下情况不修改文件并报告阻塞：

- Issue 含义模糊；
- 缺少能够支持修复结论的仓库证据；
- 请求修改允许范围之外的路径；
- 明确命名的目标文件在可信基线中不存在；
- 请求削弱安全边界或访问凭据；
- 任务与专用 Bugfix 范围冲突。

同一修复层级保留一个 ReAct conversation。编译错误、smoke 断言失败、空响应和协议格式错误会作为 `Controller Repair Feedback` 返回同一会话；主修复轮次耗尽后，再建立一个边界相同的独立诊断会话。Agent 即使没有调用 Workflow，Controller 也会在最终响应后强制执行 Gate。Gate receipt、修复轮次和有界失败事件会持久化，进程重启后可恢复上下文。

## Worktree 与修改边界

### 基线与 sparse checkout

Worktree Manager 会先在 `localRepository` 中获取配置的 `origin/<baseBranch>`，然后创建独立分支和 `--no-checkout` Worktree，只检出：

```text
src/main
src/test
```

因此部署仓库的 `origin` 和基线分支必须与 `targetRepository` 的可信基线保持一致。Worktree 使用服务生成的所有权标记；清理器只删除属于当前 Job 的资源，不会清理未知目录。

### 允许和禁止的变更

Repository Profile 只允许：

```text
src/main/java/**
src/test/java/**
```

同时明确排除：

```text
examples/**
src/main/java/examples/**
pom.xml
documents/**
resources/**
CI 配置
生成目录和 Worktree 外路径
```

绝对路径、Windows 盘符路径、`..` 路径穿越、符号链接逃逸和不属于当前 Worktree 的文件都会被拒绝。

在创建 Agent 前，服务会提取 Issue 与评论中明确出现的 `src/main/**`、`src/test/**` 等目标路径，并在未修改的基线 Worktree 中确认它们是已存在的普通文件。明确目标不存在时，Job 以 `TARGET_PATH_NOT_FOUND` 失败，而不是让模型猜测或创建同名生产类。

## Approved Gate、smoke 与修复尝试

第一段使用固定验证命令：

```bash
mvn -B -ntp -DskipTests test-compile
```

该命令的语义是：

- 编译主代码；
- 编译测试代码；
- 不执行测试；
- 最长运行 20 分钟；
- 模型不能替换命令或增加参数。

启用 `smokeTestEnabled` 后，编译通过才进入第二段。Controller 先以 `maven.test.skip=true` 安装当前 Worktree 的项目版本，再在配置的独立 JiuwenTestJava checkout 中执行：

```text
mvn -B -ntp -f <固定测试仓>/pom.xml \
  -Dagent-core-java.version=<当前项目版本> \
  -Dtest=<1～3 个固定精确类名> clean test
```

测试仓不会注册为 Agent 文件根，模型不能读取或修改测试仓，也不能选择 Maven 参数、测试类、仓库或 Job。服务不运行 `-Dgroups=smoke`、通配包、完整测试仓套件或额外 `verify`。

默认主修复最多 5 个产生新文件指纹的轮次，独立诊断层最多 3 轮。相同指纹的确定性结果直接复用，不重复执行 Maven；文件、构建策略、smoke 测试仓内容或选择器变化都会使凭据失效。重复查询缓存不消耗修复轮次。

Gate 结果按性质处理：

| 结果 | Job 行为 |
| --- | --- |
| 编译与 smoke 均通过 | 继续精确变更校验、提交和发布 |
| 编译错误或 smoke 断言失败 | 作为 `AGENT_CORRECTABLE` 反馈当前 ReAct 会话 |
| Maven、依赖或进程等瞬时故障 | 进入 `RETRY_SCHEDULED`，按持久化退避时间重试 |
| 修复轮次耗尽 | 进入 `FAILED_AUTOMATION` |
| 无变更或变更越界 | 进入自动化或策略终态，不发布 PR |

PR 中的 “Verification” 会精确列出编译 Gate，以及启用时实际通过的 smoke 测试类。它不代表完整测试仓套件、完整回归或生产验证通过；更广覆盖仍由人工或目标仓 CI 负责。

## 受控提交与 PR 发布

### 分支与提交

服务根据 Issue IID 和标题生成分支：

```text
auto-evolving/issue-<iid>-<slug>
```

标题 slug 会被规范化为小写 ASCII，最长保留 40 个字符；为空时使用 `change`。

提交信息固定为：

```text
fix: resolve GitCode issue #<iid>
```

Committer 不执行宽泛的 `git add .`，只暂存经过 Repository Profile 校验的精确文件。提交后 Publisher 再检查当前分支、HEAD SHA、文件列表、Issue 状态和 Gate 结果。

### 标准化 PR 说明

PR 标题格式为：

```text
[Auto-Evolving Demo] Resolve issue #<iid>: <Issue title>
```

PR 正文由服务固定生成：

```text
Automated demo change for <targetRepository>#<iid>

Source Issue: <Issue URL>

Verification: `mvn -B -ntp -DskipTests test-compile` and JiuwenTestJava smoke `<精确类名>`.

This PR was created by the gitcode-issue-evolver example and requires human review and merge.
```

允许范围内涉及 `auth`、`authentication` 或 `security` 路径/文件名的高影响变更会创建为 Draft；其他变更创建为可审核 PR。无论是否 Draft，服务都不会批准或合入 PR。

### 幂等发布

Publisher 在推送和创建 PR 前后会通过 Issue、源分支和提交 SHA 对账：

- 已存在匹配 open PR 时绑定已有 PR，不重复创建；
- PR 创建响应不确定时重新查询，而不是直接重发；
- 已有 PR 的 head SHA 必须与当前提交一致；
- PR 创建成功后再在原 Issue 评论其 URL；
- 评论失败不会丢失已经创建的 PR，Job 会保留 PR 信息并重试通知。

PR 创建请求以 `baseBranch` 为目标分支，以 `publishRepository` 为发布源仓，并设置配置中的 `assignees`。

## GitCode 身份与凭据

Issue Evolver 涉及三类独立凭据：

| 凭据 | 保存位置 | 用途 |
| --- | --- | --- |
| Evolver Bot `gitCodeToken` | `evolver-secrets.json` 或本地 `.local.json` | 读取 Issue/PR、推送分支、创建 PR、设置 Assignee、评论 Issue |
| `webhookSecret` | 同一 secrets 文件 | 仅在 `webhook` 或 `both` 模式校验 GitCode Webhook 签名 |
| 模型 API 凭据 | 外部 `apiconfig.json` | 调用 Worker 使用的模型服务 |

Evolver Bot Token 必须与用户提交 Issue 时使用的个人 PAT 分开。个人 PAT 不属于服务配置，也不会传入 Worker Agent。

发布到 Fork 时，Bot 身份应与 `publishRepository` 的所有者匹配，并具有完成读取、推送、创建 PR 和评论所需的最小权限。服务不会把 Token 放入 Agent Prompt 或文件工具结果。

不要把真实 Token、Webhook Secret、模型密钥、`.local.json`、运行日志或运行数据库提交到 Git。

## 持久化、租约与恢复

SQLite 默认保存以下信息：

- Webhook/Polling Delivery 及其处理结果；
- Evolution Job、状态、重试次数、租约、分支、提交 SHA 和 PR；
- 每次状态迁移事件；
- `(repo, issue_iid)` 终身准入记录；
- Polling 冻结窗口和下一页断点；
- 待审核 PR 的最近检查时间。
- 主修复/诊断轮次、结构化失败事件和下一次瞬时重试时间；
- 包含编译策略、修改内容、测试仓指纹与 smoke 选择器的 Gate receipt。

Worker 使用租约领取 Job 并定期续租。进程异常退出后，过期租约会恢复为可重试状态，避免 Job 永久卡在执行中。状态更新和 PR 对账使用版本检查；Webhook 与 Polling 并发更新时会重新读取最新状态，已经到达终态的 Job 不会回退。

可恢复的 GitCode 429、5xx、网络、模型、Maven/依赖基础设施、Git 或通知问题进入 `RETRY_SCHEDULED`。无法由代码修复的真实环境或产品合同问题进入 `BLOCKED_EXTERNAL`；自动修复耗尽、错误配置、路径策略冲突和未知内部错误分别进入独立终态，避免盲目重放。

## PR 对账与人工边界

每次 Polling 扫描完成后，协调器会分批轮转读取 `WAITING_REVIEW` Job，并查询 GitCode PR：

| GitCode PR 状态 | Job 状态 |
| --- | --- |
| open/opened | 保持 `WAITING_REVIEW`，更新最近检查时间 |
| merged | `MERGED` |
| closed | `CLOSED` |
| 未知状态 | 保持等待并记录告警 |

PR Webhook 也可以完成相同终态对账。服务不执行 Merge，不增加 Merge 权限，也不处理 Review 评论驱动的自动返工。

正常人工工作包括：

- 审查代码和 PR 说明；
- 运行必要的测试和功能复现；
- 要求人工修改、关闭或合入 PR；
- 对高影响 Draft PR 决定是否转为 Ready。

## 配置

非密配置示例位于：

```text
examples/gitcode_issue_evolver/config/evolver-config.example.json
examples/gitcode_issue_evolver/config/evolver-config.linux.example.json
```

核心字段如下：

| 字段 | 含义 | 默认/示例 |
| --- | --- | --- |
| `bindHost` | 本地 HTTP 监听地址 | `127.0.0.1` |
| `port` | 健康检查和可选 Webhook 端口 | `8081` |
| `dataDir` | SQLite 等持久化数据目录 | 仓库外目录 |
| `worktreeRoot` | Job Worktree 根目录 | 仓库外目录 |
| `localRepository` | 用于 fetch 和创建 Worktree 的可信部署仓库 | `.` 或部署路径 |
| `codingStandardSkill` | 编码规范 Skill | `resources/skills/coding-standard` |
| `issueWorkerSkill` | Bugfix Worker Skill | Example 内专用 Skill |
| `targetRepository` | Issue 和 PR 的目标仓库 | `openJiuwen/agent-core-java` |
| `publishRepository` | Bot 推送分支的 Fork | `<fork-owner>/agent-core-java` |
| `baseBranch` | Worktree 基线和 PR 目标分支 | `730` |
| `assignees` | PR Assignee 登录名列表 | 必须替换占位值 |
| `workerConcurrency` | Worker 并发数 | 当前为 `1` |
| `triggerMode` | `polling`、`webhook` 或 `both` | 新示例为 `polling` |
| `triggerLabel` | 大小写精确的 Issue 标签 | `bug` |
| `issueScanWindowHours` | Polling 的 Issue 创建时间窗口 | `24` |
| `pollIntervalMinutes` | Polling 固定延迟 | `15` |
| `maxIssueScanPages` | 每轮最多读取的 100 条分页数 | `10` |
| `maxPrimaryRepairRounds` | 同一主 ReAct 会话的修复轮次 | `5` |
| `maxDiagnosticRepairRounds` | 独立诊断 ReAct 会话的修复轮次 | `3` |
| `maxTransientStageRetries` | 瞬时阶段重试上限 | `5` |
| `smokeTestEnabled` | 是否在 PR 发布前执行 JiuwenTestJava smoke | 旧配置默认 `false`，新示例为 `true` |
| `smokeTestRepository` | 独立、可读的 JiuwenTestJava Git checkout | 仓库与 Worktree 外路径 |
| `smokeTestSelectors` | 1～3 个精确 Java 测试类全名 | 不支持分组或通配符 |
| `smokeTestTimeoutMinutes` | 安装当前版本与执行 smoke 的总超时 | `30` |
| `gitUserName` | 自动提交的 Git 用户名 | `gitcode-issue-evolver` |
| `gitUserEmail` | 自动提交的 Git 邮箱 | `gitcode-issue-evolver@localhost` |

secrets 文件只包含：

```json
{
  "gitCodeToken": "<replace-with-minimum-permission-token>",
  "webhookSecret": ""
}
```

Polling-only 允许 `webhookSecret` 为空；`webhook` 和 `both` 要求至少 32 个随机 UTF-8 字节。

## 健康检查、日志与观察

服务公开两个 loopback 健康接口：

```text
GET /health/live
GET /health/ready
```

Polling 模式的 readiness 包含：

- `status`；
- `triggerMode`；
- 最近扫描结果；
- 最近尝试时间；
- 最近成功时间。
- Controller Gate profile；
- smoke 是否启用及选择器数量。

单次 Polling 失败不会使 readiness 返回非 200；它只把扫描结果标为失败，下一周期继续重试。响应不会包含 Token、原始异常或 Issue 内容。

Linux 部署常用命令：

```bash
systemctl status gitcode-issue-evolver.service --no-pager -l
journalctl -u gitcode-issue-evolver.service -f
curl --fail http://127.0.0.1:8081/health/ready
```

Logback 还会在 `/var/log/gitcode-issue-evolver` 写入轮转日志。日志可能包含 Issue 文本、源码片段、工具输出和模型消息，只应授权给服务运维人员读取。

实时观察主要依赖 readiness、journald、轮转日志、GitCode Issue/PR 页面和 SQLite Job 记录。启用 `manualFullScanEnabled` 时，还会注册仅监听 `127.0.0.1` 的 `POST /admin/poll/full`，用于人工触发全部 open Issue 的精确标签扫描；它只忽略时间窗口，仍遵守终身准入去重。

## 启动与部署

### Windows 本地演示

公开安装 Skill 的确定性入口为：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  resources/skills/gitcode-issue-evolver/scripts/manage.ps1 `
  -Action Start `
  -RepositoryRoot "<repository-root>"
```

支持的操作还有 `Check`、`Status` 和 `Stop`。Polling-only 不启动 cloudflared；`webhook` 和 `both` 可为交互式演示启动临时 Quick Tunnel，但 GitCode Webhook URL 与 Secret 仍由用户人工配置。

安装 Skill 在 `Start` 前只检查 JDK、Maven、Git、配置与 Example 编译，不运行主工程完整 `mvn test`。单个 Bugfix Job 的编译和 smoke 是 Controller Gate，与服务启动检查相互独立。

### Linux systemd

推荐布局：

```text
/opt/gitcode-issue-evolver/repo
/etc/gitcode-issue-evolver/evolver-config.json
/etc/gitcode-issue-evolver/evolver-secrets.json
/etc/gitcode-issue-evolver/apiconfig.json
/var/lib/gitcode-issue-evolver/data
/var/lib/gitcode-issue-evolver/worktrees
/var/lib/gitcode-issue-evolver/jiuwen-test-java
/var/log/gitcode-issue-evolver
```

启用 smoke 时，应在服务停止状态下把 JiuwenTestJava 的 `agent_core_java` 分支检出到上述独立目录，并确保 `gitcode-evolver` 账号可以读取源码以及写入 Maven `target`。systemd 的 `ProtectHome=true` 会阻止服务直接读取 `/home/.../workspace/jiuwen-test-java`，因此线上配置不能复用 IDE 工作区路径。

使用专用系统账号构建 Example：

```bash
cd /opt/gitcode-issue-evolver/repo
sudo -u gitcode-evolver bash \
  examples/gitcode_issue_evolver/scripts/build-demo.sh
```

检查外部配置：

```bash
sudo -u gitcode-evolver bash \
  examples/gitcode_issue_evolver/scripts/run-service.sh \
  --config /etc/gitcode-issue-evolver/evolver-config.json \
  --secrets /etc/gitcode-issue-evolver/evolver-secrets.json \
  --llm-config /etc/gitcode-issue-evolver/apiconfig.json \
  --check
```

systemd 模板位于：

```text
examples/gitcode_issue_evolver/deploy/systemd/gitcode-issue-evolver.service
```

Polling-only 只需要访问 GitCode、模型端点和 Maven 仓库的出站网络，不需要公网入站地址。`webhook` 或 `both` 模式应使用受信任 HTTPS 反向代理；不要对公网直接开放本地 8081 端口。

## 验证

### Example 确定性测试

Linux/macOS 可运行：

```bash
bash examples/gitcode_issue_evolver/scripts/test-demo.sh
```

该入口会构建 Example，并使用伪 HTTP 响应、可注入时钟和临时 SQLite 验证：

- GitCode Issue 列表参数、分页和重试；
- 24 小时时间边界、状态和精确标签；
- 100 条分页与超过单轮上限后的断点续扫；
- Polling/Webhook 跨通道终身去重；
- 历史数据库迁移与 Issue 准入回填；
- PR open、merged、closed 对账；
- 零参数 Gate、同会话修复、Gate 指纹缓存和结构化失败分类；
- 固定 source install 与精确 JiuwenTestJava smoke 命令，且不扩大为完整套件；
- `VERIFYING → SMOKE_TESTING → COMMITTED` 状态迁移；
- polling、webhook、both 三种触发模式及 Webhook 端点注册行为。

这些确定性测试不连接真实 GitCode，也不会创建 Issue、分支、评论或 PR。部署验收还应单独使用配置中的精确 smoke 类执行一次真实 Maven Gate。

### 真实流程验收

部署到测试环境后，可按以下顺序验证：

1. 确认目标仓、发布 Fork、基线、Assignee 和 Bot 权限；
2. 运行 `--check` 并启动服务；
3. 查看 `/health/ready`，确认 Polling 已至少成功一次；
4. 人工创建一个满足 Bug Issue 合同的 open Issue，并添加精确的 `bug` 标签；
5. 等待下一轮 Polling，观察日志中的准入、Job、`VERIFYING`、`SMOKE_TESTING` 和 Gate 结果；
6. 确认发布 Fork 出现自动分支，目标仓出现标准化 PR，原 Issue 出现 PR 评论；
7. 人工检查代码、执行必要测试并合入或关闭 PR；
8. 等待下一轮 Polling 或 PR Webhook，把 Job 对账为 `MERGED` 或 `CLOSED`。

## 当前边界

使用前应明确以下限制：

- 这是面向演示的单 Worker Bugfix 服务，不是无人值守生产开发平台；
- 只处理 `src/main/java/**` 和 `src/test/java/**` 范围内的现有 Java Bug；
- 不修改 POM、CI、文档、示例、Skill、生成文件或仓库其他目录；
- 每个 Job 只运行 `test-compile` 和配置的 1～3 个 JiuwenTestJava smoke 类，不执行完整测试仓套件；
- 不支持 Feature 规格、设计、TDD 任务拆分、独立 Review Agent 或双 PR 流程；
- 已提供 Bugfix 阶段的 Approved Gate Workflow 和持久化修复上下文，但不复制 Feature 的多阶段 Gate；
- 当前没有 Feature Evolver 的无凭据 rootless 容器或自动依赖预取，Maven Gate 在宿主机运行；
- 不处理 PR Review 评论或 changes-requested 驱动的自动返工；
- 不自动把高影响 Draft PR 转为 Ready；
- 不自动 Merge、部署或发布版本；
- 同一个 Issue 终身只自动准入一次，终态后不能通过重新加标签自动重跑；
- 当前没有监控 Web UI；可选管理 API 只提供 loopback 全量标签扫描，不暴露 Job 控制；
- 文件 secrets、Quick Tunnel 和单进程 SQLite 都是示例实现取舍。

需要完整 Feature 上线与 System Test 交付时，应使用 [GitCode Feature Evolver](GitCode%20Feature%20Evolver.md)，不要扩大 Issue Evolver 的 Bugfix 合同。

## 参考入口

- [Issue Evolver Example README](../../../../examples/gitcode_issue_evolver/README.md)
- [Issue Evolver 示例配置](../../../../examples/gitcode_issue_evolver/config/evolver-config.example.json)
- [Issue Evolver 安装 Skill](../../../../resources/skills/gitcode-issue-evolver/SKILL.md)
- [Issue Evolver Worker Skill](../../../../examples/gitcode_issue_evolver/skills/gitcode-issue-evolver-worker/SKILL.md)
- [Linux 部署说明](../../../../examples/gitcode_issue_evolver/deploy/README-linux.md)
- [GitCode Feature Evolver](GitCode%20Feature%20Evolver.md)
