# single_agent 模块转译报告

## 概述

本报告记录了将 Python 版 `openjiuwen.core.single_agent` 模块转译为 Java 版 `com.openjiuwen.core.singleagent` 的完整过程，包括文件映射、设计决策、依赖处理及关键差异说明。

---

## 模块映射

| 分类 | Python 源文件 | Java 目标文件 | 状态 |
|------|-------------|-------------|------|
| **基础** | `__init__.py` | _(包级别，无需转译)_ | N/A |
| **基类** | `base.py` → `BaseAgent` | `BaseAgent.java` | ✅ 已完成 |
| **核心** | `ability_manager.py` | `AbilityManager.java` | ✅ 已完成 |
| **核心** | `agent_callback_manager.py` | `AgentCallbackManager.java` | ✅ 已完成 |
| **异常** | _(内嵌)_ | `AbilityExecutionError.java` | ✅ 已完成 |
| **Agent** | `agents/react_agent.py` → `ReActAgentConfig` | `agents/ReActAgentConfig.java` | ✅ 已完成 |
| **Agent** | `agents/react_agent.py` → `ReActAgent` | `agents/ReActAgent.java` | ✅ 已完成 |
| **Agent** | `agents/react_agent_evolve.py` | `agents/ReActAgentEvolve.java` | ✅ 已完成 |
| **Agent** | _(base.py 中 ControllerAgent)_ | `ControllerAgent.java` | ✅ 已完成 |
| **Rail** | `rail/__init__.py` | _(包级别)_ | N/A |
| **Rail** | `rail/base.py` → `AgentCallbackEvent` | `rail/AgentCallbackEvent.java` | ✅ 已完成 |
| **Rail** | `rail/base.py` → `AgentCallbackContext` | `rail/AgentCallbackContext.java` | ✅ 已完成 |
| **Rail** | `rail/base.py` → `InvokeInputs` | `rail/InvokeInputs.java` | ✅ 已完成 |
| **Rail** | `rail/base.py` → `ModelCallInputs` | `rail/ModelCallInputs.java` | ✅ 已完成 |
| **Rail** | `rail/base.py` → `ToolCallInputs` | `rail/ToolCallInputs.java` | ✅ 已完成 |
| **Rail** | `rail/base.py` → `@rail` 装饰器 | `rail/RailExecutor.java` | ✅ 已完成 |
| **Rail** | _(新增)_ | `rail/EventInputs.java` | ✅ 已完成 |
| **Rail** | _(新增)_ | `rail/AgentCallbackFirer.java` | ✅ 已完成 |
| **Rail** | _(新增)_ | `rail/AgentCallback.java` | ✅ 已完成 |
| **Rail** | _(新增)_ | `rail/AgentRail.java` | ✅ 已完成 |
| **Rail** | _(新增)_ | `rail/RetryRequest.java` | ✅ 已完成 |
| **Schema** | `schema/agent_card.py` | `schema/AgentCard.java` | ✅ 已存在 |
| **Schema** | `schema/agent_result.py` → `AgentResult` | `schema/AgentResult.java` | ✅ 已完成 |
| **Schema** | `schema/agent_result.py` → `Artifact` | `schema/Artifact.java` | ✅ 已完成 |
| **Skills** | `skills/__init__.py` | _(包级别)_ | N/A |
| **Skills** | `skills/skill_manager.py` → `Skill` | `skills/Skill.java` | ✅ 已完成 |
| **Skills** | `skills/skill_manager.py` → `SkillManager` | `skills/SkillManager.java` | ✅ 已完成 |
| **Skills** | `skills/skill_util.py` | `skills/SkillUtil.java` | ✅ 已完成 |
| **Skills** | `skills/remote_skill_util.py` → `GitHubTree` | `skills/GitHubTree.java` | ✅ 已完成 |
| **Skills** | `skills/remote_skill_util.py` → `RemoteSkillUtil` | `skills/RemoteSkillUtil.java` | ✅ 已完成 |
| **依赖** | `common.schema.part` → `Part` | `common/schema/Part.java` | ✅ 已完成 |

**统计**：共创建 28 个 Java 文件（含 1 个新增依赖 Part.java），修改 1 个已有文件（AbilityManager 实现 ToolRegistry 接口）。

---

## 包结构

