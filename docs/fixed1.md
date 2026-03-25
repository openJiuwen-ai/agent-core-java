# Java 翻译修复报告 (fixed1.md)

> 本报告基于 `l0-l2-module-comparison-report.md` 和 `l0-l2-python-java-translation-check-report.md` 两份检查报告中发现的问题，对照 Python 源码进行修复。

---

## 修复概览

| 序号 | 修复项 | 层级 | 优先级 | 涉及文件 | 操作 |
|------|--------|------|--------|----------|------|
| 1 | MessageUtils 缺失 | L0 Common | P0 | `MessageUtils.java` | 新建 |
| 2 | LoggerProtocol 缺少 handler/filter/logger API | L0 Common | P1 | `LoggerProtocol.java` | 修改 |
| 3 | LoggingUtils 缺少路径校验方法 | L0 Common | P1 | `LoggingUtils.java` | 修改 |
| 4 | LocalFunction 缺少 schema 校验、stream 行为不一致 | L1 Foundation | P0 | `LocalFunction.java` | 修改 |
| 5 | ContextEngine 内置处理器未自动注册 | L2 Context | P0 | `ContextEngine.java` | 修改 |
| 6 | WorkflowInteraction executableId 取值错误 + 异常类型不一致 | L2 Session | P0 | `WorkflowInteraction.java` | 修改 |
| 7 | CallbackManager.trigger 吞没异常 | L2 Session | P0 | `CallbackManager.java` | 修改 |
| 8 | AgentSession agentId 返回 null + 缺少 name/description | L2 Session | P1 | `AgentSession.java` | 修改 |
| 9 | AgentSessionApi 缺少 name/description/streamOutput | L2 Session | P1 | `AgentSessionApi.java` | 修改 |

---

## 详细修复说明

### Fix 1: 新建 MessageUtils.java

**路径**: `src/main/java/com/openjiuwen/core/common/utils/MessageUtils.java`

**对应 Python**: `openjiuwen/core/common/utils/message_utils.py`

**问题**: 两份报告均标注 `message_utils` 模块在 Java 侧完全缺失。该工具类提供消息去重、添加各类消息（用户/AI/Tool/Workflow）、获取聊天历史等核心能力。

**修复内容**:
- 新建 `MessageUtils.java`，完整翻译 Python 中的以下方法：
  - `shouldAddUserMessage(context, query)` — 消息去重判断
  - `addUserMessage(context, query)` — 添加用户消息
  - `addAiMessage(context, content)` — 添加 AI 回复消息
  - `addToolMessage(context, toolCallId, content)` — 添加工具调用结果消息
  - `addWorkflowMessage(context, content)` — 添加工作流消息
  - `getChatHistory(context, maxRounds)` — 获取聊天历史（带轮数限制）
- 使用 `ContextEngine.getContext()`、`ModelContext.addMessages()` 等 Java 已有 API
- 敏感信息过滤使用 `UserConfig.isSensitive()` 守卫

---

### Fix 2: LoggerProtocol 添加 handler/filter/logger API

**路径**: `src/main/java/com/openjiuwen/core/common/logging/LoggerProtocol.java`

**对应 Python**: `openjiuwen/core/common/logging/protocol.py`

**问题**: Python 的 `LoggerProtocol` 提供 `add_handler()`、`remove_handler()`、`add_filter()`、`remove_filter()`、`logger` 属性，Java 侧全部缺失。

**修复内容**:
- 添加 5 个 `default` 方法：
  - `addHandler(Handler handler)` — 添加日志处理器
  - `removeHandler(Handler handler)` — 移除日志处理器
  - `addFilter(Filter filter)` — 添加日志过滤器
  - `removeFilter(Filter filter)` — 移除日志过滤器
  - `logger()` — 获取底层 Logger 实例（默认返回 null，由实现类覆写）
- 作为 default 方法，不破坏现有实现类

---

### Fix 3: LoggingUtils 添加路径校验方法

**路径**: `src/main/java/com/openjiuwen/core/common/logging/LoggingUtils.java`

**对应 Python**: `openjiuwen/core/common/logging/utils.py`

**问题**: Python 中 `normalize_and_validate_log_path()` 负责日志路径的规范化（realpath 解析）与安全校验（敏感路径检测），Java 侧缺失。

