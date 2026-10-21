# DeepAgent 使用指南

DeepAgent 是 openjiuwen Java Harness 层的高阶智能体封装，在核心 `ReActAgent` 之上提供工作区、Rails、Skill、Tool、权限中断等能力。

完整可运行示例见：`D:\cursor\DeepAgentExample`（`mvn exec:java -Dexec.args=extended`）。

---

## 目录

- [核心概念](#核心概念)
- [快速开始](#快速开始)
- [执行模式](#执行模式)
- [高阶用法](#高阶用法)
- [扩展能力示例](#扩展能力示例)
  - [1. Skill 注册与使用](#1-skill-注册与使用日期--星期python-脚本)
  - [2. Tool 注册与使用](#2-tool-注册与使用星期--日期java-tool)
  - [3. Rail 使用](#3-rail-使用llm-调用前注入用户偏好)
  - [4. 权限中断](#4-权限中断禁止用户发起的文件删除)
  - [5. 运行预算](#5-运行预算run-budget)
  - [6. 模型调用超时分层](#6-模型调用超时分层连接--响应头等待--流式空闲)
  - [7. 多租户隔离](#7-多租户隔离)
- [Demo 命令速查](#demo-命令速查)
- [多租户隔离](#多租户隔离)
- [Stream 并发与线程池](#stream-并发与线程池)
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

---

## 执行模式

| 方法 | 未开 task loop | 开启 task loop |
|------|----------------|----------------|
| `run()` | 内层 ReAct 单轮推理 | — |
| `invoke()` | 仅元数据包装 | 外层多轮任务循环 |
| `Runner.runAgentStreaming(inner, …)` | 流式 token | — |

---

## 高阶用法

- **Task Loop**：`enableTaskLoop(true)` + `TaskCompletionRail` + `follow-up` / `steer` / `requestAbort()`
- **子 Agent**：`subagents` + `createSubagent()` / `DeepAgentSubagents`
- **Plan 模式**：`AgentMode.PLAN` + `ensurePlanFile()`
- **harness_config.yaml**：`DeepAgentsFactory.createDeepAgent(path)`

---

## 扩展能力示例

### 1. Skill 注册与使用（日期 → 星期，Python 脚本）

Skill 在 Java 侧是**元数据 + 磁盘目录**，负责让 Agent 知道「有哪些 skill、去哪里读 `SKILL.md`、如何调用脚本」。它**不是**执行器，也不会自动挂载 shell 工具。完整约定见 [Agent Skills](../高阶用法/Agent%20Skills.md)。

#### 目录结构

```text
workspace/skills/date_to_weekday/
├── SKILL.md
└── scripts/
    └── date_to_weekday.py   # 输入 YYYY-MM-DD，输出英文/中文星期
```

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

---

### 5. 运行预算（Run Budget）

运行预算给主循环加两道闸：**轮次维**（保障轮次内自由运行，之后按检查点评估进展，停滞则注入提示，触及绝对上限即终止）与**时间维**（墙钟预算，到期后不再派发新的模型/工具调用，临近截止时注入一次收尾提示）。两维独立开关，默认全部关闭（不配置即零行为变化）。任务被预算终止时返回可辨识的终止面（`result_type=error` + `turn_budget`/`time_budget` 载荷），与模型/工具异常区分开。

挂载方式与自定义 Rail 相同——创建时写入 `DeepAgentConfig.rails`（初始化时注册到内层 ReActAgent）：

```java
DeepAgentConfig.builder()
        .enableTaskLoop(true)
        .rails(List.of(new RunBudgetRail(RunBudgetConfig.builder()
                .turnEnabled(true)              // 轮次维
                .suggestedRounds(30)            // 保障轮次
                .hardLimit(60)                  // 绝对上限（缺省取 max(保障*2, 200)）
                .totalBudgetSeconds(600)        // 时间维：总预算秒数（缺省关闭）
                .nearDeadlineThresholdSeconds(60) // 临期提示阈值（缺省取 min(60, 总预算)）
                .build())))
        .build();
```

运行中重新配置：经 `reactAgent()` 拿到内层 ReActAgent 后注册新 rail（配置不可变，换配置即换 rail）：

```java
((ReActAgent) agent.reactAgent())
        .registerRail(new RunBudgetRail(newConfig)).toCompletableFuture().join();
```

行为要点：

| 配置 | 维度 | 作用 |
|------|------|------|
| `suggestedRounds` | 轮次 | 保障轮次，期内不干预 |
| `firstCheckpoint` 等 | 轮次 | 检查点节奏（缺省 max(10, 保障轮次)；显式值下限为保障轮次） |
| `hardLimit` | 轮次 | 绝对上限；触顶后先给一轮收尾（安全阀），仍不收敛则终止 |
| `totalBudgetSeconds` | 时间 | 墙钟总预算；到期即终止，不再派发新动作 |
| `nearDeadlineThresholdSeconds` | 时间 | 剩余低于阈值时注入一次「尽快收尾」提示 |

时间维生效时，单次模型调用的超时与流式重试等待会被钳制到剩余预算内（只缩不放），避免长尾调用烧穿预算。

---

### 6. 模型调用超时分层（连接 / 响应头等待 / 流式空闲）

单次模型 HTTP 调用的超时按三个独立层级解析，各层解析链互不混用：

| 层级 | 语义 | 配置项（`ModelClientConfig` 字段 / backend map 键） | 缺省 | 取值范围 |
|------|------|------|------|------|
| 连接 | 建立 TCP/TLS 连接 | `connectTimeoutSeconds` / `connect_timeout_seconds` | 10s（可被系统属性 `openjiuwen.llm.http.connect-timeout-seconds` 覆盖） | [5, 15]s |
| 响应头等待 | 请求发出后等待响应头到达 | 沿用旧 `timeout` 字段（显式设置时作为本层取值） | 未显式设置时回落为流式空闲层的解析值 | 旧语义不变 |
| 流式空闲 | 对端沉默（无任何数据段）超过窗口即掐断 | `streamIdleSeconds` / `stream_idle_seconds` | 600s | [300, 900]s |

没有「全程总时长」上限：长生成只要持续有数据返回就不会被掐断，只有对端沉默才会触发流式空闲层。

配置示例：

```java
ModelClientConfig clientConfig = ModelClientConfig.builder()
        .clientProvider(ProviderType.OPENAI)
        .apiKey(apiKey)
        .apiBase(baseUrl)
        .connectTimeoutSeconds(10.0)   // 连接层，可选，缺省 10s
        .streamIdleSeconds(600.0)      // 流式空闲层，可选，缺省 600s
        .build();
```

行为要点：

- 越界配置在 setter / builder 处直接抛 `IllegalArgumentException`，不会静默截断。
- 单次调用可用 `ModelInvokeOptions.timeout` 覆盖**流式空闲层**取值（只影响该次调用，不影响连接层与响应头等待层）。
- 超时被掐断时抛出可辨识的错误面：错误码 `181008`（MODEL_CALL_TIMEOUT），并携带超时阶段 `TimeoutPhase`（`BEFORE_CONNECT` / `BEFORE_FIRST_SEGMENT` / `AFTER_FIRST_SEGMENT`），便于按层定位是连接不通、首段迟迟不到还是流中途沉默。
- 与运行预算（见上节）组合时，预算钳制以该次调用的有效空闲超时为基线，只缩不放。

---

### 7. 多租户隔离

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