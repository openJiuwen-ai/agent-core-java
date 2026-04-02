# com.openjiuwen.core.session.ProxySession

## 类 ProxySession

```java
public class ProxySession extends BaseSession
```

把 `BaseSession` 的访问器全部转发到底层 `stub` 的代理会话实现。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ProxySession()` | 创建一个初始 `stub = null` 的代理会话。 |
| `public ProxySession(BaseSession stub)` | 使用给定的底层 `BaseSession` 创建代理。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void setSession(BaseSession stub)` | 设置后续要转发到的底层 session。 |
| `public BaseSession getStub()` | 返回当前底层 session。 |
| `public Config config()` | 转发到底层 `stub.config()`。 |
| `public State state()` | 转发到底层 `stub.state()`。 |
| `public Object tracer()` | 转发到底层 `stub.tracer()`。 |
| `public StreamWriterManager streamWriterManager()` | 转发到底层 `stub.streamWriterManager()`。 |
| `public CallbackManager callbackManager()` | 转发到底层 `stub.callbackManager()`。 |
| `public String sessionId()` | 转发到底层 `stub.sessionId()`。 |
| `public Object checkpointer()` | 转发到底层 `stub.checkpointer()`。 |

## 说明

- `ProxySession` 本身不维护独立的配置或状态；调用前需要确保 `stub` 已经设置完成。
