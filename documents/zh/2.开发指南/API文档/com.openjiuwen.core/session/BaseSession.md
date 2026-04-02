# com.openjiuwen.core.session.BaseSession

## 类 BaseSession

```java
public abstract class BaseSession implements Session
```

为具体 session 实现统一暴露配置、状态、流式输出、回调与 tracing 子系统的抽象基类。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public abstract Config config()` | 返回当前 session 的配置对象。 |
| `public abstract State state()` | 返回当前 session 的状态对象。 |
| `public abstract Object tracer()` | 返回 tracing 实例；未配置时可为 `null`。 |
| `public abstract StreamWriterManager streamWriterManager()` | 返回流 writer 管理器。 |
| `public abstract CallbackManager callbackManager()` | 返回回调管理器。 |
| `public abstract String sessionId()` | 返回唯一会话标识。 |
| `public abstract Object checkpointer()` | 返回 checkpointer 实例；未配置时可为 `null`。 |
| `public Object actorManager()` | 返回 actor manager；默认实现返回 `null`。 |
| `public String getSessionId()` | 作为 `Session` 兼容层，转发到 `sessionId()`。 |
| `public Object getState(String key)` | 通过 `state()` 读取指定键对应的状态。 |
| `public void updateState(java.util.Map<String, Object> stateMap)` | 通过 `state()` 更新状态。 |
| `public void setCurrentOperatorId(String operatorId)` | 设置当前 operator 标识。 |
| `public String getCurrentOperatorId()` | 返回当前 operator 标识。 |
| `public void close()` | 关闭会话并释放资源；默认实现为空操作。 |

## 说明

- `BaseSession` 通过实现最小 `Session` 接口，为 `ContextEngine` 等只依赖窄接口的调用方保留兼容入口。
