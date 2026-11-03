---
name: agent-team-guide
description: Agent Team quick build guide. Based on the leader-teammate team framework in the com.openjiuwen.agentteams package. Automatically applied when users build, assemble, or run an agent team, configure multi-agent collaboration, use LeaderTeammateAgentTeam, write team skill code, or call team tools such as spawn_member/create_task/send_message. Related keywords: agent team, multi-agent team, leader-teammate, LeaderTeammateAgentTeam, TeamAgent, team skill, spawn_member, team assembly, multi-agent collaboration, investment-analysis-team. Not applicable to: single-agent (ReAct/Workflow) issues, pure model configuration, discussions unrelated to the agentteams package.
---

# Agent Team Quick Build Guide

This skill guides users to quickly build leader-teammate multi-agent collaboration teams based on the `com.openjiuwen.agentteams` package. The framework main line: `LeaderTeammateAgentTeam.Builder` declares the team -> `build()` creates `TeamAgent` via `TeamFactory` -> `interact / deliverInput / broadcast / dispatchTask` triggers collaboration.

## Core Mental Model

- `LeaderTeammateAgentTeam` is the declarative entry point, `TeamAgent` is the runtime host.
- `TeamBackend` is the data and message hub, `Messager` is the transport layer, `TeamDatabase` is the storage layer.
- `CoordinatorLoop` does not make decisions; it only handles wake-up and periodic polling. The actual collaboration logic is in `EventDispatcher` + team tools.
- The leader collaborates with teammates through team tools (`send_message`/`update_task`/`claim_task`/`spawn_member`); teammates enter their own ReAct loop via `invokeForSpawn(...)`.

## Key Concepts Quick Reference

| Object | Purpose |
| --- | --- |
| `LeaderTeammateAgentTeam` | Top-level entry point and facade for the team, configured via `Builder` |
| `TeamAgent` | Team coordinator and runtime host |
| `TeamAgentSpec` | Team blueprint (name/members/modelPool/lifecycle/teammateMode/spawnMode/transport/storage) |
| `TeamMemberSpec` | Member declaration (name/role/description/modelName) |
| `TeamRole` | Four roles: `LEADER` / `MEMBER` / `HUMAN_AGENT` / `USER` |
| `TeamBackend` | Maintains members, messages, tasks; bridges `Messager` and `TeamDatabase` |
| `CoordinatorLoop` | Daemon thread, wake-up callbacks + mailbox/task periodic polling (default 30s) |
| `EventDispatcher` | Consumes `InnerEventMessage` and transport events, converts them to collaboration actions |
| `Messager` | Inter-process/in-process message bus (`InProcessMessager` / `PyZmqMessager`) |
| `TeamMonitor` | Team monitor, subscribes to team/task/message/broadcast events |

## Scenario Quick Reference Table

Jump directly to the corresponding section based on the task scenario:

| Scenario | Jump to Section |
| --- | --- |
| Assemble a minimal team from scratch | "Quick Start: Minimal Team" |
| Assemble with predefined members | "Quick Start: With Predefined Members" |
| Run the leader directly to process a task | "Run Mode: Run Leader Directly" |
| Chat-type continuous interaction | "Run Mode: Continuous Interaction with User" |
| Resume / switch session / destroy | "Run Mode: Resume and Destroy" |
| Choose build_mode vs plan_mode | "Config Choice: teammateMode" |
| Choose default / predefined / hybrid | "Config Choice: teamMode" |
| Add human member (HITT) | "HITT: Human Member Collaboration" |
| Add team skill | "Team Skill: Team Skill Discovery" |
| Multi-stage task pipeline | "Multi-stage Data Flow" |
| Check available team tools | "Team Tool Set" |
| Troubleshoot member status | "Member State Machine" |

## Quick Start: Minimal Team

Minimal example: default leader is auto-included, no predefined teammates, `teamMode=default` (leader dynamically spawns at runtime).

