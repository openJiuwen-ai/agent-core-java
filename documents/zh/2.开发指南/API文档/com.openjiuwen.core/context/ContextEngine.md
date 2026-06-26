# com.openjiuwen.core.context_engine.ContextEngine

## class ContextEngine

```java
public class ContextEngine
```

`ContextEngine` 是上下文子系统的统一入口，负责注册 `ContextProcessor`、创建或复用 `ModelContext`、清理上下文池，以及把会话中的上下文状态落回 `Session`。

## 构造方法

### `public ContextEngine()`

使用默认的 `ContextEngineConfig.builder().build()` 创建引擎，并在构造时执行 `config.validate()`。

### `public ContextEngine(ContextEngineConfig config)`

使用显式配置初始化引擎。

**说明**

- `config` 为 `null` 时会退回默认 builder。
- 静态初始化块会预注册 `CurrentRoundCompressor`、`DialogueCompressor`、`RoundLevelCompressor`、`MessageOffloader`、`MessageSummaryOffloader` 五个内置处理器。

## 主要方法

### `public ModelContext createContext(String contextId, Session session, List<ProcessorSpec> processors, List<BaseMessage> historyMessages, TokenCounter tokenCounter)`

按 `sessionId + "_" + contextId` 作为池内键创建或复用 `SessionModelContext`。

**参数**

- `contextId`: 会话内上下文 ID；为 `null` 时归一化为 `default_context_id`，并把 `.` 替换为 `_`。
- `session`: 会话对象；为 `null` 时使用 `default_session_id`。
- `processors`: 处理器规格列表，每项包含处理器类型名和对应配置对象。
- `historyMessages`: 初始历史消息；复用已有上下文时也会被合并到会话状态加载流程。
- `tokenCounter`: token 计数策略。

**返回**

- `ModelContext`: 新建或缓存中的上下文实例。

**说明**

- 命中缓存时不会重新创建上下文，而是先尝试从 `Session` 的 `context` 状态重新加载。
- 新建实例时会把 `ProcessorSpec` 转换成真实 `ContextProcessor` 列表，再构造 `SessionModelContext`。

### `public ModelContext createContext(String contextId, Session session)`

使用空处理器、空历史和空 token 计数器创建上下文的简化重载。

### `public ModelContext createContextSimple(String contextId, Session session)`

为翻译后的测试保留的兼容入口，行为等同于 `createContext(contextId, session)`。

### `public ModelContext createContextWithHistory(String contextId, Session session, List<BaseMessage> historyMessages)`

仅显式传入历史消息的兼容重载。

### `public ModelContext getContext(String contextId, String sessionId)`

从内部 `contextPool` 读取已存在的上下文；找不到时返回 `null`。

### `public ModelContext getContext(String contextId)`

按 `default_session_id` 作用域查询上下文。

### `public void clearContext(String contextId, String sessionId)`

删除指定上下文，或在 `contextId == null` 时删除某个会话下的全部上下文。

**说明**

- `sessionId == null` 时会直接清空整个上下文池。
- 目标不存在时不会抛异常，而是记录 warning 日志。

### `public void clearContext()`

清空所有会话下的上下文。

### `public void clearContextBySession(String sessionId)`

清空某个会话下的全部上下文。

### `public void saveContexts(Session session, List<String> contextIds)`

把指定或当前会话下全部上下文的运行时状态保存回 `session.updateState(Map.of("context", states))`。

**说明**

- `session == null` 时直接记录 warning 并返回。
- 仅 `StatefulContext` 实现会被持久化。
- `contextIds == null` 时自动收集该会话下全部上下文 ID。

## 静态方法

### `public static void registerProcessor(String processorType, Class<? extends ContextProcessor> processorClass, Function<Object, ContextProcessor> factory)`

同时注册处理器类型、实现类和工厂函数。

### `public static void registerProcessor(String processorType, Class<? extends ContextProcessor> processorClass)`

仅注册实现类，后续通过构造器反射实例化。

### `public static Class<? extends ContextProcessor> getProcessorClass(String processorType)`

按处理器类型名查询当前注册的处理器类。

## 嵌套类型

### `public record ProcessorSpec(String processorType, Object config)`

用于描述处理器类型名及其配置对象的轻量 record，`createContext()` 会逐项将其解析成处理器实例。

## 说明

- 当处理器工厂不存在且注册表中也找不到对应类型时，会抛出 `StatusCode.CONTEXT_EXECUTION_ERROR`。
- 反射构造处理器失败时，同样会包装为 `CONTEXT_EXECUTION_ERROR`。
- `ContextEngineTest` 覆盖了默认会话 ID、缓存复用、点号上下文 ID 归一化和处理器注册流程。
