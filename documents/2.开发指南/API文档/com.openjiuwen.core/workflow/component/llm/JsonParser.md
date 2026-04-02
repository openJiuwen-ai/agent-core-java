# com.openjiuwen.core.workflow.component.llm.JsonParser

## class JsonParser

```java
public final class JsonParser
```

LLM 响应 JSON 解析工具。

它会先清理 Markdown 代码块包裹，再使用 Jackson 把字符串解析为 `Map<String, Object>`；解析失败时会抛出组件参数错误。

## Methods

| Signature | Description |
| --- | --- |
| `public static Map<String, Object> parseJsonContent(String responseContent)` | Parse JSON content from LLM response, stripping markdown code blocks if present. |
