# com.openjiuwen.core.retrieval.embedding.VLLMEmbedding

## 类 VLLMEmbedding

```java
public class VLLMEmbedding extends OpenAIEmbedding
```

vLLM 兼容多模态 embedding 客户端，通过 `extra_body.messages` 传递 system/user 消息。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static Map<String, Object> parseMultimodalInput(MultimodalDocument document, Map<String, Object> options)` | 将多模态文档转换为 vLLM 请求参数。 |
| `public CompletableFuture<List<Float>> embedMultimodal(MultimodalDocument document)` | 异步生成多模态向量。 |
| `public CompletableFuture<List<Float>> embedMultimodal(Object input, Map<String, Object> options)` | 异步处理任意输入对象。 |
| `public CompletableFuture<List<Float>> embedMultimodal(MultimodalDocument document, Map<String, Object> options)` | 异步处理带选项的多模态文档。 |
| `public List<Float> embedMultimodalSync(MultimodalDocument document)` | 同步生成多模态向量。 |
| `public List<Float> embedMultimodalSync(Object input, Map<String, Object> options)` | 同步处理任意输入对象。 |
| `public List<Float> embedMultimodalSync(MultimodalDocument document, Map<String, Object> options)` | 同步处理带选项的多模态文档。 |

## 说明

- 若 `options` 中未显式提供 `instruction` 键，会默认插入 `"Represent the user's input."`。
- 测试确认：显式传入 `instruction = null` 时不会写入 system message。
