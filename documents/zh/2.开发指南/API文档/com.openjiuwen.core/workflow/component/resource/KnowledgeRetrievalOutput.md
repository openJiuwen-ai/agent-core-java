# com.openjiuwen.core.workflow.component.resource.KnowledgeRetrievalOutput

## 类 KnowledgeRetrievalOutput

```java
public class KnowledgeRetrievalOutput
```

知识检索输出模型，封装结果列表、上下文和元数据结果。

## 字段

| 签名 | 说明 |
| --- | --- |
| `private List<String> results = new ArrayList<>()` | 检索结果文本列表。 |
| `private String context = ""` | 拼接后的检索上下文。 |
| `private List<Map<String, Object>> resultsWithMetadata` | 带元数据的检索结果。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public KnowledgeRetrievalOutput()` | 创建 `KnowledgeRetrievalOutput` 实例。 |
| `public KnowledgeRetrievalOutput(List<String> results, String context, List<Map<String, Object>> resultsWithMetadata)` | 创建 `KnowledgeRetrievalOutput` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public List<String> getResults()` | 返回检索结果文本列表。 |
| `public void setResults(List<String> results)` | 设置检索结果文本列表。 |
| `public String getContext()` | 返回拼接后的检索上下文。 |
| `public void setContext(String context)` | 设置拼接后的检索上下文。 |
| `public List<Map<String, Object>> getResultsWithMetadata()` | 返回带元数据的检索结果。 |
| `public void setResultsWithMetadata(List<Map<String, Object>> resultsWithMetadata)` | 设置带元数据的检索结果。 |
| `public Map<String, Object> toMap()` | 转换为 `Map` 表示。 |
| `public static KnowledgeRetrievalOutput fromMap(Map<String, Object> map)` | 根据 `Map` 构造 `KnowledgeRetrievalOutput` 实例。 |
