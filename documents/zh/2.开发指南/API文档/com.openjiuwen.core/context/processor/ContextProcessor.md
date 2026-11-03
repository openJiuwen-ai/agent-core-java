# com.openjiuwen.core.context_engine.processor.ContextProcessor

## abstract class ContextProcessor

```java
public abstract class ContextProcessor
```

`ContextProcessor` 是所有上下文处理插件的抽象基类。处理器可在消息追加前和上下文窗口返回前两个阶段介入，并可选择输出处理事件、替换后的消息列表或新的 `ContextWindow`。

## 核心方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `ProcessResult` | 处理即将写入上下文的消息；默认直接透传。 |
| `onGetContextWindow(ModelContext context, ContextWindow contextWindow)` | `ProcessResult` | 处理即将返回的上下文窗口；默认直接透传。 |
| `triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `boolean` | 决定是否在追加消息前触发处理器；默认 `false`。 |
| `triggerGetContextWindow(ModelContext context, ContextWindow contextWindow)` | `boolean` | 决定是否在返回窗口前触发处理器；默认 `false`。 |
| `loadState(Map<String, Object> state)` | `void` | 恢复处理器内部状态。 |
| `saveState()` | `Map<String, Object>` | 导出处理器内部状态。 |
| `processorType()` | `String` | 返回简单类名形式的处理器类型名。 |
| `getConfig()` | `<T> T` | 读取构造时注入的配置对象。 |

## 卸载辅助

### `protected BaseMessage offloadMessages(String role, String content, List<BaseMessage> messages, ModelContext context, String offloadHandle, String offloadType, Map<String, Object> extraFields)`

把原消息写入支持卸载的上下文，并返回带 `[[OFFLOAD: handle=<id>, type=<type>]]` 标记的替代消息。

**说明**

- `offloadHandle` 为空时会自动生成去掉连字符的 UUID。
- `offloadType` 为空时默认使用 `in_memory`。
- 只有 `context instanceof OffloadCapableContext` 时才能真正执行卸载。

### 便捷重载

- `offloadMessages(String role, String content, List<BaseMessage> messages, ModelContext context, String offloadHandle, String offloadType)`
- `offloadMessages(String role, String content, List<BaseMessage> messages, ModelContext context)`

## 嵌套类型

### `public record ProcessResult(ContextEvent event, List<BaseMessage> messages, ContextWindow contextWindow)`

封装处理器输出的 record。

**静态工厂**

- `ProcessResult.ofMessages(...)`: 仅返回处理后的消息列表。
- `ProcessResult.ofContextWindow(...)`: 仅返回处理后的窗口对象。

## 说明

- `ContextEngine` 的处理器注册表以 `processorType()` 返回值作为默认类型名。
- 压缩器和卸载器都通过该基类复用统一的卸载标记和上下文写回逻辑。
