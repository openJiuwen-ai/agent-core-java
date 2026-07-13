---
name: agent-team-guide
description: Agent Team 团队应用快速构建指南。基于 com.openjiuwen.agentteams 包的 leader-teammate 团队框架。在用户构建、装配或运行 agent team、配置多智能体协作、使用 LeaderTeammateAgentTeam、写 team skill 代码、调用 spawn_member/create_task/send_message 等团队工具时主动应用。涉及关键词：agent team、智能体团队、leader-teammate、LeaderTeammateAgentTeam、TeamAgent、team skill、spawn_member、团队装配、多智能体协作、investment-analysis-team。不适用于：单 agent（ReAct/Workflow）问题、纯模型配置、与 agentteams 包无关的讨论。
---

# Agent Team 团队应用快速构建指南

本 skill 指导用户基于 `com.openjiuwen.agentteams` 包快速构建 leader-teammate 多智能体协作团队。框架主线：`LeaderTeammateAgentTeam.Builder` 声明团队 → `build()` 由 `TeamFactory` 创建 `TeamAgent` → `interact / deliverInput / broadcast / dispatchTask` 触发协作。

## 核心心智模型

- `LeaderTeammateAgentTeam` 是声明式入口，`TeamAgent` 是运行时宿主。
- `TeamBackend` 是数据与消息中枢，`Messager` 是传输层，`TeamDatabase` 是存储层。
- `CoordinatorLoop` 不做决策，只负责唤醒和周期轮询；真正的协作逻辑在 `EventDispatcher` + 团队工具里。
- leader 通过团队工具（`send_message`/`update_task`/`claim_task`/`spawn_member`）与 teammate 协作；teammate 通过 `invokeForSpawn(...)` 进入自己的 ReAct 循环。

## 关键概念速查

| 对象 | 作用 |
| --- | --- |
| `LeaderTeammateAgentTeam` | 团队顶层入口与门面，用 `Builder` 配置 |
| `TeamAgent` | 团队协调器与运行宿主 |
| `TeamAgentSpec` | 团队蓝图（name/members/modelPool/lifecycle/teammateMode/spawnMode/transport/storage） |
| `TeamMemberSpec` | 成员声明（name/role/description/modelName） |
| `TeamRole` | 四种角色：`LEADER` / `MEMBER` / `HUMAN_AGENT` / `USER` |
| `TeamBackend` | 维护成员、消息、任务，桥接 `Messager` 与 `TeamDatabase` |
| `CoordinatorLoop` | 守护线程，唤醒回调 + mailbox/task 周期轮询（默认 30 秒） |
| `EventDispatcher` | 消费 `InnerEventMessage` 和 transport 事件，转换为协作行为 |
| `Messager` | 进程间/进程内消息总线（`InProcessMessager` / `PyZmqMessager`） |
| `TeamMonitor` | 团队监控，订阅 team/task/message/broadcast 事件 |

## 场景速查表

按任务场景直接定位本 skill 的对应小节：

| 场景 | 跳转小节 |
| --- | --- |
| 从 0 装配最小团队 | "快速开始：最小团队" |
| 带预定义成员装配 | "快速开始：带预定义成员" |
| 直接运行 leader 处理任务 | "运行方式：直接运行 leader" |
| chat 类持续交互 | "运行方式：与用户持续交互" |
| 恢复 / 切换会话 / 销毁 | "运行方式：恢复与销毁" |
| 选 build_mode 还是 plan_mode | "配置选择：teammateMode" |
| 选 default / predefined / hybrid | "配置选择：teamMode" |
| 加人类成员（HITT） | "HITT：人类成员协作" |
| 加 team skill | "Team Skill：团队技能发现" |
| 多阶段任务流水线 | "多阶段数据流" |
| 查可用团队工具 | "团队工具集" |
| 排查成员状态 | "成员状态机" |

## 快速开始：最小团队

最小示例：默认 leader 自带、无预定义 teammate，`teamMode=default`（leader 运行期动态 spawn）。

