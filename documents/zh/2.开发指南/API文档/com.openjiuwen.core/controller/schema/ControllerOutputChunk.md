# com.openjiuwen.core.controller.schema.ControllerOutputChunk

## class ControllerOutputChunk

```java
public class ControllerOutputChunk extends OutputSchema
```

`ControllerOutputChunk` 是控制器写入会话流的标准输出块，继承自 `OutputSchema`，并在 payload 中承载 `ControllerOutputPayload`。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `CONTROLLER_OUTPUT_TYPE` | `String` | `"controller_output"` | 控制器输出块的固定 `type`。 |
| `controllerPayload` | `ControllerOutputPayload` | `null` | 业务载荷。 |
| `lastChunk` | `boolean` | `false` | 是否为本轮流输出的最后一个块。 |

## 构造方法

| 签名 | 说明 |
|---|---|
| `ControllerOutputChunk()` | 创建默认输出块，并把 `type` 设为 `controller_output`。 |
| `ControllerOutputChunk(int index, ControllerOutputPayload payload)` | 设置块序号和载荷，同时把 `payload` 同步写入父类。 |
| `ControllerOutputChunk(int index, ControllerOutputPayload payload, boolean lastChunk)` | 在前一个构造基础上额外标记是否为最后一个块。 |

## 说明

- `setControllerPayload()` 除了更新 `controllerPayload`，还会同步调用父类的 `setPayload()`，确保会话流消费方能看到同一份数据。
- `TaskScheduler` 会直接把执行器产出的该类型对象写入 `AgentSessionApi.writeStream()`。
