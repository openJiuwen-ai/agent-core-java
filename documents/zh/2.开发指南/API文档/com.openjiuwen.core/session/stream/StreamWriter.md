# com.openjiuwen.core.session.stream.StreamWriter

## 类 StreamWriter

```java
public class StreamWriter<S extends StreamSchema>
```

负责校验并写出流数据的 writer。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public StreamWriter(StreamEmitter streamEmitter, Class<S> schemaType, Function<Map<String, Object>, S> validator)` | 使用目标发射器、schema 类型和校验函数创建 writer。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void write(Object streamData)` | 校验输入数据后写出；支持直接传入 schema 实例或 `Map`。 |

## 说明

- 相关测试：`StreamOutputFullTest`、`StreamOutputTest`、`WorkflowInteractionTest`。
- 具体写出逻辑会在发射器可用时调用 `emit(...)`；若发射器已关闭，则只记录告警日志。
