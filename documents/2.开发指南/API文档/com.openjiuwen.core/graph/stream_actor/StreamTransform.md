# com.openjiuwen.core.graph.stream_actor.StreamTransform

## 类 StreamTransform

```java
public class StreamTransform
```

提供两种流式输入转换方式：调用自定义函数，或按 schema 从消息中提取子结构。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object getByDefinedTransformer(Object originMessage, Object transformer)` | 当 `transformer` 是 `Function` 时执行转换；否则直接返回原始消息。 |
| `public Object getByDefaultTransformer(Object originMessage, Object streamInputsSchema)` | 当 `originMessage` 是 `Map` 且 schema 非空时调用 `SessionUtils.getBySchema(...)` 提取结构；否则返回原始消息。 |
