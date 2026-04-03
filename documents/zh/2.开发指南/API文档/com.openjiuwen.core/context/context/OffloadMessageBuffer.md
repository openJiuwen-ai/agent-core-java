# com.openjiuwen.core.context.context.OffloadMessageBuffer

## class OffloadMessageBuffer

```java
public class OffloadMessageBuffer
```

`OffloadMessageBuffer` 负责缓存已经从上下文窗口卸载出去的消息集合，当前仅支持 `in_memory` 存储方式。

## 构造方法

### `public OffloadMessageBuffer()`

创建空的内存卸载缓冲区。

### `public OffloadMessageBuffer(Map<String, List<BaseMessage>> initMessages)`

用已有句柄到消息列表的映射初始化缓冲区。

## 主要方法

### `public void offload(String offloadHandle, String offloadType, List<BaseMessage> messages)`

按句柄缓存消息；只有 `offloadType == "in_memory"` 时才会真正写入。

### `public List<BaseMessage> reload(String offloadHandle, String offloadType)`

按句柄重载已缓存消息；找不到时返回空列表。

### `public void clear(String offloadHandle, String offloadType)`

清理指定句柄下的缓存消息。

### `public Map<String, List<BaseMessage>> getAll()`

返回当前全部卸载缓存。

## 说明

- `OffloadMessageBufferTest` 覆盖了卸载、重载、清理和批量读取行为。
- `SessionModelContext.saveState()` 会直接把 `getAll()` 的结果持久化到 `offload_messages`。
