# com.openjiuwen.core.session.internal.WrappedSession

## 抽象类 WrappedSession

```java
public abstract class WrappedSession
```

`WrappedSession` 为 `BaseSession` 提供包装层 API。子类在此基础上补充状态访问、流输出、trace 与交互行为。

## 字段

| 签名 | 说明 |
| --- | --- |
| `protected final BaseSession inner` | 被包装的底层会话。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object getWorkflowConfig(String workflowId)` | 从 `inner.config()` 读取指定 workflow 的配置。 |
| `public Config.MetadataLike getAgentConfig()` | 从 `inner.config()` 读取 agent 配置并转为 `Config.MetadataLike`。 |
| `public Object getEnv(String key)` | 从 `inner.config()` 读取环境变量。 |
| `public BaseSession base()` | 返回底层 `BaseSession`。 |
| `public abstract String executableId()` | 返回当前包装器代表的可执行 ID。 |
| `public abstract String sessionId()` | 返回会话 ID。 |
| `public String userId()` | 默认返回空字符串。 |
| `public abstract void updateState(Map<String, Object> data)` | 更新局部状态。 |
| `public abstract Object getState(Object key)` | 读取局部状态。 |
| `public abstract void updateGlobalState(Map<String, Object> data)` | 更新全局状态。 |
| `public abstract Object getGlobalState(Object key)` | 读取全局状态。 |
| `public abstract StreamWriter<?> streamWriter()` | 返回普通输出流 writer。 |
| `public abstract StreamWriter<?> customWriter()` | 返回自定义输出流 writer。 |
| `public abstract void writeStream(Object data)` | 写普通输出流。 |
| `public abstract void writeCustomStream(Map<String, Object> data)` | 写自定义输出流。 |
| `public abstract void trace(Map<String, Object> data)` | 上报 trace 数据。 |
| `public abstract void traceError(Exception error)` | 上报 trace 错误。 |
| `public abstract void interact(Object value)` | 触发交互。 |
| `public void postRun()` | 默认空实现的后置钩子。 |
| `public void preRun(Map<String, Object> kwargs)` | 默认空实现的前置钩子。 |
| `public void release(String sessionId)` | 默认空实现的资源释放入口。 |

## 说明

- 相关测试：`TracerDecoratorTest`。
- `WrappedSession` 自身不直接操作状态或流，具体行为由子类补充。
