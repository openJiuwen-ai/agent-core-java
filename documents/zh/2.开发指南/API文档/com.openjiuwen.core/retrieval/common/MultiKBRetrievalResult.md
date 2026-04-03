# com.openjiuwen.core.retrieval.common.MultiKBRetrievalResult

## 类 MultiKBRetrievalResult

```java
public class MultiKBRetrievalResult extends RetrievalResult
```

多知识库聚合检索结果，除基础检索结果外还保存原始分数、缩放分数与来源知识库列表。

## 说明

- `kbIds` 会复制保存，默认可为空列表。
- `SimpleKnowledgeBase.retrieveMultiKbWithSource(...)` 与 `GraphKnowledgeBase` 聚合检索会返回该类型。