```java
import com.openjiuwen.agentteams.LeaderTeammateAgentTeam;

LeaderTeammateAgentTeam team = LeaderTeammateAgentTeam.builder()
        .teamName("investment_analysis")
        .description("Investment analysis collaboration team")
        .lifecycle(LeaderTeammateAgentTeam.LIFECYCLE_TEMPORARY)
        .teammateMode(LeaderTeammateAgentTeam.TEAMMATE_MODE_BUILD)
        .spawnMode(LeaderTeammateAgentTeam.SPAWN_MODE_INPROCESS)
        .storage(LeaderTeammateAgentTeam.STORAGE_SQLITE)
        .leaderMemberName("team_leader")
        .leaderDisplayName("Investment Lead")
        .leaderPersona("Senior investment analysis expert, skilled at decomposing complex problems and assigning suitable members")
        .language("cn")
        .build()
        .build(); // First build() assembles spec, second build() creates TeamAgent

Map<String, Object> result = team.dispatchTask("Analyze the latest financial report of 600519");
```

**Two build() calls are both required**: The first generates `TeamAgentSpec`, the second actually creates `TeamAgent` via `TeamFactory.createAgentTeam(...)`.

## Quick Start: With Predefined Members

Add `addPredefinedMember(...)` to declare fixed members. As long as a non-`HUMAN_AGENT` predefined member is passed, the `Builder` automatically resolves `teamMode` to `hybrid`.

```java
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;

LeaderTeammateAgentTeam team = LeaderTeammateAgentTeam.builder()
        .teamName("investment_analysis")
        .lifecycle(LeaderTeammateAgentTeam.LIFECYCLE_TEMPORARY)
        .addPredefinedMember(TeamMemberSpec.builder()
                .name("fundamental_analyst")
                .role(TeamRole.MEMBER)
                .description("Fundamental analyst")
                .modelName("fundamental_model")
                .build())
        .addPredefinedMember(TeamMemberSpec.builder()
                .name("technical_analyst")
                .role(TeamRole.MEMBER)
                .description("Technical analyst")
                .modelName("technical_model")
                .build())
        .build()
        .build();
```

## Prerequisites (Must Read for Beginners)

Before copying the quick start code above, confirm three things, otherwise it won't run:

