# AgentTeams 团队装配与运行

这里重点说明 `com.openjiuwen.agentteams` 包：一套基于 `LeaderTeammateAgentTeam` 的 leader-teammate 团队框架。主线是用 `LeaderTeammateAgentTeam.Builder` 声明团队身份、生命周期、传输、存储和成员，调用 `build()` 由 `TeamFactory` 创建 `TeamAgent`，再通过 `interact / deliverInput / broadcast / dispatchTask` 触发协作。

## 功能定位

先看四个关键问题：

1. Java 里怎样声明一个 leader-teammate 团队，并指定其生命周期、执行模式、传输与存储后端。
2. 这个团队怎样被装配、spawn 并通过事件协调 leader、teammate、human_agent 三类成员。
3. 什么时候应该走 `build_mode`，什么时候应该走 `plan_mode`；什么时候用 `default` / `predefined` / `hybrid` 三种 `teamMode`。
4. 怎样对团队做 snapshot、恢复、销毁，以及人类成员（HITT）何时参与协作。

## 核心概念

| 对象 | 作用 | 你需要关心什么 |
| --- | --- | --- |
| `LeaderTeammateAgentTeam` | 团队顶层入口与门面 | 用 `Builder` 配置团队，`build()` 创建实例 |
| `TeamAgent` | 团队协调器与运行宿主 | 持有 `TeamBackend`、`CoordinatorLoop`、`EventDispatcher`、`StreamController` 等子系统 |
| `TeamAgentSpec` | 团队蓝图 | 声明 name、members、modelPool、lifecycle、teammateMode、spawnMode、transport、storage 等 |
| `TeamRuntimeContext` | 单成员运行上下文 | 承载 teamId、sessionId、memberName、role、metadata |
| `TeamMemberSpec` | 成员声明 | name、role（LEADER/MEMBER/HUMAN_AGENT/USER）、description、modelName 等 |
| `TeamFactory` | 团队工厂 | 根据 spec 构建 `TeamAgent`，分配 leader 模型，恢复 snapshot |
| `TeamBackend` | 团队后端 | 维护成员、消息、任务，并桥接 `Messager` 与 `TeamDatabase` |
| `CoordinatorLoop` | 协调循环 | 唤醒回调、mailbox/task 周期轮询 |
| `Messager` | 进程间/进程内消息总线 | `InProcessMessager`、`PyZmqMessager` |
| `TeamMonitor` | 团队监控 | 订阅 team/task/message/broadcast 事件 |

### 团队角色

`TeamRole` 固定为四种角色，含义和默认成员名见 `TeamConstants`：

- `LEADER`：团队领导者，默认成员名 `team_leader`。
- `MEMBER`：执行业务的 teammate，由 leader 通过 `spawn_member` 拉起，或预先声明。
- `HUMAN_AGENT`：人类协作者代理，默认成员名 `human_agent`，仅在 `enableHitt=true` 时允许存在。
- `USER`：用户伪成员，默认成员名 `user`，代表发起会话的用户。

`TeamAgentSpec.validate()` 会校验：保留名（`team_leader` / `human_agent` / `user`）不能被随意占用；若存在 `HUMAN_AGENT` 成员，必须同时打开 `enableHitt`。

### 成员状态

成员运行期会在这两套状态间迁移：

- `MemberStatus`：`UNSTARTED → READY → BUSY → SHUTDOWN_REQUESTED → SHUTDOWN`，异常路径会经过 `RESTARTING` / `ERROR`。`TeamMemberState` 每次迁移都会通过 `Messager` 发布 `member_status_changed` 事件。
- `ExecutionStatus`：细到 `IDLE → STARTING → RUNNING → COMPLETING → COMPLETED`，以及取消与失败分支（`CANCEL_REQUESTED` / `CANCELLING` / `CANCELLED` / `FAILED` / `TIMED_OUT`）。两个枚举都自带 `canTransitionTo(...)` 合法性检查。

### 团队运行生命周期

一次典型的 leader-teammate 执行：

1. 用 `LeaderTeammateAgentTeam.builder()` 配置 teamName、lifecycle、teammateMode、spawnMode、transport、storage、leader、预定义成员。
2. 调用 `build()` → `TeamFactory.createAgentTeam(spec)` 构造 `TeamAgent`，内部会装配 `ModelAllocator`、`TeamBackend`、`CoordinatorLoop`、`EventDispatcher`、`RecoveryManager`、`SpawnManager`、`StreamController`，并创建 leader 自己的 `DeepAgent`。
3. 调用 `interact(...)` / `deliverInput(...)` / `dispatchTask(...)` / `stream(...)`，触发 `CoordinatorLoop` 唤醒，`EventDispatcher` 消费事件。
4. leader 通过团队工具（`send_message`、`update_task`、`claim_task`、`spawn_member` 等）与 teammate 协作；teammate 通过 `invokeForSpawn(...)` 进入自己的 ReAct 循环。
5. 任务完成后，根据 lifecycle：`temporary` 团队由 leader 调用 `shutdown_member` 再 `clean_team` 收尾；`persistent` 团队保持运行等待新指令。

心智模型：

- `LeaderTeammateAgentTeam` 是声明式入口，`TeamAgent` 是运行时宿主。
- `TeamBackend` 是数据与消息中枢，`Messager` 是传输层，`TeamDatabase` 是存储层。
- `CoordinatorLoop` 不做决策，只负责唤醒和周期轮询；真正的协作逻辑在 `EventDispatcher` + 团队工具里。

### 事件驱动与协调机制

`TeamAgent` 的协作靠两条事件流汇入 `CoordinatorLoop` 队列，再由 `EventDispatcher.dispatch(event)` 统一分发。

**事件来源**

| 来源 | 类型 | 触发方 |
| --- | --- | --- |
| JVM 内部 | `InnerEventMessage`（`USER_INPUT` / `POLL_MAILBOX` / `POLL_TASK` / `SHUTDOWN`） | `CoordinatorLoop` 周期轮询、`interact/deliverInput` 入队、`stop()` 发 SHUTDOWN |
| transport 层 | `EventMessage`，发布到 4 个主题：`team:<name>` / `team:task` / `team:message` / `team:broadcast` | 团队工具调用、`TeamMemberState` 迁移、`TeamTaskManager` 状态变更 |

