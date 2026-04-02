# com.openjiuwen.core.retrieval.common.MultimodalDocument

## class MultimodalDocument

```java
public class MultimodalDocument extends Document
```

Multimodal document model.

## Constructors

| Signature | Description |
| --- | --- |
| `public MultimodalDocument()` | Create a new `MultimodalDocument` instance. |
| `public MultimodalDocument(String id, String text, Map<String, Object> metadata)` | Create a new `MultimodalDocument` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<Map<String, Object>> getContent()` | Return the content. |
| `public MultimodalDocument addField(String kind, Object data, Object filePath, Object dataId)` | Add field. |
| `public MultimodalDocument addField(String kind, Path filePath)` | Add field. |

## Notes

- Related tests: `RetrievalCoreTest.java`, `VLLMEmbeddingTest.java`.
