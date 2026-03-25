# Operator 模块 API 文档

> 包路径：`com.openjiuwen.core.operator`

LLM / Tool / Memory Operator 及其调优 schema。基于 `operator` 包源码逐页复核整理。

## 文档说明

- 本页覆盖 `14` 个公开类型（含嵌套公开类型）。
- 默认记录源码中显式声明的 public/protected API；接口中按语言规则公开的成员同样列出。
- Lombok 自动生成的 getter/setter/builder 不逐项展开，DTO/配置类改为记录显式字段。
- 标记为 `@Deprecated` 或位于 `legacy` 包的类型会在条目中注明兼容性。

## 包概览

| 包 | 公开类型数 |
|---|---:|
| `com.openjiuwen.core.operator` | 3 |
| `com.openjiuwen.core.operator.legacy.llm_call` | 2 |
| `com.openjiuwen.core.operator.llm_call` | 2 |
| `com.openjiuwen.core.operator.memory_call` | 3 |
| `com.openjiuwen.core.operator.tool_call` | 4 |

## `com.openjiuwen.core.operator`

公开类型：`3`

### `Operator`

- 类型：`class`
- 声明：`public abstract class Operator`
- 说明：Base class for atomic execution and optimization units.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract String getOperatorId()` | `String` | Unique operator id within a trajectory. |
| `public abstract Map<String, TunableSpec> getTunables()` | `Map<String, TunableSpec>` | Describe tunable parameters. |
| `public abstract void setParameter(String target, Object value)` | `void` | Apply a new parameter value. |
| `public abstract Map<String, Object> getState()` | `Map<String, Object>` | Snapshot current state. |
| `public abstract void loadState(Map<String, Object> state)` | `void` | Restore state from snapshot. |
| `public abstract Object invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs) throws Exception` | `Object` | Execute one operator step. |
| `public Object invoke(Map<String, Object> inputs, Session session) throws Exception` | `Object` | Convenience overload without kwargs. |
| `public OperatorStream<?> stream(Map<String, Object> inputs, Session session, Map<String, Object> kwargs) throws Exception` | `OperatorStream<?>` | Optional streaming execution. |
| `public OperatorStream<?> stream(Map<String, Object> inputs, Session session) throws Exception` | `OperatorStream<?>` | Convenience overload without kwargs. |
| `protected void setOperatorContext(Session session, String operatorId)` | `void` | - |

### `OperatorStream`

- 类型：`interface`
- 声明：`public interface OperatorStream<T> extends Iterator<T>, AutoCloseable`
- 说明：Iterator-like stream with an explicit close hook for early termination.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `default void close()` | `void` | - |

### `TunableSpec`

- 类型：`record`
- 声明：`public record TunableSpec(String name, String kind, String path, Object constraint)`
- 说明：Describes a single tunable parameter of an operator.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `name` | `String` | `private final` | `-` | - |
| `kind` | `String` | `private final` | `-` | - |
| `path` | `String` | `private final` | `-` | - |
| `constraint` | `Object` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TunableSpec(String name, String kind, String path)` | - |

## `com.openjiuwen.core.operator.legacy.llm_call`

公开类型：`2`

### `LLMCall`

