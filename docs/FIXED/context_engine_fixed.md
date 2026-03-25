# context_engine 对照检查出的缺漏项

## 1. 结论

Java 版 `context` 模块整体已经完成了主体类和主体流程的移植，但对照 Python 版 `context_engine` 后，仍有几类“API 已有、但兼容性或语义尚未完全对齐”的缺漏点。下面按影响优先级列出。

## 2. 高优先级缺漏

### 2.1 `get_context_window(..., **kwargs)` 扩展参数链路未完整移植

- Python 版:
  - `ModelContext.get_context_window(..., **kwargs)`
  - `SessionModelContext.get_context_window(..., **kwargs)`
- Java 版:
  - `ModelContext.getContextWindow(List<BaseMessage>, List<ToolInfo>, Integer, Integer)`
  - `SessionModelContext.getContextWindow(List<BaseMessage>, List<ToolInfo>, Integer, Integer)`

缺口说明:

- Python 的 `get_context_window()` 保留了 `**kwargs` 扩展入口。
- Java 版只保留固定参数，没有额外选项通道。

直接影响:

- 不能像 Python 一样把 `model`、自定义开关或后续扩展参数沿着 `SessionModelContext -> KVCacheManager / Processor` 这一链路继续传递。
- 后续如果某个 `ContextProcessor.on_get_context_window()` 需要依赖额外上下文，Java 版公开 API 无法承载。

建议:

- 在 Java 侧为 `ModelContext.getContextWindow(...)` 与 `SessionModelContext.getContextWindow(...)` 增加 `Map<String, Object> kwargs` 或等价扩展参数对象。

### 2.2 KV cache release 调用链不完整

- Python 版:
  - `SessionModelContext.get_context_window(..., **kwargs)` 中调用 `await self._kv_cache_manager.release(window, **kwargs)`
  - `KVCacheManager.release()` 从 `kwargs["model"]` 中提取 `InferenceAffinityModel`
- Java 版:
  - `SessionModelContext.getContextWindow(...)` 只调用 `kvCacheManager.release(window)`
  - `KVCacheManager.release(ContextWindow contextWindow, Object model)` 虽然存在，但没有从公开 API 被真正传入 `model`

缺口说明:

- Java 版 `KVCacheManager` 已具备 `release(window, model)` 方法，但 `SessionModelContext.getContextWindow()` 没有参数入口把 model 传下去。
- 结果是 Java 公开调用链实际上只能走“比较窗口差异”的一半逻辑，难以稳定驱动真实的 `InferenceAffinityModel.release(...)`。

建议:

- 与上一条一起修复，为 `getContextWindow` 增加扩展参数，并在 `SessionModelContext` 中把 `model` 传给 `KVCacheManager.release(window, model)`。

### 2.3 自定义 `ModelContext` 的持久化与 offload 扩展性缺失

- Python 版做法:
  - `ContextEngine._load_state_from_session()` 用 `hasattr(context, "load_state")`
  - `ContextEngine.save_contexts()` 用 `hasattr(context, "save_state")`
  - `ContextProcessor._offload_messages_to_memory()` 用 `hasattr(context, "offload_messages")`
- Java 版做法:
  - `ContextEngine.loadStateFromSession()` 写死 `context instanceof SessionModelContext`
  - `ContextEngine.saveContexts()` 写死 `context instanceof SessionModelContext`
  - `ContextProcessor.offloadMessagesToMemory()` 写死 `context instanceof SessionModelContext`

缺口说明:

- Python 版允许任何实现了 `load_state/save_state/offload_messages` 的 `ModelContext` 参与引擎流程。
- Java 版目前把持久化与 offload 能力绑定到了 `SessionModelContext` 这一具体实现上。

直接影响:

- 如果后面新增别的 `ModelContext` 子类，哪怕语义上支持保存/加载/offload，也无法被 `ContextEngine` 和 `ContextProcessor` 正常复用。

建议:

- 在 Java 侧引入更细粒度接口，比如 `StatefulModelContext`、`OffloadCapableContext`，或者直接把相关能力补进 `ModelContext` 抽象层。

### 2.4 offload 占位消息缺少原消息附加字段透传

- Python 版:
  - `create_offload_message(..., **kwargs)` 支持附加字段
  - `MessageOffloader._offload_message()` / `MessageSummaryOffloader._offload_message()` 使用 `message.model_dump()` 复制额外字段
- Java 版:
  - `OffloadMessages.createOffloadMessage(...)` 只接收 `role/content/offloadHandle/offloadType`
  - `MessageOffloader.offloadMessage(...)` 与 `MessageSummaryOffloader.offloadMessage(...)` 基本只保留 `role/content`
  - `ContextProcessor.offloadMessagesToMemory(...)` 仅对 `ToolMessage.toolCallId` 做了一个特判复制

