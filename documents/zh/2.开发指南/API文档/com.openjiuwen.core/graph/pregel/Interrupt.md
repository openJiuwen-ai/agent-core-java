# com.openjiuwen.core.graph.pregel.Interrupt

## 类 Interrupt

```java
public class Interrupt
```

图执行过程中的中断值封装。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `value` | `Object` | `-` | 中断时携带的实际载荷。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public Interrupt(Object value)` | 基于指定载荷创建 `Interrupt`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object getValue()` | 返回当前中断载荷 `value`。 |
| `public String toString()` | 以 `Interrupt{value=...}` 形式返回调试字符串。 |

## 相关测试

- `PregelTest`
- `TaskExecutorPoolTest`