```java
import com.openjiuwen.agentteams.LeaderTeammateAgentTeam;

LeaderTeammateAgentTeam team = LeaderTeammateAgentTeam.builder()
        .teamName("investment_analysis")
        .description("投资分析协作团队")
        .lifecycle(LeaderTeammateAgentTeam.LIFECYCLE_TEMPORARY)
        .teammateMode(LeaderTeammateAgentTeam.TEAMMATE_MODE_BUILD)
        .spawnMode(LeaderTeammateAgentTeam.SPAWN_MODE_INPROCESS)
        .storage(LeaderTeammateAgentTeam.STORAGE_SQLITE)
        .leaderMemberName("team_leader")
        .leaderDisplayName("投资组长")
        .leaderPersona("资深的投资分析专家，擅长拆解复杂问题并指派合适成员")
        .language("cn")
        .build()
        .build(); // 第一个 build() 组装 spec，第二个 build() 创建 TeamAgent

Map<String, Object> result = team.dispatchTask("分析 600519 的最新财报");
```

**两个 build() 缺一不可**：第一个生成 `TeamAgentSpec`，第二个才真正通过 `TeamFactory.createAgentTeam(...)` 创建 `TeamAgent`。

## 快速开始：带预定义成员

追加 `addPredefinedMember(...)` 即可声明固定成员。只要传入非 `HUMAN_AGENT` 的预定义成员，`Builder` 自动把 `teamMode` 解析为 `hybrid`。

```java
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;

LeaderTeammateAgentTeam team = LeaderTeammateAgentTeam.builder()
        .teamName("investment_analysis")
        .lifecycle(LeaderTeammateAgentTeam.LIFECYCLE_TEMPORARY)
        .addPredefinedMember(TeamMemberSpec.builder()
                .name("fundamental_analyst")
                .role(TeamRole.MEMBER)
                .description("基本面分析师")
                .modelName("fundamental_model")
                .build())
        .addPredefinedMember(TeamMemberSpec.builder()
                .name("technical_analyst")
                .role(TeamRole.MEMBER)
                .description("技术面分析师")
                .modelName("technical_model")
                .build())
        .build()
        .build();
```

## 运行前置（新手必看）

照着上面快速开始复制代码前，先确认三件事，否则跑不起来：

1. **Maven 编译路径**：`examples/` 目录默认不在 Maven 编译路径内（`pom.xml` 没把它注册为 source root）。要么把 `examples/` 挪到 `src/main/java/` 下，要么自己在 IDE 里加为 source root，否则 `examples.agent_teams.AgentTeamE2eExample` 找不到类。
2. **API 配置**：`src/main/resources/apiconfig.json` 里的 `API_BASE` / `API_KEY` / `MODEL_PROVIDER` / `MODEL_NAME` 要填真实值。`SharedExampleApiConfigLoader` 默认从 classpath 读这份文件，占位值（`your-api-key` 之类）会在启动时抛 `IllegalStateException: Missing required key in apiconfig.json`。可用 `-Dopenjiuwen.example.config=<path>` 或 `OPENJIUWEN_API_CONFIG` 环境变量覆盖。
3. **Team skill 放置**：如果用 team skill（如 `investment-analysis-team`），要把它放到框架会扫描的目录，否则 leader 发现不了：
   - 团队工作空间 `skills/`：`~/.openjiuwen/.agent_teams/<team_name>/team-workspace/skills/`
   - 全局：`~/.openjiuwen/workspace/skills/`
   - 当前工作目录的 `skills/` 子目录

启动命令（示例）：

```bash
mvn exec:java -Dexec.mainClass=examples.agent_teams.AgentTeamE2eExample
```

## 配置参数速查

| 字段 | 取值 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `lifecycle` | `temporary` / `persistent` | `temporary` | 临时团队任务完成即销毁；持久团队保持运行 |
| `teammateMode` | `build_mode` / `plan_mode` | `build_mode` | 见下方"teammateMode" |
| `spawnMode` | `inprocess` / `process` | `inprocess` | 进程内共享 JVM；process 需配 pyzmq |
| `transport` | `inprocess` / `pyzmq` | 按 spawnMode 推导 | inprocess 时默认 inprocess |
| `storage` | `sqlite` / `memory` / `postgresql` / `mysql` | `sqlite` | 团队持久化后端 |
| `teamMode` | `default` / `predefined` / `hybrid` | 由预定义成员推导 | 见下方"teamMode" |
| `modelPoolStrategy` | `round_robin` / `by_model_name` | `round_robin` | 模型分配策略 |