**修复内容**:
- 添加 `normalizeAndValidateLogPath(Object pathValue)` 方法
- 使用 `Paths.get().toRealPath()` 进行路径规范化
- 使用 `PathChecker.isSensitivePath()` 进行安全性校验
- 校验失败时抛出 `ErrorHelper.buildError(StatusCode.COMMON_LOG_PATH_INVALID, ...)`

---

### Fix 4: LocalFunction schema 校验 + stream 行为修正

**路径**: `src/main/java/com/openjiuwen/core/foundation/tool/function/LocalFunction.java`

**对应 Python**: `openjiuwen/core/foundation/tool/function.py`

**问题**:
1. Python 的 `invoke()` 会在调用前通过 `format_with_schema()` 对入参进行 schema 校验，Java 缺少此步骤
2. Python 的 `stream()` 在函数返回非可迭代结果时抛出异常，Java 却静默包装为单元素列表

**修复内容**:
- `invoke()` / `stream()`：调用前增加 `validateInputs()` 私有方法
  - 当 ToolCard 的 `inputParams` 非空时，调用 `SchemaUtils.formatWithSchema(args, inputParams)` 进行 schema 校验
- `stream()`：当结果既非 `Iterator` 也非 `Iterable` 时，抛出 `ErrorHelper.buildError(StatusCode.TOOL_LOCAL_FUNCTION_EXECUTION_ERROR, ...)` 而非静默包装

---

### Fix 5: ContextEngine 内置处理器自动注册

**路径**: `src/main/java/com/openjiuwen/core/context/ContextEngine.java`

**对应 Python**: `openjiuwen/core/context/context_engine.py`

**问题**: Python 的 `ContextEngine` 在模块加载时通过字典字面量注册 5 个内置处理器：`CurrentRoundCompressor`、`DialogueCompressor`、`RoundLevelCompressor`、`MessageOffloader`、`MessageSummaryOffloader`。Java 侧的 `PROCESSOR_FACTORY_MAP` 和 `PROCESSOR_CLASS_MAP` 虽然定义了但未预填充。

**修复内容**:
- 添加 `static {}` 初始化块，注册所有 5 个内置处理器：
  ```java
  PROCESSOR_CLASS_MAP.put("CurrentRoundCompressor", CurrentRoundCompressor.class);
  PROCESSOR_FACTORY_MAP.put("CurrentRoundCompressor", config -> new CurrentRoundCompressor((CurrentRoundCompressorConfig) config));
  // ... 同样注册其余 4 个处理器
  ```
- 添加对应的 10 个 import 语句（5 个处理器类 + 5 个 Config 类）

---

### Fix 6: WorkflowInteraction executableId 取值 + 异常类型修正

**路径**: `src/main/java/com/openjiuwen/core/session/interaction/WorkflowInteraction.java`

**对应 Python**: `openjiuwen/core/session/interaction/workflow_interaction.py` (对应 `BaseInteraction`)

**问题**:
1. Python 中 `executable_id` 取自 `session.executable_id`（NodeSession 特有属性），Java 直接 fall back 到 `session.sessionId()` 导致 ID 不一致
2. Python 中 `wait_user_inputs()` 和 `user_latest_input()` 触发 `GraphInterrupt(Interrupt(output_data))`，Java 抛出的是普通 `RuntimeException`

**修复内容**:
- `getExecutableId()`：添加 `instanceof NodeSession` 模式匹配，优先使用 `nodeSession.executableId()`
- `waitUserInputs()` 和 `userLatestInput()`：改为抛出 `GraphInterruptRuntimeWrapper(new GraphInterrupt(new Interrupt(outputData)))`
- 新增内部类 `GraphInterruptRuntimeWrapper extends RuntimeException`，包装检查异常 `GraphInterrupt`
- 添加 `GraphInterrupt`、`Interrupt`、`NodeSession` 的 import

---

### Fix 7: CallbackManager.trigger 异常传播

**路径**: `src/main/java/com/openjiuwen/core/session/callback/CallbackManager.java`

**对应 Python**: `openjiuwen/core/session/callback/callback_manager.py`

**问题**: Python 中 `await method(**kwargs)` 自然传播异常。Java 的 `trigger()` 方法在 catch 块中仅 `log.error()` 后吞没异常，导致回调失败时上层调用方无感知。

**修复内容**:
- `InvocationTargetException`：解包 `getTargetException()` 并重新抛出
  - 若目标异常为 `RuntimeException`，直接重新抛出
  - 否则包装为 `RuntimeException` 再抛出