缺口说明:

- Python 版 offload 后仍可保留大量原始消息字段，例如:
  - `ToolMessage.tool_call_id`
  - `AssistantMessage.tool_calls`
  - `AssistantMessage.usage_metadata`
  - `AssistantMessage.finish_reason`
  - `AssistantMessage.parser_content`
  - `AssistantMessage.reasoning_content`
  - `BaseMessage.name`
- Java 版目前除 `toolCallId` 特判外，其他附加字段会在 offload 占位消息生成时丢失。

直接影响:

- 经过 offload 的 assistant/tool 消息在 Java 侧可能无法完整保留原语义，尤其是工具调用链、模型返回元数据、推理内容等。

建议:

- 扩展 `OffloadMessages.createOffloadMessage(...)` 支持附加字段复制。
- 在 `MessageOffloader` / `MessageSummaryOffloader` 中补齐原消息字段透传逻辑，而不是只复制 `content`。

## 3. 中优先级缺漏

### 3.1 缺少 `TiktokenCounter` 的精确实现

- Python 版:
  - `token.tiktoken_counter.TiktokenCounter`
- Java 版:
  - `token.SimpleTokenCounter`

缺口说明:

- Java 版只有按字符长度估算的 `SimpleTokenCounter`。
- Python 版使用真正的 `tiktoken` 编码规则。

直接影响:

- 以下逻辑在 Java 与 Python 之间可能出现阈值偏差:
  - `tokens_threshold` 触发时机
  - `large_message_threshold` 判定
  - `ContextStats.total_tokens` / `tool_tokens`
  - 压缩器与 offloader 的触发结果

建议:

- Java 侧补一个精确 token counter，优先考虑接入 `jtokkit` 或同类库。

### 3.2 Session 包装兼容分支未移植

- Python 版:
  - `ContextEngine._load_state_from_session()` / `_save_state_to_session()` 同时兼容 `session.get_state/update_state` 与 `session._inner.get_state/update_state`
- Java 版:
  - 只支持 `Session.getState()` / `Session.updateState()`

缺口说明:

- Python 版为代理 Session / 包装 Session 预留了 fallback。
- Java 版当前只兼容直接实现 `Session` 接口的对象。

建议:

- 如果 Java 侧也会出现代理 Session / 装饰器 Session，建议补一层兼容适配。

### 3.3 配置模型缺少 Python Pydantic 级别的约束校验

- Python 版:
  - 多个配置字段通过 `Field(gt=0)` 强制要求正数
- Java 版:
  - 配置类多为普通 Lombok POJO
  - 只有少量处理器在运行期做了局部校验

缺口说明:

- Java 版 `ContextEngineConfig`、`CurrentRoundCompressorConfig`、`DialogueCompressorConfig`、`RoundLevelCompressorConfig` 等配置类没有统一的字段级约束。

直接影响:

- 非法配置值可能在更晚的执行路径里才暴露，甚至以静默行为偏差的形式出现。

建议:

- 为 Java 配置类补上统一校验策略，例如 Jakarta Validation、构造器断言或显式 `validate()`。

## 4. 低优先级缺漏

### 4.1 `RoundLevelCompressor._compress_messages()` 私有辅助方法未移植

- Python 版:
  - `RoundLevelCompressor._compress_messages(messages, role, context)`
- Java 版:
  - 无直接对应方法

说明:

- 这个方法在 Python 当前主流程里不是核心入口，因此影响有限。
- 但如果后续需要对“单角色消息集合”做独立压缩，Java 版少了现成辅助实现。

### 4.2 `ContextMessageBuffer.pop_back(size=None, ...)` 的“全部弹出”语义未直接暴露

- Python 版:
  - `pop_back(size: Optional[int] = None, with_history: bool = True)`
- Java 版:
  - `popBack(int size, boolean withHistory)`

说明:

- Java 版上层 `ModelContext.popMessages()` 已能满足常用场景，但 `ContextMessageBuffer` 自身 API 没有完全对齐 Python 的 `size=None` 语义。

## 5. 优先修复建议

建议优先按下面顺序补齐:

1. `getContextWindow(...)/KVCacheManager.release(...)` 的扩展参数链路
2. `OffloadMessages.createOffloadMessage(...)` 的附加字段透传
3. `ContextEngine` / `ContextProcessor` 对自定义 `ModelContext` 的扩展性支持
4. 精确 token counter
5. 配置统一校验

## 6. 备注

本文件记录的是“Python 版已有、Java 版尚未完全对齐”的缺漏项，不等同于 Java 版完全不可用。Java 版主干流程已经具备，但如果目标是做到真正的 API/行为对齐，上述几项建议尽快补齐。