### 配置选择：teammateMode

决定 teammate 领取任务后的执行路径：

- `build_mode`：teammate 自主执行并直接标记完成，无需 leader 审批。**默认选这个**。
- `plan_mode`：teammate 必须先 `write_plan` 提交计划，等 leader `approve_plan` 审批后才能执行。需要严格质量门控时用。

### 配置选择：teamMode

决定 leader 的工作流程模板与可用团队工具：

- `default`：leader 运行期动态 spawn teammate，工具集含 `spawn_member`。**最小团队默认**。
- `predefined`：所有成员装配时确定，`spawn_member` 被排除。
- `hybrid`：既有预定义成员，也允许 leader 动态扩员。**只要传了预定义成员自动变 hybrid**。

### 配置细节

- 没显式声明 leader 时，`TeamAgentSpec.ensureLeader()` 自动追加名为 `team_leader` 的默认 leader。
- `spawnMode=inprocess` 时成员共享同一 JVM，便于本地调试；`process` 模式下成员以独立进程运行，必须配 `pyzmq` 传输。
- 保留成员名（`team_leader` / `human_agent` / `user`）不能被随意占用，`TeamAgentSpec.validate()` 会校验。
- 文件布局由 `TeamPaths` 管理：团队目录 `~/.openjiuwen/.agent_teams/<team_name>/`，共享内存在其下 `team-workspace/team-memory/`。

## 运行方式：直接运行 leader

装配后直接 `dispatchTask(...)`：

```java
Map<String, Object> result = team.dispatchTask("汇总本周投资观点");
// result 含 team_id、session_id、status、leader、route、target、delivered_content、message_id
```

需要流式输出时：

```java
Iterator<Object> chunks = team.agent().stream(Map.of("query", query), sessionApi);
while (chunks.hasNext()) {
    Object chunk = chunks.next();
    // 处理流式片段
}
```

## 运行方式：与用户持续交互

`interact(...)` / `deliverInput(...)` 都把输入塞进 `CoordinatorLoop`，区别在 agent 运行时是否走 steer：

```java
team.interact("换个角度看下估值");      // 等价于 deliverInput(message, true)
team.deliverInput("补充一下最新数据", false);

// leader 主动广播
String messageId = team.broadcast("请大家检查自己负责的任务状态");
```

## 运行方式：恢复与销毁

```java
// 持久团队：用现有 snapshot 恢复
TeamAgent restored = TeamFactory.recoverAgentTeam(snapshot);

// 切到新会话：保留存活 teammate，重新拉起
team.resumeForNewSession(newSessionId);

// 接续已有会话：不重新拉起，仅做 session 绑定
team.recoverForExistingSession(existingSessionId);

// 批量拉回异常成员
List<String> restarted = team.recoverTeam();

// 销毁
team.destroyTeam();          // 等价于 destroyTeam(true)
team.destroyTeam(false);     // 不强制关闭其他成员
```

snapshot 用 `team.snapshot()` 拿到，内部含 spec、context、leader_inbox、messages 和 model allocator 状态。

## 团队工具集

`TeamTools.createTeamTools(role, backend, teammateMode, excludeTools, ...)` 按 role 过滤工具，`TeamAgent.registerTeamTools()` 给每个工具 card id 加 `team_name.member_name` 后缀避免冲突。

| 类别 | 工具 | 说明 |
| --- | --- | --- |
| Leader 专属 | `build_team` / `clean_team` / `spawn_member` / `shutdown_member` / `approve_plan` / `approve_tool` / `create_task` / `update_task` / `list_members` | 只有 leader 能调用 |
| Member 专属 | `claim_task` / `enter_worktree` / `exit_worktree` | 只有 teammate 能调用 |
| 共享 | `view_task` / `send_message` / `workspace_meta` | leader 和 teammate 都有 |
| Human agent | `send_message`（仅此一个） | `role=human_agent` 的成员只有这一个工具 |

