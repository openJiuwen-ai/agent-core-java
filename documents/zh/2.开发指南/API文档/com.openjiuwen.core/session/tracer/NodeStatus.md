# com.openjiuwen.core.session.tracer.NodeStatus

## 枚举 NodeStatus

```java
public enum NodeStatus
```

NodeStatus 描述 workflow 节点在 trace 中的运行状态。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回该状态对应的字符串值。 |

## 枚举值

| 枚举值 | 说明 |
| --- | --- |
| `START` | 节点刚开始执行。 |
| `FINISH` | 节点已经完成。 |
| `RUNNING` | 节点已有运行中事件但尚未结束。 |
| `INTERRUPTED` | 节点被中断。 |
| `ERROR` | 节点发生错误。 |