1. **Maven compile path**: The `examples/` directory is not in the Maven compile path by default (`pom.xml` doesn't register it as a source root). Either move `examples/` under `src/main/java/`, or add it as a source root in your IDE, otherwise `examples.agent_teams.AgentTeamE2eExample` won't be found.
2. **API configuration**: Fill real values for `API_BASE` / `API_KEY` / `MODEL_PROVIDER` / `MODEL_NAME` in `src/main/resources/apiconfig.json`. `SharedExampleApiConfigLoader` reads this file from classpath by default. Placeholder values (like `your-api-key`) will throw `IllegalStateException: Missing required key in apiconfig.json` at startup. Override with `-Dopenjiuwen.example.config=<path>` or `OPENJIUWEN_API_CONFIG` environment variable.
3. **Team skill placement**: If using team skills (like `investment-analysis-team`), place them in a directory the framework will scan, otherwise the leader won't discover them:
   - Team workspace `skills/`: `~/.openjiuwen/.agent_teams/<team_name>/team-workspace/skills/`
   - Global: `~/.openjiuwen/workspace/skills/`
   - `skills/` subdirectory of the current working directory

Startup command (example):

```bash
mvn exec:java -Dexec.mainClass=examples.agent_teams.AgentTeamE2eExample
```

## Configuration Parameters Quick Reference

| Field | Values | Default | Description |
| --- | --- | --- | --- |
| `lifecycle` | `temporary` / `persistent` | `temporary` | Temporary team is destroyed after task completion; persistent team keeps running |
| `teammateMode` | `build_mode` / `plan_mode` | `build_mode` | See "teammateMode" below |
| `spawnMode` | `inprocess` / `process` | `inprocess` | In-process shares JVM; process requires pyzmq config |
| `transport` | `inprocess` / `pyzmq` | Derived from spawnMode | inprocess defaults to inprocess |
| `storage` | `sqlite` / `memory` / `postgresql` / `mysql` | `sqlite` | Team persistence backend |
| `teamMode` | `default` / `predefined` / `hybrid` | Derived from predefined members | See "teamMode" below |
| `modelPoolStrategy` | `round_robin` / `by_model_name` | `round_robin` | Model allocation strategy |

### Config Choice: teammateMode

Determines the execution path after a teammate claims a task:

- `build_mode`: Teammate executes autonomously and marks completion directly, no leader approval needed. **Default choice**.
- `plan_mode`: Teammate must first `write_plan` to submit a plan, wait for leader `approve_plan` approval before executing. Use when strict quality gate control is needed.

### Config Choice: teamMode

Determines the leader's workflow template and available team tools:

- `default`: Leader dynamically spawns teammates at runtime, tool set includes `spawn_member`. **Default for minimal team**.
- `predefined`: All members are determined at assembly time, `spawn_member` is excluded.
- `hybrid`: Has both predefined members and allows leader to dynamically expand. **Automatically becomes hybrid when predefined members are passed**.

### Configuration Details

- When no explicit leader is declared, `TeamAgentSpec.ensureLeader()` automatically adds a default leader named `team_leader`.
- When `spawnMode=inprocess`, members share the same JVM, convenient for local debugging; in `process` mode, members run as independent processes and must configure `pyzmq` transport.
- Reserved member names (`team_leader` / `human_agent` / `user`) cannot be arbitrarily used; `TeamAgentSpec.validate()` checks this.
- File layout is managed by `TeamPaths`: team directory `~/.openjiuwen/.agent_teams/<team_name>/`, shared memory under `team-workspace/team-memory/`.

## Run Mode: Run Leader Directly

After assembly, call `dispatchTask(...)` directly:

```java
Map<String, Object> result = team.dispatchTask("Summarize this week's investment views");
// result contains team_id, session_id, status, leader, route, target, delivered_content, message_id
```

When streaming output is needed:

```java
Iterator<Object> chunks = team.agent().stream(Map.of("query", query), sessionApi);
while (chunks.hasNext()) {
    Object chunk = chunks.next();
    // Process streaming fragment
}
```

## Run Mode: Continuous Interaction with User

Both `interact(...)` / `deliverInput(...)` put input into `CoordinatorLoop`, the difference is whether the agent runtime goes through steer:

```java
team.interact("Look at the valuation from another angle");      // Equivalent to deliverInput(message, true)
team.deliverInput("Supplement with the latest data", false);

// Leader proactive broadcast
String messageId = team.broadcast("Please check your responsible task status");
```

## Run Mode: Resume and Destroy

```java
// Persistent team: resume from existing snapshot
TeamAgent restored = TeamFactory.recoverAgentTeam(snapshot);

// Switch to new session: keep alive teammates, re-spawn
team.resumeForNewSession(newSessionId);

// Resume existing session: no re-spawn, just session binding
team.recoverForExistingSession(existingSessionId);

// Batch recover abnormal members
List<String> restarted = team.recoverTeam();

// Destroy
team.destroyTeam();          // Equivalent to destroyTeam(true)
team.destroyTeam(false);     // Don't force-close other members
```

Get snapshot via `team.snapshot()`, which internally contains spec, context, leader_inbox, messages, and model allocator state.

## Team Tool Set

`TeamTools.createTeamTools(role, backend, teammateMode, excludeTools, ...)` filters tools by role, `TeamAgent.registerTeamTools()` adds `team_name.member_name` suffix to each tool's card id to avoid conflicts.

| Category | Tool | Description |
| --- | --- | --- |
| Leader Exclusive | `build_team` / `clean_team` / `spawn_member` / `shutdown_member` / `approve_plan` / `approve_tool` / `create_task` / `update_task` / `list_members` | Only the leader can call these |
| Member Exclusive | `claim_task` / `enter_worktree` / `exit_worktree` | Only teammates can call these |
| Shared | `view_task` / `send_message` / `workspace_meta` | Both leader and teammate have these |
| Human agent | `send_message` (only this one) | Members with `role=human_agent` only have this tool |

**Dynamic Adjustment Rules**:

- `teamMode=predefined` -> `spawn_member` is excluded, leader cannot expand members.
- `teammateMode=plan_mode` -> teammate gets extra `write_plan`, leader gets extra `approve_plan`.
- All members (including leader) additionally register `file_io` for reading/writing files in the team workspace (typically writing `.team/reports/T*.md`).

## Member State Machine

Members transition between two sets of states at runtime:

- **MemberStatus**: `UNSTARTED -> READY -> BUSY -> SHUTDOWN_REQUESTED -> SHUTDOWN`, error path goes through `RESTARTING` / `ERROR`. Each transition publishes a `member_status_changed` event via `Messager`.
- **ExecutionStatus**: `IDLE -> STARTING -> RUNNING -> COMPLETING -> COMPLETED`, cancel and failure branches: `CANCEL_REQUESTED` / `CANCELLING` / `CANCELLED` / `FAILED` / `TIMED_OUT`.

Both enums have built-in `canTransitionTo(...)` legality checks. When troubleshooting member anomalies, first check `MemberStatus` for `ERROR` / `RESTARTING`, then check `ExecutionStatus` for `FAILED` / `TIMED_OUT`.

## Event-Driven and Coordination Mechanism

`TeamAgent` collaboration relies on two event streams merging into the `CoordinatorLoop` queue, then unified dispatch by `EventDispatcher.dispatch(event)`.

**Event Sources**:

| Source | Type | Trigger |
| --- | --- | --- |
| JVM Internal | `InnerEventMessage` (`USER_INPUT` / `POLL_MAILBOX` / `POLL_TASK` / `SHUTDOWN`) | `CoordinatorLoop` periodic polling, `interact/deliverInput` enqueuing, `stop()` sends SHUTDOWN |
| Transport Layer | `EventMessage`, published to 4 topics: `team:<name>` / `team:task` / `team:message` / `team:broadcast` | Team tool calls, `TeamMemberState` transitions, `TeamTaskManager` state changes |

`CoordinationManager.subscribeTransport()` subscribes to the 4 topics when the leader first `stream(...)` or teammate `invokeForSpawn(...)`. All transport events go through **echo suppression** before enqueuing (`localMember.equals(event.getSenderId())` skips), preventing members from receiving their own events.

**Key Event Routing**:

| Event | Handling |
| --- | --- |
| `USER_INPUT` | Parse `@mention` routing or `deliverInput(text)`; `@member_name xxx` goes directly through `UserInbox.direct(...)`, doesn't enter leader ReAct |
| `POLL_MAILBOX` | 30s period, delivers unread messages to members one by one |
| `POLL_TASK` | `checkStaleClaimedTasks` (60s without completion) + `checkStalePendingTasks` (10min unassigned) |
| `team_cleaned` | Non-leader calls `shutdownSelf()` |
| `member_results_delivery` | Only when `target_assignee == localMember` then `deliverInput(content)`, supporting multi-stage pipeline |

**Auto-completion**: `StreamController` calls `tryAutoCompleteMemberTasks` at the end of a member's ReAct round -- if the member sent `send_message` to the leader this round, the framework automatically claims + completes all unfinished tasks under that member. The member doesn't need to explicitly call `complete_task`.

## Multi-stage Data Flow

Multi-stage pipelines like `investment-analysis-team` rely on framework built-in mechanisms, no need for leader explicit scheduling:

1. **Create tasks with dependencies**: Leader `create_task(..., dependencies=["T1","T2"])`, `TeamTaskManager.add(...)` sets the dependent task as `blocked` initially, records edges in `TaskDependencyRecord` table.
2. **Member completes upstream task**: After member claims and completes T1, `completeResult(...)` marks it as `completed`, publishes `task_completed`.
3. **Framework captures upstream output**: `EventDispatcher.tryAutoCompleteMemberTasks` detects member sent `send_message` to leader, collects message content into `TeamResultCollector` (key = `teamName + memberName`).
4. **Auto-unblock downstream**: `tryDeliverToNextStage` iterates all `blocked` tasks, when all `dependencies` are `completed`, retrieves each dependency assignee's output from `TeamResultCollector`, assembles a `member_results_delivery` event, publishes it to the downstream assignee via `team:message` topic.
5. **Downstream member receives**: `EventDispatcher` verifies `target_assignee == localMember` then `deliverInput(content)`, injecting upstream results into its ReAct loop.
6. **`team_mode=predefined` special handling**: When downstream assignee includes `leader`, the delivered message is accompanied by a strong constraint prompt "You are the leader, must use file_io to write the final report and complete this task yourself", preventing the leader from re-delegating the final task.

## HITT: Human Member Collaboration

When `enableHitt=true`, members with `role=HUMAN_AGENT` can be added:

- `TeamRail.buildTeamHittSection(...)` generates different collaboration rules for leader, teammate, and human_agent perspectives.
- Leader cannot ask human members in plain text, must use `send_message`.
- Human agent only has the `send_message` tool, no `claim_task` / `update_task` / `spawn_member`.
- `exposeHumanAgentsToTeammates` controls whether human members are visible to teammates.
- If `HUMAN_AGENT` members exist, `enableHitt` must also be enabled, otherwise `TeamAgentSpec.validate()` throws an error.

## Team Skill: Team Skill Discovery

`TeamAgent.setupAgent()` defaults to `enableSkillDiscovery(true)` + `skillMode("all")`, scanning directories:

- Team workspace `skills/` node
- Current working directory (`System.getProperty("user.dir")`)
- `~/.openjiuwen/workspace/skills`

`HarnessFactory` automatically appends `SkillUseRail` when `hasConfiguredSkills(source) || source.isEnableSkillDiscovery()`, responsible for:

- Scanning skill directories, loading local and remote skills (incremental hot-reload based on mtime signatures).
- Registering `list_skill` and `skill_tool` tools, enabling leader/teammate to discover and invoke skills in the ReAct loop.
- Injecting a prompt section named `skills` in `beforeModelCall`.

**Two evolutionary rails** (`com.openjiuwen.harness.rails`):

- `TeamSkillRail`: Listens to member `view_task`, triggers a "team evolution analysis" event when all tasks are complete, consolidating collaboration experience.
- `TeamSkillCreateRail`: After leader calls `spawn_member` reaching threshold (default 2 times), schedules a follow-up prompt guiding the leader to use the `team-skill-creator` skill to codify this collaboration pattern as a new team skill.

Place team skills under the team workspace `skills/` or `~/.openjiuwen/workspace/skills/`, the team will automatically discover and load them on the next execution.

## Team Skill Standard Directory Structure

A complete team skill (like `investment-analysis-team`) consists of 5 parts:

| File | Responsibility | When Read |
| --- | --- | --- |
| `SKILL.md` | Team metadata: name/version/kind: team-skill/role overview | Read after leader discovers this skill in ReAct loop |
| `roles/*.md` | Each role's identity, success criteria, Output Schema, `## Inline Persona for Teammate` section | Read before leader `spawn_member`, extract inline persona section and paste into dispatch prompt (**framework doesn't auto-load**, leader must explicitly read) |
| `workflow.md` | Complete execution script (mermaid + Step 0~7 protocol) | Read before first dispatch, is the leader's complete playbook |
| `bind.md` | Resource constraints, behavioral constraints, failure handling | Read when triggering resource constraints / failure handling / degradation; `TeamRail` injects Resource Constraints into leader prompt |
| `dependencies.yaml` | External skill / tools dependency declaration | Read at **startup** by leader's pre-flight (workflow Step 0), determines go/no-go |

