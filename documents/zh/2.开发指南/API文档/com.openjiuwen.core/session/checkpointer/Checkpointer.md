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
| `public boolean stepPaused(BaseSession session, StepCursor cursor, Map<String, Object> pauseInfo)` | 在步边界落盘用户暂停游标与元数据；带 seq 单调守卫，返回是否成功落盘。 |
| `public StepCursor lastPausedStep(String sessionId)` | 返回指定会话的暂停游标，无则返回 `null`。 |
| `public void resumePaused(StepCursor cursor)` | 按游标携带的 `sessionId` 显式恢复暂停会话；游标缺 `sessionId` 抛 `IllegalStateException`。 |
| `public void resumePaused(String sessionId)` | 按会话 id 显式恢复暂停会话；无暂停游标时抛 `IllegalStateException`。 |
| `public abstract boolean sessionExists(String sessionId)` | 判断指定 `sessionId` 是否存在任何检查点记录。 |
| `public abstract void release(String sessionId)` | 释放指定会话的全部检查点资源。 |
| `public abstract Store graphStore()` | 返回 workflow 图状态使用的 `Store` 实现。 |
| `public static String buildKey(String... parts)` | 用 `:` 连接多个片段生成存储键。 |
| `public static String buildKeyWithNamespace(String sessionId, String namespace, String entityId, String... suffixes)` | 生成 `session:namespace:entityId[:suffix...]` 结构的命名键。 |

## 说明

- 相关测试：`InMemoryCheckpointerTest`、`StepPauseResumeTest`。
- 内部辅助逻辑会优先识别 `WorkflowSession` 与 `NodeSession`，因此 workflow 与节点场景共享同一套键规则。
- 暂停/恢复族方法在 `InMemoryCheckpointer`、`PersistenceCheckpointer` 与 Redis 扩展三后端行为一致：恢复按会话身份精确定位、异常抛裸 `IllegalStateException`、恢复写回读取时的原始 blob 键。语义细节见[检查点机制：用户暂停](../../../高阶用法/Checkpointer检查点机制.md#断点续跑与用户暂停paused0117)。
