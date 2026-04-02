# com.openjiuwen.core.session.checkpointer.Checkpointer

## 抽象类 Checkpointer

```java
public abstract class Checkpointer
```

`Checkpointer` 定义了 session 检查点的统一生命周期接口。workflow 执行前后、agent 执行前后以及交互中断后的状态恢复与持久化都通过这个抽象类协调完成。

## 字段

| 签名 | 说明 |
| --- | --- |
| `public static final String SESSION_NAMESPACE_AGENT = "agent"` | agent 检查点命名空间。 |
| `public static final String SESSION_NAMESPACE_WORKFLOW = "workflow"` | workflow 检查点命名空间。 |
| `public static final String WORKFLOW_NAMESPACE_GRAPH = "workflow-graph"` | workflow 图状态的命名空间。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static String getThreadId(BaseSession session)` | 返回 `sessionId:workflowId` 形式的线程标识。 |
| `public abstract void preWorkflowExecute(BaseSession session, InteractiveInput inputs)` | 在 workflow 执行前恢复状态，并按需要处理交互输入。 |
| `public abstract void postWorkflowExecute(BaseSession session, Object result, Exception exception)` | 在 workflow 执行结束后，根据完成、异常或中断结果保存或清理检查点。 |
| `public abstract void preAgentExecute(BaseSession session, Object inputs)` | 在 agent 执行前恢复状态，并注入交互输入。 |
| `public abstract void interruptAgentExecute(BaseSession session)` | 在 agent 因交互而中断时保存检查点。 |
| `public abstract void postAgentExecute(BaseSession session)` | 在 agent 正常执行结束后保存检查点。 |
| `public abstract boolean sessionExists(String sessionId)` | 判断指定 `sessionId` 是否存在任何检查点记录。 |
| `public abstract void release(String sessionId)` | 释放指定会话的全部检查点资源。 |
| `public abstract Store graphStore()` | 返回 workflow 图状态使用的 `Store` 实现。 |
| `public static String buildKey(String... parts)` | 用 `:` 连接多个片段生成存储键。 |
| `public static String buildKeyWithNamespace(String sessionId, String namespace, String entityId, String... suffixes)` | 生成 `session:namespace:entityId[:suffix...]` 结构的命名键。 |

## 说明

- 相关测试：`InMemoryCheckpointerTest`。
- 内部辅助逻辑会优先识别 `WorkflowSession` 与 `NodeSession`，因此 workflow 与节点场景共享同一套键规则。
