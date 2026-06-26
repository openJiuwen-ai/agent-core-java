# com.openjiuwen.core.session.NodeSessionApi

## 类 NodeSessionApi

```java
public class NodeSessionApi
```

面向工作流节点的对外会话门面，封装内部 `NodeSession`，把状态、流式输出、tracing 与交互能力收敛到单一入口。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public NodeSessionApi(NodeSession session, boolean streamMode)` | 使用给定 `NodeSession` 创建门面，并指定当前是否运行在流式模式。 |
| `public NodeSessionApi(NodeSession session)` | 使用默认 `streamMode = false` 创建门面。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getWorkflowId()` | 返回所属工作流 ID。 |
| `public String getComponentId()` | 返回当前节点 ID。 |
| `public String getComponentType()` | 返回当前节点类型。 |
| `public String getComponentDescrip()` | 返回基于工作流/节点 ID 组合出的描述字符串。 |
| `public void trace(Map<String, Object> data)` | 记录当前节点的 tracing 数据；`skipTrace()` 为真时直接返回。 |
| `public void traceError(Exception error)` | 记录当前节点异常；`skipTrace()` 为真时直接返回。 |
| `public<T> T interact(Object value)` | 触发用户交互并等待新的输入结果。 |
| `public<T> T userLatestInput(Object value)` | 读取最近一次用户输入，不强制等待新的排队输入。 |
| `public String getExecutableId()` | 返回当前可执行单元 ID。 |
| `public String getSessionId()` | 返回内部 session ID。 |
| `public void updateState(Map<String, Object> data)` | 更新当前节点的组件状态。 |
| `public Object getState(Object key)` | 读取当前节点的组件状态。 |
| `public void updateGlobalState(Map<String, Object> data)` | 更新全局状态分区。 |
| `public Object getGlobalState(Object key)` | 读取全局状态分区。 |
| `public Map<String, Object> dumpState()` | 导出底层状态树快照。 |
| `public void writeStream(Object data)` | 向 output writer 写入一帧输出。 |
| `public void writeCustomStream(Map<String, Object> data)` | 向 custom writer 写入一帧自定义输出。 |
| `public Object getCallbackManager()` | 返回内部回调管理器。 |
| `public Object getEnv(String key)` | 读取配置中的环境变量。 |
| `public NodeSession getInner()` | 返回底层内部 `NodeSession`。 |

## 说明

- 相关测试：`AgentSessionApiTest`、`SessionTest`。
- 当 `streamMode = true` 时，`interact(...)` 与 `userLatestInput(...)` 会抛出 `COMP_SESSION_INTERACT_ERROR`，禁止在流式收集/转换阶段发起交互。
- 交互返回结果后，源码会调用 `TracerWorkflowUtils.traceComponentInteractiveInputs(...)` 记录输入轨迹。