## Typical Runtime Lifecycle

1. Use `LeaderTeammateAgentTeam.builder()` to configure teamName, lifecycle, teammateMode, spawnMode, transport, storage, leader, predefined members.
2. Call `build()` -> `TeamFactory.createAgentTeam(spec)` constructs `TeamAgent`, internally assembling `ModelAllocator`, `TeamBackend`, `CoordinatorLoop`, `EventDispatcher`, `RecoveryManager`, `SpawnManager`, `StreamController`, and creates the leader's own `DeepAgent`.
3. Call `interact(...)` / `deliverInput(...)` / `dispatchTask(...)` / `stream(...)` to trigger `CoordinatorLoop` wake-up, `EventDispatcher` consumes events.
4. Leader collaborates with teammates through team tools; teammates enter their own ReAct loop via `invokeForSpawn(...)`.
5. After tasks complete, `temporary` team is wrapped up by leader calling `shutdown_member` then `clean_team`; `persistent` team keeps running waiting for new instructions.

## Usage

1. **Scenario positioning**: First use the "Scenario Quick Reference Table" to jump to the corresponding section based on current task.
2. **Copy Template**: Code blocks in the quick start sections can be directly copied and modified. Remember to change `teamName`, `leaderPersona`, member names, `modelName`.
3. **Config Decision**: When unsure about configuration, check "Configuration Parameters Quick Reference" and "Config Choice" sections.
4. **Troubleshoot**: First check "Pitfall FAQ", then "Member State Machine" and "Multi-stage Data Flow".
5. **Extend Capabilities**: Need team skill see "Team Skill" section; need human collaboration see "HITT" section.
6. **Don't Fabricate When Unsure**: API details are subject to source code; this skill does not replace official API documentation.