**动态调整规则**：

- `teamMode=predefined` → `spawn_member` 被 exclude，leader 不能再扩员。
- `teammateMode=plan_mode` → teammate 多 `write_plan`，leader 多 `approve_plan`。
- 所有成员（含 leader）额外注册 `file_io`，用于在团队工作空间读写文件（典型：写 `.team/reports/T*.md`）。

## 成员状态机

成员运行期在两套状态间迁移：

- **MemberStatus**：`UNSTARTED → READY → BUSY → SHUTDOWN_REQUESTED → SHUTDOWN`，异常路径经过 `RESTARTING` / `ERROR`。每次迁移通过 `Messager` 发布 `member_status_changed` 事件。
- **ExecutionStatus**：`IDLE → STARTING → RUNNING → COMPLETING → COMPLETED`，取消与失败分支：`CANCEL_REQUESTED` / `CANCELLING` / `CANCELLED` / `FAILED` / `TIMED_OUT`。

两个枚举自带 `canTransitionTo(...)` 合法性检查。排查成员异常时，先看 `MemberStatus` 是否在 `ERROR` / `RESTARTING`，再看 `ExecutionStatus` 是否 `FAILED` / `TIMED_OUT`。

## 事件驱动与协调机制

`TeamAgent` 的协作靠两条事件流汇入 `CoordinatorLoop` 队列，再由 `EventDispatcher.dispatch(event)` 统一分发。

**事件来源**：

| 来源 | 类型 | 触发方 |
| --- | --- | --- |
| JVM 内部 | `InnerEventMessage`（`USER_INPUT` / `POLL_MAILBOX` / `POLL_TASK` / `SHUTDOWN`） | `CoordinatorLoop` 周期轮询、`interact/deliverInput` 入队、`stop()` 发 SHUTDOWN |
| transport 层 | `EventMessage`，发布到 4 个主题：`team:<name>` / `team:task` / `team:message` / `team:broadcast` | 团队工具调用、`TeamMemberState` 迁移、`TeamTaskManager` 状态变更 |

`CoordinationManager.subscribeTransport()` 在 leader 首轮 `stream(...)` 或 teammate `invokeForSpawn(...)` 时订阅 4 个主题。所有 transport 事件入队前做 **echo suppression**（`localMember.equals(event.getSenderId())` 时跳过），避免成员收到自己发出的事件。

**关键事件路由**：

| 事件 | 处理 |
| --- | --- |
| `USER_INPUT` | 解析 `@mention` 路由或 `deliverInput(text)`；`@member_name xxx` 直接走 `UserInbox.direct(...)`，不进 leader ReAct |
| `POLL_MAILBOX` | 30 秒周期，把未读消息逐条投递给成员 |
| `POLL_TASK` | `checkStaleClaimedTasks`（60 秒未完成）+ `checkStalePendingTasks`（10 分钟未指派） |
| `team_cleaned` | 非 leader 调 `shutdownSelf()` |
| `member_results_delivery` | 仅当 `target_assignee == localMember` 时 `deliverInput(content)`，支撑多阶段流水线 |

**自动收尾**：`StreamController` 在 member 的 ReAct 轮次结束时调 `tryAutoCompleteMemberTasks`——如果该 member 本轮给 leader 发过 `send_message`，框架自动 claim + complete 它名下所有未完成任务，member 不必显式调 `complete_task`。

## 多阶段数据流

`investment-analysis-team` 这类多阶段流水线靠框架内建机制，不需要 leader 显式调度：

