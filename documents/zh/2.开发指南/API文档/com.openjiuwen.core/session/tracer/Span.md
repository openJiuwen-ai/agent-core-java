# com.openjiuwen.core.session.tracer.Span

## 类 Span

```java
public class Span
```

`Span` 是 tracer 的基础数据对象，用于保存一次调用的公共追踪字段。

## 主要属性

| 属性 | 说明 |
| --- | --- |
| `traceId` | 当前 trace 的全局 ID。 |
| `startTime` / `endTime` | 调用的开始与结束时间。 |
| `inputs` / `outputs` | 输入与输出快照。 |
| `error` | 错误信息映射。 |
| `invokeId` | 当前调用 ID。 |
| `parentInvokeId` | 父调用 ID。 |
| `childInvokesId` | 子调用 ID 列表。 |
| `status` | 当前节点状态字符串。 |
| `onInvokeData` | 运行中附加的事件数据列表。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public Span()` | 创建空 span。 |
| `public Span(String traceId, String invokeId, String parentInvokeId)` | 使用基础标识信息创建 span。 |

## 主要方法

| 签名 | 说明 |
| --- | --- |
| `public void update(Map<String, Object> data)` | 用数据映射更新 span，支持若干 snake_case / camelCase 字段名。 |
| `public void appendChildInvokeId(String invokeId)` | 追加一个子调用 ID。 |
| `public Span snapshot()` | 创建当前 span 的深拷贝快照，避免已发送帧被后续更新污染。 |

## 说明

- 相关测试：`TracerTest`。
- 该类为上述属性提供标准 getter / setter。
- `snapshot()` 会深拷贝 `Map`、`List`、数组以及嵌套 `Span`。