```
com.openjiuwen.core.singleagent/
├── AbilityExecutionError.java     // 能力执行异常
├── AbilityManager.java            // 能力管理器（工具/工作流/Agent/MCP）
├── AgentCallbackManager.java      // 回调管理器
├── BaseAgent.java                 // Agent 抽象基类
├── ControllerAgent.java           // Controller 驱动的 Agent
├── agents/
│   ├── ReActAgent.java            // ReAct 范式 Agent
│   ├── ReActAgentConfig.java      // ReAct Agent 配置
│   └── ReActAgentEvolve.java      // 可自进化的 ReAct Agent
├── rail/
│   ├── AgentCallback.java         // 回调函数式接口
│   ├── AgentCallbackContext.java  // 回调上下文
│   ├── AgentCallbackEvent.java    // 回调事件枚举（8个）
│   ├── AgentCallbackFirer.java    // 回调触发接口
│   ├── AgentRail.java             // Rail 抽象基类
│   ├── EventInputs.java           // 事件输入标记接口
│   ├── InvokeInputs.java          // INVOKE 事件数据
│   ├── ModelCallInputs.java       // MODEL_CALL 事件数据
│   ├── RetryRequest.java          // 重试请求
│   ├── RailExecutor.java          // Rail 执行器（替代 @rail 装饰器）
│   └── ToolCallInputs.java        // TOOL_CALL 事件数据
├── schema/
│   ├── AgentCard.java             // Agent 名片（已存在）
│   ├── AgentResult.java           // Agent 执行结果
│   └── Artifact.java              // 结果产物
└── skills/
    ├── GitHubTree.java            // GitHub 目录树
    ├── RemoteSkillUtil.java       // 远程 Skill 工具
    ├── Skill.java                 // Skill 元数据
    ├── SkillManager.java          // Skill 注册管理
    └── SkillUtil.java             // Skill 高层工具

com.openjiuwen.core.common.schema/
└── Part.java                       // 数据片段（新增依赖）
```

---

## 关键设计决策

### 1. 异步 → 同步

Python 版大量使用 `async/await`（基于 asyncio）。Java 版统一转为同步调用，与项目中已有的 Java 模块保持一致（Runner、Controller 等均为同步 API）。

| Python | Java |
|--------|------|
| `async def invoke(...)` | `Object invoke(...)` |
| `async def stream(...)` → `AsyncIterator` | `Iterator<Object> stream(...)` |
| `await context.add_messages(msg)` | `context.addMessages(msg)` |
| `await llm.invoke(...)` | `llm.invoke(...)` |

### 2. @rail 装饰器 → RailExecutor

Python 使用 `@rail(before=X, after=Y, on_exception=Z)` 装饰器为方法添加生命周期钩子。Java 没有等价的装饰器机制，改为 `RailExecutor.execute()` 静态方法 + Lambda 表达式：

```java
return RailExecutor.execute(
    ctx,
    AgentCallbackEvent.BEFORE_MODEL_CALL,
    AgentCallbackEvent.AFTER_MODEL_CALL,
    AgentCallbackEvent.ON_MODEL_EXCEPTION,
    () -> {
        // 方法体
        return result;
    }
);
```

支持重试机制：`RetryRequest` 可在 `on_exception` 回调中设置延迟重试。

### 3. Pydantic BaseModel → Lombok

Python 的 Pydantic 数据模型统一使用 Lombok 注解替代：

| Python | Java |
|--------|------|
| `class Config(BaseModel):` | `@Data @Builder @NoArgsConstructor @AllArgsConstructor` |
| `Field(default=...)` | `@Builder.Default` |
| 自动验证 | 手动校验（Java 无运行时模型验证） |

### 4. 上下文生命周期管理

Python 使用 `async with ctx.lifecycle(BEFORE, AFTER)` 保证 BEFORE/AFTER 事件对称执行。Java 使用 `try/finally` 模式：

```java
fireCallbackEvent(BEFORE_INVOKE, ctx);
try {
    // 业务逻辑
} finally {
    fireCallbackEvent(AFTER_INVOKE, ctx);
}
```

### 5. Session → AgentSessionApi 适配

Java 的 `Session` 是最小接口（仅 `getSessionId()`, `getState()`, `updateState()`），不具备流式输出能力。`preRun()`/`postRun()`/`writeStream()`/`streamIterator()` 在 `AgentSessionApi` 上。因此 `stream()` 方法中通过 `toAgentSession()` 将 `Session` 包装为 `AgentSessionApi`。

### 6. AbilityManager 实现 ToolRegistry

为支持 `ReActAgentEvolve` 中 `ToolCallOperator` 的集成，`AbilityManager` 新增实现了 `ToolRegistry` 接口，提供 `setToolDescription()` 和 `executeAsToolExecutor()` 方法。

### 7. 命名规范转换

按照编码规范文档 `06-coding-standards.md`：

| Python | Java |
|--------|------|
| `snake_case` 变量/方法 | `camelCase` |
| `snake_case` 模块 | `PascalCase` 类名 |
| `single_agent` | `singleagent`（包名） |
| `ability_manager` | `AbilityManager` |
| `agent_callback_manager` | `AgentCallbackManager` |