## Pitfall FAQ (High-frequency Issues Quick Reference)

Most common pitfalls for beginners, located by symptom:

| Symptom | Cause | Solution |
| --- | --- | --- |
| `IllegalStateException: Missing required key in apiconfig.json` | API configuration has placeholder values | Fill real `API_KEY` / `API_BASE` / `MODEL_NAME`, see "Prerequisites" |
| Can't find `examples.agent_teams.AgentTeamE2eExample` class | `examples/` is not in Maven compile path | Move `examples/` under `src/main/java/`, or add source root in IDE |
| Error calling `dispatchTask` after `team.builder()` | Only called one `build()` | `LeaderTeammateAgentTeam.builder().build().build()`, both build calls are required: first generates spec, second creates TeamAgent |
| Member has nothing to do after leader spawns it | Only `spawn_member` without `create_task` | After spawn, use `create_task` to create a task, member can then `claim_task` |
| `spawn_member` fails under `teamMode=predefined` | Tool is excluded | predefined mode doesn't allow member expansion; use `hybrid` or `default` to expand |
| Member task status stuck at `claimed` after completion, doesn't transition to `completed` | Member didn't send `send_message` to leader | `tryAutoCompleteMemberTasks` requires member to have sent `send_message` to leader for auto-complete; or have member explicitly call `complete_task` |
| `@member_name` message enters leader's ReAct instead of going directly to member | Mention routing has wrong member name | Check member name spelling; `@member_name xxx` should go directly through `UserInbox.direct(...)`, entering leader ReAct means no match |
| `process` spawn mode startup error | No pyzmq transport configured | `spawnMode=process` requires `transport=pyzmq`; use `inprocess` for local debugging |
| Leader doesn't know team skills are available | Skill placed in wrong directory | Place under team workspace `skills/` or `~/.openjiuwen/workspace/skills/`, see "Prerequisites" |
| Member has no persona / role definition lost | `roles/*.md` inline persona section not read by leader | Framework doesn't auto-load `roles/*.md`, leader must `read_file` then extract `## Inline Persona for Teammate` section and paste into dispatch prompt |
| `TeamAgentSpec.validate()` error: HUMAN_AGENT member without HITT enabled | Has `role=HUMAN_AGENT` member but `enableHitt` not enabled | `enableHitt(true)`, or remove HUMAN_AGENT member |
| Task dependencies don't unlock, downstream member never receives `member_results_delivery` | Upstream task not truly completed, or upstream member didn't send `send_message` | Check `TaskRecord.status` is truly `completed`; check `TeamResultCollector` has upstream output (key = `teamName + memberName`) |
| `temporary` team doesn't auto-destroy after task completion | Leader didn't call `clean_team` | Temporary team needs leader to call `shutdown_member` then `clean_team` to wrap up; `CoordinatorLoop` prompts leader when `nudgeIdleAgent` determines all complete |