- 其他 `Exception`：包装为 `RuntimeException` 后重新抛出
- 保留日志记录（`log.error`）

---

### Fix 8: AgentSession agentId/agentName/agentDescription

**路径**: `src/main/java/com/openjiuwen/core/session/internal/AgentSession.java`

**对应 Python**: `openjiuwen/core/session/internal/agent_session.py`

**问题**:
1. `agentId()` 在 config 中未找到 agentId 时返回 null，Python 会 fallback 到 `card.id`
2. Python 提供 `agent_name` 和 `agent_description` 属性（从 card 读取），Java 缺失

**修复内容**:
- `agentId()`：增加 `card instanceof BaseCard baseCard` 检查，当 config 无 agentId 时返回 `baseCard.getId()`
- 新增 `agentName()` 方法：从 BaseCard 读取 `getName()`
- 新增 `agentDescription()` 方法：从 BaseCard 读取 `getDescription()`

---

### Fix 9: AgentSessionApi 补充公开 API

**路径**: `src/main/java/com/openjiuwen/core/session/AgentSessionApi.java`

**对应 Python**: `openjiuwen/core/session/agent_session_api.py`

**问题**:
1. Python 暴露了 `agent_name` 和 `agent_description`，Java 无对应 API
2. Python 提供流式回调消费 `stream_output(callback)`，Java 仅有 `streamIterator()` 返回迭代器

**修复内容**:
- 新增 `getAgentName()` → 委托 `inner.agentName()`
- 新增 `getAgentDescription()` → 委托 `inner.agentDescription()`
- 将 `streamIterator()` 标记 `@Deprecated`，指引使用 `streamOutput(Consumer)`
- 新增 `streamOutput(Consumer<Object> consumer)` — 基于回调的增量流式消费方式

---

## 编译验证

全部修改完成后执行 `mvn compile`：
- 本次修改的 9 个文件（1 个新建 + 8 个修改）均编译通过，无错误
- 项目存在若干预先存在的编译错误（`CompiledGraph.java`、`StreamProcessor.java`、`Vertex.java`），与本次修复无关

---

## 尚未修复的问题（后续跟进）

以下为两份检查报告中识别到但不在本轮修复范围内的问题：

### P0 级（功能阻断）

| 问题 | 层级 | 说明 |
|------|------|------|
| ModelClient 具体实现缺失 | L1 Foundation | Python 有多种 LLM Client（OpenAI, Qwen 等），Java 仅有接口定义 |
| Graph 序列化格式差异 | L2 Graph | Python 使用 JSON，Java 使用自定义 Binary，需确认互操作需求 |

### P1 级（功能缺失）

| 问题 | 层级 | 说明 |
|------|------|------|
| Store 具体实现缺失 | L1 Foundation | Python 有 FAISS/Chroma/Milvus Store，Java 仅有接口 |
| MCP Transport Client 缺失 | L1 Foundation | Python 有 SSE/Stdio Transport，Java 缺少 |
| MarkdownOutputParser 缺失 | L1 Foundation | Python 有 Markdown 结构解析器 |
| Embedding 接口缺失 | L1 Foundation | Python 定义了 Embedding 协议 |
| PersistenceCheckpointer 缺失 | L2 Graph | Python 有持久化 Checkpointer（SQLite等） |

### P2 级（优化项）

| 问题 | 层级 | 说明 |
|------|------|------|
| TiktokenCounter 缺失 | L0 Common | Python 有基于 tiktoken 的 token 计数 |
| Config 环境变量批量加载 | L0 Common | Python 支持 `os.environ` 批量配置 |

---

## 修复文件清单

```
# 新建文件
src/main/java/com/openjiuwen/core/common/utils/MessageUtils.java

# 修改文件
src/main/java/com/openjiuwen/core/common/logging/LoggerProtocol.java
src/main/java/com/openjiuwen/core/common/logging/LoggingUtils.java
src/main/java/com/openjiuwen/core/foundation/tool/function/LocalFunction.java
src/main/java/com/openjiuwen/core/context/ContextEngine.java
src/main/java/com/openjiuwen/core/session/interaction/WorkflowInteraction.java
src/main/java/com/openjiuwen/core/session/callback/CallbackManager.java
src/main/java/com/openjiuwen/core/session/internal/AgentSession.java
src/main/java/com/openjiuwen/core/session/AgentSessionApi.java
```
