# com.openjiuwen.core.foundation.llm.output_parsers.JsonOutputParser

## 类 JsonOutputParser

```java
public class JsonOutputParser extends BaseOutputParser
```

从模型文本输出中提取 JSON 并转换为结构化结果。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private static class JsonStreamIterator implements Iterator<Object> {` | 保存 `Object` 相关状态或配置。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object parse(Object inputs) {` | 将模型输出解析为结构化结果。 |
| `public Iterator<Object> streamParse(Iterator<?> streamingInputs) {` | 执行 `streamParse` 公开能力。 |

## 说明

- 所有签名均以当前 Java 源码为准。
- `JsonOutputParserTest` 覆盖 JSON 块提取、反序列化与异常路径。
