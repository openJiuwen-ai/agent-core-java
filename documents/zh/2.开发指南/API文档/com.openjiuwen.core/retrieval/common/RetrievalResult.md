# com.openjiuwen.core.retrieval.common.RetrievalResult

## 类 RetrievalResult

```java
public class RetrievalResult
```

面向调用方的检索结果模型，保存文本、分数、元数据以及可选的文档和分块标识。

## 说明

- `text` 不能为空。
- `metadata` 会复制保存，`docId` 与 `chunkId` 可为空。
- `StandardReranker` 会直接更新该对象的 `score` 字段并按分数重排。
