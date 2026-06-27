# com.openjiuwen.core.session.checkpointer.Storage

## 抽象类 Storage

```java
public abstract class Storage
```

`Storage` 抽象了检查点存储的最小能力，供 agent 与 workflow 的具体存储实现复用。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public abstract void save(BaseSession session)` | 保存指定会话的状态。 |
| `public abstract void recover(BaseSession session, InteractiveInput inputs)` | 将存储中的状态恢复到会话，并按需要处理恢复输入。 |
| `public void recover(BaseSession session)` | 不带交互输入地恢复状态，等价于 `recover(session, null)`。 |
| `public abstract void clear(String id)` | 按实现定义清理对应 ID 的状态。 |
| `public abstract boolean exists(BaseSession session)` | 判断该会话是否已有可恢复状态。 |

## 说明

- `clear(String id)` 的 `id` 在不同实现中可能代表 `agentId`、`workflowId` 或其他命名实体。
