# com.openjiuwen.core.session.AgentSessionApi

## 类 AgentSessionApi

```java
public class AgentSessionApi implements Session
```

面向外部调用的 agent 会话门面，封装内部 `AgentSession`，统一暴露环境变量、全局状态、流式输出与交互入口。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public AgentSessionApi(String sessionId, Map<String, Object> envs, Object card)` | 使用给定的 `sessionId`、环境变量和 `card` 创建会话；`sessionId` 为空时自动生成。 |
| `public AgentSessionApi(String sessionId, Map<String, Object> envs, Object card, List<StreamMode> streamModes)` | 在基础构造参数之外显式指定启用的 `streamModes`。 |
| `public AgentSessionApi(String sessionId, Map<String, Object> envs)` | - |
| `public AgentSessionApi(String sessionId)` | - |
| `public AgentSessionApi()` | - |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getSessionId()` | 返回当前对外暴露的会话 ID。 |
| `public Object getEnv(String key)` | 读取环境变量；不存在时返回 `null`。 |
| `public Object getEnv(String key, Object defaultValue)` | 读取环境变量；缺失时回退到 `defaultValue`。 |
| `public Map<String, Object> getEnvs()` | 返回当前环境变量快照。 |
| `public String getAgentId()` | 返回内部 `AgentSession` 持有的 agent 标识。 |
| `public String getAgentName()` | 返回内部 agent 名称。 |
| `public String getAgentDescription()` | 返回内部 agent 描述。 |
| `public void updateState(Map<String, Object> data)` | 把数据写入全局状态分区。 |
| `public Object getState(Object key)` | 按键读取全局状态。 |
| `public Object getState(String key)` | `String` 版本的全局状态读取重载。 |
| `public Map<String, Object> dumpState()` | 导出内部状态树快照。 |
| `public void writeStream(Object data)` | 把一个输出帧写入 output writer。 |
| `public void writeCustomStream(Map<String, Object> data)` | 把自定义 `Map` 载荷写入 custom writer。 |
| `public void writeTraceStream(TraceSchema data)` | 把 `TraceSchema` 载荷写入 trace writer。 |
| `@Deprecated public Iterator<Object> streamIterator()` | 以阻塞迭代器方式消费流输出。 |
| `public void streamOutput(java.util.function.Consumer<Object> consumer)` | 以回调方式逐帧消费流输出。 |
| `public void preRun(Object inputs)` | 触发 checkpointer 的 pre-agent hook。 |
| `public void postRun()` | 关闭 stream emitter，并触发 checkpointer 的 post-agent hook。 |
| `public WorkflowSessionApi createWorkflowSession()` | 基于当前内部 `AgentSession` 创建一个工作流会话。 |
| `public void interact(Object value)` | 惰性创建 `SimpleAgentInteraction` 并等待用户输入。 |
| `public AgentSession getInner()` | 返回底层内部 `AgentSession`。 |
| `public static AgentSessionApi create(String sessionId, Map<String, Object> envs, Object card)` | 以静态工厂方式创建 agent 会话。 |
| `public static AgentSessionApi create(String sessionId, Map<String, Object> envs, Object card, List<StreamMode> streamModes)` | - |

## 说明

- 相关测试：`AgentSessionApiTest`、`SessionTest`、`TracerDecoratorTest`。
- 当构造参数中的 `sessionId` 为 `null` 时，源码会通过 `UUID.randomUUID()` 自动生成会话标识。
- `preRun()` 与 `postRun()` 都带有幂等保护，重复调用会直接返回。
