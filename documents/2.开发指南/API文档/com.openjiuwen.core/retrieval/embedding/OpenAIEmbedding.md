# com.openjiuwen.core.retrieval.embedding.OpenAIEmbedding

## 类 OpenAIEmbedding

```java
public class OpenAIEmbedding extends APIEmbedding
```

OpenAI 兼容 embedding 客户端，支持 `data[].embedding` 数值数组与 base64 float32 向量响应。

## 说明

- 初始化时会把 `baseUrl` 尾部的 `/embeddings` 去掉，只保留基础地址。
- 如果构造时显式提供 `dimension`，`getDimension()` 会直接返回该值；否则复用父类的懒加载逻辑。
- 测试确认：能够解析 OpenAI 风格数组结果，也能解析 base64 向量结果。