- 类型：`class`
- 声明：`public class LLMCall`
- 说明：Legacy compatibility implementation of the pre-operator LLMCall wrapper.
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `DEFAULT_USER_PROMPT` | `String` | `public static final` | `"{{query}}"` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LLMCall(String modelName, Model llm, Object systemPrompt, Object userPrompt, boolean freezeSystemPrompt, boolean freezeUserPrompt, String llmCallId)` | - |
| `public LLMCall(String modelName, Model llm, Object systemPrompt, Object userPrompt)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public AssistantMessage invoke(Map<String, Object> inputs, Session session, List<BaseMessage> history, Object tools) throws Exception` | `AssistantMessage` | - |
| `public AssistantMessage invoke(Map<String, Object> inputs, Session session) throws Exception` | `AssistantMessage` | - |
| `public OperatorStream<AssistantMessageChunk> stream(Map<String, Object> inputs, Session session, List<BaseMessage> history, Object tools) throws Exception` | `OperatorStream<AssistantMessageChunk>` | - |
| `public OperatorStream<AssistantMessageChunk> stream(Map<String, Object> inputs, Session session) throws Exception` | `OperatorStream<AssistantMessageChunk>` | - |
| `public LegacyOptimizerCallback getOptimizerCallback()` | `LegacyOptimizerCallback` | - |
| `public void setOptimizerCallback(LegacyOptimizerCallback optimizerCallback)` | `void` | - |
| `public PromptTemplate getSystemPrompt()` | `PromptTemplate` | - |
| `public PromptTemplate getUserPrompt()` | `PromptTemplate` | - |
| `public void updateSystemPrompt(Object systemPrompt)` | `void` | - |
| `public void updateUserPrompt(Object userPrompt)` | `void` | - |
| `public void setFreezeSystemPrompt(boolean freezeSystemPrompt)` | `void` | - |
| `public void setFreezeUserPrompt(boolean freezeUserPrompt)` | `void` | - |
| `public boolean getFreezeSystemPrompt()` | `boolean` | - |
| `public boolean getFreezeUserPrompt()` | `boolean` | - |

### `LegacyOptimizerCallback`

- 类型：`interface`
- 声明：`@FunctionalInterface public interface LegacyOptimizerCallback`
- 说明：Callback for the legacy LLMCall compatibility path.
- 注解：`@FunctionalInterface`
- 兼容性：`legacy` 包/说明

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `void onComplete(String llmCallId, Map<String, Object> inputs, Object response, Session session) throws Exception` | `void` | - |

## `com.openjiuwen.core.operator.llm_call`

公开类型：`2`

### `LLMCall`

- 类型：`class`
- 声明：`public class LLMCall extends LLMCallOperator`
- 说明：Backward compatible alias of LLMCallOperator.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LLMCall(String modelName, Model llm, Object systemPrompt, Object userPrompt, boolean freezeSystemPrompt, boolean freezeUserPrompt, String llmCallId, BiConsumer<String, Object> onParameterUpdated)` | - |
| `public LLMCall(String modelName, Model llm, Object systemPrompt, Object userPrompt)` | - |

### `LLMCallOperator`

- 类型：`class`
- 声明：`public class LLMCallOperator extends Operator`
- 说明：LLM invocation operator with prompt tunables.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `DEFAULT_USER_PROMPT` | `String` | `public static final` | `"{{query}}"` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LLMCallOperator(String modelName, Model llm, Object systemPrompt, Object userPrompt, boolean freezeSystemPrompt, boolean freezeUserPrompt, String llmCallId, BiConsumer<String, Object> onParameterUpdated)` | - |
| `public LLMCallOperator(String modelName, Model llm, Object systemPrompt, Object userPrompt)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getOperatorId()` | `String` | - |
| `public Map<String, TunableSpec> getTunables()` | `Map<String, TunableSpec>` | - |
| `public void setParameter(String target, Object value)` | `void` | - |
| `public Map<String, Object> getState()` | `Map<String, Object>` | - |
| `public void loadState(Map<String, Object> state)` | `void` | - |
| `public AssistantMessage invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs) throws Exception` | `AssistantMessage` | - |
| `public OperatorStream<AssistantMessageChunk> stream(Map<String, Object> inputs, Session session, Map<String, Object> kwargs) throws Exception` | `OperatorStream<AssistantMessageChunk>` | - |
| `public PromptTemplate getSystemPrompt()` | `PromptTemplate` | - |
| `public PromptTemplate getUserPrompt()` | `PromptTemplate` | - |
| `public void updateSystemPrompt(Object value)` | `void` | - |
| `public void updateUserPrompt(Object value)` | `void` | - |
| `public void setFreezeSystemPrompt(boolean freezeSystemPrompt)` | `void` | - |
| `public void setFreezeUserPrompt(boolean freezeUserPrompt)` | `void` | - |
| `public boolean getFreezeSystemPrompt()` | `boolean` | - |
| `public boolean getFreezeUserPrompt()` | `boolean` | - |

## `com.openjiuwen.core.operator.memory_call`

公开类型：`3`

### `MemoryCallOperator`

- 类型：`class`
- 声明：`public class MemoryCallOperator extends Operator`
- 说明：Memory invocation operator with enabled and retry tunables.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MemoryCallOperator(MemoryOperation memory, String memoryCallId, MemoryInvoker memoryInvoker)` | - |
| `public MemoryCallOperator(MemoryOperation memory)` | - |
| `public MemoryCallOperator(MemoryInvoker memoryInvoker)` | - |
| `public MemoryCallOperator()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getOperatorId()` | `String` | - |
| `public Map<String, TunableSpec> getTunables()` | `Map<String, TunableSpec>` | - |
| `public void setParameter(String target, Object value)` | `void` | - |
| `public Map<String, Object> getState()` | `Map<String, Object>` | - |
| `public void loadState(Map<String, Object> state)` | `void` | - |
| `public Object invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs) throws Exception` | `Object` | - |
| `public OperatorStream<Object> stream(Map<String, Object> inputs, Session session, Map<String, Object> kwargs) throws Exception` | `OperatorStream<Object>` | - |

