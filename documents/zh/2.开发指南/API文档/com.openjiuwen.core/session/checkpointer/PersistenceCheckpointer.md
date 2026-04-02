# com.openjiuwen.core.session.checkpointer.PersistenceCheckpointer

## 类 PersistenceCheckpointer

```java
public class PersistenceCheckpointer extends Checkpointer
```

`PersistenceCheckpointer` 以 `BaseKVStore` 为底层存储，把 agent 状态、workflow 状态与图状态分别委托给内部存储类管理。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public PersistenceCheckpointer(BaseKVStore kvStore)` | 使用指定 `BaseKVStore` 初始化持久化检查点及其内部存储。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void preAgentExecute(BaseSession session, Object inputs)` | 从持久化存储恢复 agent 状态，并按需要写入交互输入。 |
| `public void interruptAgentExecute(BaseSession session)` | 在 agent 中断时保存检查点。 |
| `public void postAgentExecute(BaseSession session)` | 在 agent 完成时保存检查点。 |
| `public void preWorkflowExecute(BaseSession session, InteractiveInput inputs)` | 恢复 workflow 状态；若没有交互输入但已存在检查点，只在强制删除开关打开时清理，否则抛错。 |
| `public void postWorkflowExecute(BaseSession session, Object result, Exception exception)` | workflow 异常或中断时保存状态，正常结束时清除 workflow 与图检查点。 |
| `public boolean sessionExists(String sessionId)` | 通过 `kvStore.getByPrefix(sessionId + ":")` 判断会话是否有任何持久化记录。 |
| `public void release(String sessionId)` | 删除该会话前缀下的全部持久化资源。 |
| `public void release(String sessionId, String agentId)` | 仅释放某个 agent 的检查点；`agentId` 为空时退化为释放整个会话。 |
| `public Store graphStore()` | 返回持久化图状态存储 `PersistenceGraphStore`。 |

## 说明

- 该实现把 agent 状态交给 `PersistenceAgentStorage`，workflow 状态交给 `PersistenceWorkflowStorage`，图状态交给 `PersistenceGraphStore`。
- `release(String sessionId, String agentId)` 只清理 agent 状态，不会单独清理 workflow 状态。