`CoordinationManager.subscribeTransport()` 在 leader 首轮 `stream(...)` 或 teammate `invokeForSpawn(...)` 时订阅这 4 个主题，并注册 direct message handler。所有 transport 事件入队前会做 **echo suppression**——`localMember.equals(event.getSenderId())` 时直接跳过，避免成员收到自己发出的事件。

**`EventDispatcher` 的事件路由**

| 事件类型 | 处理 | 说明 |
| --- | --- | --- |
| `InnerEventMessage.USER_INPUT` | 解析 `@mention` 路由或 `deliverInput(text)` | `@member_name xxx` 会直接走 `UserInbox.direct(...)`，不进 leader 的 ReAct |
| `InnerEventMessage.POLL_MAILBOX` | `processUnreadMessages(memberName)` | 30 秒周期，把未读消息逐条投递给成员 |
| `InnerEventMessage.POLL_TASK` | `checkStaleClaimedTasks` + `checkStalePendingTasks` | 见下方"过期任务催办" |
| `team_standby` | `host.pausePolls()` | leader 暂停轮询，teammate 不动 |
| `team_cleaned` | 非 leader 调 `shutdownSelf()` | leader 调 `clean_team` 后广播，teammate 收到即自关 |
| `message` / `broadcast` | leader 先 `ackUserBoundMessage`（把发给 `user` 的消息标已读），再 `processUnreadMessages` | 团队内消息流转 |
| `member_results_delivery` | 仅当 `target_assignee == localMember` 时 `deliverInput(content)` | 多阶段任务的上游结果投递，见"team skill 多阶段数据流" |
| `task_*`（created/updated/claimed/completed/cancelled/unblocked） | `handleTaskBoardEvent` → `nudgeIdleAgent` | 给 leader 推任务看板或给 teammate 推可领取任务 |
| `member_*`（spawned/restarted/status_changed/execution_changed/shutdown/canceled） | leader 走 `handleLeaderMemberLifecycleEvent`；teammate 走 `handleTeammateMemberLifecycleEvent` | `member_canceled` → teammate `cancelAgent()`；`member_shutdown` → teammate `shutdownSelf()` |
| `tool_approval_result` | `resumeInterrupt(InteractiveInput)` | leader 审批 teammate 的工具调用后回传 |

**过期任务催办**

`POLL_TASK` 会触发两类催办：

- `checkStaleClaimedTasks`：claimed 状态超过 `STALE_CLAIM_MILLIS`（60 秒）的任务。leader 会给 assignee 发 `send_message` 催促；teammate 自己的 stale 任务会被**自动 complete**（防止 leader 永远卡在等汇总）。
- `checkStalePendingTasks`：仅 leader 执行，pending 状态超过 `STALE_PENDING_MILLIS`（10 分钟）的任务，提示 leader 评估并指派。

**任务完成后的自动收尾**

`StreamController` 在 member 的 ReAct 轮次结束时调 `tryAutoCompleteMemberTasks`：如果该 member 本轮给 leader 发过 `send_message`，框架会自动 claim + complete 它名下所有未完成任务，并把消息内容收进 `TeamResultCollector` 供下游阶段使用——member 不必显式调 `complete_task`。

## 快速开始

最小示例聚焦团队装配，默认 leader 自带、无预定义 teammate：

```java
import com.openjiuwen.agentteams.LeaderTeammateAgentTeam;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;

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

// 触发一次任务分发
Map<String, Object> result = team.dispatchTask("分析 600519 的最新财报");
```

如果要在创建时就带上预定义成员（例如一个基本面分析师、一个技术面分析师），追加 `addPredefinedMember(...)`：

```java
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

只要传入了非 `HUMAN_AGENT` 的预定义成员，`Builder` 会自动把 `teamMode` 解析为 `hybrid`；否则保持 `default`（leader 在运行期动态 spawn）。

## 配置与运行方式

### 团队身份与运行参数

`TeamAgentSpec` 是团队的唯一蓝图，`LeaderTeammateAgentTeam.Builder` 只是它的语义化包装。两类参数关注点不同：

- 身份类：`name`、`description`、`members`、`modelPool`、`language`。
- 运行类：`lifecycle`、`teammateMode`、`spawnMode`、`transport`、`storage`、`connectionString`、`teamMode`、`isHumanAgentEnabled` / `enableHitt`、`exposeHumanAgentsToTeammates`。

可选项与默认值速查：

| 字段 | 取值 | 默认值 |
| --- | --- | --- |
| `lifecycle` | `temporary` / `persistent` | `temporary` |
| `teammateMode` | `build_mode` / `plan_mode` | `build_mode` |
| `spawnMode` | `inprocess` / `process` | `inprocess` |
| `transport` | `inprocess` / `pyzmq`（`spawnMode=inprocess` 时默认 `inprocess`） | null（按 spawnMode 推导） |
| `storage` | `sqlite` / `memory` / `postgresql` / `mysql` | `sqlite` |
| `teamMode` | `default` / `predefined` / `hybrid` | 由预定义成员推导 |
| `modelPoolStrategy` | `round_robin` / `by_model_name` | `round_robin` |

几个需要显式记住的细节：

- `LeaderTeammateAgentTeam.Builder` 的第一个 `build()` 只生成 `TeamAgentSpec`；必须再调用 `LeaderTeammateAgentTeam.build()` 才会真正通过 `TeamFactory.createAgentTeam(...)` 创建 `TeamAgent`。两次 `build()` 的语义不同，缺一不可。
- 如果没有显式声明 leader，`TeamAgentSpec.ensureLeader()` 会自动追加一个名为 `team_leader` 的默认 leader。
- `spawnMode=inprocess` 时 `transport` 默认为 `inprocess`，成员共享同一个 JVM；`process` 模式下成员以独立进程运行，需要配 `pyzmq` 传输。
- `TeamRail` 会在 leader 的 prompt 里注入团队角色、工作流程、生命周期、人设、团队信息、成员关系等 prompt 段；当 `teamMode=predefined` 时，会从团队工具里排除 `spawn_member`。
- 团队文件布局由 `TeamPaths` 统一管理：默认根 `~/.openjiuwen`，团队目录 `~/.openjiuwen/.agent_teams/<team_name>/`，团队共享内存目录在其下的 `team-workspace/team-memory/`。

### 方式 1：直接运行 leader

最直接的写法是在装配后直接 `dispatchTask(...)`：

```java
Map<String, Object> result = team.dispatchTask("汇总本周投资观点");
// result 含 team_id、session_id、status、leader、route、target、delivered_content、message_id 等
```

如果需要流式输出（典型场景：leader 自己 ReAct 一轮）：

```java
Iterator<Object> chunks = team.agent().stream(Map.of("query", query), sessionApi);
while (chunks.hasNext()) {
    Object chunk = chunks.next();
    // 处理流式片段
}
```

### 方式 2：与用户持续交互

面向 chat 类场景，调用 `interact(...)` / `deliverInput(...)`。两者都会把输入塞进 `CoordinatorLoop`，区别在于 agent 正在运行时是否走 steer：

```java
team.interact("换个角度看下估值");      // 等价于 deliverInput(message, true)
team.deliverInput("补充一下最新数据", false);
```

leader 也可以主动广播一条消息给全员：

```java
String messageId = team.broadcast("请大家检查自己负责的任务状态");
```

### 方式 3：恢复、切换会话与销毁

`TeamAgent` 暴露的恢复接口分三种语义：

```java
// 持久团队：用现有 snapshot 恢复整个 agent
TeamAgent restored = TeamFactory.recoverAgentTeam(snapshot);

