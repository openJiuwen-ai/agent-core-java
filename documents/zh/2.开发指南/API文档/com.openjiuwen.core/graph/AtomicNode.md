# com.openjiuwen.core.graph.AtomicNode

## 抽象类 AtomicNode

```java
public abstract class AtomicNode
```

执行原子节点的通用基类，负责校验 session、调用内部逻辑并提交组件状态。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object atomicInvoke(Map<String, Object> kwargs)` | 执行原子节点调用；要求 `kwargs` 中包含 `session`，成功后提交 `WorkflowStateCollection`。 |
