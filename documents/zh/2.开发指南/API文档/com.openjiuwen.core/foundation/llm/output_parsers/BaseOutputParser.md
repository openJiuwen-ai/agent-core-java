# com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser

## 类 BaseOutputParser

```java
public abstract class BaseOutputParser
```

输出解析器基类，约定格式说明与解析入口。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public abstract Object parse(Object inputs) throws Exception` | 将模型输出解析为结构化结果。 |
| `public abstract Iterator<Object> streamParse(Iterator<?> streamingInputs) throws Exception` | 执行 `streamParse` 公开能力。 |

## 说明

- 所有签名均以当前 Java 源码为准。
