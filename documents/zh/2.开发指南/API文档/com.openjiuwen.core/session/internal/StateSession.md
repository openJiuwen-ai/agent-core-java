# com.openjiuwen.core.session.internal.StateSession

## 抽象类 StateSession

```java
public abstract class StateSession extends WrappedSession
```

`StateSession` 是 `WrappedSession` 的状态/流委托实现，负责把读写状态与写流操作转发给内部 `BaseSession`。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String executableId()` | 若内部会话是 `NodeSession`，返回其 `executableId()`；否则返回 `sessionId()`。 |
| `public String sessionId()` | 返回内部会话的 `sessionId()`。 |
| `public void updateState(Map<String, Object> data)` | 将局部状态更新委托给 `inner.state().update(data)`。 |
| `public Object getState(Object key)` | 从内部状态读取局部状态值。 |
| `public void updateGlobalState(Map<String, Object> data)` | 将全局状态更新委托给 `inner.state().updateGlobal(data)`。 |
| `public Object getGlobalState(Object key)` | 从内部状态读取全局状态值。 |
| `public StreamWriter<?> streamWriter()` | 返回输出流 writer；若不存在流管理器则返回 `null`。 |
| `public StreamWriter<?> customWriter()` | 返回自定义流 writer；若不存在流管理器则返回 `null`。 |
| `public void writeStream(Object data)` | 将数据写入普通输出流。 |
| `public void writeCustomStream(Map<String, Object> data)` | 将数据写入自定义输出流。 |

## 说明

- `writeStream()` 与 `writeCustomStream()` 都在 writer 缺失时静默跳过。
