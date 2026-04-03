# com.openjiuwen.core.graph.pregel.BarrierRouter

## 类 BarrierRouter

```java
public class BarrierRouter implements IRouter
```

向 barrier channel 发送 `BarrierMessage` 的路由器。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `targets` | `List<String>` | `-` | 当前路由器要写入的 barrier channel 键列表。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public BarrierRouter(List<String> targets)` | 基于目标 barrier 键列表创建 `BarrierRouter`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public List<Message> dispatch(String sourceNode)` | 针对每个目标键生成一条以 `sourceNode` 为 sender 的 `BarrierMessage`。 |

## 相关测试

- `PregelTest`
