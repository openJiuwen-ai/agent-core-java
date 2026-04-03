# com.openjiuwen.core.graph.pregel.GraphInterrupt

## 类 GraphInterrupt

```java
public class GraphInterrupt extends Exception
```

图执行被中断时抛出的异常。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `value` | `Interrupt` | `-` | 本次中断附带的 `Interrupt` 载荷。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public GraphInterrupt()` | 创建不带载荷的 `GraphInterrupt`。 |
| `public GraphInterrupt(Interrupt value)` | 基于指定中断值创建 `GraphInterrupt`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Interrupt getValue()` | 返回当前中断载荷 `value`。 |

## 相关测试

- `PregelTest`
- `TaskExecutorPoolTest`
