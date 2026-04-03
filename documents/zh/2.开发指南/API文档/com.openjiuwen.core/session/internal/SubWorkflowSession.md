# com.openjiuwen.core.session.internal.SubWorkflowSession

## 类 SubWorkflowSession

```java
public class SubWorkflowSession extends NodeSession
```

`SubWorkflowSession` 用于 workflow 嵌套 workflow 的场景，在节点会话基础上附加子 workflow ID、嵌套深度与独立的 `ActorManager`。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public SubWorkflowSession(BaseSession session, String nodeId, String nodeType, String workflowId)` | 基于父会话创建子 workflow 节点会话，并记录子 workflow ID。 |
| `public SubWorkflowSession(BaseSession session, String nodeId, String workflowId)` | 省略节点类型的便捷构造方法。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String workflowId()` | 返回子 workflow ID。 |
| `public int workflowNestingDepth()` | 返回子 workflow 的嵌套深度。 |
| `public ActorManager actorManager()` | 返回绑定的 `ActorManager`。 |
| `public void setActorManager(ActorManager actorManager)` | 设置 `ActorManager`。 |
| `public void close()` | 关闭子 workflow；若存在 `ActorManager`，会调用 `shutdown()`。 |

## 说明

- 构造时会优先把 `NodeSession.parent()` 作为真正父会话，以避免把子 workflow 再包在节点会话之下。