// 切到新会话：保留存活 teammate，重新拉起它们
team.resumeForNewSession(newSessionId);

// 接续已有会话：不重新拉起，仅做 session 绑定
team.recoverForExistingSession(existingSessionId);

// 把所有异常成员批量拉回
List<String> restarted = team.recoverTeam();

// 销毁：先关闭成员，再清理团队
team.destroyTeam();          // 等价于 destroyTeam(true)
team.destroyTeam(false);     // 不强制关闭其他成员
```

snapshot 用 `team.snapshot()` 拿到，内部包含 spec、context、leader_inbox、messages 和 model allocator 状态。

## 协作入口与适用场景

### `LeaderTeammateAgentTeam`：声明式入口

当你要开箱即用一个完整的 leader-teammate 团队时，优先从这里开始：

- `Builder` 把所有可调参数集中在一处，避免直接操作 spec。
- `build()` 触发 `TeamFactory`，统一完成模型分配、上下文构建、leader 装配。
- 适合大多数本地装配、单模块调用场景。

### `TeamAgent`：运行宿主

`TeamAgent` 是框架内部真正承载运行时的对象。它的子系统职责：

- `TeamBackend`：管理成员、消息、任务，并持有共享 `TeamDatabase` 与 `Messager`。
- `CoordinatorLoop`：守护线程，负责唤醒回调与 mailbox/task 周期轮询（默认 30 秒）。
- `EventDispatcher`：消费 `InnerEventMessage` 和 transport 事件，转换为协作行为。
- `SpawnManager` + `RecoveryManager`：spawn/restart 成员、session 切换时回收存活 teammate。
- `StreamController`：管理 ReAct 流、steer、follow-up、interrupt 恢复、in-flight 轮次。
- `TeamRail`：以 prompt section 的形式把团队信息、角色策略、工作流程注入 leader/teammate 的 system prompt。

只有在需要扩展协作逻辑、替换策略或注入自定义 rail 时，才需要直接操作 `TeamAgent`。

### `build_mode` vs `plan_mode`

`teammateMode` 决定 teammate 领取任务后的执行路径：

- `build_mode`：teammate 领取任务后自主执行并直接标记完成，无需 leader 审批。
- `plan_mode`：teammate 必须先通过 `write_plan` 提交计划，等待 leader 用 `approve_plan` 审批后才能开始执行。

### `default` / `predefined` / `hybrid`

`teamMode` 决定 leader 的工作流程模板与可用的团队工具：

- `default`：leader 在运行期动态 spawn teammate，工具集包含 `spawn_member`。
- `predefined`：所有成员在装配时已经确定，`spawn_member` 工具会被排除。
- `hybrid`：既有预定义成员，也允许 leader 动态扩员。

### HITT：人类成员协作

当 `enableHitt=true` 时，可以加入 `role=HUMAN_AGENT` 的成员。`TeamRail.buildTeamHittSection(...)` 会针对 leader、teammate、human_agent 三种视角生成不同的协作规则（例如 leader 不能用 plain text 向人类成员提问，必须走 `send_message`；human_agent 没有 `claim_task`、`update_task` 等工具）。`exposeHumanAgentsToTeammates` 控制人类成员是否对 teammate 可见。

### Team Skill：团队技能发现与演进

`agentteams` 包默认让每个成员的 `DeepAgent` 都具备 skill 发现能力。`TeamAgent.setupAgent()` 在构建 `DeepAgentConfig` 时会显式打开 `enableSkillDiscovery(true)` 并配置 `skillMode("all")`，扫描目录默认包含：

- 团队工作空间的 `skills/` 节点
- 当前工作目录（`System.getProperty("user.dir")`）
- `~/.openjiuwen/workspace/skills`

`HarnessFactory` 在 `hasConfiguredSkills(source) || source.isEnableSkillDiscovery()` 成立时会自动追加 `SkillUseRail`（`com.openjiuwen.harness.rails.SkillUseRail`），它负责：

- 扫描上述 skill 目录，加载本地与远程 skill（支持热重载，基于 mtime 签名增量刷新）。
- 注册 `list_skill` 和 `skill_tool` 工具，让 leader/teammate 能在 ReAct 循环里发现并调用 skill。
- 在 `beforeModelCall` 注入名为 `skills` 的 prompt section，向模型声明可用 skill。

在团队场景下，还配套两个演进类 rail（位于 `com.openjiuwen.harness.rails`，通过 `EvolutionRail` 基类接入）：

- `TeamSkillRail`：监听成员的 `view_task` 工具调用，当检测到所有任务都已完成时，触发"团队演进分析"事件（`emitApprovalEvent`），用于沉淀本轮协作经验。
- `TeamSkillCreateRail`：在 leader 调用 `spawn_member` 达到阈值（默认 2 次）后，向 leader 排发一条 follow-up prompt，引导其先用 `ask_user` 与用户确认，再调用 `team-skill-creator` skill 把这次多成员协作模式固化为新的 team skill，保存到 `skillsDir`。

把 team skill 放到团队工作空间的 `skills/` 目录或 `~/.openjiuwen/workspace/skills/` 下，团队下一次执行就会自动发现并加载。

### team skill 的多阶段数据流

`investment-analysis-team` 这类 team skill 之所以能跑通"四分析师并行 → 两研究员辩论 → 投资组合决策"的多阶段流水线，靠的是框架内建的多阶段交付机制，而不是 leader 显式调度：

1. **创建带依赖的任务**：leader 用 `create_task(..., dependencies=["T1","T2"])` 创建任务，`TeamTaskManager.add(...)` 会把带依赖的任务初始状态设为 `blocked`，并在 `TaskDependencyRecord` 表里记录边。
2. **member 完成上游任务**：member 领取并完成 T1 后，`TeamTaskManager.completeResult(...)` 会把 T1 标 `completed`，并发布 `task_completed` 事件。
3. **框架捕获上游输出**：`EventDispatcher.tryAutoCompleteMemberTasks` 检测到 member 给 leader 发过 `send_message`，会把消息内容收进 `TeamResultCollector`（以 `teamName + memberName` 为 key）。
4. **自动解锁下游**：`tryDeliverToNextStage` 遍历所有 `blocked` 任务，当某任务的全部 `dependencies` 都 `completed` 时，从 `TeamResultCollector` 取出各依赖 assignee 的输出，组装成 `member_results_delivery` 事件，通过 `team:message` 主题发布给该任务的 assignee。
5. **下游 member 接收**：`EventDispatcher` 收到 `member_results_delivery`，校验 `target_assignee == localMember` 后 `deliverInput(content)`，把上游结果注入自己的 ReAct 循环。
6. **`team_mode=predefined` 的特殊处理**：当下游 assignee 含 `leader` 时，投递消息会附带"你是 leader，必须自己用 file_io 写最终报告并 complete 该任务"的强约束提示，防止 leader 把终局任务转派出去。

这条链路解释了为什么示例 1 里 T1 完成后 T2 会"自动"进入可执行状态，以及示例 2 里分析师的报告如何到达研究员手上——都不需要 leader 在 prompt 里手动转发。

### 团队工具集

`TeamTools.createTeamTools(role, backend, teammateMode, excludeTools, ...)` 按 role 过滤工具，`TeamAgent.registerTeamTools()` 会再为每个工具的 card id 加 `team_name.member_name` 后缀，避免全局 `ResourceMgr` 冲突。

| 类别 | 工具 | 说明 |
| --- | --- | --- |
| Leader 专属 | `build_team` / `clean_team` / `spawn_member` / `shutdown_member` / `approve_plan` / `approve_tool` / `create_task` / `update_task` / `list_members` | 只有 leader 角色能调用，teammate 工具列表里不出现 |
| Member 专属 | `claim_task` / `enter_worktree` / `exit_worktree` | 只有 teammate 角色能调用 |
| 共享 | `view_task` / `send_message` / `workspace_meta` | leader 和 teammate 都有 |
| Human agent | `send_message`（仅此一个） | `role=human_agent` 的成员只有这一个工具，没有 `claim_task` / `update_task` / `spawn_member` |

工具集的动态调整：

- `teamMode=predefined` 时，`TeamAgent.registerTeamTools()` 会把 `spawn_member` 加入 `excludeTools`，leader 也不能再 spawn 新成员（因为成员已预定义）。
- `teammateMode=plan_mode` 时，teammate 工具集会额外暴露 `write_plan`，leader 工具集会额外暴露 `approve_plan`，用于计划审批闭环。
- 所有成员（含 leader）都会额外注册 `file_io` 工具（由 `TeamAgent.setupAgent()` 注入），用于在团队工作空间内读写文件（典型用途：写 `.team/reports/T*.md`）。

## 子系统总览

| 子包 | 关键类 | 职责 |
| --- | --- | --- |
| `agentteams` | `LeaderTeammateAgentTeam`、`TeamFactory`（在 `factory`）、`TeamConstants`、`I18n`、`TeamPaths` | 顶层入口、工厂、保留名、运行期 i18n 字符串、文件路径布局 |
| `agentteams.agent` | `TeamAgent`、`CoordinatorLoop`、`EventDispatcher`、`CoordinationManager`、`RecoveryManager`、`SpawnManager`、`StreamController`、`TeamRail`、`TeamToolApprovalRail`、`TeamMemberState`、`FirstIterationGate`、`AgentConfigurator`、`SessionManager`、`ModelAllocator(s)`、`AgentTeamPolicy` | 团队协调器、唤醒循环、事件分发、spawn 与恢复、流控、prompt rail、模型分配策略、leader/teammate 策略模板 |
| `agentteams.messager` | `Messager`、`MessagerFactory`、`InProcessMessager`、`PyZmqMessager`、`MessagerHandler`、`MessagerTransportConfig`、`MessagerPeerConfig`、`SubscriptionHandle` | 进程内 / PyZMQ 传输层抽象与实现 |
| `agentteams.spawn` | `SpawnHandle`、`InProcessSpawnHandle`、`ProcessSpawnHandle`、`SpawnContext`、`SharedResources` | 进程内 / 进程级 spawn 句柄与可继承的 session 上下文 |
| `agentteams.worktree` | `WorktreeManager`、`WorktreeRail`、`WorktreeSession`、`WorktreeConfig`、`WorktreeBackends`、`RemoteWorktreeBackend`、`WorktreeRemoteHandler` 等 | git worktree 隔离与生命周期管理 |
| `agentteams.teamworkspace` | `TeamWorkspaceManager`、`TeamWorkspaceConfig`、`WorkspaceFileLock`、`WorkspaceLockRequest`、`WorkspaceLockResponse`、`WorkspaceMode`、`ConflictStrategy` | 团队共享工作空间、目录初始化、文件锁 |
| `agentteams.monitor` | `TeamMonitor`、`MonitorEvent`、`MonitorEventType`、`TeamInfo`、`MemberInfo`、`TaskInfo`、`MessageInfo` | 团队事件监控与状态快照 |
| `agentteams.interaction` | `Router`、`MentionRoute`、`UserInbox`、`HumanAgentInbox`、`UnknownHumanAgentError`、`HumanAgentNotEnabledError` | mention 路由解析、用户与人类成员收件箱 |
| `agentteams.tools` | `TeamBackend`、`TeamMember`、`TeamMessage`、`TeamMessageManager`、`TeamTask`、`TeamTaskManager`、`TeamTools`、`TeamResultCollector`、`TaskOpResult` | 团队工具实现（send_message、claim_task、update_task、spawn_member、shutdown_member、clean_team 等） |
| `agentteams.tools.database` | `TeamDatabase`、`DatabaseConfig`、`DatabaseType`、`TeamRecord`、`MemberRecord`、`MessageRecord`、`TaskRecord`、`TaskDependencyRecord`、`GraphMutationResult`、`RuntimeCleanupResult`、`MemoryDatabaseConfig` | 团队持久化抽象与多后端适配 |
| `agentteams.schema.blueprint` | `TeamAgentSpec` | 团队蓝图 |
| `agentteams.schema.team` | `TeamMemberSpec`、`TeamRole`、`TeamLifecycle`、`TeamRuntimeContext`、`TeamModelConfig`、`ModelPoolEntry`、`ModelPoolEntries` | 团队/成员/运行上下文/模型池 schema |
| `agentteams.schema.status` | `MemberStatus`、`ExecutionStatus` | 成员与执行状态枚举 |
| `agentteams.schema.events` | `EventMessage` | transport 层事件信封 |
| `agentteams.schema.deep_agent_spec` | `DeepAgentSpec`、`SubAgentSpec`、`RailSpec`、`BuiltinToolSpec`、`ProgressiveToolSpec`、`SysOperationSpec`、`TeamModelConfig`、`AudioModelSpec`、`VisionModelSpec` | deep agent 子代理、rail、工具、模型声明 |

## 暂未实现的能力

Java 当前 `agentteams` 包已经具备完整的 leader-teammate 装配、spawn、协调、监控和持久化能力，但下列更高阶的特性仍以窄片形式存在或尚未对齐：

- 完整的 controller 式声明式团队编排（仍以 leader 运行期 spawn 为主要路径）。
- 多种 `ModelAllocator` 策略的统一选择器 API（当前只内建 `round_robin` 与 `by_model_name`）。
- 健康检查与自动恢复的完整生命周期（`SpawnHandle` 已暴露 `startHealthCheck`，但默认未启用）。
- 团队级传输层与存储层的独立配置对象（当前通过 spec 的 `transport` / `storage` 字段统一配置）。

因此，稳妥的写法是把团队装配建立在 `LeaderTeammateAgentTeam + TeamAgent + TeamBackend` 上；若需要更细的协作控制，再扩展 `agent` / `tools` / `monitor` 子包。

## 示例入口

- [示例：AgentTeam E2E Example](../../../../examples/agent_teams/AgentTeamE2eExample.java)

> 运行前置：
>
> 1. `examples/` 目录当前不在 Maven 编译路径内（`pom.xml` 未把它注册为 source root），直接运行会找不到类。请先把整个 `examples/` 目录复制（或移动）到 `src/main/java/` 下，使 `examples.agent_teams.AgentTeamE2eExample` 与 `examples.utils.SharedExampleApiConfigLoader` 进入主源集，再通过 IDE 的 main 入口或 `mvn exec:java -Dexec.mainClass=examples.agent_teams.AgentTeamE2eExample` 启动。
> 2. 运行前需把 `src/main/resources/apiconfig.json` 里的字段填成真实值（`API_BASE`、`API_KEY`、`MODEL_PROVIDER`、`MODEL_NAME` 等）。`SharedExampleApiConfigLoader` 默认从 classpath 读取这份文件，若仍是占位值（`your-api-key` 之类），demo 会在启动时抛 `IllegalStateException: Missing required key in apiconfig.json`。也可通过 `-Dopenjiuwen.example.config=<path>` 或 `OPENJIUWEN_API_CONFIG` 环境变量指向自定义配置文件覆盖默认查找路径。
> 3. 示例自带的 team skill 副本位于 `examples/agent_teams/skills/`（如 `investment-analysis-team`）。运行前需把它挪到团队工作空间或全局 skill 扫描目录下，框架才会发现并加载。可选位置（见上文"Team Skill"小节）：
     >    - 团队工作空间的 `skills/` 节点：`~/.openjiuwen/.agent_teams/my_project_team_java/team-workspace/skills/`
>    - 全局：`~/.openjiuwen/workspace/skills/`

### 示例 team skill：investment-analysis-team

`examples/agent_teams/skills/investment-analysis-team/` 是一个 **Debate pattern (B+A+C)** 模式的多角色投资分析团队技能——并行分解（4 分析师并行）+ 对抗视角（乐观/悲观研究员直接点对点辩论）+ 专业化流水线（质量门控 + 风控决策）。

team skill 的标准目录结构由 5 个部分组成，每个文件各司其职：

| 文件 | 本质职责 |
| --- | --- |
| `SKILL.md` | **团队元数据**——团队叫什么、要干什么、成员有哪些角色 |
| `roles/` | **角色定义**——每个 Teammate 各自负责什么、输入/输出/工具装配 |
| `workflow.md` | **协作流程**——谁先谁后、依赖关系、通信与同步机制 |
| `bind.md` | **边界约束**——遇到问题怎么处理、关键决策审批边界 |
| `dependencies.yaml` | **外部工具依赖**——各角色装配的 Skill、缺失时自动从 Hub 检索 |

就该 skill 而言，各文件的详细内容与读取时机如下：

| 文件 | 作用 | 何时被读取 |
| --- | --- | --- |
| `SKILL.md` | 技能元数据与角色总览。front matter 声明 `name`、`version`、`kind: team-skill` 与 7 个角色（`fundamental-analyst` / `technical-analyst` / `digital-media-analyst` / `macro-analyst` / `optimistic-researcher` / `pessimistic-researcher` / `portfolio-risk-controller`）的 purpose / skills / tools；正文给出工作流程摘要、角色职责表、文件清单 | leader 在 ReAct 循环里发现该 skill 后读取，作为整体编排依据 |
| `workflow.md` | 完整执行剧本。Mermaid 图 + Step 0~7 的详细协议（pre-flight 依赖检查 → 任务分发 → 四分析师并行 → 研究员 Round 1 整合 → Round 2 直接辩论 → 完成辩论校验 → 投资组合与风控 → Final 报告生成），含每个 step 的 executor / input / output / quality gate，以及 Final Report 的原文引用模板 | 首次分发前读取，是 leader 的完整 playbook |
| `bind.md` | 资源约束与失败处理。`Resource Constraints`（`max_parallel_teammates=4`、`total_wall_clock_budget=30min`、`total_token_budget=100,000`、`per_role_token_limit=15,000`、`debate_rounds_limit=2`、各角色不对称 token / 时间上限）、`Behavioral Constraints`（leader 不生成内容、分析师彼此不可见、研究员 Round 1 不可见 / Round 2 直接可见、辩论固定 2 轮不提前终止）、`Failure Handling`（teammate 失败重试 2 次、输入过载降级、质量门控失败回流、辩论失败兜底、完全失败错误报告格式） | 触发资源约束 / 失败处理 / 降级模式时读取；`TeamRail` 会把 `Resource Constraints` 注入 leader prompt |
| `dependencies.yaml` | 外部依赖声明。`skills` 段列 `gs_stock_financial_query` / `gs_stock_market_query` / `gs_economy_query` / `content-strategy`（均 `required: false`，缺失时进入纯推理模式）；`tools` 段列 `python3`（`required: true`）/ `curl` / `jq` | **启动时**由 leader 的 pre-flight（workflow Step 0）读取，验证依赖并决定 go/no-go |
| `roles/*.md` | 每个角色的身份、成功标准、Output Schema、`## Inline Persona for Teammate` 段。文件名即角色 id（`fundamental-analyst.md` / `technical-analyst.md` / `digital-media-analyst.md` / `macro-analyst.md` / `optimistic-researcher.md` / `pessimistic-researcher.md` / `portfolio-risk-controller.md`） | leader 在 `spawn_member` 前读取对应角色文件，提取 `## Inline Persona for Teammate` 段直接粘进 dispatch prompt——**框架不会自动加载**，必须由 leader 显式读取 |

两条关键机制：

- **直接点对点辩论**：Round 2 中 `optimistic-researcher` 与 `pessimistic-researcher` 通过 `send_message` 直接交换观点，无需 leader 转达。`bind.md` 把这条列为"强制要求"，优先级 `直接点对点交换 > 共享黑板 > Leader 转发`。框架通过 `member_results_delivery` 事件把上游输出投递给下游 assignee（见上文"team skill 的多阶段数据流"）支撑这条链路。
- **辩论固定 2 轮**：`bind.md` 同时声明 `debate_rounds_limit=2` 与 `min_debate_rounds=2`，leader 在 Step 5 完成"是否满 2 轮"的校验，未满则强制继续，不允许提前终止。

### Demo 详细功能实现

入口类 `examples.agent_teams.AgentTeamE2eExample` 只是一个壳，真正的 demo 逻辑在 `examples.agent_teams.AgentTeamE2eExampleSupport`，对齐 Python 版 `agent_team_e2e.py`。整体流程如下。

#### 1. 装配团队蓝图

`buildTeamSpec()` 直接构造 `TeamAgentSpec`（不走 `LeaderTeammateAgentTeam.Builder`，等价于工厂的入口层）：

- 通过 `SharedExampleApiConfigLoader` 读取 API base / key / model name / provider（与 examples 其他 demo 共用 `apiconfig.json`）。
- 构建一个 `ModelPoolEntry`，写入 `provider`、`modelName`、`apiKey`、`apiBaseUrl`、`weight=1`，并在 `metadata` 里携带 `client`（`timeout=120`、`verify_ssl=false`、`rate_limit=10.0`）与 `request`（`temperature=0.2`、`top_p=0.9`）两套子配置——这些字段会在 `TeamFactory.createAgentTeam(...)` 里被 `ModelAllocator` 解析成 `ModelClientConfig` / `ModelRequestConfig`。
- 只声明一个 `role=LEADER` 的成员（`team_leader`），不预声明 teammate，让 leader 在运行期通过 `spawn_member` 动态拉起。
- spec 字段固定为：`lifecycle="temporary"`、`teammateMode="build_mode"`、`spawnMode="inprocess"`、`transport="inprocess"`、`storage="sqlite"`、`language="cn"`、`modelPoolStrategy="round_robin"`。

#### 2. 启动 Runner 并创建 leader

```java
TeamAgent leader = TeamFactory.createAgentTeam(spec);
Runner.start();
```

`TeamFactory.createAgentTeam(...)` 内部完成：分配 leader 模型 → 构建 `TeamRuntimeContext` → `new TeamAgent().attachModelAllocator(...).configure(spec, context)`。`configure(...)` 会触发 `bootstrapCoordinationHost()`（创建 `TeamBackend`、`Messager`、`CoordinatorLoop`、`EventDispatcher`、`RecoveryManager`、`SpawnManager`、`StreamController`）和 `setupAgent()`（注册团队工具 + `file_io`，创建 leader 自己的 `DeepAgent`，挂上 `TeamRail`）。

#### 3. 流式触发首轮

```java
Iterator<Object> stream = leader.stream(inputs, sessionId);
consumeStream(stream);
```

`leader.stream(...)` 做的事：

- 把 query 写入 `pendingUserQuery`，标记 `streaming_coordination=true`。
- 创建 `LinkedBlockingQueue`，绑定到 `StreamController`。
- `coordinationManager.subscribeTransport()` 订阅团队消息主题，再 `coordinatorLoop.start()` 启动守护线程。
- 入队一条 `USER_INPUT` 事件，返回 `CoordinationStreamIterator`。

`consumeStream(...)` 负责把流里的 chunk 按 `type` 分类渲染到终端：

| chunk type | 渲染 | 说明 |
| --- | --- | --- |
| `tool_call` | 青色 `● <tool_name>` + 参数 | leader/teammate 发起工具调用（如 `spawn_member`、`send_message`、`claim_task`） |
| `tool_result` | 灰色 `⎿ <preview>`（截断 200 字符） | 工具返回结果 |
| `message` | 灰色 `⚙ <content>` | 团队内消息事件 |
| `__interaction__` | 黄色 `[Interaction] <payload>` | 交互事件（如 steer、interrupt） |
| `llm_reasoning` | 灰色 `[Reasoning] <text>` | 模型推理片段 |
| `llm_output` | 绿色 `[Output] <text>` | 模型正文输出 |
| `answer` | 黄色 `[Answer] <text>` | 最终答案（若已有 llm_output 则去重） |

`StreamController.STREAM_END` 是流结束哨兵，触发 `finalizeStreamingRound()`（提交本轮记忆抽取、清掉 `streaming_coordination` 标记、保留 `CoordinatorLoop` 运行）。

#### 4. REPL 交互循环

首轮结束后进入 `while (true)` 循环读取 stdin：

- 输入 `exit` / `quit` 退出。
- 其他输入通过 `leader.interact(userInput)` 投递给 leader。
  - 如果 leader 的 agent 正在运行，`interact` 会走 `streamController.steer(message)` 路径（运行中插入新指令）。
  - 否则走 `coordinatorLoop.enqueue(USER_INPUT)` 路径（唤醒协调循环）。
- 随后调用 `tryConsumeNewOutput(...)` 再开一次流把新输出渲染出来。

#### 5. 收尾

`finally` 块依次 `leader.close()`（关闭 `TeamMemoryManager` 和 `CoordinatorLoop`）和 `Runner.stop()`。整个 demo 不显式调用 `clean_team` / `destroyTeam`——因为 lifecycle 是 `temporary`，leader 在任务全部完成后会按 prompt 指引自行调用 `shutdown_member` 与 `clean_team`，`CoordinatorLoop` 在 `nudgeIdleAgent` 判定全部完成后会停止。

#### 关键观察点

- 该 demo 只预声明 leader，所有 teammate 都由 leader 在 ReAct 循环中通过 `spawn_member` 动态创建——这是 `teamMode=default` 的典型路径。
- `spawnMode=inprocess` 让 teammate 在同一 JVM 内运行，共享 `InProcessMessager`，便于本地调试。
- 由于 `TeamAgent.setupAgent()` 默认打开 `enableSkillDiscovery(true)`，leader/teammate 都能发现并调用放在工作空间 `skills/` 或 `~/.openjiuwen/workspace/skills/` 下的 team skill。
- `spawn_member` 调用达到 2 次后，如果挂了 `TeamSkillCreateRail`，会向 leader 排发一条 follow-up prompt 引导其沉淀 team skill；`TeamSkillRail` 会在 `view_task` 显示全部 `completed` 时触发演进分析。

### Demo 输入示例

启动 demo 时，`initialQuery` 可以从命令行第二个参数传入（`args[1]`），缺省为 `"hello"`。下面给出两条推荐输入，可以分别验证 leader 的 spawn/依赖编排能力和 team skill 加载能力。

#### 示例 1：拉 2 个人报数，分为 2 个有依赖的任务

```
拉2个人报数，分为2个有依赖的任务
```

这条输入用于验证 leader 的动态 spawn + 任务依赖编排能力，对应 `teamMode=default` 路径。真实执行路径比"leader 手动指派"更自动化：

- leader 解析指令后，通过 `spawn_member` 工具拉起两个 teammate（例如 `member_a`、`member_b`）。
- 用 `create_task(..., dependencies=["T1"])` 创建 T2 时，`TeamTaskManager.add(...)` 把 T2 初始状态设为 `blocked`（不是 `pending`），并在 `TaskDependencyRecord` 表里记录 T2 → T1 的依赖边。T1 无依赖，初始状态 `pending`。
- `member_a` 通过 `claim_task` 领取 T1，状态变 `claimed`；完成后 `complete_task` 把 T1 标 `completed`，发布 `task_completed` 事件。
- `EventDispatcher` 收到 `task_completed` 后调 `tryDeliverToNextStage`：检测到 T2 的全部依赖（T1）已完成，从 `TeamResultCollector` 取出 T1 assignee 的输出，组装成 `member_results_delivery` 事件，通过 `team:message` 主题投递给 T2 的 assignee。
- T2 的 assignee 收到 `member_results_delivery` 后 `deliverInput(content)`，进入自己的 ReAct 循环处理 T2。
- `tryAutoCompleteMemberTasks` 在每个 member 轮次结束时检查：如果该 member 给 leader 发过 `send_message`，自动 claim + complete 它名下未完成任务，无需 member 显式调 `complete_task`。
- 全部任务 `completed` 后，leader 的 `nudgeIdleAgent` 检测到任务板清空，对 `temporary` 团队 stop `CoordinatorLoop` 并提示 leader 收尾。

预期在终端看到：两次 `● spawn_member` → `● create_task`（T1，pending）→ `● create_task`（T2，blocked，dependencies=[T1]）→ `● claim_task` + `● complete_task`（T1）→ `● task_unblocked`（T2 自动转 pending）→ T2 assignee 领取并完成 → leader 汇总。

这是验证 leader 能否正确处理任务依赖、`TaskRecord.status` 在 `pending → blocked → claimed → completed` 间迁移、以及 `member_results_delivery` 多阶段投递是否正常的最小用例。

#### 示例 2：读取 investment-analysis-team team skill，以 AAPL 苹果为分析例子执行任务

```
读取investment-analysis-team这个team skill，以AAPL（苹果）为分析对象，执行投资分析
```

这条输入用于验证 team skill 加载 + 多角色协作 + 直接辩论模式：

- leader 发现 `investment-analysis-team/` skill（放在工作空间 `skills/` 或 `~/.openjiuwen/workspace/skills/` 下即可被 `SkillUseRail` 加载），读取其 `SKILL.md`。
- 按 `workflow.md` 的 Step 0 执行 pre-flight 检查（读取 `dependencies.yaml`），报告 `gs_stock_financial_query`、`gs_stock_market_query`、`gs_economy_query`、`content-strategy` 等 skill 的可用性；缺失时进入纯推理模式（inline-persona-only）。
- leader 按 `roles/*.md` 的角色定义 spawn 多个 teammate：

  | 角色 | 职责 | 输出文件（`file_io(action="write")`） |
    | --- | --- | --- |
  | `fundamental-analyst` | AAPL 财务报表、盈利能力、竞争优势 | `.team/reports/T1_fundamental_analysis.md` |
  | `technical-analyst` | AAPL 价格走势、技术指标、关键价位 | `.team/reports/T2_technical_analysis.md` |
  | `digital-media-analyst` | AAPL 社交媒体舆情、热点、异常信号 | `.team/reports/T3_digital_media_analysis.md` |
  | `macro-analyst` | 宏观经济环境、货币政策、系统性风险 | `.team/reports/T4_macro_analysis.md` |
  | `optimistic-researcher` | Round 1 整合正面观点 / Round 2 直接辩论 | `.team/reports/T5_optimistic_round1.md` / `T7_debate_optimistic.md` |
  | `pessimistic-researcher` | Round 1 整合负面观点 / Round 2 直接辩论 | `.team/reports/T6_pessimistic_round1.md` / `T8_debate_pessimistic.md` |
  | `portfolio-risk-controller` | 基于辩论结论构建投资组合与风控策略 | `.team/reports/T10_portfolio_risk.md` |

- 四个分析师并行（彼此不可见），输出报告后通过 `send_message` 向 leader 发送完成摘要与文件路径，而不是发完整内容。
- `optimistic-researcher` 与 `pessimistic-researcher` 在 Round 1 并行（不可见）；Round 2 通过 `send_message` 直接点对点交换观点进行 2 轮反驳辩论（无需 leader 转达），最后各自输出辩论结论。
- leader 完成辩论校验（必须满 2 轮）后，交给 `portfolio-risk-controller` 串行生成最终投资决策（至少 3 个风控措施）。
- 最终 leader 把所有中间报告以**原文引用**形式整合到 `.team/reports/T11_final_report.md`。

`bind.md` 中对该 team skill 的资源约束（`max_parallel_teammates=4`、`total_wall_clock_budget=30min`、`debate_rounds_limit=2`、各角色 token 上限等）会被 `TeamRail` 注入到 leader 的 prompt 中，leader 需要在分发任务时遵守。

注意：该示例依赖 `gs_stock_financial_query`、`gs_stock_market_query`、`gs_economy_query`、`content-strategy` 等 skill 与 `python3`、`curl`、`jq` 等工具；任一缺失时 team skill 会按 `dependencies.yaml` 的 `required: false` 标记降级运行，并在最终报告中标注哪些分析师启用了降级模式。

## 参考入口

- [API 文档：LeaderTeammateAgentTeam](../API文档/com.openjiuwen.agentteams/LeaderTeammateAgentTeam.md)
- [API 文档：TeamFactory](../API文档/com.openjiuwen.agentteams/factory/TeamFactory.md)
- [API 文档：TeamAgent](../API文档/com.openjiuwen.agentteams/agent/TeamAgent.md)
- [API 文档：TeamAgentSpec](../API文档/com.openjiuwen.agentteams/schema/blueprint/TeamAgentSpec.md)
- [API 文档：TeamBackend](../API文档/com.openjiuwen.agentteams/tools/TeamBackend.md)
- [API 文档：Messager](../API文档/com.openjiuwen.agentteams/messager/Messager.md)
- [API 文档：SpawnHandle](../API文档/com.openjiuwen.agentteams/spawn/SpawnHandle.md)
- [API 文档：TeamMonitor](../API文档/com.openjiuwen.agentteams/monitor/TeamMonitor.md)
- [API 文档：WorktreeManager](../API文档/com.openjiuwen.agentteams/worktree/WorktreeManager.md)
- [API 文档：TeamWorkspaceManager](../API文档/com.openjiuwen.agentteams/teamworkspace/TeamWorkspaceManager.md)
- [API 文档：Router](../API文档/com.openjiuwen.agentteams/interaction/Router.md)
- [API 文档：TeamSkillRail](../API文档/com.openjiuwen.harness/rails/TeamSkillRail.md)
- [API 文档：TeamSkillCreateRail](../API文档/com.openjiuwen.harness/rails/TeamSkillCreateRail.md)
- [API 文档：SkillUseRail](../API文档/com.openjiuwen.harness/rails/SkillUseRail.md)

## 使用边界

- 这里聚焦 Java 当前 `com.openjiuwen.agentteams` 包里已经存在的 leader-teammate 团队能力。
- 推荐主线落在 `LeaderTeammateAgentTeam.Builder + TeamAgent + TeamBackend`。
- `agent`、`messager`、`spawn`、`worktree`、`teamworkspace`、`monitor`、`interaction`、`tools` 等子包提供具体子系统实现，可按需直接使用或扩展。
- 文档不替代正式的 API 文档；具体方法签名与字段语义以源码为准。