---

## 依赖关系

### 新增依赖文件

| 文件 | 原因 |
|------|------|
| `Part.java` | `AgentResult` 依赖 `Part`，Python 中在 `common.schema.part` 模块，Java 中未实现 |

### 修改已有文件

| 文件 | 修改内容 |
|------|---------|
| `AbilityManager.java` | 添加 `implements ToolRegistry`，新增 `setToolDescription()` 和 `executeAsToolExecutor()` 方法 |

### 主要外部依赖（已存在）

- `com.openjiuwen.core.runner.Runner` — 运行器，提供 `resourceMgr()`、`runWorkflow()`、`runAgent()`
- `com.openjiuwen.core.runner.callback.CallbackFramework` — 回调框架
- `com.openjiuwen.core.controller.Controller` — 控制器
- `com.openjiuwen.core.context.ContextEngine` / `ModelContext` — 上下文引擎
- `com.openjiuwen.core.foundation.llm.Model` — LLM 模型调用
- `com.openjiuwen.core.foundation.llm.schema.*` — 消息类（AssistantMessage, UserMessage, SystemMessage, ToolCall, ToolMessage）
- `com.openjiuwen.core.foundation.tool.*` — 工具系统（Tool, ToolCard, ToolInfo）
- `com.openjiuwen.core.operator.llm_call.LLMCallOperator` — LLM 算子
- `com.openjiuwen.core.operator.tool_call.ToolCallOperator` — 工具算子
- `com.openjiuwen.core.session.Session` / `AgentSessionApi` — 会话接口
- `com.openjiuwen.core.memory.LongTermMemory` — 长期记忆

---

## 核心类说明

### BaseAgent

抽象基类，定义 Agent 通用行为：
- 持有 `AgentCard`、`AbilityManager`、`AgentCallbackManager`、`SkillUtil`
- 提供 `registerCallback()`、`registerRail()`、`unregisterRail()` 回调管理
- 声明抽象方法 `configure()`、`getConfig()`、`invoke()`、`stream()`
- 实现 `AgentCallbackFirer` 接口，用于触发回调事件

### ReActAgent

基于 ReAct（Reasoning + Acting）范式的 Agent 实现：
- 直接调用 `Model.invoke()` 执行 LLM 推理
- ReAct 循环：思考 → 行动 → 观察 → 重复
- 支持 Rail 生命周期钩子（BEFORE/AFTER_MODEL_CALL, BEFORE/AFTER_TOOL_CALL）
- 惰性初始化 LLM 实例

### ReActAgentEvolve

可自进化的 ReAct Agent：
- 使用 `LLMCallOperator` 和 `ToolCallOperator` 作为可调优算子
- `LLMCallOperator` 的 system_prompt 可在运行时自动调整
- `ToolCallOperator` 的工具描述可在运行时自动调整
- 提供 `getOperators()` 返回算子注册表，支持 Agent 进化框架

### ControllerAgent

基于 Controller 的 Agent 实现：
- 委托 `Controller.invoke()` / `Controller.stream()` 执行
- 支持事件驱动的复杂任务调度

### AbilityManager

Agent 能力管理器：
- 注册/查询 ToolCard、WorkflowCard、AgentCard、McpServerConfig
- 转换 Card → ToolInfo 供 LLM 使用
- 执行能力调用（从 ResourceManager 获取实例）
- 支持 Rail 生命周期钩子处理工具调用

### Rail 系统

完整的回调生命周期系统：
- **8 个事件**：BEFORE_INVOKE, AFTER_INVOKE, BEFORE_MODEL_CALL, AFTER_MODEL_CALL, ON_MODEL_EXCEPTION, BEFORE_TOOL_CALL, AFTER_TOOL_CALL, ON_TOOL_EXCEPTION
- **AgentRail**：抽象基类，可通过反射自动注册覆写的钩子方法
- **RailExecutor**：替代 Python `@rail` 装饰器，支持重试机制
- **AgentCallbackContext**：统一回调上下文，携带 agent、inputs、session、context 等信息

---

## 编译状态

- ✅ 所有文件无编译错误
- ⚠️ 2 个弃用警告（`AgentSessionApi.streamIterator()` 被标记为 deprecated，但功能正常）

---

## 测试建议

1. **单元测试**：为 `AbilityManager`、`AgentCallbackManager`、`RailExecutor` 编写单元测试
2. **集成测试**：测试 `ReActAgent.invoke()` 的 ReAct 循环逻辑
3. **Rail 测试**：验证 BEFORE/AFTER 事件的触发顺序和重试机制
4. **Skill 测试**：验证 SkillManager 的注册/查询功能
5. **兼容测试**：确保 ControllerAgent 与现有 Controller 的交互正确
