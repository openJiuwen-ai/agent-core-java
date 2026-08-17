# DeepAgent 使用指南

DeepAgent 是 openjiuwen Java Harness 层的高阶智能体封装，在核心 `ReActAgent` 之上提供工作区、Rails、Skill、Tool、权限中断等能力。

完整可运行示例见：`D:\cursor\DeepAgentExample`（`mvn exec:java -Dexec.args=extended`）。

---

## 目录

- [核心概念](#核心概念)
- [快速开始](#快速开始)
- [执行模式](#执行模式)
- [高阶用法](#高阶用法)
- [Stream 并发与线程池](#stream-并发与线程池)
- [扩展能力示例](#扩展能力示例)
  - [1. Skill 注册与使用](#1-skill-注册与使用日期--星期python-脚本)
  - [2. Tool 注册与使用](#2-tool-注册与使用星期--日期java-tool)
  - [3. Rail 使用](#3-rail-使用llm-调用前注入用户偏好)
  - [4. 权限中断](#4-权限中断禁止用户发起的文件删除)
- [Demo 命令速查](#demo-命令速查)
- [多租户隔离](#多租户隔离)
- [注意事项](#注意事项)

---

## 核心概念

| 概念 | 说明 |
|------|------|
| **DeepAgent** | 外层编排；`run()` 委托内层 ReAct；`invoke()` 在未开 task loop 时仅返回元数据 |
| **DeepAgentConfig** | 模型、Prompt、tools、rails、skills、subagents、permissions 等 |
| **Rails** | 生命周期钩子（`beforeModelCall` / `beforeToolCall` 等） |
| **Skill** | `skills/{name}/SKILL.md` + 脚本/资源；由 `SkillUseRail` 扫描元数据并注入 Prompt，**不**自动执行脚本 |
| **Tool** | 派生 `com.openjiuwen.core.foundation.tool.Tool`（或 `LocalFunction` / 内置工具），经 `registerHarnessTool()` 注册 |
| **Workspace** | Agent 工作区根目录 |

`HarnessFactory` 默认注入：`SecurityRail`；按需注入 `SkillUseRail`、`TaskCompletionRail`、`SubagentRail` 等。

---

## 快速开始

```java
Runner.start();

DeepAgent agent = HarnessFactory.createDeepAgent(
        AgentCard.builder().name("coding_agent").build(),
        DeepAgentConfig.builder()
                .systemPrompt("你是编码助手。")
                .model(model)  // 见 apiconfig.json
                .workspacePath("./workspace")
                .build(),
        Workspace.builder().rootPath("./workspace").language("cn").build()
);
agent.ensureInitialized();
Map<String, Object> result = (Map<String, Object>) agent.run(Map.of(
        "query", "你好",
        "conversation_id", "demo_session"
));
Runner.stop();
```

LLM 配置参见 Demo 工程 `src/main/resources/apiconfig.json`（DashScope 兼容 OpenAI API）。

---

## 执行模式

| 方法 | 未开 task loop | 开启 task loop |
|------|----------------|----------------|
| `run()` | 内层 ReAct 单轮推理 | — |
| `invoke()` | 仅元数据包装 | 外层多轮任务循环 |
| `Runner.runAgentStreaming(inner, …)` | 流式 token | — |

---

## Stream 并发与线程池

DeepAgent 的 `stream()`（含开启 task loop 时的 `streamTaskLoop`）将每条 stream 会话提交到统一模块池 **`deep-agent-stream`**（`OpenJiuwenExecutors.newBoundedModulePool`），限制**同时进行中的 stream 会话数**，与 `invoke()` 路径分离。

| 项 | 说明 |
| --- | --- |
| 默认 max | `max(32, CPU 核数 × 8)`（I/O 型 workload，随 CPU 缩放） |
| 默认 queue | `128` |
| 覆盖方式 | `-Dopenjiuwen.executor.deep-agent-stream.max-size=N` 或环境变量 `OPENJIUWEN_EXECUTOR_DEEP_AGENT_STREAM_MAX_SIZE` |
| 读取顺序 | **JVM 系统属性优先**，环境变量兜底；池创建时读取，不支持热更新 |

饱和时模块池使用 `AbortPolicy`（`RejectedExecutionException`），不会无限排队。调大 stream 槽位后，仍需关注 LLM HTTP 并发（`openjiuwen.llm.http.max-requests-per-host` 等，**仅 `-D` 系统属性**）与厂商 API 配额。

完整命名规则、模块默认表与其它模块池说明见 **[执行器 Runner — 运行时线程池配置](../执行器Runner.md#运行时线程池配置)**。

---

## 高阶用法

- **Task Loop**：`enableTaskLoop(true)` + `TaskCompletionRail` + `follow-up` / `steer` / `requestAbort()`
- **子 Agent**：`subagents` + `createSubagent()` / `DeepAgentSubagents`
- **Plan 模式**：`AgentMode.PLAN` + `ensurePlanFile()`
- **harness_config.yaml**：`DeepAgentsFactory.createDeepAgent(path)`

详见 Demo 工程 `DeepAgentDemoMain` 各 demo 名称（`mvn exec:java -Dexec.args=list`）。

---

## 扩展能力示例

以下四节与 `DeepAgentExample` 中 `extended` 预设一一对应，可直接运行验证。

### 1. Skill 注册与使用（日期 → 星期，Python 脚本）

Skill 在 Java 侧是**元数据 + 磁盘目录**，负责让 Agent 知道「有哪些 skill、去哪里读 `SKILL.md`、如何调用脚本」。它**不是**执行器，也不会自动挂载 shell 工具。完整约定见 [Agent Skills](../高阶用法/Agent%20Skills.md)。

#### 目录结构

```text
workspace/skills/date_to_weekday/
├── SKILL.md
└── scripts/
    └── date_to_weekday.py   # 输入 YYYY-MM-DD，输出英文/中文星期
```

Demo 工程在运行时通过 `SkillResourceSupport.materializeDateToWeekdaySkill(workspace)` 将 classpath 资源复制到上述路径；源码位于：

- `DeepAgentExample/skills/date_to_weekday/`（便于编辑）
- `DeepAgentExample/src/main/resources/skills/date_to_weekday/`（打包进 jar）

`SKILL.md` 建议至少包含 front matter 中的 `description:`（`SkillManager` 会解析该字段）：

```yaml
---
name: date_to_weekday
description: Use this skill to convert ISO dates (YYYY-MM-DD) to weekday names via the bundled Python script.
---
```

正文应写清脚本路径与调用方式，例如：

```bash
python skills/date_to_weekday/scripts/date_to_weekday.py 2026-06-10 --lang en
python skills/date_to_weekday/scripts/date_to_weekday.py 2026-06-10 --lang cn
```

脚本 `date_to_weekday.py` 核心逻辑（支持 `--lang en|cn`）：

```python
from datetime import datetime

WEEKDAY_EN = ("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
WEEKDAY_CN = ("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

def date_to_weekday(iso_date: str, lang: str = "en") -> str:
    day = datetime.strptime(iso_date, "%Y-%m-%d")
    index = day.weekday()  # Monday=0
    return WEEKDAY_CN[index] if lang == "cn" else WEEKDAY_EN[index]
```

#### 注册 Skill

```java
// 1. 将 skill 目录部署到工作区（Demo 专用；生产环境可直接使用已有 skills/ 目录）
Path skillsRoot = SkillResourceSupport.materializeDateToWeekdaySkill(workspace);

DeepAgentConfig config = DeepAgentConfig.builder()
        .systemPrompt("""
                用户要求日期转星期时，先用 skill_tool 阅读 date_to_weekday 的 SKILL.md，
                再用 bash 工具执行其中的 Python 脚本，不要心算。
                """)
        .skillDirectories(List.of(skillsRoot.toString()))  // 指向 skills 父目录
        .skillMode("all")          // 全量注入；或 "auto_list" 让模型先 list_skill
        .language("cn")
        .build();

DeepAgent agent = HarnessFactory.createDeepAgent(card, config, workspace);
// HarnessFactory 会自动挂载 SkillUseRail（若尚未显式提供）
agent.registerHarnessTool(DemoToolFactory.bashTool(workspace)); // 显式注册 shell，用于执行 python 脚本
agent.ensureInitialized();
```

`SkillUseRail` 会：

- 用 `SkillManager` 扫描 `skillDirectories` 下含 `SKILL.md` 的子目录；
- 在 `beforeModelCall` 时将 skill 说明写入 System Prompt；
- 注册 `list_skill` / `skill_tool`（供模型读取 `SKILL.md`）。

**不会**自动执行 Python 脚本——需像上面一样显式注册 `bash`（或 SysOperation 的 `executeCmd`）。

#### 调用

```java
agent.run(Map.of(
        "query", "请使用 date_to_weekday 技能，计算 2026-06-10 是星期几。",
        "conversation_id", sessionId
));
// 期望：skill_tool 读取 SKILL.md → bash 执行 python skills/.../date_to_weekday.py 2026-06-10
// 期望输出含 Wednesday 或 星期三
```

**Demo**：`mvn exec:java -Dexec.args=skill`（源码 `SkillUsageDemo.java`）

---

### 2. Tool 注册与使用（星期 → 日期，Java Tool）

自定义工具应**派生** `com.openjiuwen.core.foundation.tool.Tool` 抽象类，实现 `invoke()` 与 `stream()`（与 `AskUserTool` 相同模式），再通过 `registerHarnessTool()` 注册。

#### 实现示例

```java
public final class WeekdayToDateTool extends Tool {

    public WeekdayToDateTool() {
        super(ToolCard.builder()
                .id("weekday_to_date")
                .name("weekday_to_date")
                .description("将星期名称转换为 ISO 日期（YYYY-MM-DD）。")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "weekday", Map.of("type", "string"),
                                "reference_date", Map.of("type", "string")
                        ),
                        "required", List.of("weekday")
                ))
                .build());
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String weekday = String.valueOf(inputs.get("weekday"));
        String reference = inputs.containsKey("reference_date")
                ? String.valueOf(inputs.get("reference_date")) : null;
        // weekday: "Wednesday" / "星期三"
        // reference_date: "2026-06-10"（可选，默认今天）
        // 从 reference_date 当天起向后查找第一个匹配星期（含当天）
        // 例：2026-06-10 是周三 → matched_date = "2026-06-10"
        return convert(weekday, reference).toPayloadMap();  // { success, data: { matched_date, ... } }
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return Collections.emptyIterator();
    }
}
```

完整实现见 Demo 工程：`com.example.deepagent.tools.WeekdayToDateTool`。

#### 注册与调用

```java
WeekdayToDateTool weekdayTool = new WeekdayToDateTool();

DeepAgent agent = HarnessFactory.createDeepAgent(card, DeepAgentConfig.builder()
        .systemPrompt("涉及星期与日期换算时，必须调用 weekday_to_date 工具，不要自行推算。")
        .maxIterations(5)
        .build(), workspace);

agent.registerHarnessTool(weekdayTool);   // 或 DemoToolFactory.weekdayToDateTool()
agent.ensureInitialized();

agent.run(Map.of(
        "query", "参考日期 2026-06-10，用 weekday_to_date 查询 Wednesday 的日期。",
        "conversation_id", sessionId
));
// 期望 matched_date = 2026-06-10
```

要点：

- 自定义 Tool **继承 `Tool`**，在构造函数中传入 `ToolCard`；
- `ToolCard` 定义 `id`、`name`、`description`、`inputParams`（JSON Schema）；
- `registerHarnessTool()` 同时写入 `Runner.resourceMgr()` 与内层 `AbilityManager`；
- 简单函数式工具仍可用 `LocalFunction`（它本身也 extends `Tool`）；
- 卸载：`unregisterHarnessTool(tool)`。

**Demo**：`mvn exec:java -Dexec.args=custom-tool`（源码 `CustomToolDemo.java`）

---

### 3. Rail 使用（LLM 调用前注入用户偏好）

自定义 Rail 在 **每次模型调用前** 向 context 追加用户偏好 KV：

```java
public final class UserPreferenceRail extends AgentRail {

    private final Map<String, String> preferences;

    public UserPreferenceRail(Map<String, String> preferences) {
        this.preferences = preferences;
        setPriority(20);  // 数字越小越先执行
    }

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof ModelCallInputs inputs)) {
            return;
        }
        String block = preferences.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));
        String prompt = "## 用户偏好\n" + block + "\n请在回答中尊重以上偏好。";

        List<Object> messages = new ArrayList<>(inputs.getMessages());
        messages.add(0, new SystemMessage(prompt));
        inputs.setMessages(messages);
    }
}
```

挂载方式（写入 `DeepAgentConfig.rails`，`ensureInitialized()` 时注册到内层 ReActAgent）：

```java
DeepAgentConfig.builder()
        .rails(List.of(new UserPreferenceRail(Map.of(
                "图书类型", "科幻小说",
                "语言", "中文"
        ))))
        .systemPrompt("你是图书推荐助手。")
        .build();
```

调用示例：

```java
agent.run(Map.of("query", "推荐一本书，只给书名和一句话理由。", "conversation_id", sessionId));
// 期望回答偏向科幻题材
```

Rail 常见钩子：

| 钩子 | 典型用途 |
|------|----------|
| `beforeModelCall` | 注入 Prompt / 偏好 / 安全段 |
| `afterModelCall` | 记录 token、审计 |
| `beforeToolCall` | 权限检查、参数改写、拦截 |
| `afterToolCall` | 结果清洗 |

**Demo**：`mvn exec:java -Dexec.args=user-preference-rail`

---

### 4. 权限中断（禁止用户发起的文件删除）

两层机制可组合使用：

#### 4.1 策略级：`PermissionInterruptRail`

通过 `DeepAgentConfig.permissions` 配置工具级 allow / ask / deny：

```java
Map<String, Object> permissions = Map.of(
        "enabled", true,
        "tools", Map.of(
                "read_file", "ask",    // 读文件需用户确认
                "write_file", "deny",  // 写文件直接拒绝
                "bash", "allow"
        ),
        "defaults", Map.of("*", "allow")
);

DeepAgent agent = HarnessFactory.createDeepAgent(
        card, config, workspace, permissions, permissionHost);
```

`deny` 时 Rail 在 `beforeToolCall` 设置 `_skip_tool`，工具不执行并返回 synthetic 结果。

#### 4.2 指令级：`DeleteCommandGuardRail`（推荐用于「删除文件」场景）

针对用户明确要求删除文件、模型发起 `bash rm …` 的场景，在 **工具调用前** 匹配删除关键字并拒绝：

```java
public final class DeleteCommandGuardRail extends AgentRail {

    private static final Pattern DELETE_PATTERN = Pattern.compile(
            "(\\brm\\b|\\bdel\\b|remove-item|删除|移除文件)", Pattern.CASE_INSENSITIVE);

    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        ToolCallInputs inputs = (ToolCallInputs) ctx.getInputs();
        if (!"bash".equalsIgnoreCase(inputs.getToolName())) {
            return;
        }
        String command = extractCommand(inputs.getToolArgs());
        if (DELETE_PATTERN.matcher(command).find()) {
            String message = "策略拒绝：禁止执行用户发起的文件删除指令。";
            ctx.getExtra().put("_skip_tool", Boolean.TRUE);
            inputs.setToolResult(message);
            inputs.setToolMsg(ToolMessage.builder().content(message).build());
        }
    }
}
```

完整示例：

```java
// 1. 预置受保护文件
Files.writeString(workspace.resolve("notes/keep-me.txt"), "do-not-delete");

// 2. 挂载 Rail + bash 工具
DeepAgentConfig config = DeepAgentConfig.builder()
        .rails(List.of(new DeleteCommandGuardRail()))
        .permissions(Map.of("enabled", true, "tools", Map.of("bash", "allow")))
        .systemPrompt("用户要求删除文件时，使用 bash 执行删除。")
        .build();

DeepAgent agent = HarnessFactory.createDeepAgent(card, config, workspace);
agent.registerHarnessTool(DemoToolFactory.bashTool(workspace));
agent.ensureInitialized();

// 3. 用户发起删除
agent.run(Map.of(
        "query", "请删除 notes/keep-me.txt",
        "conversation_id", sessionId
));

// 4. 验证：文件仍存在；模型回复说明被拒绝
assert Files.exists(workspace.resolve("notes/keep-me.txt"));
```

| 层级 | 粒度 | 适用 |
|------|------|------|
| `PermissionInterruptRail` | 工具名 | 全局禁止 `write_file` |
| `DeleteCommandGuardRail` | 工具参数 / 命令内容 | 禁止 `rm`、`del`、删除类中文指令 |

**Demo**：`mvn exec:java -Dexec.args=permission-delete`

---

## Demo 命令速查

```powershell
cd D:\cursor\DeepAgentExample

# 四个扩展能力一次验证
mvn -q exec:java "-Dexec.args=extended"

# 单项
mvn -q exec:java "-Dexec.args=skill"
mvn -q exec:java "-Dexec.args=custom-tool"
mvn -q exec:java "-Dexec.args=user-preference-rail"
mvn -q exec:java "-Dexec.args=permission-delete"

# 全部 demo 列表
mvn -q exec:java "-Dexec.args=list"
```

---

## 多租户隔离

DeepAgent 自 `0.1.7` 起支持多租户数据隔离。在 `DeepAgentConfig` 上设置 `enableTenantIsolation(true)` 即进入**严格模式**：每次调用必须携带有效 `TenantContext`，否则 `invoke` / `stream` 在绑定工作区之前直接抛 `IllegalStateException`（`Tenant isolation is enabled but no tenantId was provided`），不会静默回退到默认工作区。

传入 tenantId 的两种 DeepAgent 入口：

```java
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.session.AgentSessionApi;

TenantContext tenantCtx = TenantContext.builder().tenantId("dept-01").build();

// 快捷入口：直接 bind/unbind
Map<String, Object> result = agent.invoke(inputs, tenantCtx);

// Session 内嵌（推荐）：session 携带 tenantContext，invoke(inputs, session) 自动提取
AgentSessionApi session = AgentSessionApi.create(sessionId, null, agent.getCard())
        .withTenantContext(tenantCtx);
agent.invoke(inputs, session);
```

- `enableTenantIsolation=false`（默认）时行为与历史完全一致，不传 tenantId 也不会报错。
- `tenantId` 必须匹配 `[a-zA-Z0-9_-]+`，否则 `TenantContextHolder.setCurrentTenant()` 抛 `IllegalArgumentException`。
- DeepAgent 实现 `AutoCloseable`，`close()` 会停止 `TmpFileCleaner`，适合 try-with-resources 管理生命周期。

完整的隔离资源清单、目录结构、KV 前缀、安全防护与清理接口，见 [多租户数据隔离](../多租户数据隔离.md)。

---

## 注意事项

1. **`invoke()` vs `run()`**：单轮推理请用 `run()`；仅开 task loop 时 `invoke()` 才驱动多轮循环。
2. **Skill 目录**：`skillDirectories` 指向包含各 skill 子目录的**父路径**（如 `workspace/skills`）；skill 名称默认取**目录名**；`SkillManager` 当前主要解析 front matter 中的 `description:`。
3. **Skill 与执行分离**：Skill 只提供元数据与 `SKILL.md`；执行脚本需显式注册 `bash` / `executeCmd` 等工具（Demo 用 `DemoToolFactory.bashTool`）。无需也不应编写 Java `ScriptRunner`。
4. **自定义 Tool**：派生 `Tool` 并实现 `invoke()` / `stream()`；`LocalFunction` 适用于简单函数包装。
5. **权限配置**：`permissions.enabled=true` 时 `ensureInitialized()` 自动挂载 `PermissionInterruptRail`；可与自定义 Guard Rail 叠加。
6. **LLM 配置**：编辑 `apiconfig.json`，或通过环境变量 `API_KEY`、`API_BASE` 等覆盖。
7. **Stream 并发**：高并发 stream 场景见 [Stream 并发与线程池](#stream-并发与线程池)；与 `invoke` 默认不走同一线程池，勿仅调 LLM 配额而忽略 `deep-agent-stream`。
8. **Todo 存储配置**：默认使用本地文件（`todoStorageType="file"`）；生产环境可配置 `todoStorageType="kv"` + `kvStoreConfig` 切换到 Redis 存储，Todo 与 Checkpointer 共享同一套 KV 连接配置。详见 [多租户数据隔离](../多租户数据隔离.md) 的「Todo 存储可替换」章节。

---

## 相关源码

| 模块 | 路径 |
|------|------|
| DeepAgent | `com.openjiuwen.harness.deep_agent.DeepAgent` |
| Tool 基类 | `com.openjiuwen.core.foundation.tool.Tool` |
| SkillUseRail | `com.openjiuwen.harness.rails.SkillUseRail` |
| Agent Skills 文档 | `documents/zh/2.开发指南/高阶用法/Agent Skills.md` |
| PermissionFactory | `com.openjiuwen.harness.security.PermissionFactory` |
| Demo · WeekdayToDateTool | `DeepAgentExample/.../tools/WeekdayToDateTool.java` |
| Demo · Skill 资源 | `DeepAgentExample/skills/date_to_weekday/` |
| Demo 示例入口 | `DeepAgentExample/src/main/java/com/example/deepagent/` |