### `MemoryInvoker`

- 类型：`interface`
- 声明：`@FunctionalInterface public interface MemoryInvoker`
- 说明：Callback hook for non-standard memory invocation flows.
- 注解：`@FunctionalInterface`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `Object invoke(Map<String, Object> inputs) throws Exception` | `Object` | - |

### `MemoryOperation`

- 类型：`interface`
- 声明：`public interface MemoryOperation`
- 说明：Minimal memory contract required by MemoryCallOperator.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | `Object` | - |
| `default boolean supportsStream()` | `boolean` | - |
| `default Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | `Iterator<Object>` | - |

## `com.openjiuwen.core.operator.tool_call`

公开类型：`4`

### `ToolCallOperator`

- 类型：`class`
- 声明：`public class ToolCallOperator extends Operator`
- 说明：Tool invocation operator; tunables cover tool descriptions only.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ToolCallOperator(Tool tool, String toolCallId, ToolExecutor toolExecutor, ToolRegistry toolRegistry)` | - |
| `public ToolCallOperator(Tool tool)` | - |
| `public ToolCallOperator(ToolExecutor toolExecutor)` | - |
| `public ToolCallOperator(Tool tool, ToolRegistry toolRegistry)` | - |
| `public ToolCallOperator()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getOperatorId()` | `String` | - |
| `public Map<String, TunableSpec> getTunables()` | `Map<String, TunableSpec>` | - |
| `public void setParameter(String target, Object value)` | `void` | - |
| `public Map<String, Object> getState()` | `Map<String, Object>` | - |
| `public void loadState(Map<String, Object> state)` | `void` | - |
| `public Object invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs) throws Exception` | `Object` | - |
| `public OperatorStream<Object> stream(Map<String, Object> inputs, Session session, Map<String, Object> kwargs) throws Exception` | `OperatorStream<Object>` | - |

### `ToolExecutionResult`

- 类型：`record`
- 声明：`public record ToolExecutionResult(Object result, ToolMessage toolMessage)`
- 说明：Result wrapper for router-mode tool execution.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `result` | `Object` | `private final` | `-` | - |
| `toolMessage` | `ToolMessage` | `private final` | `-` | - |

### `ToolExecutor`

- 类型：`interface`
- 声明：`@FunctionalInterface public interface ToolExecutor`
- 说明：Router-mode executor for tool call batches.
- 注解：`@FunctionalInterface`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `ToolExecutionResult execute(Object toolCall, Session session) throws Exception` | `ToolExecutionResult` | - |

### `ToolRegistry`

- 类型：`interface`
- 声明：`public interface ToolRegistry`
- 说明：Minimal tool registry contract required by ToolCallOperator.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `default List<Map<String, Object>> getToolDefs()` | `List<Map<String, Object>>` | - |
| `default Map<String, Tool> getTools()` | `Map<String, Tool>` | - |
| `void setToolDescription(String toolName, String description)` | `void` | - |