## Subsystem and Sub-package Quick Reference

| Sub-package | Key Classes | Responsibility |
| --- | --- | --- |
| `agentteams` | `LeaderTeammateAgentTeam`, `TeamFactory`, `TeamConstants`, `I18n`, `TeamPaths` | Top-level entry, factory, reserved names, i18n, path layout |
| `agentteams.agent` | `TeamAgent`, `CoordinatorLoop`, `EventDispatcher`, `TeamRail`, `TeamMemberState`, `AgentConfigurator`, `ModelAllocator` | Coordinator, wake loop, event dispatch, prompt rail, model allocation |
| `agentteams.messager` | `Messager`, `InProcessMessager`, `PyZmqMessager` | Transport layer abstraction and implementations |
| `agentteams.spawn` | `SpawnHandle`, `InProcessSpawnHandle`, `ProcessSpawnHandle` | Spawn handle and session context |
| `agentteams.worktree` | `WorktreeManager`, `WorktreeRail`, `WorktreeSession` | Git worktree isolation and lifecycle |
| `agentteams.teamworkspace` | `TeamWorkspaceManager`, `WorkspaceFileLock` | Team shared workspace, file locks |
| `agentteams.monitor` | `TeamMonitor`, `MonitorEvent`, `TeamInfo`, `MemberInfo`, `TaskInfo` | Team event monitoring and state snapshots |
| `agentteams.interaction` | `Router`, `MentionRoute`, `UserInbox`, `HumanAgentInbox` | Mention routing, inboxes |
| `agentteams.tools` | `TeamBackend`, `TeamTools`, `TeamTaskManager`, `TeamResultCollector` | Team tool implementations |
| `agentteams.tools.database` | `TeamDatabase`, `DatabaseConfig`, `TaskDependencyRecord` | Persistence abstraction and multi-backend adaptation |
| `agentteams.schema.blueprint` | `TeamAgentSpec` | Team blueprint |
| `agentteams.schema.team` | `TeamMemberSpec`, `TeamRole`, `TeamRuntimeContext`, `ModelPoolEntry` | Team/member/context/model pool schema |
| `agentteams.schema.status` | `MemberStatus`, `ExecutionStatus` | Member and execution status enums |

## Not Yet Implemented / Usage Boundaries

- Complete controller-style declarative team orchestration (leader runtime spawn is still the primary path).
- Unified selector API for multiple `ModelAllocator` strategies (currently only built-in `round_robin` and `by_model_name`).
- Complete lifecycle for health check and auto-recovery (`SpawnHandle` exposes `startHealthCheck`, but it's not enabled by default).

**Safe approach**: Build team assembly on `LeaderTeammateAgentTeam + TeamAgent + TeamBackend`; extend `agent` / `tools` / `monitor` sub-packages when finer collaboration control is needed.

## Reference Entry Points

- Full documentation: `documents/zh/2.Development Guide/Multi-Agent/AgentTeams.md`
- Example code: `examples/agent_teams/AgentTeamE2eExample.java` + `AgentTeamE2eExampleSupport.java`
- Example team skill: `examples/agent_teams/skills/investment-analysis-team/`
- API documentation: `documents/zh/API Documentation/com.openjiuwen.agentteams/` sub-modules
- **Minimal runnable example**: `references/MinimalRunnableExample.java` (self-contained, includes model config, copy and run)
