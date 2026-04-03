# com.openjiuwen.core.context.OffloadCapableContext

## interface OffloadCapableContext

```java
public interface OffloadCapableContext
```

`OffloadCapableContext` 用于标记支持卸载消息的上下文实现，使任意 `ModelContext` 子类都能参与卸载流程，而不必绑定到具体实现。

## 方法

### `void offloadMessages(String offloadHandle, List<BaseMessage> messages)`

把一组消息按给定句柄卸载到上下文实现管理的缓冲区中。

**参数**

- `offloadHandle`: 卸载内容的唯一标识。
- `messages`: 待卸载的消息列表。

## 说明

- `SessionModelContext` 是当前任务范围内实现该接口的具体类型。
