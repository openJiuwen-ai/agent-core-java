# com.openjiuwen.core.runner.Runner

## class Runner

```java
public final class Runner
```

`Runner` 是全局单例门面，统一代理 `RunnerImpl` 的生命周期、资源管理器访问以及 workflow / agent / agent group 执行入口。

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `GLOBAL_RUNNER` | `RunnerImpl` | `new RunnerImpl("global", RunnerConfig.DEFAULT)` | The global runner instance. |

## 方法

| Signature | Description |
| --- | --- |
| `public static ResourceMgr resourceMgr()` | Get the resource manager for workflow, agent, agent_group, tool, model, prompt... |
| `public static LocalMessageQueue pubsub()` | Get the local message queue for publish/subscribe communication. |
| `public static CallbackFramework callbackFramework()` | Get the callback framework. |
| `public static MessageQueueBase distPubsub()` | Get the distributed message queue for cross-process communication. |
| `public static ReplyTopicSubscription systemReplySub()` | Get the reply topic subscription for distributed mode. |
| `public static void setConfig(RunnerConfig config)` | Set the runner configuration with provided config object. |
| `public static RunnerConfig getConfig()` | Retrieve the current runner configuration. |
| `public static boolean start()` | Start the runner and its associated components, such as message queue. |
| `public static boolean stop()` | Stop the runner and clean up resources. |
| `public static Object runWorkflow(Object workflow, Object inputs, Object session, ModelContext context)` | Execute a workflow with given inputs. |
| `public static Object runWorkflow(Object workflow, Object inputs, Object session, ModelContext context, Map<String, Object> envs)` | Execute a workflow with given inputs and environment overrides. |
| `public static Iterator<WorkflowChunk> runWorkflowStreaming(Object workflow, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes)` | Execute a workflow with streaming output support. |
| `public static Iterator<WorkflowChunk> runWorkflowStreaming(Object workflow, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes, Map<String, Object> envs)` | Execute a workflow with streaming output support and environment overrides. |
| `public static Object runAgent(Object agent, Object inputs, Object session, ModelContext context)` | Execute a single agent with given inputs. |
| `public static Object runAgent(Object agent, Object inputs, Object session, ModelContext context, Map<String, Object> envs)` | Execute a single agent with given inputs and environment overrides. |
| `public static Iterator<Object> runAgentStreaming(Object agent, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes)` | Execute a single agent with streaming output support. |
| `public static Iterator<Object> runAgentStreaming(Object agent, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes, Map<String, Object> envs)` | Execute a single agent with streaming output support and environment overrides. |
| `public static Object runAgentGroup(Object agentGroup, Object inputs, Object session, ModelContext context)` | Execute a group of agents with given inputs. |
| `public static Object runAgentGroup(Object agentGroup, Object inputs, Object session, ModelContext context, Map<String, Object> envs)` | Execute a group of agents with given inputs and environment overrides. |
| `public static Iterator<Object> runAgentGroupStreaming(Object agentGroup, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes)` | Execute a group of agents with streaming output support. |
| `public static Iterator<Object> runAgentGroupStreaming(Object agentGroup, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes, Map<String, Object> envs)` | Execute a group of agents with streaming output support and environment overrides. |
| `public static void release(String sessionId)` | Release resources associated with a session. |

## 相关测试

- `RunnerTest`
