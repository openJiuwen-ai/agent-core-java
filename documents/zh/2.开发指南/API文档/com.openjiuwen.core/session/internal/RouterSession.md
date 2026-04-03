# com.openjiuwen.core.session.internal.RouterSession

## 类 RouterSession

```java
public class RouterSession extends StateSession
```

`RouterSession` 面向路由、分支等不需要完整会话能力的组件。除 trace 相关入口外，大多数操作都被实现为 no-op 或直接返回 `null`。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public RouterSession(BaseSession inner)` | 以内部会话 `inner` 创建路由包装器。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void interact(Object value)` | 空操作，不触发交互。 |
| `public void trace(Map<String, Object> data)` | 调用 `TracerWorkflowUtils.trace(inner, data)` 上报节点 trace 数据。 |
| `public void traceError(Exception error)` | 调用 `TracerWorkflowUtils.traceError(inner, error)` 上报错误。 |
| `public StreamWriter<?> streamWriter()` | 始终返回 `null`。 |
| `public StreamWriter<?> customWriter()` | 始终返回 `null`。 |
| `public void writeStream(Object data)` | 空操作，不写普通流。 |
| `public void writeCustomStream(Map<String, Object> data)` | 空操作，不写自定义流。 |
| `public void updateGlobalState(Map<String, Object> data)` | 空操作，不更新全局状态。 |
| `public void updateState(Map<String, Object> data)` | 空操作，不更新局部状态。 |
| `public Object getWorkflowConfig(String workflowId)` | 始终返回 `null`。 |
| `public Config.MetadataLike getAgentConfig()` | 始终返回 `null`。 |
| `public Object getEnv(String key)` | 始终返回 `null`。 |
| `public BaseSession base()` | 始终返回 `null`。 |

## 说明

- 该实现不会暴露底层 `inner`，因此与 `WrappedSession.base()` 的默认行为不同。