1. **创建带依赖的任务**：leader `create_task(..., dependencies=["T1","T2"])`，`TeamTaskManager.add(...)` 把带依赖任务初始设为 `blocked`，在 `TaskDependencyRecord` 表记录边。
2. **member 完成上游任务**：member 领取并完成 T1 后，`completeResult(...)` 标 `completed`，发布 `task_completed`。
3. **框架捕获上游输出**：`EventDispatcher.tryAutoCompleteMemberTasks` 检测到 member 给 leader 发过 `send_message`，把消息内容收进 `TeamResultCollector`（key = `teamName + memberName`）。
4. **自动解锁下游**：`tryDeliverToNextStage` 遍历所有 `blocked` 任务，当全部 `dependencies` 都 `completed` 时，从 `TeamResultCollector` 取出各依赖 assignee 的输出，组装成 `member_results_delivery` 事件，通过 `team:message` 主题发布给下游 assignee。
5. **下游 member 接收**：`EventDispatcher` 校验 `target_assignee == localMember` 后 `deliverInput(content)`，把上游结果注入自己的 ReAct 循环。
6. **`team_mode=predefined` 特殊处理**：当下游 assignee 含 `leader` 时，投递消息附带"你是 leader，必须自己用 file_io 写最终报告并 complete 该任务"的强约束提示，防止 leader 把终局任务转派。

## HITT：人类成员协作

当 `enableHitt=true` 时，可以加入 `role=HUMAN_AGENT` 的成员：

- `TeamRail.buildTeamHittSection(...)` 针对 leader、teammate、human_agent 三种视角生成不同协作规则。
- leader 不能用 plain text 向人类成员提问，必须走 `send_message`。
- human_agent 只有 `send_message` 一个工具，没有 `claim_task` / `update_task` / `spawn_member`。
- `exposeHumanAgentsToTeammates` 控制人类成员是否对 teammate 可见。
- 若存在 `HUMAN_AGENT` 成员，必须同时打开 `enableHitt`，否则 `TeamAgentSpec.validate()` 报错。

## Team Skill：团队技能发现

`TeamAgent.setupAgent()` 默认打开 `enableSkillDiscovery(true)` + `skillMode("all")`，扫描目录：

- 团队工作空间的 `skills/` 节点
- 当前工作目录（`System.getProperty("user.dir")`）
- `~/.openjiuwen/workspace/skills`

`HarnessFactory` 在 `hasConfiguredSkills(source) || source.isEnableSkillDiscovery()` 时自动追加 `SkillUseRail`，负责：

- 扫描 skill 目录，加载本地与远程 skill（基于 mtime 签名增量热重载）。
- 注册 `list_skill` 和 `skill_tool` 工具，让 leader/teammate 在 ReAct 循环里发现并调用 skill。
- 在 `beforeModelCall` 注入名为 `skills` 的 prompt section。

**两个演进类 rail**（`com.openjiuwen.harness.rails`）：

- `TeamSkillRail`：监听成员 `view_task`，所有任务完成时触发"团队演进分析"事件，沉淀协作经验。
- `TeamSkillCreateRail`：leader 调用 `spawn_member` 达阈值（默认 2 次）后，排发 follow-up prompt 引导 leader 用 `team-skill-creator` skill 把这次协作模式固化为新 team skill。

把 team skill 放到团队工作空间 `skills/` 或 `~/.openjiuwen/workspace/skills/` 下，团队下一次执行自动发现并加载。

## Team Skill 标准目录结构

一个完整的 team skill（如 `investment-analysis-team`）由 5 个部分组成：

| 文件 | 职责 | 何时被读取 |
| --- | --- | --- |
| `SKILL.md` | 团队元数据：name/version/kind: team-skill/角色总览 | leader 在 ReAct 循环里发现该 skill 后读取 |
| `roles/*.md` | 每个角色的身份、成功标准、Output Schema、`## Inline Persona for Teammate` 段 | leader 在 `spawn_member` 前读取，提取 inline persona 段粘进 dispatch prompt（**框架不会自动加载**，必须 leader 显式读取） |
| `workflow.md` | 完整执行剧本（mermaid + Step 0~7 协议） | 首次分发前读取，是 leader 的完整 playbook |
| `bind.md` | 资源约束、行为约束、失败处理 | 触发资源约束 / 失败处理 / 降级时读取；`TeamRail` 把 Resource Constraints 注入 leader prompt |
| `dependencies.yaml` | 外部 skill / tools 依赖声明 | **启动时**由 leader 的 pre-flight（workflow Step 0）读取，决定 go/no-go |

