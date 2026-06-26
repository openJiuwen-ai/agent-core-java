# com.openjiuwen.core.context_engine.context.ContextMessageBuffer

## class ContextMessageBuffer

```java
public class ContextMessageBuffer
```

`ContextMessageBuffer` 维护上下文尾部缓冲区，并区分“历史消息段”和“当前上下文消息段”，用于支撑 `SessionModelContext` 的追加、裁剪、替换和弹出操作。

## 构造方法

### `public ContextMessageBuffer(List<BaseMessage> historyMessages, Integer maxBufferSize)`

用初始历史消息创建缓冲区，并可选配置最大缓冲窗口大小。

**说明**

- `historyMessages == null` 时会退回空列表。
- `maxBufferSize != null` 时，构造和 `rebuild()` 只保留历史尾部最近的 `maxBufferSize` 条消息。

## 主要方法

### `public int size()`

返回缓冲区的有效大小；配置了 `maxBufferSize` 时返回 `min(contextMessages.size(), maxBufferSize)`。

### `public void addBack(List<BaseMessage> messages)`

把消息追加到尾部，并在必要时触发自动缩容。

**说明**

- 当内部列表长度超过 `maxBufferSize * 2` 时，会整体丢弃最早的 `maxBufferSize` 条消息，并同步修正历史段长度。

### `public List<BaseMessage> getBack(Integer size, boolean withHistory)`

从尾部读取消息。

**说明**

- `size == null` 时返回全部可见消息。
- `withHistory == false` 时会剔除当前可见窗口中的历史段，只返回本轮上下文消息。

### `public List<BaseMessage> getBack()`

读取全部可见消息的便捷重载。

### `public List<BaseMessage> popBack(int size, boolean withHistory)`

### `public List<BaseMessage> popBack(Integer size, boolean withHistory)`

从尾部弹出消息，并在需要时同步缩减历史段。

**说明**

- `withHistory == true` 且弹出量超过当前上下文段时，会从历史段继续向前弹出。
- `size == null` 的重载用于弹出全部可见消息。

### `public void setMessages(List<BaseMessage> messages, boolean withHistory)`

替换缓冲区内容。

**说明**

- `withHistory == true` 时直接重建整个缓冲区，并把 `historyMessagesSize` 置为 `0`。
- `withHistory == false` 时会保留当前历史段，仅替换后续上下文消息。

### `public void rebuild(List<BaseMessage> historyMessages)`

按新的历史消息重建缓冲区。

## 说明

- `ContextMessageBufferTest` 覆盖了尾部读取、尾部弹出、历史保留和最大缓冲窗口缩容行为。
