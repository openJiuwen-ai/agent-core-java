# com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer

## 类 InMemoryCheckpointer

```java
public class InMemoryCheckpointer extends Checkpointer
```

`InMemoryCheckpointer` 使用进程内 `Map` 保存 agent / workflow 状态，并用 `InMemoryStore` 保存 workflow 图状态，适合测试和单进程场景。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void preWorkflowExecute(BaseSession session, InteractiveInput inputs)` | 在 workflow 执行前恢复状态；若已有检查点但没有交互输入，则只在 `FORCE_DEL_WORKFLOW_STATE_KEY=true` 时强制清理，否则抛错。 |
| `public void postWorkflowExecute(BaseSession session, Object result, Exception exception)` | workflow 异常或中断时保存检查点，正常结束时清空 workflow 存储与图存储。 |
| `public void preAgentExecute(BaseSession session, Object inputs)` | 恢复 agent 状态；若提供输入，则写入 `Constant.INTERACTIVE_INPUT` 列表。 |
| `public void interruptAgentExecute(BaseSession session)` | 在交互中断时保存 agent 检查点。 |
| `public void postAgentExecute(BaseSession session)` | 在 agent 执行完成后保存 agent 检查点。 |
| `public boolean sessionExists(String sessionId)` | 判断该会话是否存在 agent 或 workflow 检查点。 |
| `public void release(String sessionId)` | 清除 workflow 图状态、workflow 存储和 agent 存储。 |
| `public Store graphStore()` | 返回内部维护的 `InMemoryStore` 图状态存储。 |

## 说明

- 相关测试：`InMemoryCheckpointerTest`。
- `preWorkflowExecute()` 会把 `InteractiveInput` 的原始输入恢复到 workflow 状态，或按节点恢复用户交互输入。
- `postWorkflowExecute()` 在检测到 `PregelConstants.TASK_STATUS_INTERRUPT` 时不会清理状态，而是保存检查点用于后续恢复。