## 典型运行生命周期

1. 用 `LeaderTeammateAgentTeam.builder()` 配置 teamName、lifecycle、teammateMode、spawnMode、transport、storage、leader、预定义成员。
2. 调 `build()` → `TeamFactory.createAgentTeam(spec)` 构造 `TeamAgent`，内部装配 `ModelAllocator`、`TeamBackend`、`CoordinatorLoop`、`EventDispatcher`、`RecoveryManager`、`SpawnManager`、`StreamController`，并创建 leader 自己的 `DeepAgent`。
3. 调 `interact(...)` / `deliverInput(...)` / `dispatchTask(...)` / `stream(...)`，触发 `CoordinatorLoop` 唤醒，`EventDispatcher` 消费事件。
4. leader 通过团队工具与 teammate 协作；teammate 通过 `invokeForSpawn(...)` 进入自己的 ReAct 循环。
5. 任务完成后，`temporary` 团队由 leader 调 `shutdown_member` 再 `clean_team` 收尾；`persistent` 团队保持运行等待新指令。

## 使用方式

1. **场景定位**：先用"场景速查表"按当前任务跳到对应小节。
2. **复制模板**：快速开始小节的代码块可直接复制修改，注意改 `teamName`、`leaderPersona`、成员名、`modelName`。
3. **配置决策**：配置不确定时查"配置参数速查"和"配置选择"小节。
4. **排查问题**：先查"踩坑 FAQ"，再查"成员状态机"和"多阶段数据流"。
5. **扩展能力**：需要 team skill 看"Team Skill"小节；需要人类协作看"HITT"小节。
6. **不确定不要编造**：API 细节以源码为准，本 skill 不替代正式 API 文档。

## 踩坑 FAQ（高频问题速查）

新手最易踩的坑，按现象定位：

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| `IllegalStateException: Missing required key in apiconfig.json` | API 配置是占位值 | 填真实 `API_KEY` / `API_BASE` / `MODEL_NAME`，见"运行前置" |
| 找不到 `examples.agent_teams.AgentTeamE2eExample` 类 | `examples/` 不在 Maven 编译路径 | 把 `examples/` 挪到 `src/main/java/` 下，或 IDE 加 source root |
| 调了 `team.builder()` 后直接 `dispatchTask` 报错 | 只调了一个 `build()` | `LeaderTeammateAgentTeam.builder().build().build()`，两个 build 缺一不可：第一个生成 spec，第二个创建 TeamAgent |
| leader 拉起 member 后 member 没活干 | 只 `spawn_member` 没 `create_task` | spawn 后用 `create_task` 创建任务，member 才能 `claim_task` |
| `teamMode=predefined` 下调 `spawn_member` 失败 | 工具被 exclude | predefined 模式不允许扩员；要扩员改用 `hybrid` 或 `default` |
| member 完成任务后状态卡在 `claimed`，不转 `completed` | member 没给 leader 发 `send_message` | `tryAutoCompleteMemberTasks` 需要 member 给 leader 发过 `send_message` 才自动 complete；或让 member 显式调 `complete_task` |
| `@member_name` 消息进了 leader 的 ReAct 而不是直达 member | mention 路由写错成员名 | 检查成员名拼写；`@member_name xxx` 应直接走 `UserInbox.direct(...)`，进 leader ReAct 说明没匹配上 |
| `process` spawn 模式启动报错 | 没配 pyzmq 传输 | `spawnMode=process` 必须配 `transport=pyzmq`；本地调试用 `inprocess` |
| leader 不知道有 team skill 可用 | skill 放错目录 | 放到团队工作空间 `skills/` 或 `~/.openjiuwen/workspace/skills/`，见"运行前置" |
| member 没有人设 / 角色定义丢失 | `roles/*.md` 的 inline persona 段没被 leader 读取 | 框架不会自动加载 `roles/*.md`，leader 必须 `read_file` 后提取 `## Inline Persona for Teammate` 段粘进 dispatch prompt |
| `TeamAgentSpec.validate()` 报错：HUMAN_AGENT 成员未启用 HITT | 有 `role=HUMAN_AGENT` 成员但没开 `enableHitt` | `enableHitt(true)`，或去掉 HUMAN_AGENT 成员 |
| 任务依赖不解锁，下游 member 一直收不到 `member_results_delivery` | 上游任务没真完成，或上游 member 没发 `send_message` | 检查 `TaskRecord.status` 是否真到 `completed`；检查 `TeamResultCollector` 是否有上游输出（key = `teamName + memberName`） |
| `temporary` 团队任务完成后不自动销毁 | leader 没调 `clean_team` | temporary 团队需要 leader 调 `shutdown_member` 再 `clean_team` 收尾；`CoordinatorLoop` 在 `nudgeIdleAgent` 判定全部完成后会提示 leader |

