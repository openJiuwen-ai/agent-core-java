# com.openjiuwen.core.session.tracer.TracerDecorator

## 类 TracerDecorator

```java
public final class TracerDecorator
```

`TracerDecorator` 为 model、tool 和 workflow 创建带 trace 的动态代理，并提供一个通用的同步调用包装器。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static<T> T decorateModelWithTrace(T model, Object agentSession)` | 若对象可代理且会话暴露 `tracer()` / `span()`，则为 model 的 `invoke` / `stream` 调用添加 trace。 |
| `public static<T> T decorateToolWithTrace(T tool, Object agentSession)` | 为 tool 的 `invoke` 调用添加 trace；若对象没有接口或会话不支持 trace，则原样返回。 |
| `public static<T> T decorateWorkflowWithTrace(T workflow, Object agentSession)` | 为 workflow 的 `invoke` / `stream` 调用添加 trace，并尝试从 `card` 提取元数据。 |
| `public static Object trace(Object session, InvokeType invokeType, Map<String, Object> instanceInfo, BiFunction<Object[], Map<String, Object>, Object> callable, Object[] args, Map<String, Object> kwargs)` | 用同步方式包装一次调用，在开始、结束和异常时分别触发对应 trace 事件。 |

## 说明

- 相关测试：`TracerDecoratorTest`。
- 动态代理只适用于实现了接口的对象；没有接口时 `createTracingProxy()` 会直接返回原对象。
- `decorateToolWithTrace()` 已验证同时支持 `AgentSessionApi` 包装器和直接传入 `AgentSession`。
