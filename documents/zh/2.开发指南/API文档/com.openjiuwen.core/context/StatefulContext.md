# com.openjiuwen.core.context.StatefulContext

## interface StatefulContext

```java
public interface StatefulContext
```

`StatefulContext` 标记支持状态持久化的上下文实现，供 `ContextEngine.saveContexts()` 与 `loadStateFromSession()` 统一读写上下文状态。

## 方法

### `Map<String, Object> saveState()`

返回可序列化的内部状态映射。

### `void loadState(Map<String, Object> state)`

从先前保存的状态映射中恢复上下文。

**参数**

- `state`: 已持久化的状态数据。

## 说明

- `SessionModelContext` 会把消息列表写入 `messages` 键，把卸载缓存写入 `offload_messages` 键。