## 子系统与子包速查

| 子包 | 关键类 | 职责 |
| --- | --- | --- |
| `agentteams` | `LeaderTeammateAgentTeam`、`TeamFactory`、`TeamConstants`、`I18n`、`TeamPaths` | 顶层入口、工厂、保留名、i18n、路径布局 |
| `agentteams.agent` | `TeamAgent`、`CoordinatorLoop`、`EventDispatcher`、`TeamRail`、`TeamMemberState`、`AgentConfigurator`、`ModelAllocator` | 协调器、唤醒循环、事件分发、prompt rail、模型分配 |
| `agentteams.messager` | `Messager`、`InProcessMessager`、`PyZmqMessager` | 传输层抽象与实现 |
| `agentteams.spawn` | `SpawnHandle`、`InProcessSpawnHandle`、`ProcessSpawnHandle` | spawn 句柄与 session 上下文 |
| `agentteams.worktree` | `WorktreeManager`、`WorktreeRail`、`WorktreeSession` | git worktree 隔离与生命周期 |
| `agentteams.teamworkspace` | `TeamWorkspaceManager`、`WorkspaceFileLock` | 团队共享工作空间、文件锁 |
| `agentteams.monitor` | `TeamMonitor`、`MonitorEvent`、`TeamInfo`、`MemberInfo`、`TaskInfo` | 团队事件监控与状态快照 |
| `agentteams.interaction` | `Router`、`MentionRoute`、`UserInbox`、`HumanAgentInbox` | mention 路由、收件箱 |
| `agentteams.tools` | `TeamBackend`、`TeamTools`、`TeamTaskManager`、`TeamResultCollector` | 团队工具实现 |
| `agentteams.tools.database` | `TeamDatabase`、`DatabaseConfig`、`TaskDependencyRecord` | 持久化抽象与多后端适配 |
| `agentteams.schema.blueprint` | `TeamAgentSpec` | 团队蓝图 |
| `agentteams.schema.team` | `TeamMemberSpec`、`TeamRole`、`TeamRuntimeContext`、`ModelPoolEntry` | 团队/成员/上下文/模型池 schema |
| `agentteams.schema.status` | `MemberStatus`、`ExecutionStatus` | 成员与执行状态枚举 |

## 暂未实现 / 使用边界

- 完整 controller 式声明式团队编排（仍以 leader 运行期 spawn 为主要路径）。
- 多种 `ModelAllocator` 策略的统一选择器 API（当前只内建 `round_robin` 与 `by_model_name`）。
- 健康检查与自动恢复的完整生命周期（`SpawnHandle` 已暴露 `startHealthCheck`，但默认未启用）。

**稳妥写法**：把团队装配建立在 `LeaderTeammateAgentTeam + TeamAgent + TeamBackend` 上；需要更细的协作控制再扩展 `agent` / `tools` / `monitor` 子包。

## 参考入口

- 完整文档：`documents/zh/2.开发指南/多智能体/AgentTeams.md`
- 示例代码：`examples/agent_teams/AgentTeamE2eExample.java` + `AgentTeamE2eExampleSupport.java`
- 示例 team skill：`examples/agent_teams/skills/investment-analysis-team/`
- API 文档：`documents/zh/API文档/com.openjiuwen.agentteams/` 下各子模块
- **最小可运行示例**：`references/MinimalRunnableExample.java`（自包含，含模型配置，复制即可跑）
